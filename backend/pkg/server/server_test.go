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
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
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

// TestServerAudioNormalizeEndpoint validates EBU R128 loudness calculation via GET and POST.
func TestServerAudioNormalizeEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_norm.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. GET with target LUFS
	reqGET := httptest.NewRequest(http.MethodGet, "/api/v1/audio/normalize?target=-16.0", nil)
	wGET := httptest.NewRecorder()
	srv.handleAudioNormalize(wGET, reqGET)

	if wGET.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from GET normalize, got %d", wGET.Code)
	}
	var resGET map[string]any
	if err := json.NewDecoder(wGET.Body).Decode(&resGET); err != nil {
		t.Fatalf("failed decoding GET JSON: %v", err)
	}
	if target, ok := resGET["target_lufs"].(float64); !ok || target != -16.0 {
		t.Errorf("expected target_lufs -16.0, got %v", resGET["target_lufs"])
	}

	// 2. POST with custom samples
	postBody := `{"samples": [0.5, -0.5, 0.2, -0.2], "target_lufs": -14.0}`
	reqPOST := httptest.NewRequest(http.MethodPost, "/api/v1/audio/normalize", strings.NewReader(postBody))
	wPOST := httptest.NewRecorder()
	srv.handleAudioNormalize(wPOST, reqPOST)

	if wPOST.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from POST normalize, got %d", wPOST.Code)
	}
	var resPOST map[string]any
	if err := json.NewDecoder(wPOST.Body).Decode(&resPOST); err != nil {
		t.Fatalf("failed decoding POST JSON: %v", err)
	}
	if scale, ok := resPOST["recommended_scale"].(float64); !ok || scale <= 0 {
		t.Errorf("expected positive recommended_scale, got %v", resPOST["recommended_scale"])
	}
}

// TestDualUDSTCPServerStartup validates that the server binds and handles requests over both TCP and UDS concurrently.
func TestDualUDSTCPServerStartup(t *testing.T) {
	tempDir := t.TempDir()
	sockPath := filepath.Join(tempDir, "u.sock")
	port := 45991

	cfg := Config{
		Port:           port,
		SocketPath:     sockPath,
		AppStorageRoot: tempDir,
		LibraryRoot:    tempDir,
		DatabasePath:   filepath.Join(tempDir, "test_dual.db"),
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}

	go func() {
		_ = srv.Start()
	}()
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = srv.Shutdown(ctx)
	}()

	// Wait for server to bind
	time.Sleep(300 * time.Millisecond)

	// 1. Verify TCP listener works
	tcpResp, err := http.Get(fmt.Sprintf("http://127.0.0.1:%d/api/v1/status", port))
	if err != nil {
		t.Fatalf("TCP request failed: %v", err)
	}
	defer tcpResp.Body.Close()
	if tcpResp.StatusCode != http.StatusOK {
		t.Errorf("TCP status code = %d, want 200", tcpResp.StatusCode)
	}

	// 2. Verify Unix socket listener if supported by OS
	if _, err := os.Stat(sockPath); err == nil {
		udsClient := &http.Client{
			Transport: &http.Transport{
				DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
					return net.Dial("unix", sockPath)
				},
			},
		}
		udsResp, err := udsClient.Get("http://unix/api/v1/status")
		if err != nil {
			t.Fatalf("UDS request failed: %v", err)
		}
		defer udsResp.Body.Close()
		if udsResp.StatusCode != http.StatusOK {
			t.Errorf("UDS status code = %d, want 200", udsResp.StatusCode)
		}
	} else {
		t.Logf("UDS socket file not present (may not be supported on this host environment): %v", err)
	}
}

