/*
 * Package: sponsorblock
 * File: client_test.go
 * Purpose: Unit tests for SponsorBlock parsing, milliseconds conversion, and 404 handling.
 * Subsystem: Playback Resilience Engine
 * Concurrency: Standard Go testing framework.
 */

package sponsorblock

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestParseSegmentsJSON(t *testing.T) {
	sampleJSON := `[
		{
			"category": "music_offtopic",
			"actionType": "skip",
			"segment": [0.0, 38.45],
			"UUID": "uuid-1234"
		},
		{
			"category": "music_offtopic",
			"actionType": "skip",
			"segment": [210.5, 235.0],
			"UUID": "uuid-5678"
		}
	]`

	segments, err := ParseSegmentsJSON([]byte(sampleJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing segments: %v", err)
	}

	if len(segments) != 2 {
		t.Fatalf("expected 2 segments, got %d", len(segments))
	}

	if segments[0].StartMs != 0 || segments[0].EndMs != 38450 {
		t.Errorf("expected segment 0 to be [0, 38450], got [%d, %d]", segments[0].StartMs, segments[0].EndMs)
	}

	if segments[1].StartMs != 210500 || segments[1].EndMs != 235000 {
		t.Errorf("expected segment 1 to be [210500, 235000], got [%d, %d]", segments[1].StartMs, segments[1].EndMs)
	}
}

func TestGetMusicSkipSegments_ServerMock(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		videoID := r.URL.Query().Get("videoID")
		if videoID == "found_video" {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`[{"category":"music_offtopic","actionType":"skip","segment":[5.0, 42.0],"UUID":"u1"}]`))
			return
		}
		if videoID == "empty_video" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusBadRequest)
	}))
	defer ts.Close()

	client := NewClient()
	client.primaryURL = ts.URL
	client.fallbackURL = ""

	// 1. Found video
	segs, err := client.GetMusicSkipSegments(context.Background(), "found_video")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(segs) != 1 || segs[0].StartMs != 5000 || segs[0].EndMs != 42000 {
		t.Errorf("unexpected segments: %+v", segs)
	}

	// Verify caching
	segsCached, err := client.GetMusicSkipSegments(context.Background(), "found_video")
	if err != nil || len(segsCached) != 1 {
		t.Errorf("expected cached segments, got %+v", segsCached)
	}

	// 2. 404 Video (No segments)
	emptySegs, err := client.GetMusicSkipSegments(context.Background(), "empty_video")
	if err != nil {
		t.Fatalf("unexpected error for 404 video: %v", err)
	}
	if len(emptySegs) != 0 {
		t.Errorf("expected empty segments for 404, got %d", len(emptySegs))
	}
}
