/*
 * Package: main
 * File: handlers_radio_test.go
 * Purpose: Integration unit tests for magic radio, taste event ingestion, and taste profile HTTP endpoints.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handler tests.
 */

package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

func setupTestDaemonWithRadio(t *testing.T) (*Daemon, *database.Repository) {
	t.Helper()
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("Failed opening in-memory database: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	repo := database.NewRepository(db)
	daemon := NewDaemon(repo, nil, nil, nil, nil)
	return daemon, repo
}

func TestHandleRecordTasteEvent(t *testing.T) {
	daemon, _ := setupTestDaemonWithRadio(t)

	payload := TasteEventRequest{
		TrackID:    "track_test_01",
		Title:      "Neon Blade",
		ArtistID:   "artist_moon",
		ArtistName: "MoonDeity",
		DurationMs: 200000,
		ListenedMs: 190000, // > 85% -> COMPLETE
	}
	jsonBytes, _ := json.Marshal(payload)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/analytics/taste_event", bytes.NewReader(jsonBytes))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	daemon.HandleRecordTasteEvent(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("Expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("Failed unmarshaling response: %v", err)
	}

	if resp["status"] != "recorded" {
		t.Errorf("Expected status 'recorded', got %v", resp["status"])
	}
	if resp["event_type"] != "COMPLETE" {
		t.Errorf("Expected event_type 'COMPLETE', got %v", resp["event_type"])
	}
}

func TestHandleGetTasteProfile(t *testing.T) {
	daemon, repo := setupTestDaemonWithRadio(t)

	// Seed taste events
	_ = repo.RecordTasteEvent(models.TasteEvent{
		TrackID:    "t1",
		Title:      "Track 1",
		ArtistID:   "art_1",
		ArtistName: "Artist One",
		DurationMs: 200000,
		ListenedMs: 190000,
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/taste/profile?limit=5", nil)
	rec := httptest.NewRecorder()

	daemon.Routes().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("Expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("Failed unmarshaling response: %v", err)
	}

	artists, ok := resp["top_artists"].([]any)
	if !ok || len(artists) != 1 {
		t.Errorf("Expected 1 top artist in profile, got %d", len(artists))
	}

	// Verify CORS header
	if rec.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Errorf("Expected Access-Control-Allow-Origin: *")
	}
}

func TestHandleMagicRadioOfflineFallback(t *testing.T) {
	daemon, repo := setupTestDaemonWithRadio(t)

	// Seed offline track
	_ = repo.UpsertLocalTrack(context.Background(), &models.LocalTrack{
		ID:           "offline_track_01",
		FilePath:     "/storage/emulated/0/Music/TestTrack.mp3",
		Title:        "Test Track",
		Artist:       "Local Musician",
		DurationMs:   200000,
		Format:       "mp3",
		SourceFolder: "music",
	})

	body := MagicRadioRequest{
		LocalHour: 15,
	}
	jsonBytes, _ := json.Marshal(body)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/radio/magic", bytes.NewReader(jsonBytes))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	daemon.HandleMagicRadio(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("Expected HTTP 200, got %d: %s", rec.Code, rec.Body.String())
	}

	var resp map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("Failed unmarshaling response: %v", err)
	}

	queue, ok := resp["queue"].([]any)
	if !ok || len(queue) == 0 {
		t.Fatalf("Expected non-empty queue, got %+v", resp)
	}
}
