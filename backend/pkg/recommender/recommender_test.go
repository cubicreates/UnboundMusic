/*
 * Package: recommender
 * File: recommender_test.go
 * Purpose: Unit tests for offline smart radio queue and similar track generation.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package recommender

import (
	"context"
	"testing"
)

// TestGenerateRadioMix validates offline smart queue synthesis.
func TestGenerateRadioMix(t *testing.T) {
	engine := NewEngine(nil)

	mix, err := engine.GenerateRadioMix(context.Background(), "kendrick_dna", 10)
	if err != nil {
		t.Fatalf("GenerateRadioMix failed: %v", err)
	}

	if mix.TotalTracks != 10 {
		t.Errorf("expected 10 tracks, got %d", mix.TotalTracks)
	}

	if len(mix.Tracks) != 10 {
		t.Errorf("expected 10 track objects, got %d", len(mix.Tracks))
	}

	for _, track := range mix.Tracks {
		if track.ID == "" || track.Title == "" || track.Artist == "" {
			t.Errorf("incomplete track metadata in radio mix: %+v", track)
		}
	}
}
