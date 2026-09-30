/*
 * Package: fallback
 * File: fallback_test.go
 * Purpose: Unit tests for Multi-Stage Fallback Engine: Verifier, Indexer format parsing,
 *          and Coordinator status handling.
 */

package fallback

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestVerifier_EmptyTitle(t *testing.T) {
	v := NewVerifier()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, err := v.VerifyTrack(ctx, "", "Daft Punk")
	if err == nil {
		t.Fatalf("expected error for empty title, got nil")
	}
}

func TestDetectAudioFormat(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"Radiohead - OK Computer (1997) [FLAC 24-96]", "FLAC"},
		{"Kendrick Lamar - GNX (2024) [MP3 320kbps]", "MP3"},
		{"Daft Punk - Discovery [Opus 160kbps]", "OPUS"},
		{"The Weeknd - After Hours [AAC 256]", "M4A"},
		{"Unknown Track Audio Archive", "AUDIO"},
	}

	for _, tc := range tests {
		actual := detectAudioFormat(tc.input)
		if actual != tc.expected {
			t.Errorf("detectAudioFormat(%q) = %s, expected %s", tc.input, actual, tc.expected)
		}
	}
}

func TestSanitizeFilename(t *testing.T) {
	input := "AC/DC: Back in Black *1980?* <Deluxe>"
	expected := "AC_DC_ Back in Black _1980__ _Deluxe_"
	actual := sanitizeFilename(input)
	if actual != expected {
		t.Errorf("sanitizeFilename(%q) = %s, expected %s", input, actual, expected)
	}
}

func TestCoordinator_AutoCache(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "unbound_fallback_test_*")
	if err != nil {
		t.Fatalf("failed creating temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	// Create dummy audio file
	dummyAudio := filepath.Join(tempDir, "temp_stream.mp3")
	if err := os.WriteFile(dummyAudio, []byte("ID3dummy_audio_bytes"), 0644); err != nil {
		t.Fatalf("failed writing dummy audio: %v", err)
	}

	coord := NewCoordinator(tempDir)
	savedPath, err := coord.AutoCacheTrack(dummyAudio, "Around the World", "Daft Punk")
	if err != nil {
		t.Fatalf("AutoCacheTrack returned error: %v", err)
	}

	if _, err := os.Stat(savedPath); os.IsNotExist(err) {
		t.Fatalf("cached file does not exist at %s", savedPath)
	}

	expectedName := "Daft Punk - Around the World.mp3"
	if filepath.Base(savedPath) != expectedName {
		t.Errorf("expected cached filename %s, got %s", expectedName, filepath.Base(savedPath))
	}
}
