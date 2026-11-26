package server

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func TestServerSearchMissingQuery(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45744,
		DatabasePath:   filepath.Join(tempDir, "test_search.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/search", nil)
	w := httptest.NewRecorder()

	srv.handleSearch(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for empty query, got %d", resp.StatusCode)
	}
}

func TestServerVibeSearchEmptyPrompt(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45745,
		DatabasePath:   filepath.Join(tempDir, "test_vibe.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	body := []byte(`{"prompt":""}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/search/vibe", bytes.NewBuffer(body))
	w := httptest.NewRecorder()

	srv.handleVibeSearch(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for empty prompt, got %d", resp.StatusCode)
	}
}

func TestServerMusicAndPodcastSearchValidation(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45746,
		DatabasePath:   filepath.Join(tempDir, "test_split_search.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Missing q on /api/v1/search/music -> 400
	reqMusic := httptest.NewRequest(http.MethodGet, "/api/v1/search/music", nil)
	wMusic := httptest.NewRecorder()
	srv.ServeHTTP(wMusic, reqMusic)
	if wMusic.Result().StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 on /api/v1/search/music without q, got %d", wMusic.Result().StatusCode)
	}

	// Wrong method on /api/v1/search/music -> 405
	reqMusicPost := httptest.NewRequest(http.MethodPost, "/api/v1/search/music?q=daft+punk", nil)
	wMusicPost := httptest.NewRecorder()
	srv.ServeHTTP(wMusicPost, reqMusicPost)
	if wMusicPost.Result().StatusCode != http.StatusMethodNotAllowed {
		t.Errorf("expected 405 on POST /api/v1/search/music, got %d", wMusicPost.Result().StatusCode)
	}

	// Missing q on /api/v1/search/podcasts -> 400
	reqPod := httptest.NewRequest(http.MethodGet, "/api/v1/search/podcasts", nil)
	wPod := httptest.NewRecorder()
	srv.ServeHTTP(wPod, reqPod)
	if wPod.Result().StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 on /api/v1/search/podcasts without q, got %d", wPod.Result().StatusCode)
	}
}
