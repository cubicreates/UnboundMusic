/*
 * Package: algorithm
 * File: engine.go
 * Purpose: Native YouTube-esque recommendation algorithm generating smart variations, artist deep cuts, and taste mixes for all users.
 * Subsystem: Recommendation & Taste Engine
 * Concurrency: Thread-safe, non-blocking cache-backed recommendation generation.
 */

package algorithm

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// SmartShelf represents an algorithmic section containing themed track variations.
type SmartShelf struct {
	ID       string         `json:"id"`
	Title    string         `json:"title"`
	Subtitle string         `json:"subtitle"`
	Type     string         `json:"type"` // "quick_picks", "similar_to", "artist_spotlight", "listen_again"
	Tracks   []models.Track `json:"tracks"`
}

// SmartFeedResponse models the complete algorithmic home feed.
type SmartFeedResponse struct {
	HasPersonalization bool         `json:"has_personalization"`
	Shelves            []SmartShelf `json:"shelves"`
}

// Engine coordinates on-device listening history and YouTube-style algorithmic generation.
type Engine struct {
	repo     *database.Repository
	ytClient *ytmusic.Client

	mu        sync.RWMutex
	feedCache *SmartFeedResponse
	cacheTime time.Time
}

// NewEngine creates a new algorithmic recommendation engine.
func NewEngine(repo *database.Repository, ytClient *ytmusic.Client) *Engine {
	return &Engine{
		repo:     repo,
		ytClient: ytClient,
	}
}

// IngestPlaybackEvent records a playback session to update the user's affinity profile.
func (e *Engine) IngestPlaybackEvent(ctx context.Context, track models.Track, durationSec, listenedSec int) error {
	if track.ID == "" || track.Title == "" {
		return fmt.Errorf("invalid track for playback ingestion")
	}

	ratio := 0.0
	if durationSec > 0 {
		ratio = float64(listenedSec) / float64(durationSec)
		if ratio > 1.0 {
			ratio = 1.0
		}
	}

	if e.repo != nil {
		// Save track definition
		_ = e.repo.SaveTrack(ctx, &track)

		// Record playback event in SQLite
		event := &models.PlaybackEvent{
			EventID:        fmt.Sprintf("ev_%d_%s", time.Now().UnixNano(), track.ID),
			TrackID:        track.ID,
			Title:          track.Title,
			Artist:         track.Artist,
			Album:          track.Album,
			Genre:          "",
			DurationSec:    durationSec,
			ListenedSec:    listenedSec,
			CompletedRatio: ratio,
			Timestamp:      time.Now().Unix(),
		}
		_ = e.repo.RecordPlaybackEvent(ctx, event)
	}

	// Invalidate feed cache so next home visit updates with newly listened song variations
	e.mu.Lock()
	e.feedCache = nil
	e.mu.Unlock()

	return nil
}

// GenerateSmartFeed builds a multi-shelf YouTube-esque personalized feed from local history.
func (e *Engine) GenerateSmartFeed(ctx context.Context) (*SmartFeedResponse, error) {
	e.mu.RLock()
	if e.feedCache != nil && time.Since(e.cacheTime) < 3*time.Minute {
		cached := e.feedCache
		e.mu.RUnlock()
		return cached, nil
	}
	e.mu.RUnlock()

	if e.repo == nil || e.ytClient == nil {
		return &SmartFeedResponse{HasPersonalization: false, Shelves: nil}, nil
	}

	// 1. Retrieve recent playback events from SQLite
	events, err := e.repo.GetRecentPlaybackEvents(ctx, 30)
	if err != nil || len(events) == 0 {
		return &SmartFeedResponse{HasPersonalization: false, Shelves: nil}, nil
	}

	// 2. Identify top completed track and top artist
	artistCounts := make(map[string]int)
	var lastPlayed *models.PlaybackEvent
	var favoriteTrack *models.PlaybackEvent
	var listenAgainTracks []models.Track
	seenTrackIDs := make(map[string]bool)

	for _, ev := range events {
		eventCopy := ev
		if !seenTrackIDs[ev.TrackID] {
			seenTrackIDs[ev.TrackID] = true
			t, tErr := e.repo.GetTrack(ctx, ev.TrackID)
			if tErr == nil && t != nil {
				listenAgainTracks = append(listenAgainTracks, *t)
			} else {
				listenAgainTracks = append(listenAgainTracks, models.Track{
					ID:           ev.TrackID,
					Title:        ev.Title,
					Artist:       ev.Artist,
					Album:        ev.Album,
					DurationMs:   int64(ev.DurationSec * 1000),
					ThumbnailURL: fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", ev.TrackID),
				})
			}
		}

		artistCounts[ev.Artist]++

		if lastPlayed == nil {
			lastPlayed = &eventCopy
		}

		if ev.CompletedRatio >= 0.70 && (favoriteTrack == nil || ev.ListenedSec > favoriteTrack.ListenedSec) {
			favoriteTrack = &eventCopy
		}
	}

	if favoriteTrack == nil {
		favoriteTrack = lastPlayed
	}

	// Find top artist
	topArtist := ""
	maxCount := 0
	for art, cnt := range artistCounts {
		if cnt > maxCount && art != "" && art != "YouTube Artist" {
			maxCount = cnt
			topArtist = art
		}
	}
	if topArtist == "" && lastPlayed != nil {
		topArtist = lastPlayed.Artist
	}

	var shelves []SmartShelf

	// Shelf A: "Similar to [Favorite Track]" (Song Variations via Seed Radio)
	if favoriteTrack != nil && favoriteTrack.TrackID != "" {
		seedVariations := e.fetchSongVariations(ctx, favoriteTrack.TrackID, favoriteTrack.Artist, favoriteTrack.Title)
		if len(seedVariations) > 0 {
			shelves = append(shelves, SmartShelf{
				ID:       "shelf_similar_" + favoriteTrack.TrackID,
				Title:    fmt.Sprintf("Similar to %s", favoriteTrack.Title),
				Subtitle: fmt.Sprintf("%s Radio & Variations", favoriteTrack.Artist),
				Type:     "similar_to",
				Tracks:   seedVariations,
			})
		}
	}

	// Shelf B: "Quick Picks" (Blended mix of familiar favorites and new discoveries)
	var quickPicks []models.Track
	if len(listenAgainTracks) > 0 {
		for i, t := range listenAgainTracks {
			if i >= 4 {
				break
			}
			quickPicks = append(quickPicks, t)
		}
	}
	// Enrich quick picks with search variations of top artist
	if topArtist != "" {
		artistPicks, _ := e.ytClient.SearchWithCategory(ctx, topArtist+" best songs", "music")
		for _, ap := range artistPicks {
			if len(quickPicks) >= 12 {
				break
			}
			if !containsTrack(quickPicks, ap.ID) {
				quickPicks = append(quickPicks, ap)
			}
		}
	}
	if len(quickPicks) > 0 {
		shelves = append(shelves, SmartShelf{
			ID:       "shelf_quick_picks",
			Title:    "Quick Picks",
			Subtitle: "Curated mix based on your recent listening",
			Type:     "quick_picks",
			Tracks:   quickPicks,
		})
	}

	// Shelf C: "More from [Top Artist]"
	if topArtist != "" {
		artistTracks, err := e.ytClient.SearchWithCategory(ctx, topArtist+" songs", "music")
		if err == nil && len(artistTracks) > 0 {
			shelves = append(shelves, SmartShelf{
				ID:       "shelf_artist_" + topArtist,
				Title:    fmt.Sprintf("More from %s", topArtist),
				Subtitle: "Deep cuts and popular releases",
				Type:     "artist_spotlight",
				Tracks:   artistTracks,
			})
		}
	}

	// Shelf D: "Listen Again"
	if len(listenAgainTracks) > 0 {
		shelves = append(shelves, SmartShelf{
			ID:       "shelf_listen_again",
			Title:    "Listen Again",
			Subtitle: "Songs you enjoyed recently",
			Type:     "listen_again",
			Tracks:   listenAgainTracks,
		})
	}

	res := &SmartFeedResponse{
		HasPersonalization: len(shelves) > 0,
		Shelves:            shelves,
	}

	e.mu.Lock()
	e.feedCache = res
	e.cacheTime = time.Now()
	e.mu.Unlock()

	return res, nil
}

// fetchSongVariations queries the YouTube Music radio / related tracks for a given seed track.
func (e *Engine) fetchSongVariations(ctx context.Context, seedID, artist, title string) []models.Track {
	// 1. Try search variations: Artist + similar music
	query := fmt.Sprintf("%s %s radio", artist, title)
	tracks, err := e.ytClient.SearchWithCategory(ctx, query, "music")
	if err == nil && len(tracks) > 0 {
		var filtered []models.Track
		for _, t := range tracks {
			if t.ID != seedID && gatekeeper.IsMusicTrack(t) {
				filtered = append(filtered, t)
			}
		}
		if len(filtered) > 0 {
			return filtered
		}
	}

	// 2. Fallback: query artist discography
	tracks, err = e.ytClient.SearchWithCategory(ctx, artist, "music")
	if err == nil && len(tracks) > 0 {
		var filtered []models.Track
		for _, t := range tracks {
			if t.ID != seedID && gatekeeper.IsMusicTrack(t) {
				filtered = append(filtered, t)
			}
		}
		return filtered
	}

	return nil
}

func containsTrack(tracks []models.Track, id string) bool {
	for _, t := range tracks {
		if t.ID == id {
			return true
		}
	}
	return false
}
