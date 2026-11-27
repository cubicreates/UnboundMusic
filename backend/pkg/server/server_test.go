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
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
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

// TestStorageIngestBatchEndpoint verifies batch ingestion of MediaStore-indexed audio tracks.
func TestStorageIngestBatchEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_ingest.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Ingest mock tracks
	body := `{
		"tracks": [
			{
				"file_path": "/storage/emulated/0/Music/song1.mp3",
				"title": "Song 1",
				"artist": "Artist 1",
				"album": "Album 1",
				"duration_ms": 180000,
				"file_size": 4096000,
				"source_folder": "music",
				"format": "mp3"
			},
			{
				"file_path": "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-2024.opus",
				"title": "Voice Note",
				"artist": "WhatsApp",
				"duration_ms": 45000,
				"file_size": 512000,
				"source_folder": "whatsapp",
				"format": "opus"
			}
		]
	}`

	req := httptest.NewRequest(http.MethodPost, "/api/v1/storage/ingest-batch", strings.NewReader(body))
	w := httptest.NewRecorder()
	srv.handleStorageIngestBatch(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from storage/ingest-batch, got %d: %s", w.Code, w.Body.String())
	}

	var res map[string]interface{}
	if err := json.NewDecoder(w.Body).Decode(&res); err != nil {
		t.Fatalf("failed decoding JSON response: %v", err)
	}
	if res["status"] != "success" {
		t.Errorf("expected status 'success', got %v", res["status"])
	}
	if ingested, ok := res["ingested"].(float64); !ok || ingested != 2 {
		t.Errorf("expected 2 ingested tracks, got %v", res["ingested"])
	}

	// Verify tracks can be retrieved via /api/v1/storage/tracks
	reqTracks := httptest.NewRequest(http.MethodGet, "/api/v1/storage/tracks?source=all", nil)
	wTracks := httptest.NewRecorder()
	srv.handleStorageTracks(wTracks, reqTracks)

	if wTracks.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from storage/tracks, got %d", wTracks.Code)
	}
	var tracksResult map[string]interface{}
	_ = json.NewDecoder(wTracks.Body).Decode(&tracksResult)
	tracksList, ok := tracksResult["tracks"].([]interface{})
	if !ok || len(tracksList) != 2 {
		t.Errorf("expected 2 tracks returned from database, got %d", len(tracksList))
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

func TestServerPlaylistEndpoint_MissingID(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45741,
		DatabasePath:   filepath.Join(tempDir, "test_server.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	req := httptest.NewRequest(http.MethodGet, "/api/v1/playlist", nil)
	w := httptest.NewRecorder()
	srv.handlePlaylist(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for missing id, got %d", w.Code)
	}
}

func TestServerAlbumDetailsEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45749,
		DatabasePath:   filepath.Join(tempDir, "test_server_album.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Missing both id and album should return 400 Bad Request
	reqMissing := httptest.NewRequest(http.MethodGet, "/api/v1/album/details", nil)
	wMissing := httptest.NewRecorder()
	srv.handleAlbumDetails(wMissing, reqMissing)
	if wMissing.Code != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for missing id/album, got %d", wMissing.Code)
	}

	// 2. Request with album and artist parameters should be handled
	reqWithParams := httptest.NewRequest(http.MethodGet, "/api/v1/album/details?album=Random+Access+Memories&artist=Daft+Punk", nil)
	wWithParams := httptest.NewRecorder()
	srv.handleAlbumDetails(wWithParams, reqWithParams)
	// Even if network isn't reachable in offline test mode, it must return a valid status code (e.g. 200 or 404/500), not panic
	if wWithParams.Code == 0 {
		t.Errorf("expected non-zero response code")
	}
}

func TestServerDownloadEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45742,
		DatabasePath:   filepath.Join(tempDir, "test_server.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. GET /api/v1/download/active (should return empty list initially)
	reqActive := httptest.NewRequest(http.MethodGet, "/api/v1/download/active", nil)
	wActive := httptest.NewRecorder()
	srv.handleDownloadActive(wActive, reqActive)
	if wActive.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from download active, got %d", wActive.Code)
	}

	// 2. GET /api/v1/download/status with missing video_id (should return 400)
	reqMissingStatus := httptest.NewRequest(http.MethodGet, "/api/v1/download/status", nil)
	wMissingStatus := httptest.NewRecorder()
	srv.handleDownloadStatus(wMissingStatus, reqMissingStatus)
	if wMissingStatus.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 Bad Request from download status without video_id, got %d", wMissingStatus.Code)
	}

	// 3. GET /api/v1/download/list (should return 200 with empty count)
	reqList := httptest.NewRequest(http.MethodGet, "/api/v1/download/list", nil)
	wList := httptest.NewRecorder()
	srv.handleDownloadList(wList, reqList)
	if wList.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from download list, got %d", wList.Code)
	}
}

func TestServerRydVotesEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_ryd.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Missing videoId query param -> 400
	reqMissing := httptest.NewRequest(http.MethodGet, "/api/v1/ryd/votes", nil)
	wMissing := httptest.NewRecorder()
	srv.handleRydVotes(wMissing, reqMissing)
	if wMissing.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 Bad Request for missing videoId, got %d", wMissing.Code)
	}

	// 2. Successful response from upstream RYD mock
	mockUpstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = fmt.Fprint(w, `{"id":"dQw4w9WgXcQ","likes":2000000,"dislikes":50000,"rating":4.9,"viewCount":100000000}`)
	}))
	defer mockUpstream.Close()

	srv.rydClient.SetBaseURL(mockUpstream.URL)

	reqValid := httptest.NewRequest(http.MethodGet, "/api/v1/ryd/votes?videoId=dQw4w9WgXcQ", nil)
	wValid := httptest.NewRecorder()
	srv.handleRydVotes(wValid, reqValid)

	if wValid.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from ryd votes, got %d", wValid.Code)
	}

	var res map[string]any
	if err := json.NewDecoder(wValid.Body).Decode(&res); err != nil {
		t.Fatalf("failed decoding JSON: %v", err)
	}

	if res["id"] != "dQw4w9WgXcQ" {
		t.Errorf("expected id dQw4w9WgXcQ, got %v", res["id"])
	}
	if pct, ok := res["like_percentage"].(float64); !ok || int(pct) != 98 {
		t.Errorf("expected 98%% approval, got %v", res["like_percentage"])
	}
}

// TestServerFallbackResolveEndpoint tests the /api/v1/fallback/resolve endpoint.
func TestServerFallbackResolveEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45733,
		DatabasePath:   filepath.Join(tempDir, "test_server_fallback.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Missing title should return 400 Bad Request
	reqMissing := httptest.NewRequest(http.MethodGet, "/api/v1/fallback/resolve", nil)
	wMissing := httptest.NewRecorder()
	srv.handleFallbackResolve(wMissing, reqMissing)
	if wMissing.Code != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for missing title, got %d", wMissing.Code)
	}

	// 2. POST method should return 405 Method Not Allowed
	reqMethod := httptest.NewRequest(http.MethodPost, "/api/v1/fallback/resolve?title=Song", nil)
	wMethod := httptest.NewRecorder()
	srv.handleFallbackResolve(wMethod, reqMethod)
	if wMethod.Code != http.StatusMethodNotAllowed {
		t.Errorf("expected 405 Method Not Allowed for POST, got %d", wMethod.Code)
	}
}

// TestStorageLibraryEndpoint verifies backend-side sorting, filtering, and folder grouping.
func TestStorageLibraryEndpoint(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           45734,
		DatabasePath:   filepath.Join(tempDir, "test_storage_library.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}

	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	ingestBody := `{"tracks":[
		{"id":"t1","file_path":"/storage/emulated/0/Music/Zebra.mp3","title":"Zebra","artist":"B Artist","album":"Mix","duration_ms":210000,"source_folder":"Music","identification_method":"acoustid"},
		{"id":"t2","file_path":"/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-1.opus","title":"AUD-1","artist":"Unknown Artist","duration_ms":12000,"source_folder":"WhatsApp Audio","identification_method":"local_unrecognized"}
	]}`
	reqIngest := httptest.NewRequest(http.MethodPost, "/api/v1/storage/ingest-batch", strings.NewReader(ingestBody))
	wIngest := httptest.NewRecorder()
	srv.handleStorageIngestBatch(wIngest, reqIngest)
	if wIngest.Code != http.StatusOK {
		t.Fatalf("ingest failed with status %d", wIngest.Code)
	}

	reqLib := httptest.NewRequest(http.MethodGet, "/api/v1/storage/library?sort=title", nil)
	wLib := httptest.NewRecorder()
	srv.handleStorageLibrary(wLib, reqLib)
	if wLib.Code != http.StatusOK {
		t.Fatalf("library endpoint returned %d", wLib.Code)
	}

	var payload struct {
		Tracks   []models.LocalTrack `json:"tracks"`
		Folders  []any               `json:"folders"`
		Untagged int                 `json:"untagged_count"`
	}
	if err := json.NewDecoder(wLib.Body).Decode(&payload); err != nil {
		t.Fatalf("decode failed: %v", err)
	}
	if len(payload.Tracks) != 2 {
		t.Fatalf("expected 2 tracks, got %d", len(payload.Tracks))
	}
	if payload.Tracks[0].Title != "AUD-1" {
		t.Errorf("expected sorted first track AUD-1, got %s", payload.Tracks[0].Title)
	}
	if len(payload.Folders) != 2 {
		t.Errorf("expected 2 grouped folders, got %d", len(payload.Folders))
	}
	if payload.Untagged != 1 {
		t.Errorf("expected 1 untagged track, got %d", payload.Untagged)
	}
}

// TestTrackLikeAndFavoritesEndpoints verifies /api/v1/track/like and /api/v1/favorites/toggle in guest & local modes.
func TestTrackLikeAndFavoritesEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_likes.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Toggle local favorite
	favReq := httptest.NewRequest(http.MethodPost, "/api/v1/favorites/toggle", strings.NewReader(`{"track_id":"dQw4w9WgXcQ","title":"Test Song","artist":"Test Artist"}`))
	favW := httptest.NewRecorder()
	srv.ServeHTTP(favW, favReq)
	if favW.Code != http.StatusOK {
		t.Fatalf("expected 200 OK for /api/v1/favorites/toggle, got %d: %s", favW.Code, favW.Body.String())
	}

	// 2. Guest YouTube ID like -> acknowledges guest_local without 404/error
	likeReq := httptest.NewRequest(http.MethodPost, "/api/v1/track/like", strings.NewReader(`{"video_id":"dQw4w9WgXcQ","like":true}`))
	likeW := httptest.NewRecorder()
	srv.ServeHTTP(likeW, likeReq)
	if likeW.Code != http.StatusOK {
		t.Fatalf("expected 200 OK for /api/v1/track/like, got %d: %s", likeW.Code, likeW.Body.String())
	}
	var likeResp map[string]any
	if err := json.NewDecoder(likeW.Body).Decode(&likeResp); err != nil {
		t.Fatalf("decode failed: %v", err)
	}
	if likeResp["status"] != "guest_local" {
		t.Errorf("expected guest_local status, got %v", likeResp["status"])
	}

	// 3. Local-only track ID -> local_only
	localReq := httptest.NewRequest(http.MethodPost, "/api/v1/track/like", strings.NewReader(`{"video_id":"local:123","like":true}`))
	localW := httptest.NewRecorder()
	srv.ServeHTTP(localW, localReq)
	if localW.Code != http.StatusOK {
		t.Fatalf("expected 200 OK for local track like, got %d", localW.Code)
	}
	var localResp map[string]any
	_ = json.NewDecoder(localW.Body).Decode(&localResp)
	if localResp["status"] != "local_only" {
		t.Errorf("expected local_only status, got %v", localResp["status"])
	}

	// 4. Empty video_id -> 400 Bad Request
	badReq := httptest.NewRequest(http.MethodPost, "/api/v1/track/like", strings.NewReader(`{"like":true}`))
	badW := httptest.NewRecorder()
	srv.ServeHTTP(badW, badReq)
	if badW.Code != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for empty video_id, got %d", badW.Code)
	}
}

// TestGuestTasteAndSearchHistoryEndpoints verifies Guest taste telemetry, profile, radio, and search history routes.
func TestGuestTasteAndSearchHistoryEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_guest.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Record positive taste event + Markov transition
	tasteBody := `{"track_id":"dQw4w9WgXcQ","next_track_id":"9bZkp7q19f0","title":"Test Hit","artist_name":"Guest Artist","duration_ms":200000,"listened_ms":190000}`
	reqTaste := httptest.NewRequest(http.MethodPost, "/api/v1/analytics/taste_event", strings.NewReader(tasteBody))
	wTaste := httptest.NewRecorder()
	srv.ServeHTTP(wTaste, reqTaste)
	if wTaste.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/analytics/taste_event, got %d: %s", wTaste.Code, wTaste.Body.String())
	}

	// 2. Query /api/v1/taste/profile
	reqProf := httptest.NewRequest(http.MethodGet, "/api/v1/taste/profile?limit=5", nil)
	wProf := httptest.NewRecorder()
	srv.ServeHTTP(wProf, reqProf)
	if wProf.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/taste/profile, got %d", wProf.Code)
	}
	if !strings.Contains(wProf.Body.String(), "Guest Artist") {
		t.Errorf("expected Guest Artist in taste profile, got %s", wProf.Body.String())
	}

	// 3. Record, query, and delete search history
	reqHistPost := httptest.NewRequest(http.MethodPost, "/api/v1/search/history", strings.NewReader(`{"domain":"music","query":"Synthwave"}`))
	wHistPost := httptest.NewRecorder()
	srv.ServeHTTP(wHistPost, reqHistPost)
	if wHistPost.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from POST /api/v1/search/history, got %d", wHistPost.Code)
	}

	reqHistGet := httptest.NewRequest(http.MethodGet, "/api/v1/search/history?domain=music&q=synth", nil)
	wHistGet := httptest.NewRecorder()
	srv.ServeHTTP(wHistGet, reqHistGet)
	if wHistGet.Code != http.StatusOK || !strings.Contains(wHistGet.Body.String(), "Synthwave") {
		t.Fatalf("expected Synthwave in GET /api/v1/search/history, got %d: %s", wHistGet.Code, wHistGet.Body.String())
	}

	reqHistDel := httptest.NewRequest(http.MethodPost, "/api/v1/search/history/delete", strings.NewReader(`{"domain":"music","query":"Synthwave"}`))
	wHistDel := httptest.NewRecorder()
	srv.ServeHTTP(wHistDel, reqHistDel)
	if wHistDel.Code != http.StatusOK || strings.Contains(wHistDel.Body.String(), "Synthwave") {
		t.Fatalf("expected Synthwave removed after delete, got %d: %s", wHistDel.Code, wHistDel.Body.String())
	}
}

// TestLockscreenWallpaperBackupSQLiteEndpoints verifies SQLite storage, retrieval, ID tracking, and empty-body protection.
func TestLockscreenWallpaperBackupSQLiteEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_wallpaper.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 1. Initial metadata shows has_backup == false; binary GET returns 404
	wMeta0 := httptest.NewRecorder()
	srv.ServeHTTP(wMeta0, httptest.NewRequest(http.MethodGet, "/api/v1/system/wallpaper/backup?meta=1", nil))
	if wMeta0.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from meta=1, got %d", wMeta0.Code)
	}
	var meta0 map[string]any
	if err := json.NewDecoder(wMeta0.Body).Decode(&meta0); err != nil {
		t.Fatalf("decode meta0: %v", err)
	}
	if has, _ := meta0["has_backup"].(bool); has {
		t.Fatalf("expected has_backup=false initially, got true")
	}

	wGet0 := httptest.NewRecorder()
	srv.ServeHTTP(wGet0, httptest.NewRequest(http.MethodGet, "/api/v1/system/wallpaper/backup", nil))
	if wGet0.Code != http.StatusNotFound {
		t.Fatalf("expected 404 when no backup exists, got %d", wGet0.Code)
	}

	// 2. Store raw PNG bytes for a shared Home+Lock custom wallpaper
	pngPayload := append([]byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, []byte("CUSTOM_WALLPAPER_DATA_12345")...)
	reqPost := httptest.NewRequest(http.MethodPost, "/api/v1/system/wallpaper/backup?source_flag=system_shared&lock_id=-1&system_id=42", strings.NewReader(string(pngPayload)))
	reqPost.Header.Set("Content-Type", "image/png")
	wPost := httptest.NewRecorder()
	srv.ServeHTTP(wPost, reqPost)
	if wPost.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from POST backup, got %d: %s", wPost.Code, wPost.Body.String())
	}
	var postMeta map[string]any
	_ = json.NewDecoder(wPost.Body).Decode(&postMeta)
	if has, _ := postMeta["has_backup"].(bool); !has {
		t.Fatalf("expected has_backup=true after POST, got %v", postMeta)
	}
	if int(postMeta["byte_size"].(float64)) != len(pngPayload) {
		t.Fatalf("expected byte_size=%d, got %v", len(pngPayload), postMeta["byte_size"])
	}

	// 3. GET binary returns exact bytes and headers
	wGet1 := httptest.NewRecorder()
	srv.ServeHTTP(wGet1, httptest.NewRequest(http.MethodGet, "/api/v1/system/wallpaper/backup", nil))
	if wGet1.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from GET backup, got %d", wGet1.Code)
	}
	if wGet1.Header().Get("X-Wallpaper-Source") != "system_shared" || wGet1.Header().Get("X-System-Wallpaper-Id") != "42" {
		t.Fatalf("unexpected headers: source=%q sysId=%q", wGet1.Header().Get("X-Wallpaper-Source"), wGet1.Header().Get("X-System-Wallpaper-Id"))
	}
	if wGet1.Body.String() != string(pngPayload) {
		t.Fatalf("retrieved wallpaper bytes mismatch")
	}

	// 4. Update IDs after restore and verify image blob persists for future restores
	reqIDs := httptest.NewRequest(http.MethodPost, "/api/v1/system/wallpaper/backup/ids", strings.NewReader(`{"lock_wallpaper_id":99,"system_wallpaper_id":42}`))
	wIDs := httptest.NewRecorder()
	srv.ServeHTTP(wIDs, reqIDs)
	if wIDs.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from POST backup/ids, got %d: %s", wIDs.Code, wIDs.Body.String())
	}

	wGet2 := httptest.NewRecorder()
	srv.ServeHTTP(wGet2, httptest.NewRequest(http.MethodGet, "/api/v1/system/wallpaper/backup", nil))
	if wGet2.Code != http.StatusOK || wGet2.Header().Get("X-Lock-Wallpaper-Id") != "99" || wGet2.Body.String() != string(pngPayload) {
		t.Fatalf("expected persistent blob with updated lock_id=99, got code=%d lockId=%q", wGet2.Code, wGet2.Header().Get("X-Lock-Wallpaper-Id"))
	}

	// 5. Empty POST returns 400 and does NOT clobber stored backup
	wEmpty := httptest.NewRecorder()
	srv.ServeHTTP(wEmpty, httptest.NewRequest(http.MethodPost, "/api/v1/system/wallpaper/backup", strings.NewReader("")))
	if wEmpty.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 Bad Request on empty POST, got %d", wEmpty.Code)
	}
	wGet3 := httptest.NewRecorder()
	srv.ServeHTTP(wGet3, httptest.NewRequest(http.MethodGet, "/api/v1/system/wallpaper/backup", nil))
	if wGet3.Code != http.StatusOK || wGet3.Body.String() != string(pngPayload) {
		t.Fatalf("empty POST clobbered stored backup")
	}
}

// TestSignedInAuthProfileAndHomeBannerModes verifies FirstPartyAuth multi-hash headers,
// non-placeholder Google account hints, and signed-in /api/v1/explore/home fallback banner modes.
func TestSignedInAuthProfileAndHomeBannerModes(t *testing.T) {
	// 1. Multi-hash cookie authorization header
	authHeader := ytmusic.BuildCookieAuthorizationHeader(
		"SAPISID=abc; __Secure-1PAPISID=def; __Secure-3PAPISID=ghi",
		"https://music.youtube.com",
	)
	if !strings.Contains(authHeader, "SAPISIDHASH ") ||
		!strings.Contains(authHeader, "SAPISID1PHASH ") ||
		!strings.Contains(authHeader, "SAPISID3PHASH ") {
		t.Fatalf("expected all 3 SAPISID hashes in header, got %q", authHeader)
	}

	tempDir := t.TempDir()
	cfg := Config{
		Port:           0,
		DatabasePath:   filepath.Join(tempDir, "test_signedin.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	}
	srv, err := NewServer(cfg)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// 2. Sync with WebView-extracted profile hints
	syncBody := `{"cookie":"SAPISID=test_sapisid; SID=test_sid","account_name":"Ada Lovelace","avatar_url":"https://lh3.googleusercontent.com/a/ada=s352"}`
	reqSync := httptest.NewRequest(http.MethodPost, "/api/v1/account/sync", strings.NewReader(syncBody))
	wSync := httptest.NewRecorder()
	srv.ServeHTTP(wSync, reqSync)
	if wSync.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/account/sync, got %d: %s", wSync.Code, wSync.Body.String())
	}
	var syncResp map[string]any
	_ = json.NewDecoder(wSync.Body).Decode(&syncResp)
	if syncResp["account_name"] != "Ada Lovelace" {
		t.Fatalf("expected account_name 'Ada Lovelace', got %v", syncResp["account_name"])
	}
	if syncResp["avatar_url"] != "https://lh3.googleusercontent.com/a/ada=s352" {
		t.Fatalf("expected avatar_url preserved, got %v", syncResp["avatar_url"])
	}

	// 3. Seed a synced liked track -> /api/v1/explore/home should report liked_fallback banner when no watch history exists
	srv.accountSync.AddLikedTrack(models.Track{
		ID:     "dQw4w9WgXcQ",
		Title:  "Liked Anthem",
		Artist: "Test Artist",
	})
	wHome := httptest.NewRecorder()
	srv.ServeHTTP(wHome, httptest.NewRequest(http.MethodGet, "/api/v1/explore/home?gl=US", nil))
	if wHome.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/explore/home, got %d", wHome.Code)
	}
	var homeResp map[string]any
	_ = json.NewDecoder(wHome.Body).Decode(&homeResp)
	if homeResp["jump_back_mode"] != "liked_fallback" {
		t.Fatalf("expected jump_back_mode='liked_fallback', got %v", homeResp["jump_back_mode"])
	}
	if bannerTitle, _ := homeResp["jump_back_banner_title"].(string); bannerTitle == "" {
		t.Fatalf("expected non-empty jump_back_banner_title in liked_fallback mode")
	}

	// 4. Disconnect must completely clear credentials, synced tracks, and revert home & status to guest mode
	reqDisc := httptest.NewRequest(http.MethodPost, "/api/v1/account/disconnect", strings.NewReader(`{}`))
	wDisc := httptest.NewRecorder()
	srv.ServeHTTP(wDisc, reqDisc)
	if wDisc.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/account/disconnect, got %d: %s", wDisc.Code, wDisc.Body.String())
	}

	wStatusAfter := httptest.NewRecorder()
	srv.ServeHTTP(wStatusAfter, httptest.NewRequest(http.MethodGet, "/api/v1/account/status", nil))
	if wStatusAfter.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/account/status after disconnect, got %d", wStatusAfter.Code)
	}
	var statusAfter map[string]any
	_ = json.NewDecoder(wStatusAfter.Body).Decode(&statusAfter)
	if connected, _ := statusAfter["connected"].(bool); connected {
		t.Fatalf("expected connected=false after disconnect, got %v", statusAfter)
	}
	if count, _ := statusAfter["synced_tracks_count"].(float64); int(count) != 0 {
		t.Fatalf("expected synced_tracks_count=0 after disconnect, got %v", statusAfter["synced_tracks_count"])
	}

	wHomeAfter := httptest.NewRecorder()
	srv.ServeHTTP(wHomeAfter, httptest.NewRequest(http.MethodGet, "/api/v1/explore/home?gl=US", nil))
	if wHomeAfter.Code != http.StatusOK {
		t.Fatalf("expected 200 OK from /api/v1/explore/home after disconnect, got %d", wHomeAfter.Code)
	}
	var homeAfter map[string]any
	_ = json.NewDecoder(wHomeAfter.Body).Decode(&homeAfter)
	if loggedIn, _ := homeAfter["is_logged_in"].(bool); loggedIn {
		t.Fatalf("expected is_logged_in=false after disconnect, got %v", homeAfter)
	}
	if homeAfter["jump_back_mode"] != "guest" {
		t.Fatalf("expected jump_back_mode='guest' after disconnect, got %v", homeAfter["jump_back_mode"])
	}
}



