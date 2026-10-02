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

	foundSadSeed := false
	for _, kw := range res.SearchKeywords {
		lower := strings.ToLower(kw)
		if strings.Contains(lower, "adele") || strings.Contains(lower, "coldplay") || strings.Contains(lower, "sad") {
			foundSadSeed = true
			break
		}
	}
	if !foundSadSeed {
		t.Errorf("expected melancholy seed track or sad in search keywords, got: %v", res.SearchKeywords)
	}
}

// TestVictorySongsSemanticQuery validates that "Victory Songs" triggers Triumphant mood and anthem queries.
func TestVictorySongsSemanticQuery(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "Victory Songs")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	foundTriumphant := false
	for _, m := range res.MoodTags {
		if m == "Triumphant" {
			foundTriumphant = true
			break
		}
	}
	if !foundTriumphant {
		t.Errorf("expected Triumphant mood tag for 'Victory Songs', got: %v", res.MoodTags)
	}

	if res.EnergyLevel != "HIGH" {
		t.Errorf("expected HIGH energy level, got: %s", res.EnergyLevel)
	}

	foundAnthems := false
	for _, kw := range res.SearchKeywords {
		lower := strings.ToLower(kw)
		if strings.Contains(lower, "anthem") || strings.Contains(lower, "queen") || strings.Contains(lower, "survivor") {
			foundAnthems = true
			break
		}
	}
	if !foundAnthems {
		t.Errorf("expected anthem or iconic champion seeds in search keywords, got: %v", res.SearchKeywords)
	}
}

// TestVictorySongsIndianRegion validates that "Victory Songs" with region "IN" returns iconic Indian victory anthems.
func TestVictorySongsIndianRegion(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "Victory Songs", "IN")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	if res.Region != "IN" {
		t.Errorf("expected region IN, got %s", res.Region)
	}

	foundChakDe := false
	foundGenericPhrase := false
	for _, kw := range res.SearchKeywords {
		lower := strings.ToLower(kw)
		if strings.Contains(lower, "chak de") || strings.Contains(lower, "zinda") || strings.Contains(lower, "lakshya") {
			foundChakDe = true
		}
		if lower == "victory songs" || lower == "victory song" {
			foundGenericPhrase = true
		}
	}

	if !foundChakDe {
		t.Errorf("expected iconic Indian victory anthem like Chak De India or Zinda in keywords, got: %v", res.SearchKeywords)
	}
	if foundGenericPhrase {
		t.Errorf("expected generic phrase 'victory songs' to be filtered out, got: %v", res.SearchKeywords)
	}
}

// TestVictorySongsWesternRegion validates that "Victory Songs" with default/US region returns Queen and Survivor.
func TestVictorySongsWesternRegion(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "Victory Songs", "US")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	foundQueen := false
	for _, kw := range res.SearchKeywords {
		lower := strings.ToLower(kw)
		if strings.Contains(lower, "queen") || strings.Contains(lower, "survivor") || strings.Contains(lower, "champions") {
			foundQueen = true
			break
		}
	}
	if !foundQueen {
		t.Errorf("expected global champion anthems in keywords, got: %v", res.SearchKeywords)
	}
}

// TestSleepySongsIndianRegion validates that "I am sleepy play me some sleepy songs" for India returns iconic Indian calm/sleep songs.
func TestSleepySongsIndianRegion(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	res, err := runner.ParseVibeQuery(ctx, "I am sleepy play me some sleepy songs", "IN")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}

	foundIndianChill := false
	for _, kw := range res.SearchKeywords {
		lower := strings.ToLower(kw)
		if strings.Contains(lower, "phir le aya") || strings.Contains(lower, "kun faya") || strings.Contains(lower, "iktara") || strings.Contains(lower, "flute") {
			foundIndianChill = true
			break
		}
	}
	if !foundIndianChill {
		t.Errorf("expected Indian relaxing / sleepy tracks in keywords, got: %v", res.SearchKeywords)
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

func TestIndianRegionalGymAndLoveVibes(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	// 1. Gym Workout Prompt in India
	resGym, err := runner.ParseVibeQuery(ctx, "gym workout motivation", "IN")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}
	if resGym.EnergyLevel != "INTENSE" {
		t.Errorf("expected INTENSE for gym prompt, got: %s", resGym.EnergyLevel)
	}
	hasMalhariOrSultan := false
	for _, kw := range resGym.SearchKeywords {
		if strings.Contains(kw, "Malhari") || strings.Contains(kw, "Sultan") || strings.Contains(kw, "Aarambh") {
			hasMalhariOrSultan = true
			break
		}
	}
	if !hasMalhariOrSultan {
		t.Errorf("expected Indian workout anthems in keywords, got: %v", resGym.SearchKeywords)
	}

	// 2. Romantic Love Prompt in India
	resLove, err := runner.ParseVibeQuery(ctx, "romantic songs for date night", "IN")
	if err != nil {
		t.Fatalf("ParseVibeQuery failed: %v", err)
	}
	hasTumHiHoOrKesariya := false
	for _, kw := range resLove.SearchKeywords {
		if strings.Contains(kw, "Tum Hi Ho") || strings.Contains(kw, "Kesariya") || strings.Contains(kw, "Raataan Lambiyan") {
			hasTumHiHoOrKesariya = true
			break
		}
	}
	if !hasTumHiHoOrKesariya {
		t.Errorf("expected Bollywood romantic seeds in keywords, got: %v", resLove.SearchKeywords)
	}
}

func TestDeduceAlbumAndYear(t *testing.T) {
	runner := NewRunner("", "")
	ctx := context.Background()

	// 1. Folder with Album and Year (2013)
	p1 := "/storage/emulated/0/Music/Random Access Memories (2013)/Daft Punk - Get Lucky.mp3"
	res1, err := runner.DeduceTrackMetadata(ctx, p1)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res1.Artist != "Daft Punk" {
		t.Errorf("expected Daft Punk, got %s", res1.Artist)
	}
	if res1.Title != "Get Lucky" {
		t.Errorf("expected Get Lucky, got %s", res1.Title)
	}
	if res1.Album != "Random Access Memories" {
		t.Errorf("expected Random Access Memories, got %s", res1.Album)
	}
	if res1.Year != "2013" {
		t.Errorf("expected 2013, got %s", res1.Year)
	}

	// 2. Folder with Artist - Album [2016]
	p2 := "/storage/emulated/0/Music/The Weeknd - Starboy [2016]/01 Starboy.mp3"
	res2, err := runner.DeduceTrackMetadata(ctx, p2)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res2.Album != "Starboy" {
		t.Errorf("expected Starboy album, got %s", res2.Album)
	}
	if res2.Year != "2016" {
		t.Errorf("expected 2016, got %s", res2.Year)
	}

	// 3. Generic downloads folder should not treat folder as album
	p3 := "/storage/emulated/0/Download/Alan Walker - Faded.mp3"
	res3, err := runner.DeduceTrackMetadata(ctx, p3)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res3.Album != "" {
		t.Errorf("expected empty album for generic Download folder, got %s", res3.Album)
	}
}

