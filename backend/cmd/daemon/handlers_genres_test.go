/*
 * Package: main
 * File: handlers_genres_test.go
 * Purpose: Integration tests for moods/genres boards and genre detail REST endpoints.
 * Subsystem: Localhost Daemon API Tests
 * Concurrency: Thread-safe test executions.
 */

package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func TestGenresEndpoints(t *testing.T) {
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	daemon := NewDaemon(repo, nil, nil, nil, nil)
	router := daemon.Routes()

	// 1. Test GET /api/v1/explore/moods_genres
	req := httptest.NewRequest(http.MethodGet, "/api/v1/explore/moods_genres?gl=US&hl=en", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 OK for moods_genres, got %d", w.Code)
	}

	var moodsResp struct {
		Sections []ytmusic.GenreSection `json:"sections"`
	}
	if err := json.NewDecoder(w.Body).Decode(&moodsResp); err != nil {
		t.Fatalf("failed to decode moods_genres response: %v", err)
	}
	if len(moodsResp.Sections) == 0 {
		t.Errorf("expected non-empty sections in moods_genres")
	}

	// 2. Test GET /api/v1/explore/genre_detail
	reqDetail := httptest.NewRequest(http.MethodGet, "/api/v1/explore/genre_detail?params=default_hip-hop&name=Hip-Hop", nil)
	wDetail := httptest.NewRecorder()
	router.ServeHTTP(wDetail, reqDetail)

	if wDetail.Code != http.StatusOK {
		t.Fatalf("expected 200 OK for genre_detail, got %d", wDetail.Code)
	}

	var detailResp struct {
		Shelves []ytmusic.PlaylistShelf `json:"shelves"`
	}
	if err := json.NewDecoder(wDetail.Body).Decode(&detailResp); err != nil {
		t.Fatalf("failed to decode genre_detail response: %v", err)
	}
	if len(detailResp.Shelves) == 0 {
		t.Errorf("expected non-empty shelves in genre_detail")
	}
}
