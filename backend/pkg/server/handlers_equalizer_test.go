package server

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func TestServerAutoEqSearch(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45747,
		DatabasePath:   filepath.Join(tempDir, "test_eq.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/autoeq/search?q=sony", nil)
	w := httptest.NewRecorder()

	srv.handleAutoEqSearch(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
	}

	var payload map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("failed to decode JSON response: %v", err)
	}

	if payload["query"] != "sony" {
		t.Errorf("expected query 'sony', got %v", payload["query"])
	}
	if _, ok := payload["headphones"]; !ok {
		t.Errorf("expected headphones field in response")
	}
}
