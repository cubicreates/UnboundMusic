/*
 * Package: ai
 * File: deduce.go
 * Purpose: Deduce and extract clean song metadata (Title, Artist, Album) from mangled filenames, scraper artifacts, and parent folder context using SmolLM2-135M or deterministic heuristics.
 * Subsystem: Edge AI Engine
 * Concurrency: Thread-safe pure inference and heuristic parsing.
 */

package ai

import (
	"context"
	"fmt"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	// Scraper prefixes to strip
	scraperPrefixes = regexp.MustCompile(`(?i)^(y2mate(\.(is|com|tools))?|y2meta(\.app)?|snaptube(_audio)?|videoplayback|spotifymate(\.com)?|aud-\d{8}-wa\d+|ptt-\d{8}-wa\d+|audio[_-]\d+)\s*[-_–—]?\s*`)

	// Pure WhatsApp audio and voice notes patterns: AUD-YYYYMMDD-WA0001 or PTT-YYYYMMDD-WA0001
	waAudioPattern = regexp.MustCompile(`(?i)^AUD-(\d{4})(\d{2})(\d{2})-WA(\d+)\s*$`)
	waVoicePattern = regexp.MustCompile(`(?i)^PTT-(\d{4})(\d{2})(\d{2})-WA(\d+)\s*$`)

	// Media metadata tags inside parentheses/brackets
	mediaTags = regexp.MustCompile(`(?i)[\(\[]\s*(official\s*(video|audio|music\s*video|lyric\s*video|lyrics|hd|4k)?|128\s*kbps|192\s*k(bps)?|256\s*kbps|320\s*k(bps)?|hq|hd|remastered|audio|lyrics)\s*[\)\]]`)

	// 11-char YouTube ID (e.g. -4NRXx6U8ABQ or _JGwWNGJdvx8)
	ytIDPattern = regexp.MustCompile(`[-_]([0-9A-Za-z_-]{11})(?:[-_]\d+k(?:bps)?)?(?:\s*[\(\[].*?[\)\]])?$`)
)

// DeduceTrackMetadata deduces Title, Artist, and Album from an audio file's path and filename
// using lightning-fast deterministic heuristics with zero external subprocess dependencies.
func (r *Runner) DeduceTrackMetadata(ctx context.Context, filePath string) (*models.TrackIdentificationResult, error) {
	cleanPath := filepath.Clean(filePath)
	baseName := filepath.Base(cleanPath)
	ext := filepath.Ext(baseName)
	rawTitle := strings.TrimSuffix(baseName, ext)
	parentFolder := filepath.Base(filepath.Dir(cleanPath))

	// Check for embedded 11-character YouTube video ID
	var ytID string
	if match := ytIDPattern.FindStringSubmatch(rawTitle); len(match) > 1 {
		ytID = match[1]
	}

	heuristicRes := CleanAndDeduceHeuristic(rawTitle, parentFolder)
	heuristicRes.YouTubeID = ytID
	return heuristicRes, nil
}

// CleanAndDeduceHeuristic performs heuristic string sanitization and splitting on track names.
func CleanAndDeduceHeuristic(rawName, parentFolder string) *models.TrackIdentificationResult {
	cleaned := strings.TrimSpace(rawName)

	// Check WhatsApp audio and voice notes before stripping
	if match := waAudioPattern.FindStringSubmatch(cleaned); len(match) == 5 {
		year, month, day, num := match[1], match[2], match[3], match[4]
		return &models.TrackIdentificationResult{
			Title:       fmt.Sprintf("WhatsApp Audio %s-%s-%s #%s", year, month, day, num),
			Artist:      "WhatsApp Audio",
			Album:       "WhatsApp Media",
			SearchQuery: fmt.Sprintf("WhatsApp Audio %s", year),
			Method:      "voice_media_parser",
			Confidence:  0.88,
		}
	}
	if match := waVoicePattern.FindStringSubmatch(cleaned); len(match) == 5 {
		year, month, day, num := match[1], match[2], match[3], match[4]
		return &models.TrackIdentificationResult{
			Title:       fmt.Sprintf("Voice Note %s-%s-%s #%s", year, month, day, num),
			Artist:      "Voice Note",
			Album:       "WhatsApp Voice Notes",
			SearchQuery: fmt.Sprintf("Voice Note %s", year),
			Method:      "voice_media_parser",
			Confidence:  0.88,
		}
	}

	// Strip scraper prefixes
	cleaned = scraperPrefixes.ReplaceAllString(cleaned, "")

	// Strip media tag brackets
	cleaned = mediaTags.ReplaceAllString(cleaned, "")

	// Strip 11-char YouTube ID at the tail if present
	cleaned = ytIDPattern.ReplaceAllString(cleaned, "")

	// Strip trailing bitrate suffixes (e.g. -192k, _320kbps)
	bitrateSuffixes := regexp.MustCompile(`(?i)[-_ ]\d+\s*k(?:bps)?$`)
	cleaned = bitrateSuffixes.ReplaceAllString(cleaned, "")

	// Replace underscores with spaces
	cleaned = strings.ReplaceAll(cleaned, "_", " ")

	// Replace multiple spaces with single space
	spaceRegex := regexp.MustCompile(`\s+`)
	cleaned = strings.TrimSpace(spaceRegex.ReplaceAllString(cleaned, " "))

	var title, artist string

	// Check if string contains standard "Artist - Title" separator
	delimiters := []string{" - ", " – ", " — ", " ~ "}
	for _, delim := range delimiters {
		if strings.Contains(cleaned, delim) {
			parts := strings.SplitN(cleaned, delim, 2)
			first := strings.TrimSpace(parts[0])
			second := strings.TrimSpace(parts[1])
			if first != "" && second != "" {
				artist = first
				title = second
				break
			}
		}
	}

	if title == "" {
		title = cleaned
		artist = "Unknown Artist"
	}

	searchQuery := title
	if artist != "" && artist != "Unknown Artist" {
		searchQuery = fmt.Sprintf("%s - %s", artist, title)
	}

	return &models.TrackIdentificationResult{
		Title:       title,
		Artist:      artist,
		Album:       "",
		SearchQuery: searchQuery,
		Method:      "semantic_heuristic",
		Confidence:  0.75,
	}
}
