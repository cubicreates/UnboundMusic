/*
 * Package: lyrics
 * File: aggregator.go
 * Purpose: Multi-source 3-tier cascade harvester with offline SQLite caching and phonetic Romanization.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Thread-safe cascade coordinator with fallback fault-tolerance.
 */

package lyrics

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// LyricsCache defines persistence contracts for offline SQLite zero-latency retrieval.
type LyricsCache interface {
	GetLyrics(ctx context.Context, trackID string) (*models.LyricsPayload, error)
	SaveLyrics(ctx context.Context, payload *models.LyricsPayload) error
}

// LRCLIBFetcher provides Tier 1 synced LRC harvesting.
type LRCLIBFetcher interface {
	Fetch(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error)
}

// GeniusLyricsFetcher provides Tier 3 lyrics scraping fallback.
type GeniusLyricsFetcher interface {
	SearchSong(ctx context.Context, title, artist string) (*genius.SongHit, error)
	FetchLyrics(ctx context.Context, hit *genius.SongHit) (*models.LyricsPayload, error)
}

// Aggregator coordinates multi-source lyrics harvesting, transliteration, and caching.
type Aggregator struct {
	cache        LyricsCache
	lrclib       LRCLIBFetcher
	ytTranscript YouTubeTranscriptFetcher
	genius       GeniusLyricsFetcher
}

// NewAggregator creates a new 3-tier lyrics harvester.
func NewAggregator(
	cache LyricsCache,
	lrclib LRCLIBFetcher,
	ytTranscript YouTubeTranscriptFetcher,
	genius GeniusLyricsFetcher,
) *Aggregator {
	if lrclib == nil {
		lrclib = NewLRCLIBClient()
	}

	return &Aggregator{
		cache:        cache,
		lrclib:       lrclib,
		ytTranscript: ytTranscript,
		genius:       genius,
	}
}

// GetLyrics retrieves synchronized lyrics via the 3-tier cascade:
// Tier 0: SQLite Local Cache (0ms latency)
// Tier 1: LRCLIB synced lyrics API
// Tier 2: YouTube InnerTube timed captions
// Tier 3: Genius scraper fallback
func (a *Aggregator) GetLyrics(ctx context.Context, trackID, title, artist string, durationSec int) (*models.LyricsPayload, error) {
	trackID = strings.TrimSpace(trackID)
	title = strings.TrimSpace(title)
	artist = strings.TrimSpace(artist)

	// Step 0: Check SQLite cache first
	if a.cache != nil && trackID != "" {
		cached, err := a.cache.GetLyrics(ctx, trackID)
		if err == nil && cached != nil {
			slog.Debug("lyrics cache hit in SQLite", "track_id", trackID)
			return cached, nil
		}
	}

	var payload *models.LyricsPayload
	var fetchErr error

	// Tier 1: LRCLIB
	if a.lrclib != nil && title != "" {
		slog.Debug("querying Tier 1 LRCLIB for lyrics", "title", title, "artist", artist)
		p, err := a.lrclib.Fetch(ctx, title, artist, durationSec)
		if err == nil && p != nil && (len(p.Lines) > 0 || p.Instrumental || p.PlainLyrics != "") {
			payload = p
		} else {
			fetchErr = err
			slog.Debug("Tier 1 LRCLIB miss or error", "error", err)
		}
	}

	// Tier 2: YouTube InnerTube timed captions
	if payload == nil && a.ytTranscript != nil && trackID != "" {
		slog.Debug("querying Tier 2 YouTube captions", "video_id", trackID)
		p, err := FetchYouTubeCaptions(ctx, a.ytTranscript, trackID, title, artist)
		if err == nil && p != nil && len(p.Lines) > 0 {
			payload = p
		} else {
			if fetchErr == nil {
				fetchErr = err
			}
			slog.Debug("Tier 2 YouTube captions miss or error", "error", err)
		}
	}

	// Tier 3: Genius scraper fallback
	if payload == nil && a.genius != nil && title != "" {
		slog.Debug("querying Tier 3 Genius scraper fallback", "title", title, "artist", artist)
		hit, err := a.genius.SearchSong(ctx, title, artist)
		if err == nil && hit != nil {
			p, err := a.genius.FetchLyrics(ctx, hit)
			if err == nil && p != nil && p.PlainLyrics != "" {
				payload = p
			} else if fetchErr == nil {
				fetchErr = err
			}
		} else if fetchErr == nil {
			fetchErr = err
		}
	}

	if payload == nil {
		if fetchErr != nil {
			return nil, fmt.Errorf("lyrics cascade exhausted with error: %w", fetchErr)
		}
		return nil, fmt.Errorf("no lyrics found for track %s (%s - %s)", trackID, artist, title)
	}

	// Ensure metadata is populated
	if payload.TrackID == "" {
		payload.TrackID = trackID
	}
	if payload.Title == "" {
		payload.Title = title
	}
	if payload.Artist == "" {
		payload.Artist = artist
	}

	// Apply pure Go phonetic Romanization to non-Latin scripts (Japanese, Korean, Devanagari)
	if len(payload.Lines) > 0 {
		payload.Lines = RomanizeLyrics(payload.Lines)
	}

	// Persist in SQLite for instant 0ms subsequent retrieval
	if a.cache != nil && payload.TrackID != "" {
		if err := a.cache.SaveLyrics(ctx, payload); err != nil {
			slog.Warn("failed to cache harvested lyrics in SQLite", "track_id", payload.TrackID, "error", err)
		} else {
			slog.Debug("persisted harvested lyrics in SQLite cache", "track_id", payload.TrackID)
		}
	}

	return payload, nil
}
