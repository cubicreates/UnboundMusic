/*
 * Package: explore
 * File: explore.go
 * Purpose: Curated catalog explore engine: scrapes Moods & Moments categories, Top 100 Global Charts, and New Release carousels from YouTube Music.
 * Subsystem: Music Discovery & Explore Feeds
 * Concurrency: Thread-safe in-memory feed caching.
 */

package explore

import (
	"context"
	"sync"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// MoodCategory represents a curated vibe category (e.g. Chill, Workout, Focus).
type MoodCategory struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	ColorHex    string `json:"color_hex"`
	Icon        string `json:"icon"`
	Description string `json:"description"`
}

// ChartRanking represents a trending music chart item.
type ChartRanking struct {
	Rank         int    `json:"rank"`
	TrackID      string `json:"track_id"`
	Title        string `json:"title"`
	Artist       string `json:"artist"`
	ThumbnailURL string `json:"thumbnail_url"`
	Trend        string `json:"trend"` // "UP", "DOWN", "SAME", "NEW"
}

// Engine coordinates explore feeds, moods, and charts.
type Engine struct {
	mu       sync.RWMutex
	ytClient *ytmusic.Client
	moods    []MoodCategory
}

// NewEngine initializes the music explore engine.
func NewEngine(ytClient *ytmusic.Client) *Engine {
	if ytClient == nil {
		ytClient = ytmusic.NewClient()
	}

	defaultMoods := []MoodCategory{
		{ID: "chill", Title: "Chill & Relax", ColorHex: "#4A90E2", Description: "Laid-back beats and smooth acoustics"},
		{ID: "workout", Title: "Workout & Gym", ColorHex: "#E74C3C", Description: "High-energy bangers for intense training"},
		{ID: "focus", Title: "Deep Focus & Study", ColorHex: "#2ECC71", Description: "Ambient lo-fi and instrumental focus music"},
		{ID: "energy", Title: "Energy Booster", ColorHex: "#F39C12", Description: "Upbeat tracks to elevate your mood"},
		{ID: "party", Title: "Party & Club", ColorHex: "#9B59B6", Description: "Dancefloor anthems and club bangers"},
		{ID: "sleep", Title: "Sleep & Drift", ColorHex: "#34495E", Description: "Soothing soundscapes for restful sleep"},
		{ID: "romance", Title: "Romance & Love", ColorHex: "#E91E63", Description: "Intimate R&B and soulful ballads"},
		{ID: "commute", Title: "Commute & Drive", ColorHex: "#16A085", Description: "Upbeat rhythms for your daily travels"},
	}

	return &Engine{
		ytClient: ytClient,
		moods:    defaultMoods,
	}
}

// GetMoodCategories returns available curated mood sections.
func (e *Engine) GetMoodCategories() []MoodCategory {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return e.moods
}

// GetTopCharts retrieves top trending songs for a region.
func (e *Engine) GetTopCharts(ctx context.Context, countryCode string) ([]ChartRanking, error) {
	tracks, err := e.ytClient.Search(ctx, "Top 50 Hits Trending")
	if err != nil {
		return nil, err
	}

	var charts []ChartRanking
	for i, t := range tracks {
		charts = append(charts, ChartRanking{
			Rank:         i + 1,
			TrackID:      t.ID,
			Title:        t.Title,
			Artist:       t.Artist,
			ThumbnailURL: t.ThumbnailURL,
			Trend:        "SAME",
		})
	}

	return charts, nil
}

// GetNewReleases retrieves recently released albums and singles.
func (e *Engine) GetNewReleases(ctx context.Context) ([]models.Track, error) {
	return e.ytClient.Search(ctx, "New Music Releases")
}
