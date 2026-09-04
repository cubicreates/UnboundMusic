/*
 * Package: ai
 * File: ai_test.go
 * Purpose: Unit tests for local semantic vibe parsing, deterministic fallback, JSON extraction, and mood classification.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package ai

import (
	"context"
	"strings"
	"testing"
	"time"
)

// TestParseVibeQuery validates natural language intent extraction using the runner.
func TestParseVibeQuery(t *testing.T) {
	runner := NewRunner("")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "dark aggressive 90s hip hop for the gym")
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

// TestHeuristicFallbackParser validates that "gym phonk workout" returns INTENSE energy and BPM > 130 without binary.
func TestHeuristicFallbackParser(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "gym phonk workout")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	if res.EnergyLevel != "INTENSE" {
		t.Errorf("expected INTENSE energy level, got: %s", res.EnergyLevel)
	}

	if res.SuggestedBPM <= 130 {
		t.Errorf("expected BPM > 130 for gym phonk workout, got: %d", res.SuggestedBPM)
	}

	foundPhonk := false
	for _, g := range res.TargetGenres {
		if strings.Contains(strings.ToLower(g), "phonk") {
			foundPhonk = true
			break
		}
	}
	if !foundPhonk {
		t.Errorf("expected Phonk in target genres, got: %v", res.TargetGenres)
	}
}

// TestJSONExtractionSanitizer validates that markdown backticks and conversational headers are stripped cleanly.
func TestJSONExtractionSanitizer(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name:     "Pure JSON",
			input:    `{"target_genres": ["Rock"], "energy_level": "HIGH"}`,
			expected: `{"target_genres": ["Rock"], "energy_level": "HIGH"}`,
		},
		{
			name: "Markdown code fence with json",
			input: "Here is your parsed vibe:\n```json\n{\"target_genres\": [\"EDM\"], \"energy_level\": \"INTENSE\"}\n```\nEnjoy your workout!",
			expected: `{"target_genres": ["EDM"], "energy_level": "INTENSE"}`,
		},
		{
			name: "Markdown code fence without json tag",
			input: "```\n{\"target_genres\": [\"Lo-Fi\"], \"energy_level\": \"CHILL\"}\n```",
			expected: `{"target_genres": ["Lo-Fi"], "energy_level": "CHILL"}`,
		},
		{
			name: "Conversational text preamble",
			input: "Sure! Below is the JSON output:\n\n{\"target_genres\": [\"Pop\"], \"suggested_bpm\": 120}\nHope this helps!",
			expected: `{"target_genres": ["Pop"], "suggested_bpm": 120}`,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := extractJSON(tc.input)
			if got != tc.expected {
				t.Errorf("extractJSON mismatch:\nGot:      %s\nExpected: %s", got, tc.expected)
			}
		})
	}
}

// TestRunnerTimeout verifies that exceeding timeout trips the heuristic fallback without hanging or panicking.
func TestRunnerTimeout(t *testing.T) {
	// Point to a non-existent or dummy path to test fallback
	runner := NewRunner("non_existent_binary", "non_existent_model.gguf")
	runner.SetTimeout(10 * time.Millisecond)

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	res, err := runner.ParseVibeQuery(ctx, "relaxing chill lofi beats for late night study")
	if err != nil {
		t.Fatalf("expected fallback to succeed, got error: %v", err)
	}

	if res == nil {
		t.Fatalf("expected non-nil result from fallback")
	}

	if res.EnergyLevel != "CHILL" {
		t.Errorf("expected CHILL energy level, got: %s", res.EnergyLevel)
	}

	if res.SuggestedBPM > 100 {
		t.Errorf("expected BPM <= 100 for chill lofi, got: %d", res.SuggestedBPM)
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
