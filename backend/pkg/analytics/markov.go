/*
 * Package: analytics
 * File: markov.go
 * Purpose: On-device Markov transition matrix and probability scoring for sequential radio playback.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Thread-safe repository wrapper for transition edge evaluation.
 */

package analytics

import (
	"fmt"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// MarkovTracker coordinates track transition edges and probability scoring.
type MarkovTracker struct {
	repo *database.Repository
}

// NewMarkovTracker creates a tracker backed by the SQLite database repository.
func NewMarkovTracker(repo *database.Repository) *MarkovTracker {
	return &MarkovTracker{repo: repo}
}

// RecordTransition logs a transition from sourceTrackID to targetTrackID.
func (mt *MarkovTracker) RecordTransition(sourceTrackID, targetTrackID string) error {
	if sourceTrackID == "" || targetTrackID == "" {
		return fmt.Errorf("source and target track IDs must not be empty")
	}
	if sourceTrackID == targetTrackID {
		return nil
	}

	return mt.repo.RecordTrackTransition(sourceTrackID, targetTrackID, time.Now().Unix())
}

// GetTopNextTracks returns historical next tracks from sourceTrackID with confidence scores.
func (mt *MarkovTracker) GetTopNextTracks(sourceTrackID string, limit int) ([]models.NextTrackSuggestion, error) {
	transitions, err := mt.repo.GetTrackTransitions(sourceTrackID, limit)
	if err != nil {
		return nil, err
	}

	suggestions := make([]models.NextTrackSuggestion, len(transitions))
	for i, t := range transitions {
		suggestions[i] = models.NextTrackSuggestion{
			TrackID:     t.TargetTrackID,
			Score:       t.Probability,
			Transitions: t.Count,
		}
	}

	return suggestions, nil
}

// ScoreCandidates calculates transition confidence scores (0.0 to 1.0) for a batch of candidate IDs given a seed track.
func (mt *MarkovTracker) ScoreCandidates(sourceTrackID string, candidateIDs []string) map[string]float64 {
	scores := make(map[string]float64, len(candidateIDs))
	if sourceTrackID == "" || len(candidateIDs) == 0 {
		return scores
	}

	transitions, err := mt.repo.GetTrackTransitions(sourceTrackID, 50)
	if err != nil || len(transitions) == 0 {
		return scores
	}

	transitionMap := make(map[string]float64, len(transitions))
	for _, t := range transitions {
		transitionMap[t.TargetTrackID] = t.Probability
	}

	for _, cid := range candidateIDs {
		if prob, exists := transitionMap[cid]; exists {
			scores[cid] = prob
		} else {
			scores[cid] = 0.0
		}
	}

	return scores
}
