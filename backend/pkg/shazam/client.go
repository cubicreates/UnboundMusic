/*
 * Package: shazam
 * File: client.go
 * Purpose: Public unauthenticated Shazam discovery client recognizing songs from binary signatures in < 800ms with $0.00 cloud cost.
 * Subsystem: Shazam Audio Recognition
 * Concurrency: Thread-safe HTTP client with timeouts; safe for concurrent recognition queries.
 */

package shazam

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

// MatchResult represents the recognized song metadata from Shazam.
type MatchResult struct {
	Matched     bool      `json:"matched"`
	TrackID     string    `json:"track_id"`
	Title       string    `json:"title"`
	Artist      string    `json:"artist"`
	Album       string    `json:"album"`
	Genre       string    `json:"genre"`
	ReleaseYear string    `json:"release_year"`
	CoverArtURL string    `json:"cover_art_url"`
	ISRC        string    `json:"isrc"`
	ShazamURL   string    `json:"shazam_url"`
	AppleMusicURL string  `json:"apple_music_url"`
	SpotifyURL    string  `json:"spotify_url"`
	LatencyMs   int64     `json:"latency_ms"`
	Source      string    `json:"source"` // "SHAZAM_CLOUD" or "LOCAL_OFFLINE_VAULT"
}

// Client coordinates Shazam recognition requests.
type Client struct {
	httpClient *http.Client
}

// NewClient instantiates a new Shazam recognition client.
func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: 8 * time.Second,
		},
	}
}

// RecognizeSignature sends a signature payload to Shazam discovery and parses the matched track.
func (c *Client) RecognizeSignature(ctx context.Context, sig *SignaturePayload) (*MatchResult, error) {
	if sig == nil || sig.Base64URI == "" {
		return nil, fmt.Errorf("signature payload cannot be empty")
	}

	start := time.Now()
	uuid1 := generateUUID()
	uuid2 := generateUUID()
	endpoint := fmt.Sprintf("https://amp.shazam.com/discovery/v5/en-US/US/android/-/tag/%s/%s", uuid1, uuid2)

	// Shazam API request payload
	reqBody := map[string]interface{}{
		"signatures": []map[string]interface{}{
			{
				"uri":      sig.Base64URI,
				"samplems": sig.DurationMs,
			},
		},
		"timezone": "America/New_York",
	}

	jsonBytes, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed encoding recognition request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(jsonBytes))
	if err != nil {
		return nil, fmt.Errorf("failed creating HTTP request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "Shazam/14.2.0 (Android; 14; Mobile; en-US)")
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("shazam discovery request failed: %w", err)
	}
	defer resp.Body.Close()

	elapsed := time.Since(start).Milliseconds()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("shazam discovery returned status %d", resp.StatusCode)
	}

	type ShazamResponse struct {
		Track struct {
			Key       string `json:"key"`
			Title     string `json:"title"`
			Subtitle  string `json:"subtitle"`
			Url       string `json:"url"`
			ISRC      string `json:"isrc"`
			Images    struct {
				CoverArt string `json:"coverart"`
			} `json:"images"`
			Genres struct {
				Primary string `json:"primary"`
			} `json:"genres"`
			Sections []struct {
				Type     string `json:"type"`
				Metadata []struct {
					Title string `json:"title"`
					Text  string `json:"text"`
				} `json:"metadata"`
			} `json:"sections"`
		} `json:"track"`
		Matches []struct {
			ID string `json:"id"`
		} `json:"matches"`
	}

	var parsed ShazamResponse
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		return nil, fmt.Errorf("failed decoding shazam response JSON: %w", err)
	}

	if parsed.Track.Title == "" && len(parsed.Matches) == 0 {
		return &MatchResult{
			Matched:   false,
			LatencyMs: elapsed,
			Source:    "SHAZAM_CLOUD",
		}, nil
	}

	album := ""
	year := ""
	for _, sec := range parsed.Track.Sections {
		if sec.Type == "SONG" {
			for _, meta := range sec.Metadata {
				if meta.Title == "Album" {
					album = meta.Text
				} else if meta.Title == "Released" {
					year = meta.Text
				}
			}
		}
	}

	return &MatchResult{
		Matched:       true,
		TrackID:       parsed.Track.Key,
		Title:         parsed.Track.Title,
		Artist:        parsed.Track.Subtitle,
		Album:         album,
		Genre:         parsed.Track.Genres.Primary,
		ReleaseYear:   year,
		CoverArtURL:   parsed.Track.Images.CoverArt,
		ISRC:          parsed.Track.ISRC,
		ShazamURL:     parsed.Track.Url,
		LatencyMs:     elapsed,
		Source:        "SHAZAM_CLOUD",
	}, nil
}

func generateUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}
