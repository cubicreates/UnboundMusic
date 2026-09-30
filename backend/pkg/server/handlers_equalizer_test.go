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

func TestServerAutoEqPresetWithBands(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45748,
		DatabasePath:   filepath.Join(tempDir, "test_eq2.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Test 5-band resampled preset
	req5 := httptest.NewRequest(http.MethodGet, "/api/v1/autoeq/preset?id=sony_wh1000xm5&bands=5", nil)
	w5 := httptest.NewRecorder()
	srv.handleAutoEqPreset(w5, req5)

	resp5 := w5.Result()
	if resp5.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK for 5-band preset, got %d", resp5.StatusCode)
	}

	var payload5 map[string]interface{}
	if err := json.NewDecoder(resp5.Body).Decode(&payload5); err != nil {
		t.Fatalf("failed to decode 5-band response: %v", err)
	}

	bands5, ok := payload5["bands"].([]interface{})
	if !ok || len(bands5) != 5 {
		t.Errorf("expected 5 bands in resampled preset, got %d", len(bands5))
	}

	// Test default 10-band preset
	req10 := httptest.NewRequest(http.MethodGet, "/api/v1/autoeq/preset?id=sony_wh1000xm5", nil)
	w10 := httptest.NewRecorder()
	srv.handleAutoEqPreset(w10, req10)

	resp10 := w10.Result()
	if resp10.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK for 10-band preset, got %d", resp10.StatusCode)
	}

	var payload10 map[string]interface{}
	if err := json.NewDecoder(resp10.Body).Decode(&payload10); err != nil {
		t.Fatalf("failed to decode 10-band response: %v", err)
	}

	bands10, ok := payload10["bands"].([]interface{})
	if !ok || len(bands10) != 10 {
		t.Errorf("expected 10 bands in default preset, got %d", len(bands10))
	}
}
