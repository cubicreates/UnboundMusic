/*
 * Package: downloader
 * File: downloader_test.go
 * Purpose: Unit tests for physical audio stream downloader and metadata tagging.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Standard Go testing framework.
 */

package downloader

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestListDownloadedFiles(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), "unbound_dl_test")
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	// Create dummy downloaded audio files
	file1 := filepath.Join(tempDir, "Kendrick Lamar - DNA.opus")
	file2 := filepath.Join(tempDir, "The Weeknd - Blinding Lights.mp3")
	_ = os.WriteFile(file1, make([]byte, 1024*100), 0644)
	_ = os.WriteFile(file2, make([]byte, 1024*100), 0644)

	mgr := NewManager(tempDir, nil)
	tracks, err := mgr.ListDownloadedFiles()
	if err != nil {
		t.Fatalf("ListDownloadedFiles failed: %v", err)
	}

	if len(tracks) != 2 {
		t.Fatalf("expected 2 downloaded tracks, got %d", len(tracks))
	}

	foundDNA := false
	for _, tr := range tracks {
		if tr.Title == "DNA" && tr.Artist == "Kendrick Lamar" {
			foundDNA = true
		}
	}

	if !foundDNA {
		t.Errorf("expected to find Kendrick Lamar - DNA in downloaded tracks list")
	}
}

func TestSanitizeFilename(t *testing.T) {
	raw := "AC/DC - Highway to Hell: Live <2020> *remaster*? | \"yes\""
	clean := sanitizeFilename(raw)
	invalidChars := []string{"/", "\\", ":", "*", "?", "\"", "<", ">", "|"}
	for _, c := range invalidChars {
		if strings.Contains(clean, c) {
			t.Errorf("sanitized filename '%s' contains invalid char '%s'", clean, c)
		}
	}
}

func TestBuildID3v2TagAndPrepend(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), "unbound_id3_test")
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	testMp3 := filepath.Join(tempDir, "test.mp3")
	dummyAudio := []byte{0xFF, 0xFB, 0x90, 0x64, 0x00, 0x00} // Fake MPEG audio frame sync
	if err := os.WriteFile(testMp3, dummyAudio, 0644); err != nil {
		t.Fatalf("failed to write test MP3: %v", err)
	}

	dummyCover := []byte{0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'}
	tagBytes := BuildID3v2Tag("Blinding Lights", "The Weeknd", "After Hours", dummyCover)
	if len(tagBytes) == 0 {
		t.Fatalf("expected non-empty ID3 tag bytes")
	}

	if string(tagBytes[:3]) != "ID3" {
		t.Fatalf("expected ID3 identifier, got %s", string(tagBytes[:3]))
	}

	if err := PrependID3v2TagToFile(testMp3, tagBytes); err != nil {
		t.Fatalf("PrependID3v2TagToFile failed: %v", err)
	}

	result, err := os.ReadFile(testMp3)
	if err != nil {
		t.Fatalf("failed to read tagged file: %v", err)
	}

	if string(result[:3]) != "ID3" {
		t.Fatalf("expected file to begin with ID3 tag, got %s", string(result[:3]))
	}

	// Verify audio frames are preserved after tag
	tagSize := int(result[6])<<21 | int(result[7])<<14 | int(result[8])<<7 | int(result[9])
	audioOffset := 10 + tagSize
	if len(result) < audioOffset+len(dummyAudio) {
		t.Fatalf("file too short to contain audio payload: got %d, expected at least %d", len(result), audioOffset+len(dummyAudio))
	}

	if string(result[audioOffset:audioOffset+len(dummyAudio)]) != string(dummyAudio) {
		t.Fatalf("audio stream corrupted after ID3 tag prepending")
	}
}

