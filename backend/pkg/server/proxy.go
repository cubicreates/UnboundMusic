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

	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
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

	// Extend write deadline specifically for streaming connections to prevent socket timeout mid-track
	rc := http.NewResponseController(w)
	_ = rc.SetWriteDeadline(time.Now().Add(15 * time.Minute))

	cacheDir := s.getAudioCacheDir()
	cachedFile := filepath.Join(cacheDir, videoID+".opus")

	// 1. Check if complete audio file exists in local cache
	if fi, err := os.Stat(cachedFile); err == nil && fi.Size() > 0 {
		// Cache Hit: serve directly via http.ServeFile with full 206 Range support
		w.Header().Set("X-Unbound-Cache", "HIT")
		http.ServeFile(w, r, cachedFile)
		return
	}

	// Resolve bare title or non-11-char ID via search
	if len(videoID) != 11 || strings.Contains(videoID, " ") {
		searchTracks, sErr := s.ytClient.Search(r.Context(), videoID)
		if sErr == nil && len(searchTracks) > 0 {
			videoID = searchTracks[0].ID
			cachedFile = filepath.Join(cacheDir, videoID+".opus")
			if fi, err := os.Stat(cachedFile); err == nil && fi.Size() > 0 {
				w.Header().Set("X-Unbound-Cache", "HIT")
				http.ServeFile(w, r, cachedFile)
				return
			}
		}
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

	// 3. Connect to upstream YouTube CDN with matching User-Agent to prevent 403 Forbidden
	reqUpstream, err := http.NewRequestWithContext(r.Context(), http.MethodGet, streamInfo.StreamURL, nil)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to build upstream request")
		return
	}

	// Forward Range header if present
	if rangeHdr := r.Header.Get("Range"); rangeHdr != "" {
		reqUpstream.Header.Set("Range", rangeHdr)
	}

	// Match the User-Agent to the client profile encoded in the signed URL
	ua := ytmusic.UserAgentIOS
	if strings.Contains(streamInfo.StreamURL, "c=WEB_REMIX") {
		ua = ytmusic.UserAgentWebRemix
	} else if strings.Contains(streamInfo.StreamURL, "c=TVHTML5") {
		ua = ytmusic.UserAgentTV
	}
	reqUpstream.Header.Set("User-Agent", ua)

	client := &http.Client{Timeout: 60 * time.Second}
	respUpstream, err := client.Do(reqUpstream)
	if err != nil {
		writeError(w, http.StatusBadGateway, fmt.Sprintf("upstream stream connection failed: %v", err))
		return
	}

	// If 403 Forbidden with specific UA, try with WebRemix fallback
	if respUpstream.StatusCode == http.StatusForbidden && ua != ytmusic.UserAgentWebRemix {
		respUpstream.Body.Close()
		reqRetry, rErr := http.NewRequestWithContext(r.Context(), http.MethodGet, streamInfo.StreamURL, nil)
		if rErr == nil {
			if rangeHdr := r.Header.Get("Range"); rangeHdr != "" {
				reqRetry.Header.Set("Range", rangeHdr)
			}
			reqRetry.Header.Set("User-Agent", ytmusic.UserAgentWebRemix)
			if retryResp, errRetry := client.Do(reqRetry); errRetry == nil && retryResp.StatusCode != http.StatusForbidden {
				respUpstream = retryResp
			}
		}
	}
	defer respUpstream.Body.Close()

	// 4. Mirror upstream response headers to client (excluding hop-by-hop headers)
	hopByHop := map[string]bool{
		"connection":          true,
		"keep-alive":          true,
		"proxy-authenticate":  true,
		"proxy-authorization": true,
		"te":                  true,
		"trailers":            true,
		"transfer-encoding":   true,
		"upgrade":             true,
	}
	for k, v := range respUpstream.Header {
		if hopByHop[strings.ToLower(k)] {
			continue
		}
		for _, val := range v {
			w.Header().Add(k, val)
		}
	}
	w.Header().Set("X-Unbound-Cache", "MISS")
	w.Header().Set("Accept-Ranges", "bytes")
	if w.Header().Get("Content-Type") == "" {
		w.Header().Set("Content-Type", "audio/webm; codecs=opus")
	}

	w.WriteHeader(respUpstream.StatusCode)

	flusher, hasFlusher := w.(http.Flusher)
	buf := make([]byte, 32*1024)

	// If streaming from byte 0, spool to disk in parallel for transparent caching
	isFullDownload := (r.Header.Get("Range") == "" || strings.HasPrefix(r.Header.Get("Range"), "bytes=0-")) &&
		(respUpstream.StatusCode == http.StatusOK || respUpstream.StatusCode == http.StatusPartialContent)
	var partFile *os.File
	if isFullDownload {
		partPath := cachedFile + ".part"
		partFile, _ = os.Create(partPath)
	}

	for {
		n, rErr := respUpstream.Body.Read(buf)
		if n > 0 {
			if _, wErr := w.Write(buf[:n]); wErr != nil {
				break
			}
			if hasFlusher {
				flusher.Flush()
			}
			if partFile != nil {
				_, _ = partFile.Write(buf[:n])
			}
		}
		if rErr != nil {
			break
		}
	}

	if partFile != nil {
		_ = partFile.Close()
		partPath := cachedFile + ".part"
		if pfi, sErr := os.Stat(partPath); sErr == nil && pfi.Size() > 0 {
			_ = os.Rename(partPath, cachedFile)
			go s.pruneAudioCacheIfNeeded(cacheDir)
		} else {
			_ = os.Remove(partPath)
		}
	}
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
