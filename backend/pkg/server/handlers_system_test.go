package server

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"
)

func TestServerPingEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45749,
		DatabasePath:   filepath.Join(tempDir, "test_ping.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/ping", nil)
	w := httptest.NewRecorder()

	srv.handlePing(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
	}

	var payload map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("failed to decode JSON: %v", err)
	}

	if payload["pong"] != true {
		t.Errorf("expected pong: true, got %v", payload["pong"])
	}
}

func TestServerHealthEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45740,
		DatabasePath:   filepath.Join(tempDir, "test_health.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	w := httptest.NewRecorder()

	srv.handleHealth(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
	}

	var payload map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("failed to decode JSON: %v", err)
	}

	if payload["status"] != "HEALTHY" {
		t.Errorf("expected HEALTHY, got %v", payload["status"])
	}
	if payload["version"] != "1.0.0-FOSS" {
		t.Errorf("expected 1.0.0-FOSS, got %v", payload["version"])
	}
}

func TestServerStatusTelemetry(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45741,
		DatabasePath:   filepath.Join(tempDir, "test_status.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/status", nil)
	w := httptest.NewRecorder()

	srv.handleStatus(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
	}

	var payload map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("failed to decode JSON: %v", err)
	}

	if payload["status"] != "ONLINE" {
		t.Errorf("expected ONLINE, got %v", payload["status"])
	}
	if _, ok := payload["goroutines"]; !ok {
		t.Errorf("expected goroutines count in status payload")
	}
	if _, ok := payload["allocated_ram_mb"]; !ok {
		t.Errorf("expected allocated_ram_mb in status payload")
	}
}

func TestServerEventsBusSubscription(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45742,
		DatabasePath:   filepath.Join(tempDir, "test_events.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	bus := srv.EventBus()
	if bus == nil {
		t.Fatalf("expected non-nil EventBus")
	}

	sub := bus.Subscribe()
	defer bus.Unsubscribe(sub)

	go func() {
		time.Sleep(10 * time.Millisecond)
		bus.Publish("test.ping", "pong")
	}()

	select {
	case evt := <-sub:
		if evt.Type != "test.ping" {
			t.Errorf("expected event type test.ping, got %s", evt.Type)
		}
	case <-time.After(500 * time.Millisecond):
		t.Errorf("timed out waiting for event bus publish")
	}
}
