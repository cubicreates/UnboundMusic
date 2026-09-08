/*
 * Package: server
 * File: proxy.go
 * Purpose: Streaming reverse proxy with transparent on-disk LRU caching for zero-data repeat playback.
 * Subsystem: Streaming Reverse Proxy & Audio Caching
 * Concurrency: Thread-safe HTTP streaming proxy with Range 206 partial content support.
 */

package server

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
	"sync"
	"time"
)

var (
	proxyCacheMu sync.Mutex
)

const (
	MaxAudioCacheSizeBytes = int64(1024 * 1024 * 1024) // 1 GB audio cache quota
)

// getAudioCacheDir resolves or creates the canonical audio cache directory.
func (s *Server) getAudioCacheDir() string {
	dir := filepath.Join(os.TempDir(), "unbound_audio_cache")
	if s.provisioner != nil {
		if tree, err := s.provisioner.ProvisionLayout(); err == nil && tree.CachePath != "" {
			dir = filepath.Join(tree.CachePath, "audio")
		}
	} else if s.cfg.AppStorageRoot != "" {
		dir = filepath.Join(s.cfg.AppStorageRoot, "cache", "audio")
	}
	_ = os.MkdirAll(dir, 0755)
	return dir
}

// handleProxyStream intercepts audio streaming requests, serving from local cache or streaming & spooling to disk.
func (s *Server) handleProxyStream(w http.ResponseWriter, r *http.Request) {
	defer func() {
		if rec := recover(); rec != nil {
			log.Printf("[PANIC RECOVERED] handleProxyStream: %v\nStack trace:\n%s", rec, string(debug.Stack()))
			writeError(w, http.StatusInternalServerError, "internal streaming proxy error")
		}
	}()

	videoID := strings.TrimSpace(r.URL.Query().Get("id"))
	if videoID == "" {
		videoID = strings.TrimSpace(r.URL.Query().Get("videoId"))
	}
	if videoID == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' is required")
		return
	}

	cacheDir := s.getAudioCacheDir()
	cachedFile := filepath.Join(cacheDir, videoID+".opus")

	// 1. Check if complete audio file exists in local cache
	if fi, err := os.Stat(cachedFile); err == nil && fi.Size() > 0 {
		// Cache Hit: serve directly via http.ServeFile with full 206 Range support
		w.Header().Set("X-Unbound-Cache", "HIT")
		http.ServeFile(w, r, cachedFile)
		return
	}

	// 2. Cache Miss: resolve stream URL from YouTube Innertube
	streamInfo, err := s.ytClient.GetStreamInfo(r.Context(), videoID)
	if err != nil || streamInfo == nil || streamInfo.StreamURL == "" {
		errMsg := "unknown stream resolution error"
		if err != nil {
			errMsg = err.Error()
		}
		writeError(w, http.StatusBadGateway, fmt.Sprintf("stream resolution failed: %s", errMsg))
		return
	}

	// 3. Connect to upstream YouTube CDN
	reqUpstream, err := http.NewRequestWithContext(r.Context(), http.MethodGet, streamInfo.StreamURL, nil)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to build upstream request")
		return
	}

	// Forward Range header if present
	if rangeHdr := r.Header.Get("Range"); rangeHdr != "" {
		reqUpstream.Header.Set("Range", rangeHdr)
	}
	reqUpstream.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

	client := &http.Client{Timeout: 60 * time.Second}
	respUpstream, err := client.Do(reqUpstream)
	if err != nil {
		writeError(w, http.StatusBadGateway, fmt.Sprintf("upstream stream connection failed: %v", err))
		return
	}
	defer respUpstream.Body.Close()

	// 4. Mirror upstream response headers to client
	for k, v := range respUpstream.Header {
		for _, val := range v {
			w.Header().Add(k, val)
		}
	}
	w.Header().Set("X-Unbound-Cache", "MISS")
	if w.Header().Get("Content-Type") == "" {
		w.Header().Set("Content-Type", "audio/ogg; codecs=opus")
	}

	w.WriteHeader(respUpstream.StatusCode)

	// If streaming from byte 0, spool to disk in parallel for transparent caching
	isFullDownload := (r.Header.Get("Range") == "" || strings.HasPrefix(r.Header.Get("Range"), "bytes=0-")) && respUpstream.StatusCode == http.StatusOK
	if isFullDownload {
		partPath := cachedFile + ".part"
		partFile, pErr := os.Create(partPath)
		if pErr == nil {
			multiWriter := io.MultiWriter(w, partFile)
			_, _ = StreamWithLookahead(r.Context(), multiWriter, respUpstream.Body, 256*1024, 2)
			_ = partFile.Close()

			// Check size and rename .part to .opus
			if pfi, sErr := os.Stat(partPath); sErr == nil && pfi.Size() > 0 {
				_ = os.Rename(partPath, cachedFile)
				go s.pruneAudioCacheIfNeeded(cacheDir)
			} else {
				_ = os.Remove(partPath)
			}
			return
		}
	}

	// Stream with 2-chunk lookahead buffer (512 KB) to prevent audio underruns on flaky cellular links
	_, _ = StreamWithLookahead(r.Context(), w, respUpstream.Body, 256*1024, 2)
}

// StreamWithLookahead pipes data from upstream Reader to downstream Writer using a 2-chunk lookahead buffer.
func StreamWithLookahead(ctx context.Context, dst io.Writer, src io.Reader, chunkSize int, numLookaheadChunks int) (int64, error) {
	if chunkSize <= 0 {
		chunkSize = 256 * 1024 // 256 KB per chunk
	}
	if numLookaheadChunks <= 0 {
		numLookaheadChunks = 2 // 2 chunks lookahead = 512 KB prefetch
	}

	type chunkResult struct {
		data []byte
		err  error
	}

	ch := make(chan chunkResult, numLookaheadChunks)

	// Background producer goroutine: prefetches chunks from upstream
	go func() {
		defer close(ch)
		for {
			buf := make([]byte, chunkSize)
			n, rErr := io.ReadFull(src, buf)
			if n > 0 {
				select {
				case <-ctx.Done():
					return
				case ch <- chunkResult{data: buf[:n]}:
				}
			}
			if rErr != nil {
				if rErr != io.EOF && rErr != io.ErrUnexpectedEOF {
					select {
					case <-ctx.Done():
					case ch <- chunkResult{err: rErr}:
					}
				}
				return
			}
		}
	}()

	var totalWritten int64
	for chunk := range ch {
		if len(chunk.data) > 0 {
			wN, wErr := dst.Write(chunk.data)
			totalWritten += int64(wN)
			if wErr != nil {
				return totalWritten, wErr
			}
		}
		if chunk.err != nil {
			return totalWritten, chunk.err
		}
	}

	return totalWritten, nil
}

// pruneAudioCacheIfNeeded enforces the 1 GB cache size ceiling by removing least recently modified files.
func (s *Server) pruneAudioCacheIfNeeded(dir string) {
	proxyCacheMu.Lock()
	defer proxyCacheMu.Unlock()

	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}

	var totalSize int64
	type fileItem struct {
		path    string
		modTime time.Time
		size    int64
	}
	var files []fileItem

	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".opus") {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		totalSize += info.Size()
		files = append(files, fileItem{
			path:    filepath.Join(dir, e.Name()),
			modTime: info.ModTime(),
			size:    info.Size(),
		})
	}

	if totalSize <= MaxAudioCacheSizeBytes {
		return
	}

	// Sort oldest first
	for i := 0; i < len(files)-1; i++ {
		for j := i + 1; j < len(files); j++ {
			if files[i].modTime.After(files[j].modTime) {
				files[i], files[j] = files[j], files[i]
			}
		}
	}

	for _, f := range files {
		_ = os.Remove(f.path)
		totalSize -= f.size
		if totalSize <= MaxAudioCacheSizeBytes {
			break
		}
	}
}
