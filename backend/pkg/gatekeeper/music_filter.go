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
		regexp.MustCompile(`(?i)(#shorts\b|#short\b|\bshorts\b|\breels?\b|\btiktok\b)`),
	}

	// Channel / Artist names that are vloggers, couple channels, or content creators (not musicians)
	nonMusicArtistPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)\b(vlogs?|couples?|family|gaming|shorts|podcast|reacts?|reaction|pranks?|comedy)\b`),
		regexp.MustCompile(`(?i)\b(sam\s*and\s*monica|troom\s*troom|mrbeast|dude\s*perfect|pewdiepie|sidemen|dhar\s*mann|5-minute\s*crafts)\b`),
	}

	// Long form music indicators that allow durations > 20 minutes
	longFormMusicPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)\b(full\s*album|ost|soundtrack|orchestral|symphony|dj\s*set|live\s*set|concert|lofi\s*hip\s*hop|mix)\b`),
	}
)

// IsShortsVideo returns true if a track has YouTube Shorts duration or hashtags.
func IsShortsVideo(track models.Track) bool {
	// YouTube Shorts are strictly <= 60 seconds. Rejecting <= 75s eliminates all Shorts and intro bumpers.
	if track.DurationMs > 0 && track.DurationMs < 75000 {
		return true
	}
	lowerTitle := strings.ToLower(track.Title)
	if strings.Contains(lowerTitle, "#short") || strings.Contains(lowerTitle, "#shorts") || strings.Contains(lowerTitle, "shorts") {
		return true
	}
	return false
}

// IsAuthenticMusicArtist checks if a channel or artist is authentic musical creator rather than a vlog/shorts channel.
func IsAuthenticMusicArtist(artist string) bool {
	trimmed := strings.TrimSpace(artist)
	if trimmed == "" || trimmed == "YouTube Artist" || trimmed == "Various Artists" {
		return false
	}
	lower := strings.ToLower(trimmed)
	for _, p := range nonMusicArtistPatterns {
		if p.MatchString(lower) {
			return false
		}
	}
	return true
}

// IsMusicTrack inspects a track's metadata to determine if it is authentic musical content.
func IsMusicTrack(track models.Track) bool {
	title := strings.TrimSpace(track.Title)
	if title == "" || track.ID == "" {
		return false
	}

	// Strict YouTube Shorts elimination
	if IsShortsVideo(track) {
		return false
	}

	lowerTitle := strings.ToLower(title)
	lowerArtist := strings.ToLower(strings.TrimSpace(track.Artist))

	// Reject if artist is a known non-music channel or creator
	for _, p := range nonMusicArtistPatterns {
		if p.MatchString(lowerArtist) {
			return false
		}
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

// FilterRelaxedTracks provides a fallback music filter while strictly barring YouTube Shorts, vlogs, and non-music channels.
func FilterRelaxedTracks(tracks []models.Track) []models.Track {
	result := make([]models.Track, 0, len(tracks))
	for _, t := range tracks {
		if strings.TrimSpace(t.Title) == "" || t.ID == "" {
			continue
		}
		// Never allow YouTube Shorts even in relaxed mode
		if IsShortsVideo(t) {
			continue
		}
		lowerTitle := strings.ToLower(strings.TrimSpace(t.Title))
		lowerArtist := strings.ToLower(strings.TrimSpace(t.Artist))

		// Check non-music video patterns (vlog, podcast, prank, tutorial, gameplay)
		isNonMusic := false
		for _, p := range nonMusicPatterns {
			if p.MatchString(lowerTitle) || p.MatchString(lowerArtist) {
				isNonMusic = true
				break
			}
		}
		if isNonMusic {
			continue
		}

		// Check non-music creator patterns
		for _, p := range nonMusicArtistPatterns {
			if p.MatchString(lowerArtist) {
				isNonMusic = true
				break
			}
		}
		if isNonMusic {
			continue
		}

		result = append(result, t)
	}
	return result
}
