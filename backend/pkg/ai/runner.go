/*
 * Package: ai
 * File: runner.go
 * Purpose: Single-shot on-demand AI runner executing natural language vibe queries via llama-cli with heuristic fallback.
 * Subsystem: Edge AI Engine
 * Concurrency: Thread-safe pure inference methods safe for concurrent execution across worker goroutines.
 */

package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
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

// Runner coordinates local edge language model inference.
type Runner struct {
	llamaCliPath string
	modelPath    string
	timeout      time.Duration
}

// NewRunner instantiates a new on-device AI runner.
// Supports NewRunner(llamaCliPath, modelPath string) or legacy single-path NewRunner(modelPath string).
func NewRunner(paths ...string) *Runner {
	var cliPath, modelPath string
	if len(paths) == 1 {
		modelPath = paths[0]
	} else if len(paths) >= 2 {
		cliPath = paths[0]
		modelPath = paths[1]
	}

	return &Runner{
		llamaCliPath: cliPath,
		modelPath:    modelPath,
		timeout:      3500 * time.Millisecond,
	}
}

// SetTimeout configures the execution timeout for single-shot inference.
func (r *Runner) SetTimeout(d time.Duration) {
	r.timeout = d
}

// buildPrompt constructs the JSON-constrained system prompt for llama-cli.
func buildPrompt(prompt string) string {
	return fmt.Sprintf(`You are a music vibe parser. Output ONLY valid raw JSON with NO markdown formatting, NO backticks, and NO conversational text.
Follow this schema:
{"target_genres": ["..."], "mood_tags": ["..."], "energy_level": "CHILL|MEDIUM|HIGH|INTENSE", "suggested_bpm": 120, "search_keywords": ["..."]}
User query: %s`, prompt)
}

// extractJSON sanitizes raw CLI output by extracting the outermost JSON object and removing markdown fences.
func extractJSON(raw string) string {
	cleaned := strings.TrimSpace(raw)
	// Strip markdown code block if present
	if idx := strings.Index(cleaned, "```json"); idx != -1 {
		cleaned = cleaned[idx+7:]
	} else if idx := strings.Index(cleaned, "```"); idx != -1 {
		cleaned = cleaned[idx+3:]
	}
	if endIdx := strings.LastIndex(cleaned, "```"); endIdx != -1 {
		cleaned = cleaned[:endIdx]
	}

	// Find the first '{' and last '}'
	start := strings.Index(cleaned, "{")
	end := strings.LastIndex(cleaned, "}")
	if start != -1 && end != -1 && end > start {
		return cleaned[start : end+1]
	}

	return strings.TrimSpace(cleaned)
}

// fileExists checks if a target file exists on disk and is not a directory.
func fileExists(path string) bool {
	if path == "" {
		return false
	}
	info, err := os.Stat(path)
	if err != nil {
		return false
	}
	return !info.IsDir()
}

// ParseVibeQuery translates a natural language vibe query into structured catalog filters.
// It executes the bundled static llama-cli binary in single-shot mode, falling back to deterministic
// heuristic parsing if the binary/model is missing, times out, or produces unparseable output.
func (r *Runner) ParseVibeQuery(ctx context.Context, prompt string) (*models.VibeQueryResult, error) {
	trimmed := strings.TrimSpace(prompt)
	if trimmed == "" {
		return nil, fmt.Errorf("prompt cannot be empty")
	}

	// Check if binary and model exist on disk; if missing, fallback gracefully
	if !fileExists(r.llamaCliPath) || !fileExists(r.modelPath) {
		return r.parseVibeQueryHeuristic(trimmed)
	}

	timeout := r.timeout
	if timeout <= 0 {
		timeout = 3500 * time.Millisecond
	}

	execCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	cmd := exec.CommandContext(execCtx, r.llamaCliPath,
		"-m", r.modelPath,
		"-p", buildPrompt(trimmed),
		"-n", "64",
		"--temp", "0.2",
		"-t", "4",
		"--no-display-prompt",
		"--log-disable",
	)

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		// On timeout or execution error, fallback to deterministic heuristic
		return r.parseVibeQueryHeuristic(trimmed)
	}

	rawOutput := stdout.String()
	jsonStr := extractJSON(rawOutput)

	var result models.VibeQueryResult
	if err := json.Unmarshal([]byte(jsonStr), &result); err != nil {
		return r.parseVibeQueryHeuristic(trimmed)
	}

	result.OriginalPrompt = trimmed
	return &result, nil
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
