/*
 * Package: ytmusic
 * File: radio_test.go
 * Purpose: Unit tests for InnerTube /next parsing, radio generation, and offline storage fallback.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Thread-safe in-memory SQLite testing.
 */

package ytmusic

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/analytics"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
)

const mockNextJSON = `{
  "contents": {
    "singleColumnMusicWatchNextResultsRenderer": {
      "tabbedRenderer": {
        "watchNextTabbedResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "musicQueueRenderer": {
                    "content": {
                      "playlistPanelRenderer": {
                        "contents": [
                          {
                            "playlistPanelVideoRenderer": {
                              "videoId": "vid_01",
                              "title": { "runs": [ { "text": "Midnight City" } ] },
                              "shortBylineText": { "runs": [ { "text": "M83" } ] },
                              "thumbnail": { "thumbnails": [ { "url": "https://lh3.googleusercontent.com/test1=w120-h120" } ] }
                            }
                          },
                          {
                            "playlistPanelVideoRenderer": {
                              "videoId": "vid_02",
                              "title": { "runs": [ { "text": "Resonance" } ] },
                              "shortBylineText": { "runs": [ { "text": "HOME" } ] },
                              "thumbnail": { "thumbnails": [ { "url": "https://lh3.googleusercontent.com/test2=w120-h120" } ] }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              }
            }
          ]
        }
      }
    }
  }
}`

func TestParseNextTracks(t *testing.T) {
	tracks, err := ParseNextTracks([]byte(mockNextJSON))
	if err != nil {
		t.Fatalf("ParseNextTracks failed: %v", err)
	}

	if len(tracks) != 2 {
		t.Fatalf("Expected 2 candidate tracks, got %d", len(tracks))
	}

	if tracks[0].ID != "vid_01" || tracks[0].Title != "Midnight City" || tracks[0].Artist != "M83" {
		t.Errorf("Track 0 mismatch: %+v", tracks[0])
	}
	if tracks[1].ID != "vid_02" || tracks[1].Title != "Resonance" || tracks[1].Artist != "HOME" {
		t.Errorf("Track 1 mismatch: %+v", tracks[1])
	}
}

func TestRadioOfflineStorageFallback(t *testing.T) {
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("Failed opening memory db: %v", err)
	}
	defer db.Close()
	repo := database.NewRepository(db)

	// Seed local tracks in SQLite
	err = repo.UpsertLocalTrack(context.Background(), &models.LocalTrack{
		ID:           "local_1",
		FilePath:     "/storage/emulated/0/Music/SongA.mp3",
		Title:        "Song A",
		Artist:       "Local Artist",
		DurationMs:   210000,
		Format:       "mp3",
		SourceFolder: "music",
	})
	if err != nil {
		t.Fatalf("Failed saving local track: %v", err)
	}

	rg := NewRadioGenerator(NewClient(), repo, analytics.NewMarkovTracker(repo), recommender.NewReRanker())

	resp, err := rg.fallbackToOfflineStorage(time.Now())
	if err != nil {
		t.Fatalf("Offline fallback failed: %v", err)
	}

	if resp.Source != "offline_storage" {
		t.Errorf("Expected source offline_storage, got %s", resp.Source)
	}
	if len(resp.Queue) != 1 {
		t.Fatalf("Expected 1 queued track, got %d", len(resp.Queue))
	}
	if !strings.HasPrefix(resp.Queue[0].FilePath, "file://") {
		t.Errorf("Expected file:// file path, got %s", resp.Queue[0].FilePath)
	}
}

func TestGenerateMagicRadioOfflineExecution(t *testing.T) {
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("Failed opening memory db: %v", err)
	}
	defer db.Close()
	repo := database.NewRepository(db)

	_ = repo.UpsertLocalTrack(context.Background(), &models.LocalTrack{
		ID:           "loc_01",
		FilePath:     "/storage/emulated/0/Music/OfflineTrack.flac",
		Title:        "Offline Track",
		Artist:       "Offline Artist",
		DurationMs:   180000,
		Format:       "flac",
		SourceFolder: "music",
	})

	rg := NewRadioGenerator(NewClient(), repo, analytics.NewMarkovTracker(repo), recommender.NewReRanker())

	// Call GenerateMagicRadio with non-existent seed and no network (falls back to local storage)
	resp, err := rg.GenerateMagicRadio(context.Background(), 14, "")
	if err != nil {
		t.Fatalf("GenerateMagicRadio failed on offline fallback: %v", err)
	}

	if resp == nil || len(resp.Queue) == 0 {
		t.Fatalf("Expected non-empty queue, got %+v", resp)
	}
	if resp.SeedTrack.Title == "" {
		t.Errorf("Expected non-empty seed track title, got %+v", resp.SeedTrack)
	}
}
