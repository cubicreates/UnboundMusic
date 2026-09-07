/*
 * Package: main
 * File: handlers_lyrics_test.go
 * Purpose: Integration tests for /api/v1/lyrics REST endpoint.
 * Subsystem: Localhost Daemon API Tests
 * Concurrency: Thread-safe test executions.
 */

package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestLyricsEndpoints(t *testing.T) {
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	daemon := NewDaemon(repo, nil, nil, nil, nil)
	router := daemon.Routes()

	// 1. Missing params: 400 Bad Request
	reqBad := httptest.NewRequest(http.MethodGet, "/api/v1/lyrics", nil)
	wBad := httptest.NewRecorder()
	router.ServeHTTP(wBad, reqBad)
	if wBad.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for missing params, got %d", wBad.Code)
	}

	// 2. Method not allowed: 405
	reqPost := httptest.NewRequest(http.MethodPost, "/api/v1/lyrics?track_id=test", nil)
	wPost := httptest.NewRecorder()
	router.ServeHTTP(wPost, reqPost)
	if wPost.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected 405 for POST, got %d", wPost.Code)
	}

	// 3. Pre-seed SQLite cache with lyrics containing Romanized line
	testPayload := &models.LyricsPayload{
		TrackID:     "cached_track_1",
		Title:       "Test Song",
		Artist:      "Test Artist",
		PlainLyrics: "Just a test",
		Lines: []models.LyricLine{
			{
				StartMs:   1500,
				EndMs:     4000,
				Text:      "ありがとう",
				Romanized: "arigatou",
			},
		},
		Source: "SQLite Cache",
	}
	if err := repo.SaveLyrics(context.Background(), testPayload); err != nil {
		t.Fatalf("failed to seed lyrics in sqlite: %v", err)
	}

	// 4. GET /api/v1/lyrics?track_id=cached_track_1
	reqGet := httptest.NewRequest(http.MethodGet, "/api/v1/lyrics?track_id=cached_track_1", nil)
	wGet := httptest.NewRecorder()
	router.ServeHTTP(wGet, reqGet)

	if wGet.Code != http.StatusOK {
		t.Fatalf("expected 200 OK for cached lyrics, got %d: %s", wGet.Code, wGet.Body.String())
	}

	var resp models.LyricsPayload
	if err := json.NewDecoder(wGet.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode lyrics payload: %v", err)
	}

	if resp.TrackID != "cached_track_1" {
		t.Errorf("expected track_id 'cached_track_1', got %q", resp.TrackID)
	}
	if len(resp.Lines) != 1 {
		t.Fatalf("expected 1 lyric line, got %d", len(resp.Lines))
	}
	if resp.Lines[0].Romanized != "arigatou" {
		t.Errorf("expected romanized 'arigatou', got %q", resp.Lines[0].Romanized)
	}
}
