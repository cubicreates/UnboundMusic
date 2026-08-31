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
