/*
 * Package: main
 * File: handlers_test.go
 * Purpose: Automated HTTP controller tests for Directive 08 REST API endpoints.
 * Subsystem: Daemon Test Suite
 * Concurrency: Thread-safe testing.
 */

package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/moods"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// setupTestDaemon creates an isolated Daemon instance using an in-memory or temp SQLite database.
func setupTestDaemon(t *testing.T) (*Daemon, func()) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "daemon_test.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to initialize test database: %v", err)
	}

	repo := database.NewRepository(db)
	ytClient := ytmusic.NewClient()
	exploreEng := ytmusic.NewExploreEngine(repo)
	moodEng := moods.NewEngine(exploreEng)
	aiRunner := ai.NewRunner() // Heuristic fallback mode with 0 MB idle RAM

	d := NewDaemon(repo, exploreEng, moodEng, aiRunner, ytClient)

	cleanup := func() {
		_ = db.Close()
	}

	return d, cleanup
}

// TestGetChartsHandlerReturns200AndJSON asserts 200 status and valid JSON payload.
func TestGetChartsHandlerReturns200AndJSON(t *testing.T) {
	d, cleanup := setupTestDaemon(t)
	defer cleanup()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/explore/charts?gl=US&hl=en", nil)
	rec := httptest.NewRecorder()

	d.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp struct {
		Tracks []models.TrackItem `json:"tracks"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode charts JSON: %v", err)
	}

	t.Logf("Received %d regional chart tracks", len(resp.Tracks))
}

// TestMoodCapsulesHandlerReturns200 asserts 200 status and valid dayparting structure.
func TestMoodCapsulesHandlerReturns200(t *testing.T) {
	d, cleanup := setupTestDaemon(t)
	defer cleanup()

	// Test with explicit hour 18 (WORKOUT_AND_DRIVE)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/explore/moods?hour=18", nil)
	rec := httptest.NewRecorder()

	d.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp struct {
		ActiveWindow string               `json:"active_window"`
		LocalHour    int                  `json:"local_hour"`
		Capsules     []models.MoodCapsule `json:"capsules"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode moods JSON: %v", err)
	}

	if resp.ActiveWindow != "WORKOUT_AND_DRIVE" {
		t.Errorf("expected active window 'WORKOUT_AND_DRIVE', got %q", resp.ActiveWindow)
	}

	if len(resp.Capsules) == 0 {
		t.Errorf("expected non-empty capsules array for workout window")
	}
}

// TestVibeSearchHandlerFallback passes query to /api/v1/search/vibe with heuristic AI runner and asserts valid parsed output.
func TestVibeSearchHandlerFallback(t *testing.T) {
	d, cleanup := setupTestDaemon(t)
	defer cleanup()

	body := []byte(`{"prompt": "chill rainy late night beats"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/search/vibe", bytes.NewReader(body))
	rec := httptest.NewRecorder()

	d.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp struct {
		VibeResult  models.VibeQueryResult `json:"vibe_result"`
		RadioTracks []models.TrackItem     `json:"radio_tracks"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode vibe search response: %v", err)
	}

	if resp.VibeResult.OriginalPrompt != "chill rainy late night beats" {
		t.Errorf("expected original prompt 'chill rainy late night beats', got %q", resp.VibeResult.OriginalPrompt)
	}

	if resp.VibeResult.EnergyLevel == "" {
		t.Errorf("expected parsed energy level, got empty string")
	}
}

// TestStorageScanHandlerWithPaths asserts valid scan response payload.
func TestStorageScanHandlerWithPaths(t *testing.T) {
	d, cleanup := setupTestDaemon(t)
	defer cleanup()

	tempDir := t.TempDir()
	// Create a dummy audio file with ID3 header
	audioFile := filepath.Join(tempDir, "test_song.mp3")
	dummyData := append([]byte("ID3\x03\x00\x00\x00\x00\x00\x00"), make([]byte, 1024)...)
	if err := os.WriteFile(audioFile, dummyData, 0644); err != nil {
		t.Fatalf("failed creating dummy audio file: %v", err)
	}

	payload := map[string][]string{
		"paths": {tempDir},
	}
	body, _ := json.Marshal(payload)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/storage/scan", bytes.NewReader(body))
	rec := httptest.NewRecorder()

	d.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp struct {
		Status           string `json:"status"`
		ScannedFiles     int    `json:"scanned_files"`
		AudioFilesFound  int    `json:"audio_files_found"`
		NewTracksIndexed int    `json:"new_tracks_indexed"`
		UnchangedTracks  int    `json:"unchanged_tracks"`
		ElapsedMs        int64  `json:"elapsed_ms"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode scan response JSON: %v", err)
	}

	if resp.Status != "completed" {
		t.Errorf("expected status 'completed', got %q", resp.Status)
	}

	if resp.AudioFilesFound != 1 {
		t.Errorf("expected 1 audio file found, got %d", resp.AudioFilesFound)
	}

	if resp.NewTracksIndexed != 1 {
		t.Errorf("expected 1 new track indexed, got %d", resp.NewTracksIndexed)
	}
}

// TestGetLocalTracksHandler verifies local tracks retrieval.
func TestGetLocalTracksHandler(t *testing.T) {
	d, cleanup := setupTestDaemon(t)
	defer cleanup()

	// Seed database with a track
	track := &models.LocalTrack{
		ID:           "test-id-1",
		FilePath:     "/storage/music/song.mp3",
		Title:        "Test Song",
		Artist:       "Test Artist",
		Album:        "Test Album",
		DurationMs:   180000,
		Format:       "mp3",
		FileSize:     5000000,
		SourceFolder: "whatsapp",
		DateIndexed:  1725350000,
		MTime:        1725340000,
	}
	_ = d.repo.UpsertLocalTrack(context.Background(), track)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/storage/tracks?source=whatsapp", nil)
	rec := httptest.NewRecorder()

	d.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d", rec.Code)
	}

	var resp struct {
		Tracks []models.LocalTrack `json:"tracks"`
	}
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode tracks JSON: %v", err)
	}

	if len(resp.Tracks) != 1 {
		t.Fatalf("expected 1 track, got %d", len(resp.Tracks))
	}

	if resp.Tracks[0].Title != "Test Song" {
		t.Errorf("expected title 'Test Song', got %q", resp.Tracks[0].Title)
	}
}

// TestCORSHeaders validates that CORS headers are appropriately injected.
func TestCORSHeaders(t *testing.T) {
	d, cleanup := setupTestDaemon(t)
	defer cleanup()

	req := httptest.NewRequest(http.MethodOptions, "/api/v1/explore/charts", nil)
	rec := httptest.NewRecorder()

	d.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Errorf("expected 204 No Content on OPTIONS, got %d", rec.Code)
	}

	if rec.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Errorf("missing or incorrect Access-Control-Allow-Origin header")
	}
}
