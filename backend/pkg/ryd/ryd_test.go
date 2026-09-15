/*
 * Package: ryd
 * File: ryd_test.go
 * Purpose: Unit tests for Return YouTube Dislike (RYD) API integration, caching, error handling, and ratio calculations.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package ryd

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestGetVotes_EmptyVideoID(t *testing.T) {
	client := NewClient()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, err := client.GetVotes(ctx, "")
	if err == nil {
		t.Fatalf("expected error for empty videoID, got nil")
	}
}

func TestGetVotes_SuccessAndCache(t *testing.T) {
	var requestCount int32

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&requestCount, 1)
		vid := r.URL.Query().Get("videoId")
		if vid != "test12345" {
			w.WriteHeader(http.StatusNotFound)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = fmt.Fprint(w, `{
			"id": "test12345",
			"likes": 900,
			"dislikes": 100,
			"rating": 4.8,
			"viewCount": 50000,
			"deleted": false
		}`)
	}))
	defer ts.Close()

	client := NewClient()
	client.SetBaseURL(ts.URL)

	ctx := context.Background()

	// First call - hits server
	data, err := client.GetVotes(ctx, "test12345")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if data.ID != "test12345" {
		t.Errorf("expected ID 'test12345', got '%s'", data.ID)
	}
	if data.Likes != 900 {
		t.Errorf("expected 900 likes, got %d", data.Likes)
	}
	if data.Dislikes != 100 {
		t.Errorf("expected 100 dislikes, got %d", data.Dislikes)
	}
	if data.LikePercentage != 90 {
		t.Errorf("expected 90%% like ratio, got %d%%", data.LikePercentage)
	}
	if data.ViewCount != 50000 {
		t.Errorf("expected 50000 views, got %d", data.ViewCount)
	}

	// Second call - must hit cache
	cachedData, err := client.GetVotes(ctx, "test12345")
	if err != nil {
		t.Fatalf("unexpected error on cached call: %v", err)
	}
	if cachedData.LikePercentage != 90 {
		t.Errorf("cached like ratio mismatch: %d", cachedData.LikePercentage)
	}

	count := atomic.LoadInt32(&requestCount)
	if count != 1 {
		t.Errorf("expected exactly 1 upstream request due to cache, got %d", count)
	}
}

func TestGetVotes_NotFound(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer ts.Close()

	client := NewClient()
	client.SetBaseURL(ts.URL)

	_, err := client.GetVotes(context.Background(), "unknown_id")
	if err == nil {
		t.Fatalf("expected error for 404 response, got nil")
	}
}

func TestGetVotes_RatioBoundaryCalculations(t *testing.T) {
	testCases := []struct {
		name        string
		jsonResp    string
		expectedPct int
	}{
		{
			name:        "Zero votes defaults to 100",
			jsonResp:    `{"id":"vid0","likes":0,"dislikes":0,"rating":5.0,"viewCount":10}`,
			expectedPct: 100,
		},
		{
			name:        "All dislikes is 0",
			jsonResp:    `{"id":"vid1","likes":0,"dislikes":50,"rating":1.0,"viewCount":100}`,
			expectedPct: 0,
		},
		{
			name:        "Even split is 50",
			jsonResp:    `{"id":"vid2","likes":500,"dislikes":500,"rating":3.0,"viewCount":2000}`,
			expectedPct: 50,
		},
		{
			name:        "High approval 98 percent",
			jsonResp:    `{"id":"vid3","likes":98,"dislikes":2,"rating":4.9,"viewCount":1000}`,
			expectedPct: 98,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Content-Type", "application/json")
				_, _ = fmt.Fprint(w, tc.jsonResp)
			}))
			defer ts.Close()

			client := NewClient()
			client.SetBaseURL(ts.URL)

			data, err := client.GetVotes(context.Background(), "sample")
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if data.LikePercentage != tc.expectedPct {
				t.Errorf("expected %d%%, got %d%%", tc.expectedPct, data.LikePercentage)
			}
		})
	}
}
