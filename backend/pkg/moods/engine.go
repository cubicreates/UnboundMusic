/*
 * Package: moods
 * File: engine.go
 * Purpose: Time-aware situational recommendation engine implementing 24-hour temporal dayparting and mood capsules.
 * Subsystem: Recommendation & Mood Engine
 * Concurrency: Thread-safe pure evaluation functions and HTTP client wrappers.
 */

package moods

import (
	"context"
	"fmt"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// Engine evaluates temporal windows and resolves situational mood capsules into radio streams.
type Engine struct {
	exploreEngine *ytmusic.ExploreEngine
}

// NewEngine instantiates a new temporal dayparting and mood recommendation engine.
func NewEngine(exploreEngine *ytmusic.ExploreEngine) *Engine {
	return &Engine{
		exploreEngine: exploreEngine,
	}
}

// GetDaypartingState computes active temporal window based on local device hour (0-23).
func (e *Engine) GetDaypartingState(localHour int) *models.DaypartingState {
	// Normalize negative hours or values >= 24 via modulo
	h := (localHour%24 + 24) % 24

	var window string
	var capsules []models.MoodCapsule

	switch {
	case h >= 6 && h < 11:
		window = "MORNING_COMMUTE"
		capsules = []models.MoodCapsule{
			{Tag: "#WakeUp", Title: "Morning Acoustic", BrowseID: "FEmusic_moods_and_genres_category_chill", Description: "Gentle acoustic melodies to start your morning", ColorHex: "#FFB74D", IconName: "ic_coffee"},
			{Tag: "#Commute", Title: "Daily Commute", BrowseID: "FEmusic_moods_and_genres_category_commute", Description: "Upbeat tracks for traffic and public transit", ColorHex: "#64B5F6", IconName: "ic_car"},
			{Tag: "#PopPicks", Title: "Upbeat Morning Pop", BrowseID: "FEmusic_moods_and_genres_category_pop", Description: "Energizing pop radio hits", ColorHex: "#81C784", IconName: "ic_sun"},
		}
	case h >= 11 && h < 17:
		window = "DEEP_FOCUS"
		capsules = []models.MoodCapsule{
			{Tag: "#Focus", Title: "Deep Work Flow", BrowseID: "FEmusic_moods_and_genres_category_focus", Description: "Instrumental soundtracks for maximum productivity", ColorHex: "#4DB6AC", IconName: "ic_brain"},
			{Tag: "#LoFi", Title: "Lo-Fi Study Beats", BrowseID: "FEmusic_moods_and_genres_category_lofi", Description: "Chill lo-fi hip hop to study and code to", ColorHex: "#BA68C8", IconName: "ic_book"},
			{Tag: "#Ambient", Title: "Calm Atmosphere", BrowseID: "FEmusic_moods_and_genres_category_ambient", Description: "Textured soundscapes and peaceful piano", ColorHex: "#90A4AE", IconName: "ic_cloud"},
		}
	case h >= 17 && h < 21:
		window = "WORKOUT_AND_DRIVE"
		capsules = []models.MoodCapsule{
			{Tag: "#Workout", Title: "Gym Heavy Intensity", BrowseID: "FEmusic_moods_and_genres_category_workout", Description: "High-tempo phonk and aggressive bass for heavy sets", ColorHex: "#E57373", IconName: "ic_dumbbell"},
			{Tag: "#Drive", Title: "Sunset Drive", BrowseID: "FEmusic_moods_and_genres_category_energy", Description: "Driving electronic beats and energetic anthems", ColorHex: "#FF8A65", IconName: "ic_steering_wheel"},
			{Tag: "#EDMHype", Title: "Pure Adrenaline", BrowseID: "FEmusic_moods_and_genres_category_dance", Description: "Electrifying festival anthems and bass drops", ColorHex: "#F06292", IconName: "ic_bolt"},
		}
	default:
		window = "LATE_NIGHT_CHILL"
		capsules = []models.MoodCapsule{
			{Tag: "#LateNight", Title: "Midnight Vibes", BrowseID: "FEmusic_moods_and_genres_category_rnb", Description: "Atmospheric R&B, slow jams, and nocturnal melodies", ColorHex: "#7986CB", IconName: "ic_moon"},
			{Tag: "#Chill", Title: "Slowed Down & Reverb", BrowseID: "FEmusic_moods_and_genres_category_chill", Description: "Relaxed tempo tracks and mellow vocals", ColorHex: "#9575CD", IconName: "ic_feather"},
			{Tag: "#Sleep", Title: "Drift Into Sleep", BrowseID: "FEmusic_moods_and_genres_category_sleep", Description: "Deep delta wave textures and sleep soundscapes", ColorHex: "#4FC3F7", IconName: "ic_bed"},
		}
	}

	return &models.DaypartingState{
		ActiveWindow: window,
		LocalHour:    h,
		Capsules:     capsules,
	}
}

// FetchMoodRadio resolves a mood capsule browseID into a list of playable tracks.
// Falls back seamlessly to regional charts if the browse query encounters network or parsing failure.
func (e *Engine) FetchMoodRadio(ctx context.Context, browseID, countryCode, langCode string) ([]models.TrackItem, error) {
	if e.exploreEngine == nil {
		return nil, fmt.Errorf("explore engine not configured")
	}

	// 1. Attempt to fetch specific mood radio / category endpoint
	if browseID != "" {
		tracks, err := e.exploreEngine.FetchBrowse(ctx, browseID, countryCode, langCode)
		if err == nil && len(tracks) > 0 {
			return tracks, nil
		}
	}

	// 2. Fallback gracefully to regional charts
	fallbackTracks, err := e.exploreEngine.FetchRegionalCharts(ctx, countryCode, langCode)
	if err != nil {
		return nil, fmt.Errorf("both mood radio and fallback charts failed: %w", err)
	}

	return fallbackTracks, nil
}
