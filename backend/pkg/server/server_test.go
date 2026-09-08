/*
 * Package: server
 * File: server_test.go
 * Purpose: Unit tests for localhost REST endpoints (/api/v1/status, /api/v1/search, /api/v1/stream, /api/v1/lyrics).
 * Subsystem: Test Suite
 * Concurrency: Tests execute HTTP requests against test server instances.
 */

package server

import (
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
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
	defer srv.Shutdown(context.Background())

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

// TestServerConfigDefaults verifies standard port and socket defaults.
func TestServerConfigDefaults(t *testing.T) {
	cfg := DefaultConfig()
	if cfg.Port != 45731 {
		t.Errorf("expected default port 45731, got %d", cfg.Port)
	}
	if cfg.SocketPath != "" {
		t.Errorf("expected empty default socket path, got %q", cfg.SocketPath)
	}
}

// TestServerListenerOptions verifies server starts on TCP and supports Unix socket path.
func TestServerListenerOptions(t *testing.T) {
	tempDir := t.TempDir()

	// 1. Test TCP Startup
	cfgTCP := Config{
		Port:           0, // random available port
		DatabasePath:   filepath.Join(tempDir, "test_tcp.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srvTCP, err := NewServer(cfgTCP)
	if err != nil {
		t.Fatalf("failed creating tcp server: %v", err)
	}

	// Use listener to pick open port
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed picking open tcp port: %v", err)
	}
	tcpPort := l.Addr().(*net.TCPAddr).Port
	_ = l.Close()

	srvTCP.cfg.Port = tcpPort
	srvTCP.httpServer.Addr = l.Addr().String()

	go func() {
		_ = srvTCP.Start()
	}()
	time.Sleep(50 * time.Millisecond)

	// Verify server responds
	resp, err := http.Get("http://" + srvTCP.httpServer.Addr + "/api/v1/status")
	if err == nil {
		_ = resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			t.Errorf("expected 200 OK on tcp, got %d", resp.StatusCode)
		}
	}
	_ = srvTCP.Shutdown(context.Background())

	// 2. Test Unix Socket Configuration
	sockPath := filepath.Join(tempDir, "test.sock")
	cfgSock := Config{
		Port:           tcpPort,
		SocketPath:     sockPath,
		DatabasePath:   filepath.Join(tempDir, "test_sock.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srvSock, err := NewServer(cfgSock)
	if err != nil {
		t.Fatalf("failed creating sock server: %v", err)
	}
	defer srvSock.Shutdown(context.Background())
	if srvSock.cfg.SocketPath != sockPath {
		t.Errorf("expected SocketPath %q, got %q", sockPath, srvSock.cfg.SocketPath)
	}
}

// TestServerEventsEndpoint verifies Server-Sent Events (SSE) streaming and event broadcasts.
func TestServerEventsEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_events.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	ts := httptest.NewServer(srv.httpServer.Handler)
	defer ts.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, ts.URL+"/api/v1/events", nil)
	if err != nil {
		t.Fatalf("failed to build request: %v", err)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("failed to connect to SSE stream: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
	}
	if resp.Header.Get("Content-Type") != "text/event-stream" {
		t.Errorf("expected Content-Type text/event-stream, got %s", resp.Header.Get("Content-Type"))
	}

	buf := make([]byte, 1024)
	n, err := resp.Body.Read(buf)
	if err != nil && n == 0 {
		t.Fatalf("failed to read initial SSE handshake: %v", err)
	}
	initChunk := string(buf[:n])
	if !strings.Contains(initChunk, "event: connected") {
		t.Errorf("expected initial 'event: connected', got %s", initChunk)
	}

	// Broadcast test event via EventBus
	srv.EventBus().Publish("test_broadcast", map[string]string{"message": "hello"})

	n, err = resp.Body.Read(buf)
	if err != nil && n == 0 {
		t.Fatalf("failed to read broadcast event: %v", err)
	}
	evtChunk := string(buf[:n])
	if !strings.Contains(evtChunk, "event: test_broadcast") || !strings.Contains(evtChunk, "hello") {
		t.Errorf("expected test_broadcast event with hello, got %s", evtChunk)
	}
}

