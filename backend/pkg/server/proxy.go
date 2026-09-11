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
	"net/url"
	"os"
	"path/filepath"
	"runtime/debug"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

var (
	proxyCacheMu   sync.Mutex
	spoolingMu     sync.Mutex
	spoolingTracks = make(map[string]bool)
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

// parseRangeHeader parses HTTP Range header: "bytes=start-end" or "bytes=start-"
func parseRangeHeader(rangeHdr string) (start int64, end int64, hasRange bool) {
	if rangeHdr == "" || !strings.HasPrefix(rangeHdr, "bytes=") {
		return 0, -1, false
	}
	spec := strings.TrimPrefix(rangeHdr, "bytes=")
	parts := strings.Split(spec, "-")
	start, _ = strconv.ParseInt(parts[0], 10, 64)
	end = -1
	if len(parts) > 1 && parts[1] != "" {
		if e, err := strconv.ParseInt(parts[1], 10, 64); err == nil {
			end = e
		}
	}
	return start, end, true
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

	upstreamURL := streamInfo.StreamURL
	if pot := strings.TrimSpace(r.URL.Query().Get("pot")); pot != "" && !strings.Contains(upstreamURL, "&pot=") {
		upstreamURL += "&pot=" + url.QueryEscape(pot)
	}

	// Match the User-Agent to the client profile encoded in the signed URL
	ua := ytmusic.ConfigVisionOS.UserAgent
	if strings.Contains(upstreamURL, "c=VISIONOS") {
		ua = ytmusic.ConfigVisionOS.UserAgent
	} else if strings.Contains(upstreamURL, "c=ANDROID") {
		ua = ytmusic.ConfigAndroid.UserAgent
	} else if strings.Contains(upstreamURL, "c=WEB_REMIX") {
		ua = ytmusic.UserAgentWebRemix
	} else if strings.Contains(upstreamURL, "c=TVHTML5") {
		ua = ytmusic.UserAgentTV
	} else if strings.Contains(upstreamURL, "c=IOS") {
		ua = ytmusic.UserAgentIOS
	}

	// 3. Parse client Range request
	clientStart, clientEnd, hasRange := parseRangeHeader(r.Header.Get("Range"))
	if clientStart < 0 {
		clientStart = 0
	}

	// Bounded chunk size accepted by YouTube CDN (avoids 403 on open-ended Range bytes=0-)
	const upstreamChunkSize = int64(256 * 1024)

	firstEnd := clientStart + upstreamChunkSize - 1
	if clientEnd >= 0 && firstEnd > clientEnd {
		firstEnd = clientEnd
	}

	client := &http.Client{Timeout: 60 * time.Second}
	reqUpstream, err := http.NewRequestWithContext(r.Context(), http.MethodGet, upstreamURL, nil)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to build upstream request")
		return
	}
	reqUpstream.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", clientStart, firstEnd))
	reqUpstream.Header.Set("User-Agent", ua)
	if ua == ytmusic.UserAgentWebRemix {
		reqUpstream.Header.Set("Referer", "https://music.youtube.com/")
		reqUpstream.Header.Set("Origin", "https://music.youtube.com")
	}

	respUpstream, err := client.Do(reqUpstream)
	if err != nil {
		writeError(w, http.StatusBadGateway, fmt.Sprintf("upstream stream connection failed: %v", err))
		return
	}

	// If 403 Forbidden with specific UA, try with WebRemix fallback
	if respUpstream.StatusCode == http.StatusForbidden && ua != ytmusic.UserAgentWebRemix {
		respUpstream.Body.Close()
		reqRetry, rErr := http.NewRequestWithContext(r.Context(), http.MethodGet, upstreamURL, nil)
		if rErr == nil {
			reqRetry.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", clientStart, firstEnd))
			reqRetry.Header.Set("User-Agent", ytmusic.UserAgentWebRemix)
			reqRetry.Header.Set("Referer", "https://music.youtube.com/")
			reqRetry.Header.Set("Origin", "https://music.youtube.com")
			if retryResp, errRetry := client.Do(reqRetry); errRetry == nil && retryResp.StatusCode != http.StatusForbidden {
				respUpstream = retryResp
				ua = ytmusic.UserAgentWebRemix
			}
		}
	}

	if respUpstream.StatusCode != http.StatusOK && respUpstream.StatusCode != http.StatusPartialContent {
		writeError(w, respUpstream.StatusCode, fmt.Sprintf("upstream returned status %d", respUpstream.StatusCode))
		respUpstream.Body.Close()
		return
	}

	// 4. Determine total file size from upstream Content-Range
	totalSize := streamInfo.ContentLength
	contentRangeHdr := respUpstream.Header.Get("Content-Range")
	if slashIdx := strings.LastIndex(contentRangeHdr, "/"); slashIdx != -1 {
		if parsedTotal, pErr := strconv.ParseInt(contentRangeHdr[slashIdx+1:], 10, 64); pErr == nil && parsedTotal > 0 {
			totalSize = parsedTotal
		}
	}
	if totalSize <= 0 {
		totalSize = firstEnd + 1
	}

	effectiveEnd := totalSize - 1
	if clientEnd >= 0 && clientEnd < totalSize {
		effectiveEnd = clientEnd
	}
	downstreamContentLength := effectiveEnd - clientStart + 1
	if downstreamContentLength < 0 {
		downstreamContentLength = 0
	}

	// 5. Send downstream response headers to ExoPlayer / client
	w.Header().Set("X-Unbound-Cache", "MISS")
	w.Header().Set("Accept-Ranges", "bytes")
	contentType := respUpstream.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "audio/webm; codecs=opus"
	}
	w.Header().Set("Content-Type", contentType)

	if hasRange {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", clientStart, effectiveEnd, totalSize))
		w.Header().Set("Content-Length", strconv.FormatInt(downstreamContentLength, 10))
		w.WriteHeader(http.StatusPartialContent)
	} else {
		w.Header().Set("Content-Length", strconv.FormatInt(totalSize, 10))
		w.WriteHeader(http.StatusOK)
	}

	flusher, hasFlusher := w.(http.Flusher)
	isFullDownload := (clientStart == 0 && (clientEnd < 0 || clientEnd >= totalSize-1))
	var partFile *os.File
	partPath := cachedFile + ".part"
	if isFullDownload {
		spoolingMu.Lock()
		if !spoolingTracks[videoID] {
			spoolingTracks[videoID] = true
			partFile, _ = os.Create(partPath)
		}
		spoolingMu.Unlock()
	}

	buf := make([]byte, 32*1024)
	bytesWritten := int64(0)
	for {
		n, rErr := respUpstream.Body.Read(buf)
		if n > 0 {
			if partFile != nil {
				_, _ = partFile.Write(buf[:n])
			}
			if _, wErr := w.Write(buf[:n]); wErr != nil {
				respUpstream.Body.Close()
				if partFile != nil {
					f := partFile
					partFile = nil
					go s.finishSpoolingInBackground(videoID, f, partPath, cachedFile, upstreamURL, ua, clientStart+bytesWritten+int64(n), totalSize, upstreamChunkSize)
				}
				return
			}
			if hasFlusher {
				flusher.Flush()
			}
			bytesWritten += int64(n)
		}
		if rErr != nil {
			break
		}
	}
	respUpstream.Body.Close()

	// 6. Stream subsequent chunks sequentially to client while spooling
	curStart := clientStart + bytesWritten
	for curStart <= effectiveEnd {
		if r.Context().Err() != nil {
			break
		}
		curEnd := curStart + upstreamChunkSize - 1
		if curEnd > effectiveEnd {
			curEnd = effectiveEnd
		}
		if curStart > curEnd {
			break
		}

		reqChunk, cErr := http.NewRequestWithContext(r.Context(), http.MethodGet, upstreamURL, nil)
		if cErr != nil {
			break
		}
		reqChunk.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", curStart, curEnd))
		reqChunk.Header.Set("User-Agent", ua)
		if ua == ytmusic.UserAgentWebRemix {
			reqChunk.Header.Set("Referer", "https://music.youtube.com/")
			reqChunk.Header.Set("Origin", "https://music.youtube.com")
		}

		respChunk, doErr := client.Do(reqChunk)
		if doErr != nil {
			break
		}
		if respChunk.StatusCode == http.StatusForbidden && ua != ytmusic.UserAgentWebRemix {
			respChunk.Body.Close()
			reqRetry, rErr := http.NewRequestWithContext(r.Context(), http.MethodGet, upstreamURL, nil)
			if rErr == nil {
				reqRetry.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", curStart, curEnd))
				reqRetry.Header.Set("User-Agent", ytmusic.UserAgentWebRemix)
				reqRetry.Header.Set("Referer", "https://music.youtube.com/")
				reqRetry.Header.Set("Origin", "https://music.youtube.com")
				if retryResp, errRetry := client.Do(reqRetry); errRetry == nil && retryResp.StatusCode != http.StatusForbidden {
					respChunk = retryResp
					ua = ytmusic.UserAgentWebRemix
				}
			}
		}
		if respChunk.StatusCode != http.StatusOK && respChunk.StatusCode != http.StatusPartialContent {
			respChunk.Body.Close()
			break
		}

		chunkBytes := int64(0)
		for {
			n, rErr := respChunk.Body.Read(buf)
			if n > 0 {
				if partFile != nil {
					_, _ = partFile.Write(buf[:n])
				}
				if _, wErr := w.Write(buf[:n]); wErr != nil {
					respChunk.Body.Close()
					if partFile != nil {
						f := partFile
						partFile = nil
						go s.finishSpoolingInBackground(videoID, f, partPath, cachedFile, upstreamURL, ua, curStart+chunkBytes+int64(n), totalSize, upstreamChunkSize)
					}
					return
				}
				if hasFlusher {
					flusher.Flush()
				}
				chunkBytes += int64(n)
			}
			if rErr != nil {
				break
			}
		}
		respChunk.Body.Close()

		if chunkBytes == 0 {
			break
		}
		curStart += chunkBytes
	}

	if partFile != nil {
		if curStart >= totalSize && totalSize > 0 {
			_ = partFile.Sync()
			_ = partFile.Close()
			_ = os.Rename(partPath, cachedFile)
			spoolingMu.Lock()
			delete(spoolingTracks, videoID)
			spoolingMu.Unlock()
			go s.pruneAudioCacheIfNeeded(cacheDir)
		} else {
			f := partFile
			partFile = nil
			go s.finishSpoolingInBackground(videoID, f, partPath, cachedFile, upstreamURL, ua, curStart, totalSize, upstreamChunkSize)
		}
	}
}

// finishSpoolingInBackground continues downloading audio chunks into the .part file after the client disconnects or pauses.
func (s *Server) finishSpoolingInBackground(videoID string, partFile *os.File, partPath, cachedFile, upstreamURL, ua string, curStart, totalSize int64, chunkSize int64) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("[BACKGROUND SPOOL PANIC]: %v", r)
		}
		spoolingMu.Lock()
		delete(spoolingTracks, videoID)
		spoolingMu.Unlock()
		if partFile != nil {
			_ = partFile.Close()
		}
	}()

	client := &http.Client{Timeout: 45 * time.Second}
	buf := make([]byte, 32*1024)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	for curStart < totalSize {
		curEnd := curStart + chunkSize - 1
		if curEnd >= totalSize {
			curEnd = totalSize - 1
		}
		if curStart > curEnd {
			break
		}

		reqChunk, err := http.NewRequestWithContext(ctx, http.MethodGet, upstreamURL, nil)
		if err != nil {
			_ = os.Remove(partPath)
			return
		}
		reqChunk.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", curStart, curEnd))
		reqChunk.Header.Set("User-Agent", ua)
		if ua == ytmusic.UserAgentWebRemix {
			reqChunk.Header.Set("Referer", "https://music.youtube.com/")
			reqChunk.Header.Set("Origin", "https://music.youtube.com")
		}

		respChunk, doErr := client.Do(reqChunk)
		if doErr != nil {
			_ = os.Remove(partPath)
			return
		}
		if respChunk.StatusCode != http.StatusOK && respChunk.StatusCode != http.StatusPartialContent {
			respChunk.Body.Close()
			_ = os.Remove(partPath)
			return
		}

		chunkBytes := int64(0)
		for {
			n, rErr := respChunk.Body.Read(buf)
			if n > 0 {
				if _, wErr := partFile.Write(buf[:n]); wErr != nil {
					respChunk.Body.Close()
					_ = os.Remove(partPath)
					return
				}
				chunkBytes += int64(n)
			}
			if rErr != nil {
				break
			}
		}
		respChunk.Body.Close()

		if chunkBytes == 0 {
			_ = os.Remove(partPath)
			return
		}
		curStart += chunkBytes
	}

	if curStart >= totalSize && totalSize > 0 {
		_ = partFile.Sync()
		_ = partFile.Close()
		partFile = nil
		_ = os.Rename(partPath, cachedFile)
		s.pruneAudioCacheIfNeeded(filepath.Dir(cachedFile))
	} else {
		_ = os.Remove(partPath)
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
