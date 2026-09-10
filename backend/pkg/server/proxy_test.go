/*
 * Package: server
 * File: proxy_test.go
 * Purpose: Unit tests for streaming reverse proxy and transparent audio disk caching.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrent proxy and caching scenarios.
 */

package server

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestProxyCacheHitAndRange(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45735,
		DatabasePath:   filepath.Join(tempDir, "test_proxy.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	cacheDir := srv.getAudioCacheDir()
	testVideoID := "test_cached_vid"
	cachedAudioFile := filepath.Join(cacheDir, testVideoID+".opus")

	// Write mock audio data to cache
	mockAudioData := []byte("OggS" + strings.Repeat("MOCK_AUDIO_DATA_PAYLOAD", 50))
	if err := os.WriteFile(cachedAudioFile, mockAudioData, 0644); err != nil {
		t.Fatalf("failed to write mock audio file: %v", err)
	}

	// 1. Test standard GET request (Cache Hit)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/proxy/stream?id="+testVideoID, nil)
	w := httptest.NewRecorder()
	srv.handleProxyStream(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK on cached file, got %d", resp.StatusCode)
	}
	if resp.Header.Get("X-Unbound-Cache") != "HIT" {
		t.Errorf("expected X-Unbound-Cache: HIT, got %q", resp.Header.Get("X-Unbound-Cache"))
	}
	if w.Body.Len() != len(mockAudioData) {
		t.Errorf("expected %d bytes, got %d", len(mockAudioData), w.Body.Len())
	}

	// 2. Test Range request (HTTP 206 Partial Content)
	reqRange := httptest.NewRequest(http.MethodGet, "/api/v1/proxy/stream?id="+testVideoID, nil)
	reqRange.Header.Set("Range", "bytes=0-9")
	wRange := httptest.NewRecorder()
	srv.handleProxyStream(wRange, reqRange)

	respRange := wRange.Result()
	if respRange.StatusCode != http.StatusPartialContent {
		t.Errorf("expected 206 Partial Content, got %d", respRange.StatusCode)
	}
	if wRange.Body.Len() != 10 {
		t.Errorf("expected 10 bytes for bytes=0-9, got %d", wRange.Body.Len())
	}
}

func TestProxyMissingParam(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45736,
		DatabasePath:   filepath.Join(tempDir, "test_proxy_err.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/proxy/stream", nil)
	w := httptest.NewRecorder()
	srv.handleProxyStream(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request on missing id, got %d", w.Code)
	}
}

// TestStreamWithLookahead validates the preemptive lookahead buffer for streaming.
func TestStreamWithLookahead(t *testing.T) {
	dataSize := 1024 * 1024 // 1 MB
	originalData := make([]byte, dataSize)
	for i := 0; i < dataSize; i++ {
		originalData[i] = byte(i % 256)
	}

	src := strings.NewReader(string(originalData))
	var dst strings.Builder

	written, err := StreamWithLookahead(context.Background(), &dst, src, 64*1024, 2)
	if err != nil {
		t.Fatalf("StreamWithLookahead failed: %v", err)
	}

	if written != int64(dataSize) {
		t.Errorf("expected written bytes %d, got %d", dataSize, written)
	}
	if dst.Len() != dataSize {
		t.Errorf("expected destination length %d, got %d", dataSize, dst.Len())
	}
	if dst.String() != string(originalData) {
		t.Error("stream content mismatch between source and lookahead output")
	}
}

func TestLiveProxyStream(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45737,
		DatabasePath:   filepath.Join(tempDir, "test_live_proxy.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Test with a real video ID
	req := httptest.NewRequest(http.MethodGet, "/api/v1/proxy/stream?id=T6eK-2OQtew", nil)
	req.Header.Set("Range", "bytes=0-1024")
	w := httptest.NewRecorder()
	srv.handleProxyStream(w, req)

	resp := w.Result()
	t.Logf("Live proxy stream response: status=%d, headers=%v, bodyLen=%d", resp.StatusCode, resp.Header, w.Body.Len())
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		t.Errorf("expected 200 or 206 from proxy, got %d: %s", resp.StatusCode, w.Body.String())
	}
	if w.Body.Len() == 0 {
		t.Errorf("expected non-empty body from live proxy stream")
	}
}

func TestProxyStreamNoTimeout(t *testing.T) {
	tempDir := t.TempDir()
	srv, err := NewServer(Config{
		Port:           45788,
		DatabasePath:   filepath.Join(tempDir, "test.db"),
		LibraryRoot:    filepath.Join(tempDir, "library"),
		AppStorageRoot: filepath.Join(tempDir, "storage"),
	})
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	if srv.httpServer.WriteTimeout != 0 {
		t.Errorf("expected httpServer.WriteTimeout to be 0 for streaming, got %v", srv.httpServer.WriteTimeout)
	}
	if srv.httpServer.ReadTimeout < 30*time.Second {
		t.Errorf("expected ReadTimeout >= 30s, got %v", srv.httpServer.ReadTimeout)
	}
}
