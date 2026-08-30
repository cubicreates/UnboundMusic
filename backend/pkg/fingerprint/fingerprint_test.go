/*
 * Package: fingerprint
 * File: fingerprint_test.go
 * Purpose: Unit tests for audio header inspection, voice memo filtering, and safe WhatsApp storage ingestion.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package fingerprint

import (
	"context"
	"os"
	"path/filepath"
	"testing"
)

// TestClassifyAudioVoiceMemo verifies that short voice memos are properly classified as non-music.
func TestClassifyAudioVoiceMemo(t *testing.T) {
	voiceMeta := &AudioMetadata{
		FilePath:   "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-20241108-WA0012.opus",
		DurationMs: 12000, // 12 seconds
		SampleRate: 16000,
		Channels:   1,
	}

	result := ClassifyAudio(voiceMeta)
	if result.IsMusic {
		t.Errorf("expected voice memo to be classified as non-music")
	}

	musicMeta := &AudioMetadata{
		FilePath:   "/storage/emulated/0/Download/Kendrick_Lamar_DNA.mp3",
		DurationMs: 186000, // 3 minutes 6 seconds
		SampleRate: 44100,
		Channels:   2,
	}

	musicResult := ClassifyAudio(musicMeta)
	if !musicResult.IsMusic {
		t.Errorf("expected song to be classified as music: %s", musicResult.Reason)
	}
}

// TestIsProtectedChatMedia verifies WhatsApp/Telegram detection.
func TestIsProtectedChatMedia(t *testing.T) {
	tests := []struct {
		path     string
		expected bool
	}{
		{"/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/track.mp3", true},
		{"/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/song.opus", true},
		{"/sdcard/Telegram/Telegram Audio/mix.mp3", true},
		{"/sdcard/Download/Kendrick.mp3", false},
		{"C:\\Users\\User\\Music\\Album\\track.flac", false},
	}

	for _, tt := range tests {
		got := IsProtectedChatMedia(tt.path)
		if got != tt.expected {
			t.Errorf("IsProtectedChatMedia(%q) = %v, expected %v", tt.path, got, tt.expected)
		}
	}
}

// TestIngestTrackSafeRules tests non-destructive COPY for chat audio and MOVE for downloads.
func TestIngestTrackSafeRules(t *testing.T) {
	tempDir := t.TempDir()
	libraryRoot := filepath.Join(tempDir, "Library")

	// 1. Test WhatsApp Media: Should be COPIED
	chatDir := filepath.Join(tempDir, "WhatsApp", "Media", "WhatsApp Audio")
	os.MkdirAll(chatDir, 0755)
	chatFile := filepath.Join(chatDir, "song.mp3")
	os.WriteFile(chatFile, []byte("dummy audio content longer than 32kb"), 0644)

	chatMeta := &AudioMetadata{
		FilePath:     chatFile,
		Extension:    ".mp3",
		DurationMs:   120000,
		AcousticHash: "hash_whatsapp_123",
	}

	cfg := IngestionConfig{LibraryRootDir: libraryRoot, DryRun: false}
	res, err := IngestTrack(chatMeta, "Cardi B", "Invasion of Privacy", "WAP", cfg)
	if err != nil {
		t.Fatalf("IngestTrack failed: %v", err)
	}

	if res.Action != "COPIED" {
		t.Errorf("expected WhatsApp media action to be COPIED, got %s", res.Action)
	}

	// Original file must still exist!
	if _, err := os.Stat(chatFile); os.IsNotExist(err) {
		t.Errorf("original WhatsApp audio was deleted; COPY rule violated!")
	}

	// 2. Test Download Media: Should be MOVED
	dlDir := filepath.Join(tempDir, "Downloads")
	os.MkdirAll(dlDir, 0755)
	dlFile := filepath.Join(dlDir, "Kendrick.mp3")
	os.WriteFile(dlFile, []byte("dummy loose download audio content"), 0644)

	dlMeta := &AudioMetadata{
		FilePath:     dlFile,
		Extension:    ".mp3",
		DurationMs:   200000,
		AcousticHash: "hash_download_456",
	}

	resMove, err := IngestTrack(dlMeta, "Kendrick Lamar", "DAMN", "DNA", cfg)
	if err != nil {
		t.Fatalf("IngestTrack move failed: %v", err)
	}

	if resMove.Action != "MOVED" {
		t.Errorf("expected download media action to be MOVED, got %s", resMove.Action)
	}

	// Original download file must be moved away
	if _, err := os.Stat(dlFile); !os.IsNotExist(err) {
		t.Errorf("download audio was not moved; file still exists in source!")
	}
}

// TestScanDirectory verifies concurrent scanning on a mock folder.
func TestScanDirectory(t *testing.T) {
	tempDir := t.TempDir()
	os.WriteFile(filepath.Join(tempDir, "song1.mp3"), []byte("mp3 mock content"), 0644)
	os.WriteFile(filepath.Join(tempDir, "song2.flac"), []byte("flac mock content"), 0644)
	os.WriteFile(filepath.Join(tempDir, "readme.txt"), []byte("text content"), 0644)

	summary, err := ScanDirectory(context.Background(), tempDir, 4)
	if err != nil {
		t.Fatalf("ScanDirectory failed: %v", err)
	}

	if summary.TotalFilesScanned != 2 {
		t.Errorf("expected 2 audio files scanned, got %d", summary.TotalFilesScanned)
	}
}
