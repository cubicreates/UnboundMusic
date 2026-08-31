/*
 * Package: analytics
 * File: analytics.go
 * Purpose: On-device listening analytics, playback event logging, and "Unbound Recap" generation with decade distribution and taste diversity metrics.
 * Subsystem: Analytics & Listening Intelligence
 * Concurrency: Thread-safe in-memory event tracking with persistent SQLite sync.
 */

package analytics

import (
	"fmt"
	"math"
	"sort"
	"sync"
	"time"
)

// PlaybackEvent represents a completed or partial song play event.
type PlaybackEvent struct {
	TrackID         string    `json:"track_id"`
	Title           string    `json:"title"`
	Artist          string    `json:"artist"`
	Album           string    `json:"album"`
	DurationSec     int64     `json:"duration_sec"`
	ListenedSec     int64     `json:"listened_sec"`
	Year            int       `json:"year"`
	Timestamp       time.Time `json:"timestamp"`
}

// TopItem represents an aggregated ranking item (track, artist, or album).
type TopItem struct {
	Name        string `json:"name"`
	PlayCount   int    `json:"play_count"`
	TotalSec    int64  `json:"total_sec"`
}

// RecapReport encapsulates the complete "Unbound Recap" (Spotify Wrapped equivalent).
type RecapReport struct {
	TotalListeningMinutes int64               `json:"total_listening_minutes"`
	TotalTracksPlayed     int                 `json:"total_tracks_played"`
	UniqueArtistsCount    int                 `json:"unique_artists_count"`
	TopTracks             []TopItem           `json:"top_tracks"`
	TopArtists            []TopItem           `json:"top_artists"`
	TopAlbums             []TopItem           `json:"top_albums"`
	DecadeDistribution    map[string]int      `json:"decade_distribution"`
	TasteDiversityScore   float64             `json:"taste_diversity_score"` // 0.0 to 100.0
	GeneratedAt           time.Time           `json:"generated_at"`
}

// Engine coordinates listening analytics and recap calculations.
type Engine struct {
	mu     sync.RWMutex
	events []PlaybackEvent
}

// NewEngine initializes a listening analytics engine.
func NewEngine() *Engine {
	return &Engine{
		events: make([]PlaybackEvent, 0, 100),
	}
}

// LogPlayback records a song listening event.
func (e *Engine) LogPlayback(event PlaybackEvent) {
	e.mu.Lock()
	defer e.mu.Unlock()

	if event.Timestamp.IsZero() {
		event.Timestamp = time.Now()
	}
	e.events = append(e.events, event)
}

// GenerateRecap computes listening stats, rankings, decade breakdown, and entropy score.
func (e *Engine) GenerateRecap() *RecapReport {
	e.mu.RLock()
	defer e.mu.RUnlock()

	trackCounts := make(map[string]*TopItem)
	artistCounts := make(map[string]*TopItem)
	albumCounts := make(map[string]*TopItem)
	decadeMap := make(map[string]int)

	var totalSec int64

	for _, ev := range e.events {
		totalSec += ev.ListenedSec

		// Track aggregation
		tKey := fmt.Sprintf("%s - %s", ev.Title, ev.Artist)
		if item, exists := trackCounts[tKey]; exists {
			item.PlayCount++
			item.TotalSec += ev.ListenedSec
		} else {
			trackCounts[tKey] = &TopItem{Name: tKey, PlayCount: 1, TotalSec: ev.ListenedSec}
		}

		// Artist aggregation
		if ev.Artist != "" {
			if item, exists := artistCounts[ev.Artist]; exists {
				item.PlayCount++
				item.TotalSec += ev.ListenedSec
			} else {
				artistCounts[ev.Artist] = &TopItem{Name: ev.Artist, PlayCount: 1, TotalSec: ev.ListenedSec}
			}
		}

		// Album aggregation
		if ev.Album != "" {
			if item, exists := albumCounts[ev.Album]; exists {
				item.PlayCount++
				item.TotalSec += ev.ListenedSec
			} else {
				albumCounts[ev.Album] = &TopItem{Name: ev.Album, PlayCount: 1, TotalSec: ev.ListenedSec}
			}
		}

		// Decade classification
		if ev.Year > 1900 {
			decade := fmt.Sprintf("%ds", (ev.Year/10)*10)
			decadeMap[decade]++
		}
	}

	// Calculate Taste Diversity (Shannon Entropy)
	diversityScore := calculateDiversityScore(artistCounts, len(e.events))

	return &RecapReport{
		TotalListeningMinutes: totalSec / 60,
		TotalTracksPlayed:     len(e.events),
		UniqueArtistsCount:    len(artistCounts),
		TopTracks:             sortTopItems(trackCounts, 10),
		TopArtists:            sortTopItems(artistCounts, 5),
		TopAlbums:             sortTopItems(albumCounts, 5),
		DecadeDistribution:    decadeMap,
		TasteDiversityScore:   diversityScore,
		GeneratedAt:           time.Now(),
	}
}

func calculateDiversityScore(artists map[string]*TopItem, totalPlays int) float64 {
	if totalPlays <= 1 || len(artists) <= 1 {
		return 50.0
	}

	var entropy float64
	for _, item := range artists {
		p := float64(item.PlayCount) / float64(totalPlays)
		if p > 0 {
			entropy -= p * math.Log(p)
		}
	}

	maxEntropy := math.Log(float64(len(artists)))
	if maxEntropy <= 0 {
		return 50.0
	}

	score := (entropy / maxEntropy) * 100.0
	if score > 100.0 {
		score = 100.0
	}
	return math.Round(score*10) / 10
}

func sortTopItems(m map[string]*TopItem, limit int) []TopItem {
	var list []TopItem
	for _, v := range m {
		list = append(list, *v)
	}

	sort.Slice(list, func(i, j int) bool {
		if list[i].PlayCount == list[j].PlayCount {
			return list[i].TotalSec > list[j].TotalSec
		}
		return list[i].PlayCount > list[j].PlayCount
	})

	if len(list) > limit {
		return list[:limit]
	}
	return list
}
