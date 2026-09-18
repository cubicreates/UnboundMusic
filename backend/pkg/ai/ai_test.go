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

// TestConversationalVibeQuery tests conversational natural language input stripping pleasantries and mapping to mood.
func TestConversationalVibeQuery(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "Hey I am feeling Sad play some music")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	foundMelancholic := false
	for _, m := range res.MoodTags {
		if m == "Melancholic" {
			foundMelancholic = true
		}
	}
	if !foundMelancholic {
		t.Errorf("expected Melancholic mood tag, got: %v", res.MoodTags)
	}

	if res.EnergyLevel != "CHILL" {
		t.Errorf("expected CHILL energy level for sad mood, got: %s", res.EnergyLevel)
	}

	if len(res.SearchKeywords) == 0 || res.SearchKeywords[0] != "sad songs" {
		t.Errorf("expected 'sad songs' as primary search query, got: %v", res.SearchKeywords)
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

// TestDeduceTrackMetadata verifies stripping downloader artifacts and extracting title/artist.
func TestDeduceTrackMetadata(t *testing.T) {
	runner := NewRunner("")
	ctx := context.Background()

	testCases := []struct {
		filePath       string
		expectedTitle  string
		expectedArtist string
		expectedYTID   string
	}{
		{
			filePath:       "/storage/emulated/0/Download/y2mate.is - The Weeknd - Blinding Lights (Official Video)-4NRXx6U8ABQ-192k.mp3",
			expectedTitle:  "Blinding Lights",
			expectedArtist: "The Weeknd",
			expectedYTID:   "4NRXx6U8ABQ",
		},
		{
			filePath:       "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp Audio/AUD-20240915-WA0032_Travis_Scott_-_FEIN_(Remix).m4a",
			expectedTitle:  "FEIN",
			expectedArtist: "Travis Scott",
		},
		{
			filePath:       "/storage/emulated/0/Download/Alan Walker - Faded.opus",
			expectedTitle:  "Faded",
			expectedArtist: "Alan Walker",
		},
		{
			filePath:       "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-20240915-WA0001.opus",
			expectedTitle:  "WhatsApp Audio 2024-09-15 #0001",
			expectedArtist: "WhatsApp Audio",
		},
	}

	for _, tc := range testCases {
		res, err := runner.DeduceTrackMetadata(ctx, tc.filePath)
		if err != nil {
			t.Fatalf("DeduceTrackMetadata failed for %s: %v", tc.filePath, err)
		}
		if !strings.Contains(strings.ToLower(res.Title), strings.ToLower(tc.expectedTitle)) {
			t.Errorf("for %s expected title containing %q, got %q", tc.filePath, tc.expectedTitle, res.Title)
		}
		if tc.expectedArtist != "" && !strings.Contains(strings.ToLower(res.Artist), strings.ToLower(tc.expectedArtist)) {
			t.Errorf("for %s expected artist containing %q, got %q", tc.filePath, tc.expectedArtist, res.Artist)
		}
		if tc.expectedYTID != "" && res.YouTubeID != tc.expectedYTID {
			t.Errorf("for %s expected YouTube ID %q, got %q", tc.filePath, tc.expectedYTID, res.YouTubeID)
		}
	}
}
