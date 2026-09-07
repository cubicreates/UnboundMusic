/*
 * Package: recommender
 * File: reranker.go
 * Purpose: On-device 2-tier re-ranker evaluating candidate tracks with local affinity, Markov transitions, and novelty injection.
 * Subsystem: On-Device Taste Engine & Serendipity Sequencing
 * Concurrency: Pure stateless scoring functions safe for concurrent execution.
 */

package recommender

import (
	"sort"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// ReRanker performs on-device local re-scoring and pruning of candidate tracks.
type ReRanker struct{}

// NewReRanker instantiates a new candidate re-ranker.
func NewReRanker() *ReRanker {
	return &ReRanker{}
}

// ScoredCandidate pairs a track item with its computed composite ranking score.
type ScoredCandidate struct {
	Track models.TrackItem
	Score float64
}

// ReRankCandidates filters, scores, sorts, and injects novelty into candidate tracks.
func (rr *ReRanker) ReRankCandidates(
	seed models.TrackItem,
	candidates []models.TrackItem,
	affinityMap map[string]models.ArtistAffinity,
	markovScores map[string]float64,
	targetCount int,
) []models.TrackItem {
	if len(candidates) == 0 {
		return nil
	}
	if targetCount <= 0 {
		targetCount = 25
	}

	var validCandidates []ScoredCandidate
	totalCandidates := float64(len(candidates))

	for i, c := range candidates {
		// Prune track if artist is actively banned
		artistKey := c.Artist
		if aff, exists := affinityMap[artistKey]; exists && aff.IsBanned {
			continue
		}

		// 1. YouTube Candidate Position Weight (1.0 down to 0.0)
		youtubeWeight := 1.0 - (float64(i) / totalCandidates)

		// 2. On-Device Artist Affinity Score
		var affinityScore float64
		var skipPenalty float64
		if aff, exists := affinityMap[artistKey]; exists {
			affinityScore = aff.DecayedScore
			if affinityScore > 5.0 {
				affinityScore = 5.0
			}
			if affinityScore < -2.0 {
				affinityScore = -2.0
			}
			skipPenalty = float64(aff.FastSkipStreak) * 0.50
		}

		// 3. Markov Transitional Confidence
		var markovScore float64
		if markovScores != nil {
			markovScore = markovScores[c.ID]
		}

		// Composite Ranking Formula
		finalScore := youtubeWeight + (affinityScore * 0.40) + (markovScore * 0.30) - skipPenalty

		validCandidates = append(validCandidates, ScoredCandidate{
			Track: c,
			Score: finalScore,
		})
	}

	if len(validCandidates) == 0 {
		// If all were filtered, return raw candidates to avoid dead silence
		if len(candidates) > targetCount {
			return candidates[:targetCount]
		}
		return candidates
	}

	// Sort descending by composite score
	sort.SliceStable(validCandidates, func(i, j int) bool {
		return validCandidates[i].Score > validCandidates[j].Score
	})

	// Prepare final queue
	result := make([]models.TrackItem, 0, targetCount)
	for _, sc := range validCandidates {
		result = append(result, sc.Track)
	}

	// Novelty Exploration Injection (Slot 4 and Slot 9)
	// Swap in high-entropy exploratory tracks from lower positions to prevent echo chambers
	if len(result) > 10 && len(validCandidates) > 12 {
		// Find discovery candidate for Slot 4: different artist from seed and top 3
		seenArtists := map[string]bool{
			seed.Artist: true,
		}
		for idx := 0; idx < 4 && idx < len(result); idx++ {
			seenArtists[result[idx].Artist] = true
		}

		for candidateIdx := 10; candidateIdx < len(result); candidateIdx++ {
			art := result[candidateIdx].Artist
			if !seenArtists[art] && art != "" {
				// Swap into position 4
				result[4], result[candidateIdx] = result[candidateIdx], result[4]
				seenArtists[art] = true
				break
			}
		}

		// Find deep discovery candidate for Slot 9
		if len(result) > 14 {
			for candidateIdx := 12; candidateIdx < len(result); candidateIdx++ {
				art := result[candidateIdx].Artist
				if !seenArtists[art] && art != "" {
					result[9], result[candidateIdx] = result[candidateIdx], result[9]
					break
				}
			}
		}
	}

	if len(result) > targetCount {
		result = result[:targetCount]
	}

	return result
}
