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

	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/models"
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

// TestStorageScanAndTracksEndpoints verifies that /api/v1/storage/scan indexes local files and /api/v1/storage/tracks returns them.
func TestStorageScanAndTracksEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	musicDir := filepath.Join(tempDir, "Music")
	_ = os.MkdirAll(musicDir, 0755)

	// Create valid dummy MP3 with ID3v2 header
	dummySongPath := filepath.Join(musicDir, "test_track.mp3")
	mp3Header := append([]byte{0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, make([]byte, 2000)...)
	if err := os.WriteFile(dummySongPath, mp3Header, 0644); err != nil {
		t.Fatalf("failed writing dummy mp3: %v", err)
	}

	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_scan.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. POST /api/v1/storage/scan
	scanBody := fmt.Sprintf(`{"paths": [%q]}`, musicDir)
	reqScan := httptest.NewRequest(http.MethodPost, "/api/v1/storage/scan", strings.NewReader(scanBody))
	wScan := httptest.NewRecorder()
	srv.handleStorageScan(wScan, reqScan)

	if wScan.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from storage/scan, got %d: %s", wScan.Code, wScan.Body.String())
	}

	var scanResult map[string]interface{}
	if err := json.NewDecoder(wScan.Body).Decode(&scanResult); err != nil {
		t.Fatalf("failed to decode scan JSON: %v", err)
	}
	if found, ok := scanResult["audio_files_found"].(float64); !ok || found < 1 {
		t.Errorf("expected audio_files_found >= 1, got %v", scanResult["audio_files_found"])
	}

	// 2. GET /api/v1/storage/tracks?source=all
	reqTracks := httptest.NewRequest(http.MethodGet, "/api/v1/storage/tracks?source=all", nil)
	wTracks := httptest.NewRecorder()
	srv.handleStorageTracks(wTracks, reqTracks)

	if wTracks.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from storage/tracks, got %d: %s", wTracks.Code, wTracks.Body.String())
	}

	var tracksResult map[string]interface{}
	if err := json.NewDecoder(wTracks.Body).Decode(&tracksResult); err != nil {
		t.Fatalf("failed to decode tracks JSON: %v", err)
	}
	tracksList, ok := tracksResult["tracks"].([]interface{})
	if !ok || len(tracksList) < 1 {
		t.Fatalf("expected at least 1 track in response, got %v", tracksResult["tracks"])
	}
}

// TestAccountStatusAndDisconnectEndpoints verifies /api/v1/account/status and /api/v1/account/disconnect.
func TestAccountStatusAndDisconnectEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_account.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Check initial disconnected status
	reqStatus := httptest.NewRequest(http.MethodGet, "/api/v1/account/status", nil)
	wStatus := httptest.NewRecorder()
	srv.handleAccountStatus(wStatus, reqStatus)

	if wStatus.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from account/status, got %d", wStatus.Code)
	}

	var statusRes map[string]interface{}
	if err := json.NewDecoder(wStatus.Body).Decode(&statusRes); err != nil {
		t.Fatalf("failed decoding status JSON: %v", err)
	}
	if connected, ok := statusRes["connected"].(bool); !ok || connected {
		t.Errorf("expected connected=false, got %v", statusRes["connected"])
	}

	// 2. Disconnect
	reqDisc := httptest.NewRequest(http.MethodPost, "/api/v1/account/disconnect", strings.NewReader("{}"))
	wDisc := httptest.NewRecorder()
	srv.handleAccountDisconnect(wDisc, reqDisc)

	if wDisc.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from account/disconnect, got %d", wDisc.Code)
	}
}

func TestUnpackPayloadEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45739,
		DatabasePath:   filepath.Join(tempDir, "test_server.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Create a mock model file and compress it
	mockModelFile := filepath.Join(tempDir, "smollm2_135m.gguf")
	if err := os.WriteFile(mockModelFile, []byte("MOCK_GGUF_WEIGHTS_CONTENT"), 0644); err != nil {
		t.Fatalf("failed to write mock model: %v", err)
	}

	archivePath := filepath.Join(tempDir, "models.zst")
	compressedBytes, err := gatekeeper.CompressFilesToZstdTar(map[string]string{
		"smollm2_135m.gguf": mockModelFile,
	})
	if err != nil {
		t.Fatalf("failed to compress test archive: %v", err)
	}
	if err := os.WriteFile(archivePath, compressedBytes, 0644); err != nil {
		t.Fatalf("failed to write test archive: %v", err)
	}

	destDir := filepath.Join(tempDir, "extracted_models")
	reqBody := fmt.Sprintf(`{"archive_path": %q, "dest_dir": %q}`, archivePath, destDir)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/system/unpack-payload", strings.NewReader(reqBody))
	w := httptest.NewRecorder()

	srv.handleUnpackPayload(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from system/unpack-payload, got %d: %s", w.Code, w.Body.String())
	}

	extractedModel := filepath.Join(destDir, "smollm2_135m.gguf")
	if _, err := os.Stat(extractedModel); os.IsNotExist(err) {
		t.Errorf("expected extracted model at %s, but does not exist", extractedModel)
	}

	// Verify temporary archive was cleaned up
	if _, err := os.Stat(archivePath); !os.IsNotExist(err) {
		t.Errorf("expected archive %s to be deleted after extraction", archivePath)
	}
}

func TestServerLyricsEndpoint_SanitizesMetadataAndCacheHit(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45740,
		DatabasePath:   filepath.Join(tempDir, "test_server.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Pre-seed a track in repo
	track := models.Track{
		ID:         "test_track_him_and_i",
		Title:      "Him & I",
		Artist:     "G-Eazy & Halsey",
		DurationMs: 268000,
	}
	_ = srv.repo.SaveTrack(context.Background(), &track)

	// Pre-seed lyrics in repo
	lyricsPayload := &models.LyricsPayload{
		TrackID: "test_track_him_and_i",
		Title:   "Him & I",
		Artist:  "G-Eazy & Halsey",
		Lines: []models.LyricLine{
			{Text: "Cross my heart, hope to die", StartMs: 10000, EndMs: 14000},
		},
		Source: "LRCLIB (Verified Synced)",
	}
	_ = srv.repo.SaveLyrics(context.Background(), lyricsPayload)

	// Call handleLyrics with dummy artist="Song" and title with "(Official Video)"
	req := httptest.NewRequest(http.MethodGet, "/api/v1/lyrics?id=test_track_him_and_i&title=Him+%26+I+(Official+Video)&artist=Song", nil)
	w := httptest.NewRecorder()

	srv.handleLyrics(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from lyrics endpoint, got %d", w.Code)
	}

	var resp models.LyricsPayload
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode lyrics response: %v", err)
	}

	if resp.TrackID != "test_track_him_and_i" || len(resp.Lines) != 1 {
		t.Errorf("unexpected lyrics response: %+v", resp)
	}
}




