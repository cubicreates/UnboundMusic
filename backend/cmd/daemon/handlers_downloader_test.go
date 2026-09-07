/*
 * Package: main
 * File: handlers_downloader_test.go
 * Purpose: Integration unit tests for download manager REST API endpoints.
 * Subsystem: Localhost Daemon API
 * Concurrency: Standard Go testing framework.
 */

package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/downloader"
)

func setupTestDaemonWithDownloader(t *testing.T) (*Daemon, string, func()) {
	tempDir := filepath.Join(os.TempDir(), "unbound_daemon_dl_test")
	_ = os.MkdirAll(tempDir, 0755)

	dbPath := filepath.Join(tempDir, "test.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test db: %v", err)
	}
	repo := database.NewRepository(db)

	dlDir := filepath.Join(tempDir, "downloads")
	_ = os.MkdirAll(dlDir, 0755)
	dlMgr := downloader.NewManager(dlDir, nil, repo)

	d := NewDaemon(repo, nil, nil, nil, nil, dlMgr)

	cleanup := func() {
		db.Close()
		_ = os.RemoveAll(tempDir)
	}

	return d, dlDir, cleanup
}

func TestDownloadEndpoints(t *testing.T) {
	daemon, _, cleanup := setupTestDaemonWithDownloader(t)
	defer cleanup()

	handler := daemon.Routes()

	// 1. POST /api/v1/download/start
	startBody := map[string]string{
		"video_id":    "dl_test_vid_1",
		"title":       "Midnight City",
		"artist":      "M83",
		"album":       "Hurry Up We're Dreaming",
		"artwork_url": "https://example.com/art.jpg",
	}
	startBytes, _ := json.Marshal(startBody)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/download/start", bytes.NewReader(startBytes))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("POST /api/v1/download/start returned %d: %s", w.Code, w.Body.String())
	}

	var startResp downloader.DownloadTask
	if err := json.Unmarshal(w.Body.Bytes(), &startResp); err != nil {
		t.Fatalf("failed to decode start response: %v", err)
	}
	if startResp.VideoID != "dl_test_vid_1" {
		t.Errorf("expected video_id dl_test_vid_1, got %s", startResp.VideoID)
	}
	if startResp.Title != "Midnight City" {
		t.Errorf("expected title Midnight City, got %s", startResp.Title)
	}

	// 2. GET /api/v1/download/status?video_id=dl_test_vid_1
	reqStatus := httptest.NewRequest(http.MethodGet, "/api/v1/download/status?video_id=dl_test_vid_1", nil)
	wStatus := httptest.NewRecorder()
	handler.ServeHTTP(wStatus, reqStatus)

	if wStatus.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/download/status returned %d: %s", wStatus.Code, wStatus.Body.String())
	}

	var statusResp downloader.DownloadTask
	if err := json.Unmarshal(wStatus.Body.Bytes(), &statusResp); err != nil {
		t.Fatalf("failed to decode status response: %v", err)
	}
	if statusResp.VideoID != "dl_test_vid_1" {
		t.Errorf("expected video_id dl_test_vid_1 in status, got %s", statusResp.VideoID)
	}

	// 3. GET /api/v1/download/active
	reqActive := httptest.NewRequest(http.MethodGet, "/api/v1/download/active", nil)
	wActive := httptest.NewRecorder()
	handler.ServeHTTP(wActive, reqActive)

	if wActive.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/download/active returned %d: %s", wActive.Code, wActive.Body.String())
	}

	var activeResp []*downloader.DownloadTask
	if err := json.Unmarshal(wActive.Body.Bytes(), &activeResp); err != nil {
		t.Fatalf("failed to decode active response: %v", err)
	}
	if len(activeResp) == 0 {
		t.Fatalf("expected at least 1 active download, got 0")
	}

	// 4. POST /api/v1/download/cancel
	cancelBody := map[string]string{"video_id": "dl_test_vid_1"}
	cancelBytes, _ := json.Marshal(cancelBody)
	reqCancel := httptest.NewRequest(http.MethodPost, "/api/v1/download/cancel", bytes.NewReader(cancelBytes))
	reqCancel.Header.Set("Content-Type", "application/json")
	wCancel := httptest.NewRecorder()
	handler.ServeHTTP(wCancel, reqCancel)

	if wCancel.Code != http.StatusOK {
		t.Fatalf("POST /api/v1/download/cancel returned %d: %s", wCancel.Code, wCancel.Body.String())
	}

	// 5. POST /api/v1/download/delete
	delBody := map[string]interface{}{"video_id": "dl_test_vid_1", "delete_file": true}
	delBytes, _ := json.Marshal(delBody)
	reqDel := httptest.NewRequest(http.MethodPost, "/api/v1/download/delete", bytes.NewReader(delBytes))
	reqDel.Header.Set("Content-Type", "application/json")
	wDel := httptest.NewRecorder()
	handler.ServeHTTP(wDel, reqDel)

	if wDel.Code != http.StatusOK {
		t.Fatalf("POST /api/v1/download/delete returned %d: %s", wDel.Code, wDel.Body.String())
	}
}
