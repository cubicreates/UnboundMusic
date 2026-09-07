/*
 * Package: models
 * File: markov.go
 * Purpose: Domain models for track-to-track Markov transition and co-listening probabilities.
 * Subsystem: On-Device Taste Engine & Serendipity Sequencing
 * Concurrency: Immutable struct models safe for concurrent reads and serialization.
 */

package models

// TrackTransition represents a directed edge between two sequential audio tracks.
type TrackTransition struct {
	SourceTrackID string  `json:"source_track_id"`
	TargetTrackID string  `json:"target_track_id"`
	Count         int     `json:"count"`
	Probability   float64 `json:"probability,omitempty"`
	LastSeenAt    int64   `json:"last_seen_at"`
}

// NextTrackSuggestion encapsulates a candidate track with transition confidence.
type NextTrackSuggestion struct {
	TrackID     string  `json:"track_id"`
	Score       float64 `json:"score"`
	Transitions int     `json:"transitions"`
}
