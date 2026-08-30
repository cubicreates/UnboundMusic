/*
 * Package: models
 * File: models.go
 * Purpose: Defines core domain entities, data transfer objects, and service contracts for Unbound Music.
 * Subsystem: Core Domain Layer
 * Concurrency: Structs are plain data holders and thread-safe for concurrent read access.
 */

package models

import "time"

// Track represents an audio entity in the music catalog, either streamed from YouTube Music or indexed locally.
type Track struct {
	// ID is the unique identifier (YouTube Video ID or local audio content hash).
	ID string `json:"id"`

	// Title is the name of the track.
	Title string `json:"title"`

	// Artist is the primary artist or performing group.
	Artist string `json:"artist"`

	// Album is the album or EP title the track belongs to.
	Album string `json:"album"`

	// DurationMs is the total length of the audio in milliseconds.
	DurationMs int64 `json:"duration_ms"`

	// ThumbnailURL is the remote CDN URL for album art.
	ThumbnailURL string `json:"thumbnail_url"`

	// StreamURL is the extracted direct playback link or local file URI.
	StreamURL string `json:"stream_url,omitempty"`

	// Codec describes the audio encoding format (e.g. Opus, AAC, FLAC, MP3).
	Codec string `json:"codec,omitempty"`

	// BitrateKbps is the audio stream quality in kilobits per second.
	BitrateKbps int `json:"bitrate_kbps,omitempty"`

	// IsLocal indicates whether the track exists on device storage.
	IsLocal bool `json:"is_local"`

	// LocalPath is the absolute file path if stored locally on device.
	LocalPath string `json:"local_path,omitempty"`

	// ISRC is the International Standard Recording Code if available.
	ISRC string `json:"isrc,omitempty"`
}

// StreamInfo encapsulates technical metadata and direct streaming URLs for audio playback.
type StreamInfo struct {
	// VideoID is the unique YouTube Video ID.
	VideoID string `json:"video_id"`

	// StreamURL is the direct playable media URL with signature deciphered.
	StreamURL string `json:"stream_url"`

	// Codec is the audio container format (e.g. opus, mp4a).
	Codec string `json:"codec"`

	// BitrateKbps is the average bit rate in kbps.
	BitrateKbps int `json:"bitrate_kbps"`

	// SampleRate is the audio frequency in Hz (e.g. 44100, 48000).
	SampleRate int `json:"sample_rate"`

	// ContentLength is the size of the remote audio file in bytes.
	ContentLength int64 `json:"content_length"`

	// ExpiresAt is the timestamp after which the streaming URL becomes invalid.
	ExpiresAt time.Time `json:"expires_at"`

	// AudioChannels is the number of channels (e.g. 2 for stereo).
	AudioChannels int `json:"audio_channels"`
}

// Syllable represents a single syllable or word slice with millisecond boundaries for kinetic rendering.
type Syllable struct {
	// Text is the exact syllable or sub-word string.
	Text string `json:"text"`

	// StartMs is the millisecond timestamp when vocalization starts.
	StartMs int64 `json:"start_ms"`

	// EndMs is the millisecond timestamp when vocalization ends.
	EndMs int64 `json:"end_ms"`
}

// LyricLine represents a synchronized line containing words and millisecond start/end intervals.
type LyricLine struct {
	// Text is the full line text string.
	Text string `json:"text"`

	// StartMs is the timestamp when the line begins.
	StartMs int64 `json:"start_ms"`

	// EndMs is the timestamp when the line finishes.
	EndMs int64 `json:"end_ms"`

	// Syllables contains fine-grained syllable level timing for kinetic word-by-word glow.
	Syllables []Syllable `json:"syllables,omitempty"`
}

// LyricsPayload encapsulates synchronized lyrics data and annotations for a specific track.
type LyricsPayload struct {
	// TrackID is the matching YouTube or local track identifier.
	TrackID string `json:"track_id"`

	// Title is the song name.
	Title string `json:"title"`

	// Artist is the performer name.
	Artist string `json:"artist"`

	// PlainLyrics contains the raw unsynchronized lyrics text.
	PlainLyrics string `json:"plain_lyrics"`

	// Lines holds time-stamped lines for synchronized scrolling.
	Lines []LyricLine `json:"lines,omitempty"`

	// IsWordSynced specifies if fine-grained syllable level timing is present.
	IsWordSynced bool `json:"is_word_synced"`

	// Source identifies the provider (e.g. "Genius + Local CTC", "LRCLIB").
	Source string `json:"source"`

	// Annotations holds verified trivia or background explanation cards from Genius.
	Annotations []string `json:"annotations,omitempty"`
}

// IngestionResult summarizes the action taken when indexing a discovered audio file.
type IngestionResult struct {
	// SourcePath is the original file path on storage.
	SourcePath string `json:"source_path"`

	// TargetPath is the destination organized path (if moved or copied).
	TargetPath string `json:"target_path"`

	// Action describes the operation performed: "COPIED", "MOVED", "IGNORED", or "INDEXED".
	Action string `json:"action"`

	// Fingerprint is the Chromaprint acoustic hash calculated for the file.
	Fingerprint string `json:"fingerprint,omitempty"`

	// DurationMs is the duration of the audio in milliseconds.
	DurationMs int64 `json:"duration_ms"`

	// IsNoise indicates if the file was classified as a voice memo or short sound effect.
	IsNoise bool `json:"is_noise"`
}

// ScraperService defines the interface for interacting with YouTube Music Innertube endpoints.
type ScraperService interface {
	// Search executes a query against YouTube Music and returns matching tracks.
	Search(query string, filter string) ([]Track, error)

	// GetStreamInfo extracts direct audio streaming URLs and metadata for a given video ID.
	GetStreamInfo(videoID string) (*StreamInfo, error)
}

// LyricsService defines the interface for fetching and aligning lyrics from Genius and local CTC aligner.
type LyricsService interface {
	// GetLyrics fetches synchronized lyrics, attempting local cache before web scrapers.
	GetLyrics(trackID string, title string, artist string, durationMs int64) (*LyricsPayload, error)
}
