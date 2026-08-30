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
	// regexLyricsContainer captures DOM containers containing lyrics text
	regexLyricsContainer = regexp.MustCompile(`(?s)<div[^>]*data-lyrics-container="true"[^>]*>(.*?)</div>`)

	// regexBr matches <br> and <br/> tags
	regexBr = regexp.MustCompile(`(?i)<br\s*/?>`)

	// regexHtmlTag matches any generic HTML tag
	regexHtmlTag = regexp.MustCompile(`<[^>]+>`)

	// regexMultipleNewlines cleans up excess empty lines
	regexMultipleNewlines = regexp.MustCompile(`\n{3,}`)

	// regexSectionHeader identifies bracketed structural song tags like [Verse 1], [Chorus]
	regexSectionHeader = regexp.MustCompile(`^\[.*\]$`)
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
		Source:       "Genius FOSS Web Scraper",
	}, nil
}

// extractLyricsFromHTML searches for data-lyrics-container divs and transforms them into clean plain text.
func extractLyricsFromHTML(rawHTML string) (string, error) {
	matches := regexLyricsContainer.FindAllStringSubmatch(rawHTML, -1)
	if len(matches) == 0 {
		return "", fmt.Errorf("no lyrics container elements found in page HTML")
	}

	var sb strings.Builder
	for _, m := range matches {
		if len(m) > 1 {
			containerHTML := m[1]
			// Replace <br> with newlines
			withNewlines := regexBr.ReplaceAllString(containerHTML, "\n")
			// Strip all other HTML tags
			plainText := regexHtmlTag.ReplaceAllString(withNewlines, "")
			// Unescape HTML entities (&amp;, &#x27;, &quot;)
			unescaped := html.UnescapeString(plainText)
			sb.WriteString(unescaped)
			sb.WriteString("\n")
		}
	}

	result := sb.String()
	result = regexMultipleNewlines.ReplaceAllString(result, "\n\n")
	result = strings.TrimSpace(result)

	if result == "" {
		return "", fmt.Errorf("extracted lyrics string is empty")
	}

	return result, nil
}

// parsePlainLyricsToLines transforms a plain text lyrics block into structured LyricLine models.
func parsePlainLyricsToLines(plainText string) []models.LyricLine {
	rawLines := strings.Split(plainText, "\n")
	var result []models.LyricLine

	for _, line := range rawLines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}

		result = append(result, models.LyricLine{
			Text: trimmed,
		})
	}

	return result
}
