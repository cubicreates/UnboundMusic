/*
 * Package: router
 * File: router_test.go
 * Purpose: Unit tests for zero-data hybrid playback stream interception.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary SQLite instances.
 */

package router

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// TestRouterLocalZeroDataInterception tests intercepting a stream request when the local audio file exists.
func TestRouterLocalZeroDataInterception(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_router.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	ctx := context.Background()

	// Create mock local file on disk
	localFile := filepath.Join(tempDir, "kendrick_dna.mp3")
	os.WriteFile(localFile, []byte("dummy audio content for zero data test"), 0644)

	// Save local track into DB
	track := &models.Track{
		ID:          "kendrick_dna",
		Title:       "DNA.",
		Artist:      "Kendrick Lamar",
		IsLocal:     true,
		LocalPath:   localFile,
		Codec:       "mp3",
		BitrateKbps: 320,
	}
	if err := repo.SaveTrack(ctx, track); err != nil {
		t.Fatalf("SaveTrack failed: %v", err)
	}

	playbackRouter := NewRouter(nil, repo)

	resolved, err := playbackRouter.ResolvePlayback(ctx, "kendrick_dna", "DNA.", "Kendrick Lamar")
	if err != nil {
		t.Fatalf("ResolvePlayback failed: %v", err)
	}

	if resolved.StreamType != StreamTypeLocalZeroData {
		t.Errorf("expected StreamTypeLocalZeroData, got %s", resolved.StreamType)
	}

	if resolved.DataConsumed != 0 {
		t.Errorf("expected 0 data consumed, got %d", resolved.DataConsumed)
	}

	if resolved.LocalPath != localFile {
		t.Errorf("expected local path %s, got %s", localFile, resolved.LocalPath)
	}
}
