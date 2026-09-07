/*
 * Package: lyrics
 * File: youtube.go
 * Purpose: Transforms YouTube InnerTube timed caption transcripts into synchronized kinetic LyricLines.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Thread-safe conversion functions.
 */

package lyrics

import (
	"context"
	"fmt"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// YouTubeTranscriptFetcher defines the capability to retrieve video transcripts from YouTube.
type YouTubeTranscriptFetcher interface {
	GetTranscript(ctx context.Context, videoID string) ([]ytmusic.LyricLine, error)
}

// ConvertYouTubeTranscript converts InnerTube timed captions into domain LyricLine models with syllables.
func ConvertYouTubeTranscript(rawLines []ytmusic.LyricLine) []models.LyricLine {
	if len(rawLines) == 0 {
		return nil
	}

	result := make([]models.LyricLine, 0, len(rawLines))
	for _, raw := range rawLines {
		trimmed := strings.TrimSpace(raw.Text)
		if trimmed == "" {
			continue
		}

		endMs := raw.EndMs
		if endMs <= raw.StartMs {
			endMs = raw.StartMs + 3000
		}

		line := models.LyricLine{
			StartMs:   raw.StartMs,
			EndMs:     endMs,
			Text:      trimmed,
			Syllables: TokenizeSyllables(trimmed, raw.StartMs, endMs),
		}

		result = append(result, line)
	}

	return result
}

// FetchYouTubeCaptions retrieves transcript captions for a video and formats them as a LyricsPayload.
func FetchYouTubeCaptions(ctx context.Context, client YouTubeTranscriptFetcher, videoID, title, artist string) (*models.LyricsPayload, error) {
	if client == nil {
		return nil, fmt.Errorf("youtube transcript fetcher is not configured")
	}
	if strings.TrimSpace(videoID) == "" {
		return nil, fmt.Errorf("video ID cannot be empty")
	}

	rawLines, err := client.GetTranscript(ctx, videoID)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch youtube transcript: %w", err)
	}

	lines := ConvertYouTubeTranscript(rawLines)
	if len(lines) == 0 {
		return nil, fmt.Errorf("no caption lines parsed from transcript")
	}

	var plainBuilder strings.Builder
	for i, l := range lines {
		if i > 0 {
			plainBuilder.WriteByte('\n')
		}
		plainBuilder.WriteString(l.Text)
	}

	return &models.LyricsPayload{
		TrackID:      videoID,
		Title:        title,
		Artist:       artist,
		PlainLyrics:  plainBuilder.String(),
		Lines:        lines,
		IsWordSynced: false,
		Source:       "YouTube InnerTube Captions",
	}, nil
}
