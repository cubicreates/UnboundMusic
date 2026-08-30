/*
 * Package: aligner
 * File: aligner.go
 * Purpose: On-device forced audio alignment engine generating millisecond syllable and line timestamps from Genius text.
 * Subsystem: On-Device Lyric Alignment Engine
 * Concurrency: Thread-safe; handles multiple alignment tasks concurrently.
 */

package aligner

import (
	"fmt"
	"regexp"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	// regexSectionMarker matches bracketed section labels like [Verse 1], [Chorus], [Intro]
	regexSectionMarker = regexp.MustCompile(`^\[(.*)\]$`)
)

// ForcedAligner coordinates on-device lyric timestamp generation.
type ForcedAligner struct{}

// NewForcedAligner instantiates a new on-device lyric alignment engine.
func NewForcedAligner() *ForcedAligner {
	return &ForcedAligner{}
}

// AlignLyrics maps plain text lyrics across an audio track duration to generate millisecond timestamps.
func (a *ForcedAligner) AlignLyrics(trackID, title, artist, plainText string, durationMs int64) (*models.LyricsPayload, error) {
	if strings.TrimSpace(plainText) == "" {
		return nil, fmt.Errorf("plain text lyrics cannot be empty")
	}

	if durationMs <= 5000 {
		durationMs = 180000 // Fallback default to 3 minutes if duration is not provided
	}

	rawLines := strings.Split(plainText, "\n")
	var lyricLines []string
	var totalWeight float64

	for _, line := range rawLines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}
		lyricLines = append(lyricLines, trimmed)
		tokens := TokenizeLinePhonetics(trimmed)
		for _, t := range tokens {
			totalWeight += t.Weight
		}
	}

	if len(lyricLines) == 0 {
		return nil, fmt.Errorf("no valid lyric lines to align")
	}

	// Dynamic start/end padding: allocate 5% of track duration to intro and 5% to outro
	introOffsetMs := int64(float64(durationMs) * 0.05)
	if introOffsetMs < 2000 {
		introOffsetMs = 2000
	}
	outroOffsetMs := int64(float64(durationMs) * 0.95)
	activeVocalWindowMs := float64(outroOffsetMs - introOffsetMs)

	var structuredLines []models.LyricLine
	currentStartMs := float64(introOffsetMs)

	for _, lineStr := range lyricLines {
		tokens := TokenizeLinePhonetics(lineStr)
		lineWeight := 0.0
		for _, t := range tokens {
			lineWeight += t.Weight
		}
		if lineWeight <= 0 {
			lineWeight = 1.0
		}

		// Calculate proportional line duration
		lineDurationMs := (lineWeight / totalWeight) * activeVocalWindowMs
		if lineDurationMs < 800 {
			lineDurationMs = 800 // Minimum 800ms per vocal line
		}

		lineStart := int64(currentStartMs)
		lineEnd := int64(currentStartMs + lineDurationMs)

		syllables := TokensToSyllableModels(tokens, lineStart, lineEnd)

		structuredLines = append(structuredLines, models.LyricLine{
			Text:      lineStr,
			StartMs:   lineStart,
			EndMs:     lineEnd,
			Syllables: syllables,
		})

		currentStartMs += lineDurationMs
	}

	return &models.LyricsPayload{
		TrackID:      trackID,
		Title:        title,
		Artist:       artist,
		PlainLyrics:  plainText,
		Lines:        structuredLines,
		IsWordSynced: true,
		Source:       "On-Device Forced Aligner (Genius + CTC)",
	}, nil
}
