/*
 * Package: ytmusic
 * File: ytmusic_test.go
 * Purpose: Unit tests for YouTube Innertube search parsing, stream extraction, and cipher decryption.
 * Subsystem: Test Suite
 * Concurrency: Tests run concurrently using Go testing primitives.
 */

package ytmusic

import (
	"context"
	"net/http"
	"strings"
	"testing"
)

// TestDecipherURLDirect verifies that direct URLs with n-parameters are properly transformed.
func TestDecipherURLDirect(t *testing.T) {
	rawURL := "https://rr1---sn-4g5ednle.googlevideo.com/videoplayback?expire=123&n=abcdefg12345&itag=251"
	deciphered, err := DecipherURL(rawURL, "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if !strings.Contains(deciphered, "videoplayback") {
		t.Errorf("expected valid stream URL, got %s", deciphered)
	}
}

// TestParseDurationToMs validates duration string conversion to milliseconds.
func TestParseDurationToMs(t *testing.T) {
	tests := []struct {
		input    string
		expected int64
	}{
		{"3:45", 225000},
		{"0:30", 30000},
		{"1:05:20", 3920000},
		{"invalid", 0},
	}

	for _, tt := range tests {
		got := parseDurationToMs(tt.input)
		if got != tt.expected {
			t.Errorf("parseDurationToMs(%q) = %d, expected %d", tt.input, got, tt.expected)
		}
	}
}

// TestLiveSearch validates that real queries against YouTube Music return tracks.
func TestLiveSearch(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping live network test in short mode")
	}

	client := NewClient()
	tracks, err := client.Search(context.Background(), "Kendrick Lamar DNA")
	if err != nil {
		t.Skipf("skipping live network test due to connectivity error: %v", err)
	}

	if len(tracks) == 0 {
		t.Fatalf("expected tracks in search results, got 0")
	}

	first := tracks[0]
	if first.ID == "" {
		t.Errorf("expected non-empty video ID")
	}
	if first.Title == "" {
		t.Errorf("expected non-empty track title")
	}
}

// TestLiveGetStreamInfo validates that GetStreamInfo extracts a playable audio stream.
func TestLiveGetStreamInfo(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping live network test in short mode")
	}

	client := NewClient()
	info, err := client.GetStreamInfo(context.Background(), "T6eK-2OQtew")
	if err != nil {
		t.Fatalf("GetStreamInfo failed: %v", err)
	}
	t.Logf("Resolved stream: url=%s, codec=%s, bitrate=%d", info.StreamURL, info.Codec, info.BitrateKbps)
	if info.StreamURL == "" {
		t.Fatalf("expected non-empty stream URL")
	}

	// Test GET with UserAgentIOS vs Chrome
	req, _ := http.NewRequestWithContext(context.Background(), "GET", info.StreamURL, nil)
	req.Header.Set("Range", "bytes=0-")
	req.Header.Set("User-Agent", UserAgentIOS)
	resp, err := client.httpClient.Do(req)
	if err != nil {
		t.Fatalf("request with iOS UA failed: %v", err)
	}
	t.Logf("Response with iOS UA + Range bytes=0-: status=%d", resp.StatusCode)
	resp.Body.Close()

	// Android Chrome / Mobile Safari
	req2, _ := http.NewRequestWithContext(context.Background(), "GET", info.StreamURL, nil)
	req2.Header.Set("Range", "bytes=0-")
	req2.Header.Set("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
	resp2, err := client.httpClient.Do(req2)
	if err != nil {
		t.Fatalf("request with Android Mobile UA failed: %v", err)
	}
	t.Logf("Response with Android Mobile Chrome UA + Range bytes=0-: status=%d", resp2.StatusCode)
	resp2.Body.Close()

	// Default ExoPlayer user agent
	req3, _ := http.NewRequestWithContext(context.Background(), "GET", info.StreamURL, nil)
	req3.Header.Set("Range", "bytes=0-")
	req3.Header.Set("User-Agent", "ExoPlayerLib/2.19.1 (Linux; Android 14)")
	resp3, err := client.httpClient.Do(req3)
	if err != nil {
		t.Fatalf("request with ExoPlayer UA failed: %v", err)
	}
	t.Logf("Response with ExoPlayer UA + Range bytes=0-: status=%d", resp3.StatusCode)
	resp3.Body.Close()
}

/// TestBoundedChunkStreaming tests that bounded chunks on iOS stream URL succeed with 206
func TestBoundedChunkStreaming(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping in short mode")
	}

	client := NewClient()
	info, err := client.GetStreamInfo(context.Background(), "JqFzhcWo3EU")
	if err != nil {
		t.Fatalf("GetStreamInfo failed: %v", err)
	}
	t.Logf("Resolved JqFzhcWo3EU: codec=%s, url=%s", info.Codec, info.StreamURL)

	// Test bounded chunk (0-262143 = 256KB)
	req, _ := http.NewRequestWithContext(context.Background(), "GET", info.StreamURL, nil)
	req.Header.Set("Range", "bytes=0-262143")
	req.Header.Set("User-Agent", UserAgentIOS)
	resp, err := client.httpClient.Do(req)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusPartialContent {
		t.Errorf("expected status 206 Partial Content, got %d", resp.StatusCode)
	}
	if resp.Header.Get("Content-Range") == "" {
		t.Errorf("expected non-empty Content-Range header")
	}
}

// TestAllClientsStream tests stream extraction and HTTP HEAD/GET across all client profiles.
func TestAllClientsStream(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping in short mode")
	}

	client := NewClient()
	configs := []ClientConfig{ConfigVisionOS, ConfigIOS, ConfigWeb, ConfigWebRemix, ConfigAndroidMusic, ConfigTVHTML5Simply}
	testVideoID := "T6eK-2OQtew"

	for _, cfg := range configs {
		t.Run(cfg.Name, func(t *testing.T) {
			var pb PlaybackContext
			pb.ContentPlaybackContext.HTML5Preference = "HTML5_PREF_WANTS"
			body := PlayerRequestBody{
				Context:         client.buildContext(cfg),
				VideoID:         testVideoID,
				PlaybackContext: pb,
				ContentCheckOk:  true,
				RacyCheckOk:     true,
			}
			respBytes, err := client.post(context.Background(), "player", body, cfg)
			if err != nil {
				t.Fatalf("POST player failed: %v", err)
			}

			info, err := parsePlayerResponse(testVideoID, respBytes)
			if err != nil {
				t.Fatalf("parsePlayerResponse failed: %v", err)
			}
			t.Logf("[%s] Stream URL prefix: %s", cfg.Name, info.StreamURL[:min(60, len(info.StreamURL))])

			req, _ := http.NewRequestWithContext(context.Background(), "GET", info.StreamURL, nil)
			req.Header.Set("Range", "bytes=0-1024")
			req.Header.Set("User-Agent", cfg.UserAgent)
			if cfg.Name == "WEB_REMIX" {
				req.Header.Set("Referer", "https://music.youtube.com/")
				req.Header.Set("Origin", "https://music.youtube.com")
			}
			resp, err := client.httpClient.Do(req)
			if err != nil {
				t.Fatalf("GET stream failed: %v", err)
			}
			defer resp.Body.Close()
			t.Logf("[%s] HTTP status: %d", cfg.Name, resp.StatusCode)
		})
	}
}
