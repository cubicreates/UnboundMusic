/*
 * Package: database
 * File: database_test.go
 * Purpose: Unit tests for SQLite database initialization, table migrations, and DAO operations.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary SQLite databases.
 */

package database

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// TestDatabaseOpenAndMigrate validates SQLite schema migration on startup.
func TestDatabaseOpenAndMigrate(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_unbound.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	if _, err := os.Stat(dbPath); os.IsNotExist(err) {
		t.Errorf("database file was not created at %s", dbPath)
	}
}

// TestRepositoryTrackAndLyricsCRUD tests saving and reading tracks and timed lyrics from SQLite.
func TestRepositoryTrackAndLyricsCRUD(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_crud.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	// 1. Test Track Save & Get
	sampleTrack := &models.Track{
		ID:           "test_track_123",
		Title:        "DNA.",
		Artist:       "Kendrick Lamar",
		Album:        "DAMN.",
		DurationMs:   186000,
		Codec:        "opus",
		BitrateKbps:  160,
		IsLocal:      true,
		LocalPath:    "/Music/DAMN/02_DNA.mp3",
		ThumbnailURL: "https://example.com/art.jpg",
	}

	if err := repo.SaveTrack(ctx, sampleTrack); err != nil {
		t.Fatalf("SaveTrack failed: %v", err)
	}

	retrievedTrack, err := repo.GetTrack(ctx, "test_track_123")
	if err != nil {
		t.Fatalf("GetTrack failed: %v", err)
	}
	if retrievedTrack == nil {
		t.Fatalf("expected track, got nil")
	}

	if retrievedTrack.Title != "DNA." || retrievedTrack.Artist != "Kendrick Lamar" || !retrievedTrack.IsLocal {
		t.Errorf("retrieved track fields do not match inserted track: %+v", retrievedTrack)
	}

	// 2. Test Lyrics Save & Get
	sampleLyrics := &models.LyricsPayload{
		TrackID:      "test_track_123",
		Title:        "DNA.",
		Artist:       "Kendrick Lamar",
		PlainLyrics:  "I got loyalty, got royalty inside my DNA",
		IsWordSynced: true,
		Source:       "Genius + On-Device Aligner",
		Lines: []models.LyricLine{
			{
				Text:    "I got loyalty, got royalty inside my DNA",
				StartMs: 2500,
				EndMs:   6800,
				Syllables: []models.Syllable{
					{Text: "Loyalty", StartMs: 2500, EndMs: 3500},
					{Text: "Royalty", StartMs: 3500, EndMs: 4800},
					{Text: "DNA", StartMs: 4800, EndMs: 6800},
				},
			},
		},
	}

	if err := repo.SaveLyrics(ctx, sampleLyrics); err != nil {
		t.Fatalf("SaveLyrics failed: %v", err)
	}

	cachedLyrics, err := repo.GetLyrics(ctx, "test_track_123")
	if err != nil {
		t.Fatalf("GetLyrics failed: %v", err)
	}
	if cachedLyrics == nil {
		t.Fatalf("expected cached lyrics, got nil")
	}

	if len(cachedLyrics.Lines) != 1 || cachedLyrics.Lines[0].StartMs != 2500 {
		t.Errorf("cached lyrics mismatch: %+v", cachedLyrics)
	}

	// 3. Test Fingerprint Save & Lookup
	if err := repo.SaveFingerprint(ctx, "hash_abc_999", "/Music/DAMN/02_DNA.mp3", 186000); err != nil {
		t.Fatalf("SaveFingerprint failed: %v", err)
	}

	matchedPath, err := repo.GetPathByFingerprint(ctx, "hash_abc_999")
	if err != nil {
		t.Fatalf("GetPathByFingerprint failed: %v", err)
	}
	if matchedPath != "/Music/DAMN/02_DNA.mp3" {
		t.Errorf("fingerprint matched path mismatch: %s", matchedPath)
	}
}
