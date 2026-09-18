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
		"Triumphant":   {"victory", "victorious", "win", "winning", "won", "champion", "champions", "triumph", "triumphant", "conquer", "hero", "glory", "unstoppable"},
		"Motivational": {"motivation", "motivational", "inspire", "inspiring", "ambition", "determined"},
		"Aggressive":    {"aggressive", "hard", "angry", "rage", "intense", "gym", "hype", "heavy", "deadlift", "workout"},
		"Melancholic":   {"sad", "depressed", "melancholy", "heartbreak", "crying", "dark", "gloomy"},
		"Euphoric":      {"happy", "uplifting", "party", "celebrate", "summer", "joy", "bright"},
		"Chill":         {"chill", "relaxed", "calm", "mellow", "vibe", "peaceful", "laid back"},
		"Romantic":      {"romantic", "love", "sensual", "date night", "affection"},
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
	} else if strings.Contains(lower, "victory") || strings.Contains(lower, "win") || strings.Contains(lower, "champion") ||
		strings.Contains(lower, "triumph") || strings.Contains(lower, "conquer") {
		res.EnergyLevel = "HIGH"
		res.SuggestedBPM = 135
	} else if strings.Contains(lower, "party") || strings.Contains(lower, "dance") || strings.Contains(lower, "run") {
		res.EnergyLevel = "HIGH"
		res.SuggestedBPM = 128
	} else if strings.Contains(lower, "chill") || strings.Contains(lower, "sleep") || strings.Contains(lower, "slow") ||
		strings.Contains(lower, "study") || strings.Contains(lower, "relax") || strings.Contains(lower, "rainy") ||
		strings.Contains(lower, "sad") || strings.Contains(lower, "melancholy") || strings.Contains(lower, "crying") {
		res.EnergyLevel = "CHILL"
		res.SuggestedBPM = 85
	}

	// 4. Clean conversational stop words
	stopWords := map[string]bool{
		"hey": true, "hello": true, "hi": true, "please": true, "can": true, "could": true,
		"you": true, "put": true, "on": true, "play": true, "some": true, "music": true,
		"songs": true, "song": true, "tracks": true, "track": true, "tunes": true, "tune": true,
		"i": true, "want": true, "need": true, "am": true, "im": true, "i'm": true,
		"feeling": true, "feel": true, "feels": true, "vibe": true, "vibes": true, "mood": true,
		"something": true, "for": true, "me": true, "give": true, "listen": true, "to": true,
		"audio": true, "like": true, "with": true, "a": true, "an": true, "the": true,
		"of": true, "and": true, "or": true, "in": true, "at": true, "about": true,
	}

	rawTokens := strings.Fields(lower)
	var cleanTokens []string
	for _, tok := range rawTokens {
		cleaned := strings.Trim(tok, `.,!?;:"'()[]{}`)
		if cleaned != "" && !stopWords[cleaned] {
			cleanTokens = append(cleanTokens, cleaned)
		}
	}

	cleanQuery := strings.Join(cleanTokens, " ")

	// 5. Synthesize high-yield targeted music search queries
	var searchQueries []string

	// Primary synthesized query based on recognized mood / genre
	if len(res.MoodTags) > 0 {
		switch res.MoodTags[0] {
		case "Triumphant":
			searchQueries = append(searchQueries,
				"epic victory anthems",
				"stadium rock hype motivation",
				"we are the champions queen",
				"eye of the tiger survivor",
				"hall of fame the script",
				"remember the name fort minor",
			)
		case "Motivational":
			searchQueries = append(searchQueries,
				"motivation workout anthems",
				"inspiring epic cinematic music",
				"till i collapse eminem",
				"stronger kanye west",
			)
		case "Melancholic":
			if cleanQuery != "" && cleanQuery != "sad" {
				searchQueries = append(searchQueries, cleanQuery+" sad songs")
			} else {
				searchQueries = append(searchQueries, "sad songs", "melancholy acoustic songs")
			}
		case "Aggressive":
			if strings.Contains(lower, "phonk") {
				searchQueries = append(searchQueries, "gym phonk workout", "aggressive drift phonk")
			} else if len(res.TargetGenres) > 0 {
				searchQueries = append(searchQueries, res.TargetGenres[0]+" gym workout hype")
			} else {
				searchQueries = append(searchQueries, "workout gym motivation music")
			}
		case "Euphoric":
			searchQueries = append(searchQueries, "upbeat feel good happy songs", "dance party hits")
		case "Chill":
			if strings.Contains(lower, "sleep") || strings.Contains(lower, "bedtime") {
				searchQueries = append(searchQueries, "deep sleep relaxing ambient music")
			} else if strings.Contains(lower, "study") || strings.Contains(lower, "focus") || strings.Contains(lower, "coding") {
				searchQueries = append(searchQueries, "study beats lofi focus music")
			} else {
				searchQueries = append(searchQueries, "lofi chill beats to relax")
			}
		case "Romantic":
			searchQueries = append(searchQueries, "romantic love songs", "slow acoustic romance")
		}
	}

	// If clean query has specific keywords preserved, prioritize or append
	if cleanQuery != "" {
		if len(searchQueries) == 0 {
			searchQueries = append(searchQueries, cleanQuery)
		} else {
			searchQueries = append(searchQueries, cleanQuery)
		}
	}

	// Always ensure raw keywords exist for token fallback
	for _, tok := range cleanTokens {
		searchQueries = append(searchQueries, tok)
	}

	// Absolute fallback if everything was stripped
	if len(searchQueries) == 0 {
		searchQueries = strings.Fields(trimmed)
	}

	res.SearchKeywords = searchQueries

	return res, nil
}
