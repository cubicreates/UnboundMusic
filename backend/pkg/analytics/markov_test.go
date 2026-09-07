/*
 * Package: analytics
 * File: markov_test.go
 * Purpose: Unit tests for on-device Markov transition tracking and candidate scoring.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Thread-safe in-memory SQLite testing.
 */

package analytics

import (
	"math"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

func setupTestMarkov(t *testing.T) (*database.Repository, *MarkovTracker) {
	t.Helper()
	db, err := database.Open(":memory:")
	if err != nil {
		t.Fatalf("Failed opening in-memory database: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	repo := database.NewRepository(db)
	tracker := NewMarkovTracker(repo)
	return repo, tracker
}

func TestMarkovTransitionRecordingAndProbability(t *testing.T) {
	_, tracker := setupTestMarkov(t)

	// 1. Record 3 transitions: trackA -> trackB
	for i := 0; i < 3; i++ {
		if err := tracker.RecordTransition("trackA", "trackB"); err != nil {
			t.Fatalf("Failed recording transition A->B: %v", err)
		}
	}

	// 2. Record 1 transition: trackA -> trackC
	if err := tracker.RecordTransition("trackA", "trackC"); err != nil {
		t.Fatalf("Failed recording transition A->C: %v", err)
	}

	// 3. Query top next tracks from trackA
	suggestions, err := tracker.GetTopNextTracks("trackA", 10)
	if err != nil {
		t.Fatalf("Failed getting top next tracks: %v", err)
	}

	if len(suggestions) != 2 {
		t.Fatalf("Expected 2 suggestions from trackA, got %d", len(suggestions))
	}

	// First should be trackB (3/4 = 0.75 probability)
	if suggestions[0].TrackID != "trackB" || suggestions[0].Transitions != 3 {
		t.Errorf("Expected trackB with 3 transitions, got %+v", suggestions[0])
	}
	if math.Abs(suggestions[0].Score-0.75) > 0.001 {
		t.Errorf("Expected P(trackB)=0.75, got %.4f", suggestions[0].Score)
	}

	// Second should be trackC (1/4 = 0.25 probability)
	if suggestions[1].TrackID != "trackC" || suggestions[1].Transitions != 1 {
		t.Errorf("Expected trackC with 1 transition, got %+v", suggestions[1])
	}
	if math.Abs(suggestions[1].Score-0.25) > 0.001 {
		t.Errorf("Expected P(trackC)=0.25, got %.4f", suggestions[1].Score)
	}
}

func TestMarkovSelfTransitionFiltered(t *testing.T) {
	_, tracker := setupTestMarkov(t)

	// Attempt self-loop: trackA -> trackA
	err := tracker.RecordTransition("trackA", "trackA")
	if err != nil {
		t.Fatalf("RecordTransition should not error on self-transition: %v", err)
	}

	suggestions, err := tracker.GetTopNextTracks("trackA", 10)
	if err != nil {
		t.Fatalf("GetTopNextTracks failed: %v", err)
	}

	if len(suggestions) != 0 {
		t.Errorf("Expected 0 suggestions for filtered self-transition, got %d", len(suggestions))
	}
}

func TestMarkovScoreCandidates(t *testing.T) {
	_, tracker := setupTestMarkov(t)

	_ = tracker.RecordTransition("seed1", "candidate1")
	_ = tracker.RecordTransition("seed1", "candidate1")
	_ = tracker.RecordTransition("seed1", "candidate2")

	candidates := []string{"candidate1", "candidate2", "candidate3_unknown"}
	scores := tracker.ScoreCandidates("seed1", candidates)

	if len(scores) != 3 {
		t.Fatalf("Expected 3 candidate scores, got %d", len(scores))
	}

	// candidate1: 2/3 = 0.6667
	if math.Abs(scores["candidate1"]-(2.0/3.0)) > 0.01 {
		t.Errorf("Expected candidate1 ~0.67, got %.4f", scores["candidate1"])
	}

	// candidate2: 1/3 = 0.3333
	if math.Abs(scores["candidate2"]-(1.0/3.0)) > 0.01 {
		t.Errorf("Expected candidate2 ~0.33, got %.4f", scores["candidate2"])
	}

	// candidate3: unobserved -> 0.0
	if scores["candidate3_unknown"] != 0.0 {
		t.Errorf("Expected candidate3_unknown 0.0, got %.4f", scores["candidate3_unknown"])
	}
}
