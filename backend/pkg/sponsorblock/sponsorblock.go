/*
 * Package: sponsorblock
 * File: sponsorblock.go
 * Purpose: Queries open SponsorBlock database to extract non-music segments, extended silences, and intro/outro timestamps for stream skipping.
 * Subsystem: Stream Filtering & Playback Resilience Engine
 * Concurrency: Thread-safe HTTP client with in-memory TTL caching and connection pooling.
 */

package sponsorblock

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

const (
	// PrimaryURL is the high-performance public SponsorBlock mirror.
	PrimaryURL = "https://sponsorblock.kavin.rocks/api/skipSegments"

	// FallbackURL is the upstream official SponsorBlock API endpoint.
	FallbackURL = "https://sponsor.ajay.app/api/skipSegments"

	// UserAgent identifies Unbound Music client requests.
	UserAgent = "UnboundMusic/1.0.0 (FOSS Android Client)"

	// DefaultTimeout bounds network operations.
	DefaultTimeout = 6 * time.Second
)

// Segment represents a timestamp range marked for skipping.
type Segment struct {
	Category string  `json:"category"` // "music_offtopic", "sponsor", "intro", "outro"
	Action   string  `json:"action"`   // "skip", "mute"
	StartSec float64 `json:"start_sec"`
	EndSec   float64 `json:"end_sec"`
	StartMs  int64   `json:"start_ms"`
	EndMs    int64   `json:"end_ms"`
	UUID     string  `json:"uuid,omitempty"`
}

// rawSegment models the JSON response item from SponsorBlock.
type rawSegment struct {
	Category   string    `json:"category"`
	ActionType string    `json:"actionType"`
	Segment    []float64 `json:"segment"`
	UUID       string    `json:"UUID"`
}

type cacheEntry struct {
	segments  []Segment
	expiresAt time.Time
}

// Client coordinates requests to the open SponsorBlock API.
type Client struct {
	httpClient  *http.Client
	primaryURL  string
	fallbackURL string
	cacheMu     sync.RWMutex
	cache       map[string]cacheEntry
}

// NewClient instantiates a new SponsorBlock client.
func NewClient() *Client {
	return &Client{
		primaryURL:  PrimaryURL,
		fallbackURL: FallbackURL,
		httpClient: &http.Client{
			Timeout: DefaultTimeout,
			Transport: &http.Transport{
				MaxIdleConns:        20,
				MaxIdleConnsPerHost: 5,
				IdleConnTimeout:     30 * time.Second,
			},
		},
		cache: make(map[string]cacheEntry),
	}
}

// GetSkipSegments fetches skip intervals for a YouTube video ID across all music & sponsor categories.
func (c *Client) GetSkipSegments(ctx context.Context, videoID string) ([]Segment, error) {
	return c.fetchWithCategories(ctx, videoID, `["music_offtopic","sponsor","intro","outro","preview"]`)
}

// GetMusicSkipSegments fetches non-music skit intervals specifically for music_offtopic category.
func (c *Client) GetMusicSkipSegments(ctx context.Context, videoID string) ([]Segment, error) {
	return c.fetchWithCategories(ctx, videoID, `["music_offtopic"]`)
}

func (c *Client) fetchWithCategories(ctx context.Context, videoID, categoriesJSON string) ([]Segment, error) {
	videoID = strings.TrimSpace(videoID)
	if videoID == "" {
		return nil, fmt.Errorf("videoID cannot be empty")
	}

	cacheKey := fmt.Sprintf("%s:%s", videoID, categoriesJSON)

	// 1. Check in-memory cache
	c.cacheMu.RLock()
	entry, found := c.cache[cacheKey]
	c.cacheMu.RUnlock()
	if found && time.Now().Before(entry.expiresAt) {
		return entry.segments, nil
	}

	// 2. Query primary endpoint
	q := url.Values{}
	q.Set("videoID", videoID)
	q.Set("categories", categoriesJSON)

	segments, err := c.fetchFromEndpoint(ctx, c.primaryURL, q)
	if err != nil && c.fallbackURL != "" {
		// Try fallback mirror
		segments, err = c.fetchFromEndpoint(ctx, c.fallbackURL, q)
	}

	if err != nil {
		return nil, err
	}

	// 3. Cache valid response for 6 hours
	c.cacheMu.Lock()
	c.cache[cacheKey] = cacheEntry{
		segments:  segments,
		expiresAt: time.Now().Add(6 * time.Hour),
	}
	c.cacheMu.Unlock()

	return segments, nil
}

func (c *Client) fetchFromEndpoint(ctx context.Context, endpoint string, q url.Values) ([]Segment, error) {
	targetURL := fmt.Sprintf("%s?%s", endpoint, q.Encode())
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed creating sponsorblock request: %w", err)
	}

	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("sponsorblock request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return []Segment{}, nil
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("sponsorblock API returned status: %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed reading sponsorblock body: %w", err)
	}

	return ParseSegmentsJSON(body)
}

// ParseSegmentsJSON decodes raw SponsorBlock JSON bytes into domain Segment slices.
func ParseSegmentsJSON(data []byte) ([]Segment, error) {
	var rawSegments []rawSegment
	if err := json.Unmarshal(data, &rawSegments); err != nil {
		return nil, fmt.Errorf("failed decoding sponsorblock json: %w", err)
	}

	results := make([]Segment, 0, len(rawSegments))
	for _, r := range rawSegments {
		if len(r.Segment) >= 2 {
			startSec := r.Segment[0]
			endSec := r.Segment[1]
			if endSec <= startSec {
				continue
			}

			results = append(results, Segment{
				Category: r.Category,
				Action:   r.ActionType,
				StartSec: startSec,
				EndSec:   endSec,
				StartMs:  int64(startSec * 1000),
				EndMs:    int64(endSec * 1000),
				UUID:     r.UUID,
			})
		}
	}

	return results, nil
}
