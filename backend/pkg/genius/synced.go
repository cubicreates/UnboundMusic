/*
 * Package: genius
 * File: synced.go
 * Purpose: Provides LRCLIB pre-synchronized timestamp fetching and phonetic syllable tokenization.
 * Subsystem: FOSS Lyrics Engine
 * Concurrency: Thread-safe; handles concurrent synchronization and parsing tasks.
 */

package genius

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"unicode"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	// regexLRCLine matches standard LRC line timestamps like [01:23.45] or [00:12.345]
	regexLRCLine = regexp.MustCompile(`^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$`)
)

// LRCLIBResponse models the JSON payload returned by the LRCLIB API.
type LRCLIBResponse struct {
	ID           int64   `json:"id"`
	TrackName    string  `json:"trackName"`
	ArtistName   string  `json:"artistName"`
	AlbumName    string  `json:"albumName"`
	Duration     float64 `json:"duration"`
	SyncedLyrics string  `json:"syncedLyrics"`
	PlainLyrics  string  `json:"plainLyrics"`
}

// FetchLRCLIBSynced attempts to query the free open community LRCLIB database for pre-synced timestamps.
func (c *Client) FetchLRCLIBSynced(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error) {
	title = strings.TrimSpace(title)
	if title == "" {
		return nil, fmt.Errorf("title cannot be empty")
	}

	cleanArtist := strings.TrimSpace(artist)
	switch strings.ToLower(cleanArtist) {
	case "song", "video", "youtube artist", "artist":
		cleanArtist = ""
	}

	// Try exact match if cleanArtist is provided
	if cleanArtist != "" {
		q := url.Values{}
		q.Set("track_name", title)
		q.Set("artist_name", cleanArtist)
		if durationSec > 0 {
			q.Set("duration", strconv.Itoa(durationSec))
		}

		targetURL := fmt.Sprintf("%s/get?%s", LRCLIBBaseURL, q.Encode())
		respBytes, err := c.get(ctx, targetURL)
		if err == nil {
			var lrclib LRCLIBResponse
			if err := json.Unmarshal(respBytes, &lrclib); err == nil && lrclib.SyncedLyrics != "" {
				lines := ParseLRCLyrics(lrclib.SyncedLyrics)
				if len(lines) > 0 {
					return &models.LyricsPayload{
						TrackID:      fmt.Sprintf("lrclib:%d", lrclib.ID),
						Title:        lrclib.TrackName,
						Artist:       lrclib.ArtistName,
						PlainLyrics:  lrclib.PlainLyrics,
						Lines:        lines,
						IsWordSynced: false,
						Source:       "LRCLIB Community Database",
					}, nil
				}
			}
		}
	}

	// Fallback 1: Search LRCLIB with "title artist"
	if cleanArtist != "" {
		searchURL := fmt.Sprintf("%s/search?q=%s", LRCLIBBaseURL, url.QueryEscape(fmt.Sprintf("%s %s", title, cleanArtist)))
		respBytes, err := c.get(ctx, searchURL)
		if err == nil {
			var results []LRCLIBResponse
			if err := json.Unmarshal(respBytes, &results); err == nil && len(results) > 0 {
				for _, item := range results {
					if item.SyncedLyrics != "" {
						lines := ParseLRCLyrics(item.SyncedLyrics)
						if len(lines) > 0 {
							return &models.LyricsPayload{
								TrackID:      fmt.Sprintf("lrclib:%d", item.ID),
								Title:        item.TrackName,
								Artist:       item.ArtistName,
								PlainLyrics:  item.PlainLyrics,
								Lines:        lines,
								IsWordSynced: false,
								Source:       "LRCLIB Community Database",
							}, nil
						}
					}
				}
			}
		}
	}

	// Fallback 2: Search LRCLIB with title alone
	searchURL := fmt.Sprintf("%s/search?q=%s", LRCLIBBaseURL, url.QueryEscape(title))
	respBytes, err := c.get(ctx, searchURL)
	if err == nil {
		var results []LRCLIBResponse
		if err := json.Unmarshal(respBytes, &results); err == nil && len(results) > 0 {
			for _, item := range results {
				if item.SyncedLyrics != "" {
					lines := ParseLRCLyrics(item.SyncedLyrics)
					if len(lines) > 0 {
						return &models.LyricsPayload{
							TrackID:      fmt.Sprintf("lrclib:%d", item.ID),
							Title:        item.TrackName,
							Artist:       item.ArtistName,
							PlainLyrics:  item.PlainLyrics,
							Lines:        lines,
							IsWordSynced: false,
							Source:       "LRCLIB Community Database",
						}, nil
					}
				}
			}
		}
	}

	return nil, fmt.Errorf("no synced lyrics available in LRCLIB for %s - %s", artist, title)
}

// ParseLRCLyrics parses a multi-line LRC formatted string into structured LyricLine models with millisecond timestamps.
func ParseLRCLyrics(lrcText string) []models.LyricLine {
	rawLines := strings.Split(lrcText, "\n")
	var result []models.LyricLine

	for _, line := range rawLines {
		trimmed := strings.TrimSpace(line)
		matches := regexLRCLine.FindStringSubmatch(trimmed)
		if len(matches) < 5 {
			continue
		}

		min, _ := strconv.ParseInt(matches[1], 10, 64)
		sec, _ := strconv.ParseInt(matches[2], 10, 64)
		millisStr := matches[3]

		// Pad 2-digit centiseconds to 3-digit milliseconds
		if len(millisStr) == 2 {
			millisStr += "0"
		}
		millis, _ := strconv.ParseInt(millisStr, 10, 64)

		startMs := (min*60+sec)*1000 + millis
		lineText := strings.TrimSpace(matches[4])

		if lineText == "" {
			continue
		}

		result = append(result, models.LyricLine{
			Text:      lineText,
			StartMs:   startMs,
			Syllables: TokenizeSyllables(lineText, startMs, 0),
		})
	}

	// Compute EndMs for each line based on the start timestamp of the succeeding line
	for i := 0; i < len(result); i++ {
		if i+1 < len(result) {
			result[i].EndMs = result[i+1].StartMs
		} else {
			// Estimate final line duration as 4 seconds
			result[i].EndMs = result[i].StartMs + 4000
		}
		// Adjust syllable duration across the computed line range
		if len(result[i].Syllables) > 0 {
			duration := result[i].EndMs - result[i].StartMs
			step := duration / int64(len(result[i].Syllables))
			for sIdx := range result[i].Syllables {
				result[i].Syllables[sIdx].StartMs = result[i].StartMs + int64(sIdx)*step
				result[i].Syllables[sIdx].EndMs = result[i].Syllables[sIdx].StartMs + step
			}
		}
	}

	return result
}

// TokenizeSyllables splits a lyric line into word or sub-word phonetic units for kinetic rendering.
func TokenizeSyllables(lineText string, startMs, endMs int64) []models.Syllable {
	words := strings.Fields(lineText)
	if len(words) == 0 {
		return nil
	}

	var syllables []models.Syllable
	for _, word := range words {
		clean := strings.TrimFunc(word, func(r rune) bool {
			return !unicode.IsLetter(r) && !unicode.IsNumber(r)
		})
		if clean != "" {
			syllables = append(syllables, models.Syllable{
				Text:    clean,
				StartMs: startMs,
				EndMs:   endMs,
			})
		}
	}

	return syllables
}
