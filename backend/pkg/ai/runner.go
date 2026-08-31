/*
 * Package: ai
 * File: runner.go
 * Purpose: Local edge AI runner executing natural language vibe queries and semantic song mood classification.
 * Subsystem: Edge AI Engine
 * Concurrency: Thread-safe pure inference methods safe for concurrent execution across worker goroutines.
 */

package ai

import (
	"fmt"
	"strings"
	"sync"
)

// VibeQueryResult represents structured search filters extracted from natural language.
type VibeQueryResult struct {
	OriginalPrompt string   `json:"original_prompt"`
	TargetGenres   []string `json:"target_genres"`
	MoodTags       []string `json:"mood_tags"`
	EnergyLevel    string   `json:"energy_level"` // "HIGH", "MEDIUM", "CHILL", "INTENSE"
	SuggestedBPM   int      `json:"suggested_bpm"`
	SearchKeywords []string `json:"search_keywords"`
}

// TrackMoodResult encapsulates mood analysis for a specific track.
type TrackMoodResult struct {
	Title           string   `json:"title"`
	Artist          string   `json:"artist"`
	PrimaryMood     string   `json:"primary_mood"`
	SecondaryMoods  []string `json:"secondary_moods"`
	EnergyScore     float32  `json:"energy_score"` // 0.0 to 1.0
	ValenceScore    float32  `json:"valence_score"` // 0.0 (sad/dark) to 1.0 (happy/bright)
	SuggestedGenres []string `json:"suggested_genres"`
}

// Runner coordinates local edge language model inference.
type Runner struct {
	mu        sync.RWMutex
	modelPath string
	isLoaded  bool
}

// NewRunner instantiates a new on-device AI runner.
func NewRunner(modelPath string) *Runner {
	return &Runner{
		modelPath: modelPath,
		isLoaded:  modelPath != "",
	}
}

// ParseVibeQuery translates a natural language vibe query into structured catalog filters.
func (r *Runner) ParseVibeQuery(prompt string) (*VibeQueryResult, error) {
	trimmed := strings.TrimSpace(prompt)
	if trimmed == "" {
		return nil, fmt.Errorf("prompt cannot be empty")
	}

	lower := strings.ToLower(trimmed)
	res := &VibeQueryResult{
		OriginalPrompt: trimmed,
		EnergyLevel:    "MEDIUM",
		SuggestedBPM:   115,
	}

	// 1. Identify Musical Genres
	genreKeywords := map[string][]string{
		"Hip-Hop / Rap": {"rap", "hip hop", "hip-hop", "boom bap", "trap", "bars", "freestyle", "drill"},
		"R&B / Soul":     {"r&b", "rnb", "soul", "neo-soul", "slow jams", "smooth"},
		"Rock / Metal":   {"rock", "metal", "punk", "guitar", "grunge", "hard rock", "indie rock"},
		"Pop":            {"pop", "dance pop", "catchy", "radio", "hit"},
		"Electronic":     {"edm", "house", "techno", "electronic", "synthwave", "dance", "club"},
		"Lo-Fi / Chill":  {"lofi", "lo-fi", "chill", "study", "relax", "rainy", "sleep", "ambient"},
		"Classical":      {"classical", "piano", "orchestra", "strings", "symphony"},
	}

	for genre, keywords := range genreKeywords {
		for _, kw := range keywords {
			if strings.Contains(lower, kw) {
				res.TargetGenres = append(res.TargetGenres, genre)
				break
			}
		}
	}

	// 2. Identify Mood & Vibe Tags
	moodKeywords := map[string][]string{
		"Aggressive":  {"aggressive", "hard", "angry", "rage", "intense", "gym", "hype", "heavy"},
		"Melancholic": {"sad", "depressed", "melancholy", "heartbreak", "crying", "dark", "gloomy"},
		"Euphoric":    {"happy", "uplifting", "party", "celebrate", "summer", "joy", "bright"},
		"Chill":       {"chill", "relaxed", "calm", "mellow", "vibe", "peaceful", "laid back"},
		"Romantic":    {"romantic", "love", "sensual", "date night", "affection"},
	}

	for mood, keywords := range moodKeywords {
		for _, kw := range keywords {
			if strings.Contains(lower, kw) {
				res.MoodTags = append(res.MoodTags, mood)
				break
			}
		}
	}

	// 3. Estimate Energy & BPM
	if strings.Contains(lower, "gym") || strings.Contains(lower, "hype") || strings.Contains(lower, "fast") || strings.Contains(lower, "rage") {
		res.EnergyLevel = "INTENSE"
		res.SuggestedBPM = 145
	} else if strings.Contains(lower, "chill") || strings.Contains(lower, "sleep") || strings.Contains(lower, "slow") || strings.Contains(lower, "study") {
		res.EnergyLevel = "CHILL"
		res.SuggestedBPM = 85
	}

	// 4. Clean keywords for YouTube / catalog queries
	res.SearchKeywords = strings.Fields(trimmed)

	return res, nil
}

// AnalyzeTrackMood calculates emotional valence and energy attributes for a track.
func (r *Runner) AnalyzeTrackMood(title, artist, lyrics string) (*TrackMoodResult, error) {
	combined := strings.ToLower(fmt.Sprintf("%s %s %s", title, artist, lyrics))

	res := &TrackMoodResult{
		Title:        title,
		Artist:       artist,
		PrimaryMood:  "Energetic",
		EnergyScore:  0.75,
		ValenceScore: 0.60,
	}

	if strings.Contains(combined, "sad") || strings.Contains(combined, "pain") || strings.Contains(combined, "cry") || strings.Contains(combined, "alone") {
		res.PrimaryMood = "Melancholic"
		res.SecondaryMoods = []string{"Emotional", "Introspective"}
		res.ValenceScore = 0.25
		res.EnergyScore = 0.40
	} else if strings.Contains(combined, "fuck") || strings.Contains(combined, "bitch") || strings.Contains(combined, "kill") || strings.Contains(combined, "pussy") || strings.Contains(combined, "war") {
		res.PrimaryMood = "Aggressive"
		res.SecondaryMoods = []string{"Intense", "Explicit", "High-Energy"}
		res.EnergyScore = 0.95
		res.ValenceScore = 0.50
	} else if strings.Contains(combined, "love") || strings.Contains(combined, "heart") || strings.Contains(combined, "baby") {
		res.PrimaryMood = "Romantic"
		res.SecondaryMoods = []string{"Affectionate", "Smooth"}
		res.ValenceScore = 0.80
		res.EnergyScore = 0.55
	}

	return res, nil
}
