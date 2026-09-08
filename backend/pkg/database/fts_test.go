/*
 * Package: database
 * File: fts_test.go
 * Purpose: Unit tests for SQLite FTS5 full-text search with automatic sync triggers and BM25 relevance ranking.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe repository test execution.
 */

package database

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestFTS5FullTextSearch(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_fts.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	// 1. Insert local tracks
	now := time.Now().Unix()
	track1 := &models.LocalTrack{
		ID:           "track_1",
		FilePath:     "/music/Kendrick Lamar - DNA.mp3",
		Title:        "DNA.",
		Artist:       "Kendrick Lamar",
		Album:        "DAMN.",
		DurationMs:   185000,
		Format:       "mp3",
		FileSize:     5000000,
		SourceFolder: "music",
		DateIndexed:  now,
		MTime:        now,
	}

	track2 := &models.LocalTrack{
		ID:           "track_2",
		FilePath:     "/music/Kendrick Lamar - HUMBLE.mp3",
		Title:        "HUMBLE.",
		Artist:       "Kendrick Lamar",
		Album:        "DAMN.",
		DurationMs:   177000,
		Format:       "mp3",
		FileSize:     4800000,
		SourceFolder: "music",
		DateIndexed:  now,
		MTime:        now,
	}

	track3 := &models.LocalTrack{
		ID:           "track_3",
		FilePath:     "/music/Daft Punk - Instant Crush.flac",
		Title:        "Instant Crush",
		Artist:       "Daft Punk",
		Album:        "Random Access Memories",
		DurationMs:   337000,
		Format:       "flac",
		FileSize:     35000000,
		SourceFolder: "music",
		DateIndexed:  now,
		MTime:        now,
	}

	if err := repo.UpsertLocalTrack(ctx, track1); err != nil {
		t.Fatalf("failed to upsert track1: %v", err)
	}
	if err := repo.UpsertLocalTrack(ctx, track2); err != nil {
		t.Fatalf("failed to upsert track2: %v", err)
	}
	if err := repo.UpsertLocalTrack(ctx, track3); err != nil {
		t.Fatalf("failed to upsert track3: %v", err)
	}

	// 2. Query FTS5 by artist prefix: "Kendrick"
	results, err := repo.SearchTracksFTS(ctx, "Kendrick", 10)
	if err != nil {
		t.Fatalf("FTS search failed: %v", err)
	}
	if len(results) != 2 {
		t.Fatalf("expected 2 results for 'Kendrick', got %d", len(results))
	}

	// 3. Query FTS5 by title prefix: "Instant"
	daftResults, err := repo.SearchTracksFTS(ctx, "Instant", 10)
	if err != nil {
		t.Fatalf("FTS search failed: %v", err)
	}
	if len(daftResults) != 1 || daftResults[0].Artist != "Daft Punk" {
		t.Fatalf("expected 1 result for Daft Punk, got %+v", daftResults)
	}

	// 4. Test delete trigger sync
	if err := repo.DeleteLocalTrack(ctx, track3.FilePath); err != nil {
		t.Fatalf("failed to delete track3: %v", err)
	}

	postDeleteResults, err := repo.SearchTracksFTS(ctx, "Instant", 10)
	if err != nil {
		t.Fatalf("FTS search after delete failed: %v", err)
	}
	if len(postDeleteResults) != 0 {
		t.Fatalf("expected 0 results after deletion, got %d", len(postDeleteResults))
	}
}
