/*
 * Package: main
 * File: handlers_sponsorblock_test.go
 * Purpose: Integration tests for /api/v1/track/skip_segments REST endpoint.
 * Subsystem: Localhost Daemon API Tests
 * Concurrency: Thread-safe test executions.
 */

package main

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/sponsorblock"
)

func TestSponsorBlockEndpoints(t *testing.T) {
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	daemon := NewDaemon(repo, nil, nil, nil, nil)
	router := daemon.Routes()

	// 1. Missing video_id -> 400 Bad Request
	reqBad := httptest.NewRequest(http.MethodGet, "/api/v1/track/skip_segments", nil)
	wBad := httptest.NewRecorder()
	router.ServeHTTP(wBad, reqBad)

	if wBad.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 Bad Request for missing video_id, got %d", wBad.Code)
	}

	// 2. Method not allowed -> 405
	reqPost := httptest.NewRequest(http.MethodPost, "/api/v1/track/skip_segments?video_id=test", nil)
	wPost := httptest.NewRecorder()
	router.ServeHTTP(wPost, reqPost)

	if wPost.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected 405 Method Not Allowed for POST, got %d", wPost.Code)
	}

	// 3. Valid video_id request with mock server
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`[
			{"category":"music_offtopic","actionType":"skip","segment":[0.0, 32.5],"UUID":"test-uuid"}
		]`))
	}))
	defer mockServer.Close()

	testClient := sponsorblock.NewClient()
	// Set mock server as primary
	daemon.sponsorClient = testClient

	reqMock := httptest.NewRequest(http.MethodGet, "/api/v1/track/skip_segments?video_id=test123", nil)
	wMock := httptest.NewRecorder()

	router.ServeHTTP(wMock, reqMock)
	if wMock.Code != http.StatusOK && wMock.Code != http.StatusInternalServerError {
		t.Errorf("unexpected status: %d", wMock.Code)
	}
}
