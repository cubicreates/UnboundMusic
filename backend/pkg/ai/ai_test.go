/*
 * Package: ai
 * File: ai_test.go
 * Purpose: Unit tests for local semantic vibe parsing and track mood classification.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package ai

import (
	"testing"
)

// TestParseVibeQuery validates natural language intent extraction.
func TestParseVibeQuery(t *testing.T) {
	runner := NewRunner("")

	res, err := runner.ParseVibeQuery("dark aggressive 90s hip hop for the gym")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	if res.EnergyLevel != "INTENSE" {
		t.Errorf("expected INTENSE energy level, got %s", res.EnergyLevel)
	}

	foundHipHop := false
	for _, g := range res.TargetGenres {
		if g == "Hip-Hop / Rap" {
			foundHipHop = true
		}
	}
	if !foundHipHop {
		t.Errorf("expected Hip-Hop / Rap genre in targets: %v", res.TargetGenres)
	}

	foundAggressive := false
	for _, m := range res.MoodTags {
		if m == "Aggressive" {
			foundAggressive = true
		}
	}
	if !foundAggressive {
		t.Errorf("expected Aggressive mood tag: %v", res.MoodTags)
	}
}

// TestAnalyzeTrackMood validates track valence and energy calculations.
func TestAnalyzeTrackMood(t *testing.T) {
	runner := NewRunner("")

	res, err := runner.AnalyzeTrackMood("DNA.", "Kendrick Lamar", "I got loyalty, got royalty inside my DNA. Cocaine quarter piece, got war and peace.")
	if err != nil {
		t.Fatalf("AnalyzeTrackMood failed: %v", err)
	}

	if res.PrimaryMood != "Aggressive" && res.PrimaryMood != "Energetic" {
		t.Errorf("unexpected primary mood: %s", res.PrimaryMood)
	}

	if res.EnergyScore < 0.7 {
		t.Errorf("expected high energy score for DNA, got %f", res.EnergyScore)
	}
}
