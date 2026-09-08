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
