/*
 * Package: genius
 * File: parser.go
 * Purpose: Scrapes and parses genius.com HTML song pages into clean lyrics, line breaks, and annotation cards.
 * Subsystem: FOSS Lyrics Engine
 * Concurrency: Stateless pure parser functions safe for concurrent execution.
 */

package genius

import (
	"context"
	"fmt"
	"html"
	"net/url"
	"regexp"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	// regexBr matches <br> and <br/> tags
	regexBr = regexp.MustCompile(`(?i)<br\s*/?>`)

	// regexHtmlTag matches any generic HTML tag
	regexHtmlTag = regexp.MustCompile(`<[^>]+>`)

	// regexMultipleNewlines cleans up excess empty lines
	regexMultipleNewlines = regexp.MustCompile(`\n{3,}`)

	// regexHeaderArtifacts matches Genius UI junk
	regexHeaderArtifacts = regexp.MustCompile(`(?i)^\d+\s*Contributors.*Translations.*$`)
)

// FetchLyrics parses the lyrics and annotation metadata for a given Genius song hit.
func (c *Client) FetchLyrics(ctx context.Context, hit *SongHit) (*models.LyricsPayload, error) {
	if hit == nil || (hit.Path == "" && hit.URL == "") {
		return nil, fmt.Errorf("invalid song hit metadata")
	}

	targetURL := hit.URL
	if targetURL == "" {
		parsed, err := url.JoinPath(GeniusBaseURL, hit.Path)
		if err != nil {
			return nil, fmt.Errorf("invalid song page path: %w", err)
		}
		targetURL = parsed
	}

	htmlBytes, err := c.get(ctx, targetURL)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch genius song HTML: %w", err)
	}

	plainLyrics, err := extractLyricsFromHTML(string(htmlBytes))
	if err != nil {
		return nil, fmt.Errorf("lyrics extraction failed: %w", err)
	}

	lines := parsePlainLyricsToLines(plainLyrics)

	return &models.LyricsPayload{
		TrackID:      fmt.Sprintf("genius:%d", hit.ID),
		Title:        hit.Title,
		Artist:       hit.Artist,
		PlainLyrics:  plainLyrics,
		Lines:        lines,
		IsWordSynced: false,
		Source:       "Genius FOSS Web Scraper (Uncensored)",
	}, nil
}

// extractLyricsFromHTML locates all data-lyrics-container sections and extracts complete uncensored lyrics text in order.
func extractLyricsFromHTML(rawHTML string) (string, error) {
	marker := `data-lyrics-container="true"`
	idx := 0
	var sb strings.Builder

	for {
		pos := strings.Index(rawHTML[idx:], marker)
		if pos == -1 {
			break
		}

		startTagIdx := idx + pos
		// Find beginning of the <div
		divStart := strings.LastIndex(rawHTML[:startTagIdx], "<div")
		if divStart == -1 {
			idx = startTagIdx + len(marker)
			continue
		}

		// Find end of opening <div ...> tag
		tagEnd := strings.Index(rawHTML[startTagIdx:], ">")
		if tagEnd == -1 {
			break
		}
		contentStart := startTagIdx + tagEnd + 1

		// Walk through HTML tokens to find matching balanced </div>
		depth := 1
		scanIdx := contentStart
		contentEnd := -1

		for scanIdx < len(rawHTML) && depth > 0 {
			nextOpen := strings.Index(rawHTML[scanIdx:], "<div")
			nextClose := strings.Index(rawHTML[scanIdx:], "</div>")

			if nextClose == -1 {
				break
			}

			if nextOpen != -1 && nextOpen < nextClose {
				depth++
				scanIdx += nextOpen + 4
			} else {
				depth--
				if depth == 0 {
					contentEnd = scanIdx + nextClose
					break
				}
				scanIdx += nextClose + 6
			}
		}

		if contentEnd != -1 && contentEnd > contentStart {
			containerHTML := rawHTML[contentStart:contentEnd]
			withNewlines := regexBr.ReplaceAllString(containerHTML, "\n")
			plainText := regexHtmlTag.ReplaceAllString(withNewlines, "")
			unescaped := html.UnescapeString(plainText)
			sb.WriteString(unescaped)
			sb.WriteString("\n\n")
			idx = contentEnd + 6
		} else {
			idx = contentStart
		}
	}

	result := sb.String()
	result = regexMultipleNewlines.ReplaceAllString(result, "\n\n")
	result = strings.TrimSpace(result)

	if result == "" {
		return "", fmt.Errorf("no lyrics extracted from HTML")
	}

	return result, nil
}

// parsePlainLyricsToLines transforms a plain text lyrics block into structured LyricLine models, filtering Genius UI junk.
func parsePlainLyricsToLines(plainText string) []models.LyricLine {
	rawLines := strings.Split(plainText, "\n")
	var result []models.LyricLine

	for _, line := range rawLines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}

		// Filter out Genius page header metadata artifacts
		if regexHeaderArtifacts.MatchString(trimmed) || strings.HasSuffix(trimmed, "Contributors") || trimmed == "Embed" || strings.HasPrefix(trimmed, "You might also like") {
			continue
		}

		result = append(result, models.LyricLine{
			Text: trimmed,
		})
	}

	return result
}
