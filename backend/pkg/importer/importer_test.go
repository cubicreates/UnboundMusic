/*
 * Package: importer
 * File: importer_test.go
 * Purpose: Unit tests for M3U, M3U8, and CSV playlist parsing and exporting.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package importer

import (
	"strings"
	"testing"
)

// TestParseAndExportM3U validates M3U/M3U8 roundtrip serialization.
func TestParseAndExportM3U(t *testing.T) {
	rawM3U := `#EXTM3U
#PLAYLIST:Workout Bangers
#EXTINF:185,Kendrick Lamar - DNA.
Music/Kendrick Lamar/DAMN/04. DNA.mp3
#EXTINF:294,Michael Jackson - Billie Jean
Music/Michael Jackson/Thriller/06. Billie Jean.mp3
`

	playlist, err := ParseM3U(strings.NewReader(rawM3U))
	if err != nil {
		t.Fatalf("ParseM3U failed: %v", err)
	}

	if playlist.TrackCount != 2 {
		t.Errorf("expected 2 tracks, got %d", playlist.TrackCount)
	}

	if playlist.Tracks[0].Title != "DNA." || playlist.Tracks[0].Artist != "Kendrick Lamar" {
		t.Errorf("unexpected first track: %+v", playlist.Tracks[0])
	}

	// Test Export
	exported := ExportM3U("My Exported List", playlist.Tracks)
	if !strings.Contains(exported, "#EXTM3U") || !strings.Contains(exported, "Kendrick Lamar - DNA.") {
		t.Errorf("exported M3U does not contain expected header or tracks: %s", exported)
	}
}

// TestParseCSV validates CSV playlist parsing.
func TestParseCSV(t *testing.T) {
	rawCSV := `Title,Artist,Album
DNA.,Kendrick Lamar,DAMN.
Billie Jean,Michael Jackson,Thriller
`

	playlist, err := ParseCSV(strings.NewReader(rawCSV))
	if err != nil {
		t.Fatalf("ParseCSV failed: %v", err)
	}

	if playlist.TrackCount != 2 {
		t.Errorf("expected 2 tracks from CSV, got %d", playlist.TrackCount)
	}
}
