/*
 * Package: analytics
 * File: analytics_test.go
 * Purpose: Unit tests for listening event tracking, recap rankings, decade breakdown, and Shannon entropy diversity calculations.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package analytics

import (
	"testing"
)

// TestAnalyticsRecapGeneration validates aggregation, top rankings, and diversity score.
func TestAnalyticsRecapGeneration(t *testing.T) {
	engine := NewEngine()

	// Log diverse tracks
	engine.LogPlayback(PlaybackEvent{Title: "DNA.", Artist: "Kendrick Lamar", Album: "DAMN.", ListenedSec: 185, Year: 2017})
	engine.LogPlayback(PlaybackEvent{Title: "HUMBLE.", Artist: "Kendrick Lamar", Album: "DAMN.", ListenedSec: 177, Year: 2017})
	engine.LogPlayback(PlaybackEvent{Title: "Billie Jean", Artist: "Michael Jackson", Album: "Thriller", ListenedSec: 294, Year: 1982})

	recap := engine.GenerateRecap()

	if recap.TotalTracksPlayed != 3 {
		t.Errorf("expected 3 tracks played, got %d", recap.TotalTracksPlayed)
	}

	if recap.UniqueArtistsCount != 2 {
		t.Errorf("expected 2 unique artists, got %d", recap.UniqueArtistsCount)
	}

	if len(recap.TopArtists) == 0 || recap.TopArtists[0].Name != "Kendrick Lamar" {
		t.Errorf("expected Kendrick Lamar as top artist, got %v", recap.TopArtists)
	}

	if recap.DecadeDistribution["2010s"] != 2 {
		t.Errorf("expected 2 plays in 2010s decade, got %d", recap.DecadeDistribution["2010s"])
	}

	if recap.DecadeDistribution["1980s"] != 1 {
		t.Errorf("expected 1 play in 1980s decade, got %d", recap.DecadeDistribution["1980s"])
	}

	if recap.TasteDiversityScore <= 0 {
		t.Errorf("expected non-zero taste diversity score, got %f", recap.TasteDiversityScore)
	}
}
