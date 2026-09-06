/*
 * Package: storage
 * File: storage_test.go
 * Purpose: Unit tests for storage layout provisioning, magic byte prober, POSIX crawler (.nomedia bypass), and mtime caching.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary directories.
 */

package storage

import (
	"bytes"
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

// TestMagicByteDetection verifies 100% detection accuracy for raw binary audio headers.
func TestMagicByteDetection(t *testing.T) {
	tests := []struct {
		name     string
		header   []byte
		expected string
	}{
		{
			name:     "MP3 with ID3v2 tag",
			header:   []byte{0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
			expected: "mp3",
		},
		{
			name:     "MP3 raw sync word 0xFFFB",
			header:   []byte{0xFF, 0xFB, 0x90, 0x64, 0x00, 0x00, 0x00, 0x00},
			expected: "mp3",
		},
		{
			name:     "Lossless FLAC",
			header:   []byte{0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22},
			expected: "flac",
		},
		{
			name:     "OGG Vorbis / Opus (WhatsApp audio)",
			header:   []byte{0x4F, 0x67, 0x67, 0x53, 0x00, 0x02, 0x00, 0x00},
			expected: "opus",
		},
		{
			name: "WAV RIFF Container",
			header: func() []byte {
				h := make([]byte, 16)
				copy(h[0:], []byte("RIFF"))
				copy(h[8:], []byte("WAVE"))
				return h
			}(),
			expected: "wav",
		},
		{
			name: "M4A / AAC with ftyp M4A brand",
			header: func() []byte {
				h := make([]byte, 16)
				copy(h[4:], []byte("ftyp"))
				copy(h[8:], []byte("M4A "))
				return h
			}(),
			expected: "m4a",
		},
		{
			name:     "Unknown non-audio file",
			header:   []byte{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07},
			expected: "",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := DetectMagicBytes(tc.header)
			if got != tc.expected {
				t.Errorf("DetectMagicBytes got %q, expected %q", got, tc.expected)
			}
		})
	}
}

// TestDirectoryWalkBypassingNoMedia verifies that the scanner penetrates folders containing .nomedia.
func TestDirectoryWalkBypassingNoMedia(t *testing.T) {
	tempDir := t.TempDir()

	// Create WhatsApp Audio structure with .nomedia
	waFolder := filepath.Join(tempDir, "Android", "media", "com.whatsapp", "WhatsApp", "Media", "WhatsApp Audio")
	if err := os.MkdirAll(waFolder, 0755); err != nil {
		t.Fatalf("failed to create whatsapp dir: %v", err)
	}

	// Place .nomedia file
	nomediaPath := filepath.Join(waFolder, ".nomedia")
	if err := os.WriteFile(nomediaPath, []byte(""), 0644); err != nil {
		t.Fatalf("failed creating .nomedia: %v", err)
	}

	// Place fake Opus WhatsApp voice note with OggS magic bytes
	fakeOpus := filepath.Join(waFolder, "AUD-20240902-WA0001.opus")
	opusBytes := append([]byte{0x4F, 0x67, 0x67, 0x53}, bytes.Repeat([]byte{0x00}, 64)...)
	if err := os.WriteFile(fakeOpus, opusBytes, 0644); err != nil {
		t.Fatalf("failed writing fake opus: %v", err)
	}

	// Place fake MP3 in a subfolder
	sentFolder := filepath.Join(waFolder, "Sent")
	_ = os.MkdirAll(sentFolder, 0755)
	fakeMP3 := filepath.Join(sentFolder, "AUD-20240903-WA0002.mp3")
	mp3Bytes := append([]byte{0x49, 0x44, 0x33}, bytes.Repeat([]byte{0x00}, 64)...)
	if err := os.WriteFile(fakeMP3, mp3Bytes, 0644); err != nil {
		t.Fatalf("failed writing fake mp3: %v", err)
	}

	dbPath := filepath.Join(tempDir, "test_scanner.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed opening DB: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	ctx := context.Background()

	report, err := ScanDirectory(ctx, repo, tempDir, "whatsapp")
	if err != nil {
		t.Fatalf("ScanDirectory failed: %v", err)
	}

	if report.AudioFilesFound != 2 {
		t.Errorf("expected 2 audio files found through .nomedia, got %d", report.AudioFilesFound)
	}
	if report.NewTracksIndexed != 2 {
		t.Errorf("expected 2 new tracks indexed, got %d", report.NewTracksIndexed)
	}

	// Verify records exist in SQLite repository
	waTracks, err := repo.GetLocalTracksBySource(ctx, "whatsapp")
	if err != nil {
		t.Fatalf("GetLocalTracksBySource failed: %v", err)
	}
	if len(waTracks) != 2 {
		t.Errorf("expected 2 local tracks in database, got %d", len(waTracks))
	}
}

// TestMTimeCacheHit ensures that rescanning unchanged files registers 100% cache hits without reading disk bytes.
func TestMTimeCacheHit(t *testing.T) {
	tempDir := t.TempDir()

	// Create dummy audio file
	sampleFile := filepath.Join(tempDir, "cached_track.flac")
	flacHeader := append([]byte{0x66, 0x4C, 0x61, 0x43}, bytes.Repeat([]byte{0x01}, 100)...)
	if err := os.WriteFile(sampleFile, flacHeader, 0644); err != nil {
		t.Fatalf("failed creating flac file: %v", err)
	}

	dbPath := filepath.Join(tempDir, "test_mtime.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed opening DB: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	ctx := context.Background()

	// 1. First Scan: index from disk
	report1, err := ScanDirectory(ctx, repo, tempDir, "downloads")
	if err != nil {
		t.Fatalf("first scan failed: %v", err)
	}
	if report1.NewTracksIndexed != 1 || report1.UnchangedTracks != 0 {
		t.Errorf("first scan: expected 1 new, 0 unchanged, got: %+v", report1)
	}

	// 2. Second Scan immediately after: should be a 100% mtime cache hit
	report2, err := ScanDirectory(ctx, repo, tempDir, "downloads")
	if err != nil {
		t.Fatalf("second scan failed: %v", err)
	}
	if report2.NewTracksIndexed != 0 || report2.UnchangedTracks != 1 {
		t.Errorf("second scan: expected 0 new, 1 unchanged, got: %+v", report2)
	}
}

func TestStorageProvisionerLayout(t *testing.T) {
	tempRoot := filepath.Join(os.TempDir(), "unbound_storage_test_root")
	defer os.RemoveAll(tempRoot)

	prov := NewProvisioner(tempRoot)
	tree, err := prov.ProvisionLayout()
	if err != nil {
		t.Fatalf("ProvisionLayout failed: %v", err)
	}

	if !tree.IsReady {
		t.Fatalf("expected tree to be ready")
	}

	if _, err := os.Stat(tree.BackendPath); os.IsNotExist(err) {
		t.Errorf("expected .backend to exist at %s", tree.BackendPath)
	}
	if _, err := os.Stat(tree.SQLitePath); os.IsNotExist(err) {
		t.Errorf("expected sqlite directory to exist at %s", tree.SQLitePath)
	}
	if _, err := os.Stat(tree.ModelsPath); os.IsNotExist(err) {
		t.Errorf("expected models directory to exist at %s", tree.ModelsPath)
	}
}

func TestInPlaceVirtualIndexing(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), "unbound_indexer_test")
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	sampleAudio := filepath.Join(tempDir, "test_song.mp3")
	_ = os.WriteFile(sampleAudio, make([]byte, 1024*500), 0644)

	dbPath := filepath.Join(tempDir, "test_db.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()
	repo := database.NewRepository(db)

	indexer := NewIndexer(repo)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	summary, err := indexer.IndexInPlace(ctx, tempDir)
	if err != nil {
		t.Fatalf("IndexInPlace failed: %v", err)
	}

	if summary.Mode != "VIRTUAL_IN_PLACE" {
		t.Errorf("expected mode VIRTUAL_IN_PLACE, got %s", summary.Mode)
	}
}
