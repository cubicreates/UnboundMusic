/*
 * Package: main
 * File: handlers_playlists_test.go
 * Purpose: Integration unit tests for /api/v1/playlists and /api/v1/favorites endpoints.
 * Subsystem: Localhost Daemon API Tests
 */

package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestPlaylistsAndFavoritesEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_daemon.db")

	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test database: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	daemon := &Daemon{
		repo: repo,
	}

	handler := daemon.Routes()

	// 1. POST /api/v1/playlists - Create custom playlist
	createBody, _ := json.Marshal(map[string]interface{}{
		"title":       "Late Night Chill",
		"description": "Lo-Fi beats and chill vibes",
		"cover_url":   "https://example.com/cover.jpg",
		"tracks": []models.PlaylistTrack{
			{
				ID:         "track_1",
				Title:      "Midnight City",
				Artist:     "M83",
				DurationMs: 240000,
			},
		},
	})

	req := httptest.NewRequest(http.MethodPost, "/api/v1/playlists", bytes.NewReader(createBody))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("expected 201 for POST /api/v1/playlists, got %d: %s", rec.Code, rec.Body.String())
	}

	var createResp struct {
		Status   string                `json:"status"`
		Playlist models.CustomPlaylist `json:"playlist"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &createResp)

	if createResp.Playlist.Title != "Late Night Chill" {
		t.Fatalf("expected playlist title 'Late Night Chill', got %s", createResp.Playlist.Title)
	}
	if len(createResp.Playlist.Tracks) != 1 {
		t.Fatalf("expected 1 track in playlist, got %d", len(createResp.Playlist.Tracks))
	}

	playlistID := createResp.Playlist.ID

	// 2. GET /api/v1/playlists - List playlists
	req = httptest.NewRequest(http.MethodGet, "/api/v1/playlists", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for GET /api/v1/playlists, got %d", rec.Code)
	}

	var listResp struct {
		Playlists []models.CustomPlaylist `json:"playlists"`
		Count     int                     `json:"count"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &listResp)

	if listResp.Count != 1 {
		t.Fatalf("expected 1 playlist, got %d", listResp.Count)
	}

	// 3. POST /api/v1/playlists/{id}/tracks - Add track
	addTrackBody, _ := json.Marshal(models.PlaylistTrack{
		ID:         "track_2",
		Title:      "Resonance",
		Artist:     "HOME",
		DurationMs: 212000,
	})

	req = httptest.NewRequest(http.MethodPost, "/api/v1/playlists/"+playlistID+"/tracks", bytes.NewReader(addTrackBody))
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for POST /api/v1/playlists/{id}/tracks, got %d: %s", rec.Code, rec.Body.String())
	}

	// 4. POST /api/v1/favorites/toggle - Favorite a track
	favBody, _ := json.Marshal(models.UserFavorite{
		TrackID:    "fav_001",
		Title:      "Favorite Song",
		Artist:     "Top Artist",
		DurationMs: 180000,
		Source:     "local",
	})

	req = httptest.NewRequest(http.MethodPost, "/api/v1/favorites/toggle", bytes.NewReader(favBody))
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for POST /api/v1/favorites/toggle, got %d: %s", rec.Code, rec.Body.String())
	}

	// 5. GET /api/v1/favorites - List favorites
	req = httptest.NewRequest(http.MethodGet, "/api/v1/favorites", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for GET /api/v1/favorites, got %d", rec.Code)
	}

	var favListResp struct {
		Favorites []models.UserFavorite `json:"favorites"`
		Count     int                   `json:"count"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &favListResp)

	if favListResp.Count != 1 || favListResp.Favorites[0].TrackID != "fav_001" {
		t.Fatalf("expected 1 favorite with ID fav_001, got count %d", favListResp.Count)
	}

	// 6. DELETE /api/v1/playlists/{id} - Delete playlist
	req = httptest.NewRequest(http.MethodDelete, "/api/v1/playlists/"+playlistID, nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for DELETE /api/v1/playlists/{id}, got %d", rec.Code)
	}
}
