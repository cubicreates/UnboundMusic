/*
 * Package: sponsorblock
 * File: sponsorblock.go
 * Purpose: Queries open SponsorBlock database to extract non-music segments, extended silences, and intro/outro timestamps for stream skipping.
 * Subsystem: Stream Filtering & Secondary Services
 * Concurrency: Thread-safe HTTP client with timeouts; safe for concurrent goroutines.
 */

package sponsorblock

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"sync"
	"time"
)

// Segment represents a timestamp range marked for skipping.
type Segment struct {
	Category string    `json:"category"` // "music_offtopic", "sponsor", "intro", "outro"
	Action   string    `json:"action"`   // "skip", "mute"
	StartSec float64   `json:"start_sec"`
	EndSec   float64   `json:"end_sec"`
	StartMs  int64     `json:"start_ms"`
	EndMs    int64     `json:"end_ms"`
}

// Client coordinates requests to the open SponsorBlock API.
type Client struct {
	httpClient *http.Client
	cache      map[string][]Segment
	mu         sync.RWMutex
}

// NewClient instantiates a new SponsorBlock client.
func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
		},
		cache: make(map[string][]Segment),
	}
}

// GetSkipSegments fetches skip intervals for a YouTube video ID.
func (c *Client) GetSkipSegments(ctx context.Context, videoID string) ([]Segment, error) {
	if videoID == "" {
		return nil, fmt.Errorf("videoID cannot be empty")
	}

	c.mu.RLock()
	if cached, found := c.cache[videoID]; found {
		c.mu.RUnlock()
		return cached, nil
	}
	c.mu.RUnlock()

	endpoint := fmt.Sprintf("https://sponsor.ajay.app/api/skipSegments?videoID=%s&categories=%s",
		url.QueryEscape(videoID),
		url.QueryEscape(`["music_offtopic","sponsor","intro","outro","preview"]`),
	)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("failed creating sponsorblock request: %w", err)
	}
	req.Header.Set("User-Agent", "UnboundMusic/1.0.0 (FOSS Android Client)")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("sponsorblock request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return []Segment{}, nil
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("sponsorblock API returned status: %d", resp.StatusCode)
	}

	type RawSegment struct {
		Category    string    `json:"category"`
		ActionType  string    `json:"actionType"`
		Segment     []float64 `json:"segment"`
	}

	var rawSegments []RawSegment
	if err := json.NewDecoder(resp.Body).Decode(&rawSegments); err != nil {
		return nil, fmt.Errorf("failed decoding sponsorblock json: %w", err)
	}

	var results []Segment
	for _, r := range rawSegments {
		if len(r.Segment) >= 2 {
			startSec := r.Segment[0]
			endSec := r.Segment[1]
			results = append(results, Segment{
				Category: r.Category,
				Action:   r.ActionType,
				StartSec: startSec,
				EndSec:   endSec,
				StartMs:  int64(startSec * 1000),
				EndMs:    int64(endSec * 1000),
			})
		}
	}

	c.mu.Lock()
	c.cache[videoID] = results
	c.mu.Unlock()

	return results, nil
}
