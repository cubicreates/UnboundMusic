package algorithm

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func TestAlgorithmEngine(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "algo_test_*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	db, err := database.Open(filepath.Join(tempDir, "algo_test.db"))
	if err != nil {
		t.Fatalf("failed to open test db: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)
	ytClient := ytmusic.NewClient()
	engine := NewEngine(repo, ytClient)

	// 1. Ingest test track playback
	track := models.Track{
		ID:         "test_track_1",
		Title:      "Blinding Lights",
		Artist:     "The Weeknd",
		DurationMs: 200000,
	}

	ctx := context.Background()
	err = engine.IngestPlaybackEvent(ctx, track, 200, 195)
	if err != nil {
		t.Fatalf("failed to ingest playback event: %v", err)
	}

	// 2. Query Smart Feed
	feed, err := engine.GenerateSmartFeed(ctx)
	if err != nil {
		t.Fatalf("failed to generate smart feed: %v", err)
	}

	if !feed.HasPersonalization {
		t.Fatalf("expected HasPersonalization to be true after playback event")
	}

	if len(feed.Shelves) == 0 {
		t.Fatalf("expected at least 1 smart shelf, got 0")
	}

	foundListenAgain := false
	for _, shelf := range feed.Shelves {
		if shelf.Type == "listen_again" {
			foundListenAgain = true
			if len(shelf.Tracks) == 0 {
				t.Errorf("Listen Again shelf has 0 tracks")
			}
		}
	}

	if !foundListenAgain {
		t.Errorf("Expected 'listen_again' shelf in feed")
	}
}
