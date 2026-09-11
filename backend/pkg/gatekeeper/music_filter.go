/*
 * Package: gatekeeper
 * File: music_filter.go
 * Purpose: Content validation gatekeeper that strictly filters for musical content and rejects non-music videos (vlogs, gameplay, tutorials, news).
 * Subsystem: Quality & Ingestion Gatekeeper
 * Concurrency: Thread-safe, stateless pure functions.
 */

package gatekeeper

import (
	"regexp"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	// Regex patterns for obvious non-music video content
	nonMusicPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)\b(gameplay|walkthrough|playthrough|let's\s*play|speedrun)\b`),
		regexp.MustCompile(`(?i)\b(unboxing|product\s*review|tech\s*review)\b`),
		regexp.MustCompile(`(?i)\b(tutorial|how\s*to\s*(make|code|build|fix|cook)|diy)\b`),
		regexp.MustCompile(`(?i)\b(vlog\s*#?\d*|daily\s*vlog|my\s*day\s*in\s*the\s*life)\b`),
		regexp.MustCompile(`(?i)\b(podcast\s*ep(isode)?\s*#?\d+|full\s*podcast)\b`),
		regexp.MustCompile(`(?i)\b(reaction\s*video|reacting\s*to)\b`),
		regexp.MustCompile(`(?i)\b(breaking\s*news|press\s*conference)\b`),
	}

	// Long form music indicators that allow durations > 20 minutes
	longFormMusicPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)\b(full\s*album|ost|soundtrack|orchestral|symphony|dj\s*set|live\s*set|concert|lofi\s*hip\s*hop|mix)\b`),
	}
)

// IsMusicTrack inspects a track's metadata to determine if it is authentic musical content.
func IsMusicTrack(track models.Track) bool {
	title := strings.TrimSpace(track.Title)
	if title == "" || track.ID == "" {
		return false
	}

	lowerTitle := strings.ToLower(title)
	lowerArtist := strings.ToLower(strings.TrimSpace(track.Artist))

	// Duration constraints
	// Less than 15 seconds is almost certainly a meme, sound effect, or notification sound
	if track.DurationMs > 0 && track.DurationMs < 15000 {
		return false
	}

	// Tracks exceeding 20 minutes (1,200,000 ms) must contain explicit music album / mix indicators
	if track.DurationMs > 1200000 {
		isLongMusic := false
		for _, p := range longFormMusicPatterns {
			if p.MatchString(lowerTitle) {
				isLongMusic = true
				break
			}
		}
		if !isLongMusic {
			return false
		}
	}

	// Reject if title or artist matches non-music video patterns
	for _, p := range nonMusicPatterns {
		if p.MatchString(lowerTitle) || p.MatchString(lowerArtist) {
			return false
		}
	}

	return true
}

// FilterMusicTracks filters an array of tracks, returning only those that satisfy music criteria.
func FilterMusicTracks(tracks []models.Track) []models.Track {
	result := make([]models.Track, 0, len(tracks))
	for _, t := range tracks {
		if IsMusicTrack(t) {
			result = append(result, t)
		}
	}
	return result
}

// FilterRelaxedTracks provides a permissive filter for user's personal libraries when strict music filter yields 0 items.
func FilterRelaxedTracks(tracks []models.Track) []models.Track {
	result := make([]models.Track, 0, len(tracks))
	for _, t := range tracks {
		if strings.TrimSpace(t.Title) == "" || t.ID == "" {
			continue
		}
		if t.DurationMs > 0 && t.DurationMs < 10000 {
			continue // skip under 10s sound clips
		}
		result = append(result, t)
	}
	return result
}
