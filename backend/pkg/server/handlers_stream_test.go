package server

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func TestServerStreamParameterValidation(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45743,
		DatabasePath:   filepath.Join(tempDir, "test_stream.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Request with missing 'id' and 'title' should return 400 Bad Request
	req := httptest.NewRequest(http.MethodGet, "/api/v1/stream", nil)
	w := httptest.NewRecorder()

	srv.handleStream(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for missing stream params, got %d", resp.StatusCode)
	}
}

func TestServerStreamSilentFallbackPath(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45744,
		DatabasePath:   filepath.Join(tempDir, "test_stream2.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// When YouTube extraction fails or returns 500 for an unknown track, it attempts fallback
	req := httptest.NewRequest(http.MethodGet, "/api/v1/stream?title=NonExistentSilentSongTestXYZ123", nil)
	w := httptest.NewRecorder()

	srv.handleStream(w, req)

	resp := w.Result()
	// Should fail gracefully with 500 if both YouTube and fallback cannot find it, without crashing
	if resp.StatusCode != http.StatusInternalServerError && resp.StatusCode != http.StatusOK {
		t.Errorf("expected 500 or 200, got %d", resp.StatusCode)
	}
}
