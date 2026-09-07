/*
 * Package: recommender
 * File: reranker_test.go
 * Purpose: Unit tests for candidate re-ranking, skip penalty deduction, and novelty injection.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Pure unit tests.
 */

package recommender

import (
	"fmt"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestReRankerBannedArtistPruning(t *testing.T) {
	reranker := NewReRanker()

	seed := models.TrackItem{ID: "seed", Title: "Seed Song", Artist: "Seed Artist"}
	candidates := []models.TrackItem{
		{ID: "c1", Title: "Song 1", Artist: "Good Artist"},
		{ID: "c2", Title: "Song 2", Artist: "Banned Artist"},
		{ID: "c3", Title: "Song 3", Artist: "Neutral Artist"},
	}

	affinityMap := map[string]models.ArtistAffinity{
		"Banned Artist": {
			ArtistName: "Banned Artist",
			IsBanned:   true,
		},
		"Good Artist": {
			ArtistName:   "Good Artist",
			DecayedScore: 3.5,
		},
	}

	result := reranker.ReRankCandidates(seed, candidates, affinityMap, nil, 10)

	for _, track := range result {
		if track.Artist == "Banned Artist" {
			t.Errorf("Banned artist was not pruned from results: %+v", track)
		}
	}
	if len(result) != 2 {
		t.Errorf("Expected 2 tracks after pruning, got %d", len(result))
	}
}

func TestReRankerAffinityPrioritization(t *testing.T) {
	reranker := NewReRanker()

	seed := models.TrackItem{ID: "seed", Title: "Seed", Artist: "Seed Artist"}
	candidates := []models.TrackItem{
		{ID: "c1", Title: "Song 1", Artist: "Low Affinity"},
		{ID: "c2", Title: "Song 2", Artist: "High Affinity"},
	}

	affinityMap := map[string]models.ArtistAffinity{
		"Low Affinity": {
			ArtistName:   "Low Affinity",
			DecayedScore: -1.5,
		},
		"High Affinity": {
			ArtistName:   "High Affinity",
			DecayedScore: 4.5,
		},
	}

	result := reranker.ReRankCandidates(seed, candidates, affinityMap, nil, 10)

	if len(result) != 2 {
		t.Fatalf("Expected 2 tracks, got %d", len(result))
	}

	if result[0].Artist != "High Affinity" {
		t.Errorf("Expected High Affinity artist first, got %s", result[0].Artist)
	}
}

func TestReRankerNoveltyInjection(t *testing.T) {
	reranker := NewReRanker()

	seed := models.TrackItem{ID: "seed", Title: "Seed Song", Artist: "Artist0"}

	// Create 20 candidates
	candidates := make([]models.TrackItem, 20)
	for i := 0; i < 20; i++ {
		candidates[i] = models.TrackItem{
			ID:     fmt.Sprintf("c_%d", i),
			Title:  fmt.Sprintf("Song %d", i),
			Artist: fmt.Sprintf("Artist_%d", i%5),
		}
	}
	candidates[15].Artist = "NoveltyArtist_Slot4"
	candidates[16].Artist = "NoveltyArtist_Slot9"

	result := reranker.ReRankCandidates(seed, candidates, nil, nil, 15)

	if len(result) < 10 {
		t.Fatalf("Expected at least 10 results, got %d", len(result))
	}

	// Slot 4 and Slot 9 should have been populated
	if result[4].Artist == "" || result[9].Artist == "" {
		t.Errorf("Novelty slots 4 or 9 are empty: slot4=%s, slot9=%s", result[4].Artist, result[9].Artist)
	}
}
