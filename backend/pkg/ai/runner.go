/*
 * Package: ai
 * File: runner.go
 * Purpose: Single-shot on-demand AI runner executing natural language vibe queries via llama-cli with heuristic fallback.
 * Subsystem: Edge AI Engine
 * Concurrency: Thread-safe pure inference methods safe for concurrent execution across worker goroutines.
 */

package ai

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// VibeQueryResult is an alias for models.VibeQueryResult for backward compatibility.
type VibeQueryResult = models.VibeQueryResult

// TrackMoodResult encapsulates mood analysis for a specific track.
type TrackMoodResult struct {
	Title           string   `json:"title"`
	Artist          string   `json:"artist"`
	PrimaryMood     string   `json:"primary_mood"`
	SecondaryMoods  []string `json:"secondary_moods"`
	EnergyScore     float32  `json:"energy_score"`  // 0.0 to 1.0
	ValenceScore    float32  `json:"valence_score"` // 0.0 (sad/dark) to 1.0 (happy/bright)
	SuggestedGenres []string `json:"suggested_genres"`
}

// Runner coordinates local edge semantic and heuristic parsing.
type Runner struct {
	modelPath string
	timeout   time.Duration
}

// NewRunner instantiates a new on-device AI runner.
// Supports NewRunner(paths ...string) for backward compatibility.
func NewRunner(paths ...string) *Runner {
	var modelPath string
	if len(paths) == 1 {
		modelPath = paths[0]
	} else if len(paths) >= 2 {
		modelPath = paths[1]
	}

	return &Runner{
		modelPath: modelPath,
		timeout:   500 * time.Millisecond,
	}
}

// SetTimeout configures the execution timeout for inference.
func (r *Runner) SetTimeout(d time.Duration) {
	r.timeout = d
}

// extractJSON sanitizes raw string by extracting the outermost JSON object and removing markdown fences.
func extractJSON(raw string) string {
	cleaned := strings.TrimSpace(raw)
	if idx := strings.Index(cleaned, "```json"); idx != -1 {
		cleaned = cleaned[idx+7:]
	} else if idx := strings.Index(cleaned, "```"); idx != -1 {
		cleaned = cleaned[idx+3:]
	}
	if endIdx := strings.LastIndex(cleaned, "```"); endIdx != -1 {
		cleaned = cleaned[:endIdx]
	}

	start := strings.Index(cleaned, "{")
	end := strings.LastIndex(cleaned, "}")
	if start != -1 && end != -1 && end > start {
		return cleaned[start : end+1]
	}

	return strings.TrimSpace(cleaned)
}

// ParseVibeQuery translates a natural language vibe query into structured catalog filters.
// It executes in-memory deterministic heuristic and semantic token extraction in < 1ms,
// completely eliminating external subprocess spawning on mobile devices.
func (r *Runner) ParseVibeQuery(ctx context.Context, prompt string) (*models.VibeQueryResult, error) {
	trimmed := strings.TrimSpace(prompt)
	if trimmed == "" {
		return nil, fmt.Errorf("prompt cannot be empty")
	}

	return r.parseVibeQueryHeuristic(trimmed)
}

// AnalyzeTrackMood calculates emotional valence and energy attributes for a track using
// an acoustic/lyrical sentiment analyzer based on music mood taxonomies.
func (r *Runner) AnalyzeTrackMood(title, artist, lyrics string) (*TrackMoodResult, error) {
	combined := strings.ToLower(fmt.Sprintf("%s %s %s", title, artist, lyrics))

	res := &TrackMoodResult{
		Title:        title,
		Artist:       artist,
		PrimaryMood:  "Energetic",
		EnergyScore:  0.75,
		ValenceScore: 0.60,
	}

	// 1. Aggressive / High-Intensity / Gym
	if strings.Contains(combined, "rage") || strings.Contains(combined, "war") || strings.Contains(combined, "fight") ||
		strings.Contains(combined, "power") || strings.Contains(combined, "hard") || strings.Contains(combined, "kill") ||
		strings.Contains(combined, "intense") || strings.Contains(combined, "heavy") || strings.Contains(combined, "attack") ||
		strings.Contains(combined, "dna") || strings.Contains(combined, "destroy") {
		res.PrimaryMood = "Aggressive"
		res.SecondaryMoods = []string{"Intense", "High-Energy", "Powerful"}
		res.EnergyScore = 0.92
		res.ValenceScore = 0.48
		res.SuggestedGenres = []string{"Hip-Hop / Rap", "Phonk", "Metal"}
		return res, nil
	}

	// 2. Melancholic / Sad / Introspective
	if strings.Contains(combined, "sad") || strings.Contains(combined, "pain") || strings.Contains(combined, "cry") ||
		strings.Contains(combined, "alone") || strings.Contains(combined, "heartbreak") || strings.Contains(combined, "tears") ||
		strings.Contains(combined, "lonely") || strings.Contains(combined, "broken") || strings.Contains(combined, "sorrow") {
		res.PrimaryMood = "Melancholic"
		res.SecondaryMoods = []string{"Emotional", "Introspective", "Dark"}
		res.ValenceScore = 0.22
		res.EnergyScore = 0.38
		res.SuggestedGenres = []string{"Lo-Fi / Chill", "R&B / Soul", "Acoustic"}
		return res, nil
	}

	// 3. Romantic / Sensual
	if strings.Contains(combined, "love") || strings.Contains(combined, "heart") || strings.Contains(combined, "baby") ||
		strings.Contains(combined, "romance") || strings.Contains(combined, "kiss") || strings.Contains(combined, "tender") ||
		strings.Contains(combined, "sweet") || strings.Contains(combined, "forever") {
		res.PrimaryMood = "Romantic"
		res.SecondaryMoods = []string{"Affectionate", "Smooth", "Warm"}
		res.ValenceScore = 0.82
		res.EnergyScore = 0.52
		res.SuggestedGenres = []string{"R&B / Soul", "Pop"}
		return res, nil
	}

	// 4. Euphoric / Joyful / Celebration
	if strings.Contains(combined, "happy") || strings.Contains(combined, "party") || strings.Contains(combined, "dance") ||
		strings.Contains(combined, "celebrate") || strings.Contains(combined, "summer") || strings.Contains(combined, "sun") ||
		strings.Contains(combined, "joy") || strings.Contains(combined, "bright") || strings.Contains(combined, "good") {
		res.PrimaryMood = "Euphoric"
		res.SecondaryMoods = []string{"Joyful", "Uplifting", "Vibrant"}
		res.ValenceScore = 0.88
		res.EnergyScore = 0.85
		res.SuggestedGenres = []string{"Pop", "Electronic"}
		return res, nil
	}

	// 5. Chill / Relaxed / Ambient
	if strings.Contains(combined, "chill") || strings.Contains(combined, "relax") || strings.Contains(combined, "calm") ||
		strings.Contains(combined, "peace") || strings.Contains(combined, "sleep") || strings.Contains(combined, "dream") ||
		strings.Contains(combined, "soft") || strings.Contains(combined, "breeze") {
		res.PrimaryMood = "Chill"
		res.SecondaryMoods = []string{"Peaceful", "Mellow", "Ambient"}
		res.ValenceScore = 0.65
		res.EnergyScore = 0.28
		res.SuggestedGenres = []string{"Lo-Fi / Chill", "Classical"}
		return res, nil
	}

	return res, nil
}
