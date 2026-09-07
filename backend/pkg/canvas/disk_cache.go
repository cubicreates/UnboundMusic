/*
 * Package: canvas
 * File: disk_cache.go
 * Purpose: High-performance on-disk LRU cache for Spotify Canvas MP4 videos and visual album art backgrounds.
 * Subsystem: Visual Aesthetics & Canvas Video
 * Concurrency: Thread-safe cache synchronization across concurrent worker goroutines.
 */

package canvas

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	DefaultMaxCacheSizeBytes = 250 * 1024 * 1024 // 250 MB cache quota
)

// DiskLRUCache manages local on-disk storage of visual canvas assets with automatic LRU pruning.
type DiskLRUCache struct {
	mu           sync.Mutex
	dir          string
	maxSizeBytes int64
	httpClient   *http.Client
}

// NewDiskLRUCache instantiates an LRU disk cache in the specified directory.
func NewDiskLRUCache(dir string, maxSizeBytes int64) (*DiskLRUCache, error) {
	if strings.TrimSpace(dir) == "" {
		dir = filepath.Join(os.TempDir(), "unbound_canvas_cache")
	}
	if maxSizeBytes <= 0 {
		maxSizeBytes = DefaultMaxCacheSizeBytes
	}

	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, fmt.Errorf("failed creating canvas cache directory %q: %w", dir, err)
	}

	return &DiskLRUCache{
		dir:          dir,
		maxSizeBytes: maxSizeBytes,
		httpClient:   &http.Client{Timeout: 30 * time.Second},
	}, nil
}

// sanitizeKey produces a safe filesystem filename from track title/artist/id.
func sanitizeKey(key string) string {
	h := sha256.Sum256([]byte(strings.ToLower(strings.TrimSpace(key))))
	return fmt.Sprintf("%x", h[:12])
}

// Get checks if a visual asset is already cached on disk and updates its access time.
func (c *DiskLRUCache) Get(key string) (string, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()

	prefix := sanitizeKey(key)
	pattern := filepath.Join(c.dir, prefix+".*")
	matches, err := filepath.Glob(pattern)
	if err != nil || len(matches) == 0 {
		return "", false
	}

	target := matches[0]
	info, err := os.Stat(target)
	if err != nil || info.Size() == 0 {
		return "", false
	}

	// Touch file to update access/modification timestamp for LRU ordering
	now := time.Now()
	_ = os.Chtimes(target, now, now)

	return target, true
}

// DownloadAsync asynchronously fetches the remote visual file and saves it to the LRU cache.
func (c *DiskLRUCache) DownloadAsync(key, remoteURL string) {
	if remoteURL == "" {
		return
	}

	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, remoteURL, nil)
		if err != nil {
			return
		}
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

		resp, err := c.httpClient.Do(req)
		if err != nil || resp.StatusCode != http.StatusOK {
			return
		}
		defer resp.Body.Close()

		// Determine extension from Content-Type or URL
		ext := ".mp4"
		ct := resp.Header.Get("Content-Type")
		if strings.Contains(ct, "image/jpeg") || strings.HasSuffix(remoteURL, ".jpg") {
			ext = ".jpg"
		} else if strings.Contains(ct, "image/png") || strings.HasSuffix(remoteURL, ".png") {
			ext = ".png"
		} else if strings.Contains(ct, "video/mp4") {
			ext = ".mp4"
		}

		c.mu.Lock()
		defer c.mu.Unlock()

		c.pruneLocked(resp.ContentLength)

		destPath := filepath.Join(c.dir, sanitizeKey(key)+ext)
		tmpPath := destPath + ".tmp"

		out, err := os.Create(tmpPath)
		if err != nil {
			return
		}

		_, copyErr := io.Copy(out, resp.Body)
		_ = out.Close()

		if copyErr == nil {
			_ = os.Rename(tmpPath, destPath)
		} else {
			_ = os.Remove(tmpPath)
		}
	}()
}

// pruneLocked enforces the maximum cache size by removing the oldest accessed files.
func (c *DiskLRUCache) pruneLocked(incomingSize int64) {
	entries, err := os.ReadDir(c.dir)
	if err != nil || len(entries) == 0 {
		return
	}

	type fileItem struct {
		path    string
		size    int64
		modTime time.Time
	}

	var items []fileItem
	var totalSize int64

	for _, e := range entries {
		if e.IsDir() || strings.HasSuffix(e.Name(), ".tmp") {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		items = append(items, fileItem{
			path:    filepath.Join(c.dir, e.Name()),
			size:    info.Size(),
			modTime: info.ModTime(),
		})
		totalSize += info.Size()
	}

	// If total size plus incoming file exceeds quota, delete oldest
	if totalSize+incomingSize <= c.maxSizeBytes {
		return
	}

	// Sort ascending by modification time (oldest first)
	sort.Slice(items, func(i, j int) bool {
		return items[i].modTime.Before(items[j].modTime)
	})

	for _, it := range items {
		if totalSize+incomingSize <= c.maxSizeBytes {
			break
		}
		if err := os.Remove(it.path); err == nil {
			totalSize -= it.size
		}
	}
}
