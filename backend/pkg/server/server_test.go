/*
 * Package: server
 * File: server_test.go
 * Purpose: Unit tests for localhost REST endpoints (/api/v1/status, /api/v1/search, /api/v1/stream, /api/v1/lyrics).
 * Subsystem: Test Suite
 * Concurrency: Tests execute HTTP requests against test server instances.
 */

package server

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

// TestServerStatusEndpoint validates that /api/v1/status returns valid JSON and health state.
func TestServerStatusEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45732,
		DatabasePath:   filepath.Join(tempDir, "test_server.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(nil)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/status", nil)
	w := httptest.NewRecorder()

	srv.handleStatus(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK, got %d", resp.StatusCode)
	}

	var payload map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("failed to decode JSON response: %v", err)
	}

	if payload["status"] != "ONLINE" {
		t.Errorf("expected ONLINE status, got %v", payload["status"])
	}
}
