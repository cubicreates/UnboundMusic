package upstream

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestMirrorManagerPipedSuccess(t *testing.T) {
	// Mock Piped instance server
	mockPiped := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/streams/test_video_123" {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{
				"title": "Mock Mirror Song",
				"duration": 210,
				"audioStreams": [
					{
						"url": "https://googlevideo.mock/audio.opus",
						"bitrate": 160000,
						"codec": "opus",
						"format": "webm",
						"contentLength": 4200000
					}
				]
			}`))
			return
		}
		if r.URL.Path == "/search" {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(`{
				"items": [
					{
						"url": "/watch?v=dQw4w9WgXcQ",
						"title": "Search Result Song",
						"uploaderName": "Search Artist",
						"duration": 180,
						"thumbnail": "https://img.mock/cover.jpg"
					}
				]
			}`))
			return
		}
		http.NotFound(w, r)
	}))
	defer mockPiped.Close()

	inst := &MirrorInstance{
		BaseURL:  mockPiped.URL,
		Type:     TypePiped,
		IsActive: true,
	}

	mgr := NewMirrorManager([]*MirrorInstance{inst})

	// Test stream resolution
	stream, err := mgr.FetchStreamFromMirror(context.Background(), "test_video_123")
	if err != nil {
		t.Fatalf("FetchStreamFromMirror failed: %v", err)
	}
	if stream.StreamURL != "https://googlevideo.mock/audio.opus" {
		t.Errorf("unexpected stream URL: %s", stream.StreamURL)
	}
	if stream.Codec != "OPUS" {
		t.Errorf("expected OPUS codec, got %s", stream.Codec)
	}
	if stream.DurationMs != 210000 {
		t.Errorf("expected 210000ms, got %d", stream.DurationMs)
	}

	// Test search fallback
	tracks, err := mgr.SearchTracksFromMirror(context.Background(), "Adele")
	if err != nil {
		t.Fatalf("SearchTracksFromMirror failed: %v", err)
	}
	if len(tracks) != 1 {
		t.Fatalf("expected 1 track, got %d", len(tracks))
	}
	if tracks[0].ID != "dQw4w9WgXcQ" {
		t.Errorf("expected video ID dQw4w9WgXcQ, got %s", tracks[0].ID)
	}
	if tracks[0].Title != "Search Result Song" {
		t.Errorf("expected Title 'Search Result Song', got %s", tracks[0].Title)
	}
}

func TestMirrorManagerFailoverToInvidious(t *testing.T) {
	// Failing Piped server (HTTP 500)
	failingPiped := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "upstream rate limited", http.StatusServiceUnavailable)
	}))
	defer failingPiped.Close()

	// Working Invidious server
	workingInvidious := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{
			"title": "Invidious Track",
			"lengthSeconds": 240,
			"adaptiveFormats": [
				{
					"url": "https://googlevideo.mock/invidious_stream.m4a",
					"bitrate": "128000",
					"type": "audio/mp4; codecs=\"mp4a.40.2\"",
					"encoding": "aac",
					"clen": "3500000"
				}
			]
		}`))
	}))
	defer workingInvidious.Close()

	mgr := NewMirrorManager([]*MirrorInstance{
		{BaseURL: failingPiped.URL, Type: TypePiped, IsActive: true},
		{BaseURL: workingInvidious.URL, Type: TypeInvidious, IsActive: true},
	})

	stream, err := mgr.FetchStreamFromMirror(context.Background(), "vid_fallback_test")
	if err != nil {
		t.Fatalf("expected successful failover to Invidious, got: %v", err)
	}
	if stream.StreamURL != "https://googlevideo.mock/invidious_stream.m4a" {
		t.Errorf("expected Invidious stream URL, got %s", stream.StreamURL)
	}
	if stream.Codec != "AAC" {
		t.Errorf("expected AAC, got %s", stream.Codec)
	}
}

func TestMirrorManagerCooldownAndSorting(t *testing.T) {
	mgr := NewMirrorManager([]*MirrorInstance{
		{BaseURL: "http://failing.mirror", Type: TypePiped, FailedCount: 3, LastFailure: time.Now(), IsActive: true},
		{BaseURL: "http://healthy.mirror", Type: TypePiped, FailedCount: 0, LatencyMs: 45, IsActive: true},
	})

	healthy := mgr.getHealthyInstances()
	if len(healthy) != 1 {
		t.Fatalf("expected 1 healthy instance, got %d", len(healthy))
	}
	if healthy[0].BaseURL != "http://healthy.mirror" {
		t.Errorf("expected healthy mirror, got %s", healthy[0].BaseURL)
	}
}
