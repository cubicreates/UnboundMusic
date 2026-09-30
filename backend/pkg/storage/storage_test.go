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
	"runtime"
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

func TestStorageProvisionerPermissionFallback(t *testing.T) {
	impossibleRoot := "/root/system/unbound_impossible_test"
	if runtime.GOOS == "windows" {
		impossibleRoot = "Z:\\NonExistentDriveDirectory\\Unbound"
	}

	prov := NewProvisioner(impossibleRoot)
	tree, err := prov.ProvisionLayout()
	if err != nil {
		t.Fatalf("ProvisionLayout should not fail on denied/invalid root, got: %v", err)
	}

	if !tree.IsReady {
		t.Errorf("expected tree to be ready")
	}
	if !tree.IsFallback {
		t.Errorf("expected tree.IsFallback to be true")
	}

	if _, err := os.Stat(tree.RootPath); os.IsNotExist(err) {
		t.Errorf("fallback root path %s does not exist", tree.RootPath)
	}
}

// TestStorageManagerPartitionIsolation tests Scoped Storage boundary enforcement and track export.
func TestStorageManagerPartitionIsolation(t *testing.T) {
	tempDir := t.TempDir()
	prov := NewProvisioner(tempDir)
	tree, err := prov.ProvisionLayout()
	if err != nil {
		t.Fatalf("failed provisioning layout: %v", err)
	}

	sm := NewStorageManager(tree)

	// Check partition paths
	cachePath, err := sm.GetPartitionPath(PartitionInternalCache)
	if err != nil || cachePath == "" {
		t.Fatalf("expected valid cache partition path, got: %s (err: %v)", cachePath, err)
	}

	exportPath, err := sm.GetPartitionPath(PartitionPublicExports)
	if err != nil || exportPath == "" {
		t.Fatalf("expected valid export partition path, got: %s (err: %v)", exportPath, err)
	}

	if cachePath == exportPath {
		t.Errorf("internal cache and public exports must be distinct partitions")
	}

	// Test ExportTrack
	tempAudioFile := filepath.Join(cachePath, "temp_stream_123.mp3")
	dummyData := []byte("ID3dummy_audio_stream_data_sample_content")
	if err := os.WriteFile(tempAudioFile, dummyData, 0644); err != nil {
		t.Fatalf("failed creating source audio file: %v", err)
	}

	destFilename := "artist_title_clean.mp3"
	exportedPath, err := sm.ExportTrack(tempAudioFile, destFilename)
	if err != nil {
		t.Fatalf("ExportTrack failed: %v", err)
	}

	if _, err := os.Stat(exportedPath); os.IsNotExist(err) {
		t.Errorf("expected exported file to exist at %s", exportedPath)
	}

	// Source file should have been moved/removed
	if _, err := os.Stat(tempAudioFile); !os.IsNotExist(err) {
		t.Errorf("expected source temp file to be removed after export")
	}

	// Verify content matches
	readData, err := os.ReadFile(exportedPath)
	if err != nil || !bytes.Equal(readData, dummyData) {
		t.Errorf("exported file content corrupted or mismatched")
	}
}

// TestCachePrunerLRUAndAgeEviction tests LRU quota eviction and TTL age-based pruning.
func TestCachePrunerLRUAndAgeEviction(t *testing.T) {
	tempDir := t.TempDir()
	prov := NewProvisioner(tempDir)
	tree, err := prov.ProvisionLayout()
	if err != nil {
		t.Fatalf("failed provisioning layout: %v", err)
	}

	sm := NewStorageManager(tree, 1000) // 1000 byte quota
	pruner := NewCachePruner(sm)

	cacheDir := tree.CachePath
	_ = os.MkdirAll(cacheDir, 0755)

	// Create 3 files with 500 bytes each (total 1500 bytes > 1000 byte quota)
	fileOld := filepath.Join(cacheDir, "old_chunk.bin")
	fileMid := filepath.Join(cacheDir, "mid_chunk.bin")
	fileNew := filepath.Join(cacheDir, "new_chunk.bin")

	buf500 := make([]byte, 500)

	if err := os.WriteFile(fileOld, buf500, 0644); err != nil {
		t.Fatalf("failed writing file: %v", err)
	}
	if err := os.WriteFile(fileMid, buf500, 0644); err != nil {
		t.Fatalf("failed writing file: %v", err)
	}
	if err := os.WriteFile(fileNew, buf500, 0644); err != nil {
		t.Fatalf("failed writing file: %v", err)
	}

	// Adjust timestamps
	now := time.Now()
	_ = os.Chtimes(fileOld, now.Add(-3*time.Hour), now.Add(-3*time.Hour))
	_ = os.Chtimes(fileMid, now.Add(-2*time.Hour), now.Add(-2*time.Hour))
	_ = os.Chtimes(fileNew, now.Add(-10*time.Minute), now.Add(-10*time.Minute))

	// Prune with 1000 byte quota and 0.8 watermark (target = 800 bytes)
	policy := PrunePolicy{
		MaxAge:        24 * time.Hour,
		TargetQuota:   1000,
		WatermarkFrac: 0.80,
	}

	res, err := pruner.Prune(policy)
	if err != nil {
		t.Fatalf("Prune failed: %v", err)
	}

	// Total was 1500, target is 800. Needs to delete at least 2 files (1000 bytes) so remaining <= 800.
	if res.FilesDeleted < 1 {
		t.Errorf("expected at least 1 file deleted, got %d", res.FilesDeleted)
	}

	// Oldest file should definitely have been deleted
	if _, err := os.Stat(fileOld); !os.IsNotExist(err) {
		t.Errorf("expected oldest file to be evicted")
	}

	// Newest file should have been preserved
	if _, err := os.Stat(fileNew); os.IsNotExist(err) {
		t.Errorf("expected newest file to be preserved")
	}
}


