/*
 * Package: lyrics
 * File: lrclib.go
 * Purpose: LRCLIB API client and LRC line parser for synchronized lyrics harvesting.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Thread-safe HTTP client with connection pooling.
 */

package lyrics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

const (
	// LRCLIBBaseURL points to the public community LRCLIB REST endpoint.
	LRCLIBBaseURL = "https://lrclib.net/api"

	// LRCLIBUserAgent identifies Unbound Music client requests.
	LRCLIBUserAgent = "UnboundMusic/1.0 (https://github.com/cubicreates/unbound-engine)"

	// DefaultLRCTimeout provides bounded network wait time.
	DefaultLRCTimeout = 8 * time.Second
)

var (
	// regexLRCLine matches standard LRC timestamp formats: [mm:ss.xx] or [mm:ss.xxx] followed by text.
	regexLRCLine = regexp.MustCompile(`^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$`)
)

// LRCLIBResponse models the JSON payload returned by the LRCLIB API.
type LRCLIBResponse struct {
	ID           int64   `json:"id"`
	TrackName    string  `json:"trackName"`
	ArtistName   string  `json:"artistName"`
	AlbumName    string  `json:"albumName"`
	Duration     float64 `json:"duration"`
	Instrumental bool    `json:"instrumental"`
	PlainLyrics  string  `json:"plainLyrics"`
	SyncedLyrics string  `json:"syncedLyrics"`
}

// LRCLIBClient manages HTTP queries to the LRCLIB database.
type LRCLIBClient struct {
	httpClient *http.Client
	baseURL    string
}

// NewLRCLIBClient creates a new thread-safe LRCLIB API client.
func NewLRCLIBClient() *LRCLIBClient {
	return &LRCLIBClient{
		baseURL: LRCLIBBaseURL,
		httpClient: &http.Client{
			Timeout: DefaultLRCTimeout,
			Transport: &http.Transport{
				MaxIdleConns:        20,
				MaxIdleConnsPerHost: 5,
				IdleConnTimeout:     30 * time.Second,
			},
		},
	}
}

// Fetch queries the LRCLIB API by title, artist, and duration.
func (c *LRCLIBClient) Fetch(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error) {
	if strings.TrimSpace(title) == "" {
		return nil, fmt.Errorf("title cannot be empty")
	}

	q := url.Values{}
	q.Set("track_name", strings.TrimSpace(title))
	if artist != "" {
		q.Set("artist_name", strings.TrimSpace(artist))
	}
	if durationSec > 0 {
		q.Set("duration", strconv.Itoa(durationSec))
	}

	targetURL := fmt.Sprintf("%s/get?%s", c.baseURL, q.Encode())
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create LRCLIB request: %w", err)
	}

	req.Header.Set("User-Agent", LRCLIBUserAgent)
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("LRCLIB request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, fmt.Errorf("no lyrics found on LRCLIB for %s - %s", artist, title)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("LRCLIB returned status %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read LRCLIB response body: %w", err)
	}

	var data LRCLIBResponse
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		return nil, fmt.Errorf("failed to parse LRCLIB response: %w", err)
	}

	if data.Instrumental {
		return &models.LyricsPayload{
			Title:        data.TrackName,
			Artist:       data.ArtistName,
			PlainLyrics:  "[Instrumental Track - Pure Audio]",
			Instrumental: true,
			Source:       "LRCLIB (Instrumental)",
		}, nil
	}

	if strings.TrimSpace(data.SyncedLyrics) == "" {
		if strings.TrimSpace(data.PlainLyrics) != "" {
			return &models.LyricsPayload{
				Title:       data.TrackName,
				Artist:      data.ArtistName,
				PlainLyrics: data.PlainLyrics,
				Source:      "LRCLIB (Plain)",
			}, nil
		}
		return nil, fmt.Errorf("no synced or plain lyrics returned from LRCLIB")
	}

	lines := ParseLRCLyrics(data.SyncedLyrics)
	if len(lines) == 0 {
		return nil, fmt.Errorf("failed to parse any timestamped lines from LRCLIB synced payload")
	}

	return &models.LyricsPayload{
		Title:        data.TrackName,
		Artist:       data.ArtistName,
		PlainLyrics:  data.PlainLyrics,
		Lines:        lines,
		IsWordSynced: false,
		Source:       "LRCLIB Synced Lyrics",
	}, nil
}

// ParseLRCLyrics parses standard LRC text into a slice of LyricLine models with millisecond timestamps.
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

	// Compute EndMs for each line based on the next line start or estimate 4000ms for final line
	for i := 0; i < len(result); i++ {
		if i+1 < len(result) {
			result[i].EndMs = result[i+1].StartMs
		} else {
			result[i].EndMs = result[i].StartMs + 4000
		}

		// Interpolate syllable timings
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

// TokenizeSyllables splits line text into phonetic word units for kinetic syllable glow.
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
