/*
 * Package: models
 * File: taste.go
 * Purpose: Domain models for on-device implicit behavioral taste tracking and artist affinity.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Immutable struct models safe for concurrent reads and serialization.
 */

package models

import "time"

// TasteEventType identifies the physical interaction type with an audio track.
type TasteEventType string

const (
	TasteEventComplete TasteEventType = "COMPLETE"  // > 85% listened (+1.00)
	TasteEventLoop     TasteEventType = "LOOP"      // Replayed track (+2.50)
	TasteEventFastSkip TasteEventType = "FAST_SKIP" // Skipped < 15s (-2.00)
	TasteEventMildSkip TasteEventType = "MILD_SKIP" // Skipped 15-45s (-0.50)
	TasteEventVolumeUp TasteEventType = "VOLUME_UP" // Volume increased (+0.50)
	TasteEventSeekBack TasteEventType = "SEEK_BACK" // Re-heard section (+1.50)
	TasteEventQueueAdd TasteEventType = "QUEUE_ADD" // Manual queue addition (+1.50)
)

// TasteEvent records a discrete interaction payload with an audio track.
type TasteEvent struct {
	EventID         int64          `json:"event_id"`
	TrackID         string         `json:"track_id"`
	Title           string         `json:"title"`
	ArtistID        string         `json:"artist_id"`
	ArtistName      string         `json:"artist_name"`
	Genre           string         `json:"genre,omitempty"`
	DurationMs      int64          `json:"duration_ms"`
	ListenedMs      int64          `json:"listened_ms"`
	CompletionRatio float64        `json:"completion_ratio"`
	ScoreDelta      float64        `json:"score_delta"`
	EventType       TasteEventType `json:"event_type"`
	Timestamp       int64          `json:"timestamp"`
}

// ArtistAffinity records the aggregated preference weight for a musical artist.
type ArtistAffinity struct {
	ArtistID          string    `json:"artist_id"`
	ArtistName        string    `json:"artist_name"`
	AffinityScore     float64   `json:"affinity_score"`
	PlayCount         int       `json:"play_count"`
	SkipCount         int       `json:"skip_count"`
	FastSkipStreak    int       `json:"fast_skip_streak"`
	IsBanned          bool      `json:"is_banned"`
	BanUntilTimestamp int64     `json:"ban_until_timestamp"`
	LastListenedAt    time.Time `json:"last_listened_at"`
	DecayedScore      float64   `json:"decayed_score,omitempty"`
}
