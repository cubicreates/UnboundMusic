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
