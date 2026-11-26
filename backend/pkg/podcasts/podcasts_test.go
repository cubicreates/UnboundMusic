/*
 * Package: podcasts
 * File: podcasts_test.go
 * Purpose: Unit tests for podcast playback resumption timestamps and show browsing.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package podcasts

import (
	"context"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// TestPodcastResumePositions validates recording and retrieving exact second episode timestamps.
func TestPodcastResumePositions(t *testing.T) {
	eng := NewEngine(nil)

	episodeID := "episode_huberman_123"
	eng.SaveResumePosition(episodeID, 345000) // 5 minutes 45 seconds

	pos := eng.GetResumePosition(episodeID)
	if pos != 345000 {
		t.Errorf("expected 345000ms resume position, got %d", pos)
	}

	// Non-existent episode should return 0
	unknownPos := eng.GetResumePosition("non_existent")
	if unknownPos != 0 {
		t.Errorf("expected 0ms for unknown episode, got %d", unknownPos)
	}
}

func TestPartitionPodcastTracksExcludesShortMusic(t *testing.T) {
	eng := NewEngine(nil)
	tracks := []models.Track{
		{
			ID:         "shortSong01",
			Title:      "Pop Song",
			Artist:     "Pop Artist",
			DurationMs: 95000, // < 120s music track should be excluded
			ItemType:   "song",
		},
		{
			ID:         "epLex000001",
			Title:      "AI Systems & Compilers Episode 412",
			Artist:     "Lex Fridman",
			Album:      "Lex Fridman Podcast",
			DurationMs: 5400000,
			ItemType:   "song",
		},
		{
			ID:       "PLpodcast001",
			BrowseID: "PLpodcast001",
			Title:    "The Daily Tech Brief",
			Artist:   "Tech Network",
			ItemType: "playlist",
		},
	}

	episodes, shows := eng.partitionPodcastTracks(tracks, "Technology")
	if len(episodes) != 1 {
		t.Fatalf("expected 1 podcast episode after music filtering, got %d", len(episodes))
	}
	if episodes[0].ID != "epLex000001" {
		t.Errorf("expected epLex000001, got %s", episodes[0].ID)
	}
	if len(shows) != 2 {
		t.Errorf("expected 2 podcast shows (series + episode host), got %d", len(shows))
	}
}

func TestPodcastLibraryFollowSaveAndProgress(t *testing.T) {
	eng := NewEngine(nil)
	ctx := context.Background()

	show := PodcastShow{
		ID:       "show_lex_01",
		Title:    "Lex Fridman Podcast",
		Author:   "Lex Fridman",
		Category: "AI & Tech",
	}
	followed, err := eng.ToggleFollowShow(ctx, show)
	if err != nil || !followed {
		t.Fatalf("expected show to be followed, got followed=%v err=%v", followed, err)
	}

	ep := PodcastEpisode{
		ID:         "ep_lex_418",
		ShowID:     show.ID,
		ShowTitle:  show.Title,
		Author:     show.Author,
		Title:      "Ep 418: Systems & Intelligence",
		DurationMs: 3600000,
	}
	saved, err := eng.ToggleSaveEpisode(ctx, ep)
	if err != nil || !saved {
		t.Fatalf("expected episode to be saved, got saved=%v err=%v", saved, err)
	}

	eng.SaveResumePosition(ep.ID, 900000)
	eng.RecordEpisodeMetadata(ctx, ep)

	state := eng.GetLibraryState(ctx)
	if len(state.FollowedShows) != 1 || state.FollowedShows[0].ID != show.ID {
		t.Errorf("unexpected followed shows: %+v", state.FollowedShows)
	}
	if len(state.SavedEpisodes) != 1 || state.SavedEpisodes[0].ResumePosMs != 900000 {
		t.Errorf("unexpected saved episodes: %+v", state.SavedEpisodes)
	}
	if len(state.InProgressEpisodes) != 1 || state.InProgressEpisodes[0].ResumePosMs != 900000 {
		t.Errorf("unexpected in-progress episodes: %+v", state.InProgressEpisodes)
	}

	// Toggle off
	followed2, err := eng.ToggleFollowShow(ctx, show)
	if err != nil || followed2 {
		t.Fatalf("expected show to be unfollowed, got followed=%v err=%v", followed2, err)
	}
}

func TestUpscalePodcastArt(t *testing.T) {
	got := upscalePodcastArt("https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/img.jpg/100x100bb.jpg")
	want := "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/img.jpg/544x544bb.jpg"
	if got != want {
		t.Errorf("upscalePodcastArt() = %q, want %q", got, want)
	}
}
