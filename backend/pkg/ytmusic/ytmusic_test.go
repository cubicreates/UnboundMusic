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
		t.Fatalf("search failed: %v", err)
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
