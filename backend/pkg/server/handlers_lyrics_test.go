package server

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func TestServerLyricsMissingParameters(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45746,
		DatabasePath:   filepath.Join(tempDir, "test_lyrics.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Request with missing 'id' and 'title' should return 400 Bad Request
	req := httptest.NewRequest(http.MethodGet, "/api/v1/lyrics", nil)
	w := httptest.NewRecorder()

	srv.handleLyrics(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for missing lyrics params, got %d", resp.StatusCode)
	}
}
