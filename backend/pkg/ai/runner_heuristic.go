/*
 * Package: ai
 * File: runner_heuristic.go
 * Purpose: Deterministic keyword-based fallback parser for natural language vibe queries.
 * Subsystem: Edge AI Engine
 * Concurrency: Thread-safe pure function; operates entirely in-memory with zero I/O.
 */

package ai

import (
	"fmt"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// parseVibeQueryHeuristic evaluates keywords across musical genres, moods, and energy levels.
func (r *Runner) parseVibeQueryHeuristic(prompt string) (*models.VibeQueryResult, error) {
	trimmed := strings.TrimSpace(prompt)
	if trimmed == "" {
		return nil, fmt.Errorf("prompt cannot be empty")
	}

	lower := strings.ToLower(trimmed)
	res := &models.VibeQueryResult{
		OriginalPrompt: trimmed,
		EnergyLevel:    "MEDIUM",
		SuggestedBPM:   115,
	}

	// 1. Identify Musical Genres
	genreKeywords := map[string][]string{
		"Hip-Hop / Rap": {"rap", "hip hop", "hip-hop", "boom bap", "trap", "bars", "freestyle", "drill"},
		"Phonk":         {"phonk", "drift", "cowbell", "kordhell", "brazilian phonk", "memphis"},
		"R&B / Soul":     {"r&b", "rnb", "soul", "neo-soul", "slow jams", "smooth"},
		"Rock / Metal":   {"rock", "metal", "punk", "guitar", "grunge", "hard rock", "indie rock", "heavy metal"},
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
		"Aggressive":  {"aggressive", "hard", "angry", "rage", "intense", "gym", "hype", "heavy", "deadlift", "workout"},
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
	if strings.Contains(lower, "gym") || strings.Contains(lower, "hype") || strings.Contains(lower, "fast") ||
		strings.Contains(lower, "rage") || strings.Contains(lower, "intense") || strings.Contains(lower, "workout") ||
		strings.Contains(lower, "deadlift") || strings.Contains(lower, "hard") {
		res.EnergyLevel = "INTENSE"
		res.SuggestedBPM = 145
	} else if strings.Contains(lower, "party") || strings.Contains(lower, "dance") || strings.Contains(lower, "run") {
		res.EnergyLevel = "HIGH"
		res.SuggestedBPM = 128
	} else if strings.Contains(lower, "chill") || strings.Contains(lower, "sleep") || strings.Contains(lower, "slow") ||
		strings.Contains(lower, "study") || strings.Contains(lower, "relax") || strings.Contains(lower, "rainy") {
		res.EnergyLevel = "CHILL"
		res.SuggestedBPM = 85
	}

	// 4. Tokenize search keywords
	res.SearchKeywords = strings.Fields(trimmed)

	return res, nil
}
