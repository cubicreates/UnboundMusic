/*
 * Package: ryd
 * File: ryd.go
 * Purpose: Integration with Return YouTube Dislike (RYD) API to retrieve live likes, dislikes, ratings, and approval percentages.
 * Subsystem: Community Metadata & Social Rating Engine
 * Concurrency: Thread-safe HTTP client with TTL in-memory caching and connection pooling.
 */

package ryd

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"sync"
	"time"
)

const (
	// PrimaryURL is the official Return YouTube Dislike API endpoint.
	PrimaryURL = "https://returnyoutubedislikeapi.com/votes"

	// UserAgent identifies Unbound Music requests.
	UserAgent = "UnboundMusic/1.0.0 (FOSS Android Client)"

	// DefaultTimeout bounds external API operations.
	DefaultTimeout = 6 * time.Second

	// CacheTTL holds vote data in memory for 1 hour to prevent rate limiting.
	CacheTTL = 1 * time.Hour
)

// VoteData represents public sentiment for a YouTube track/video.
type VoteData struct {
	ID             string  `json:"id"`
	Likes          int64   `json:"likes"`
	Dislikes       int64   `json:"dislikes"`
	Rating         float64 `json:"rating"`
	ViewCount      int64   `json:"view_count"`
	LikePercentage int     `json:"like_percentage"`
}

// rawRYDResponse represents the JSON response from returnyoutubedislikeapi.com.
type rawRYDResponse struct {
	ID        string  `json:"id"`
	Likes     int64   `json:"likes"`
	Dislikes  int64   `json:"dislikes"`
	Rating    float64 `json:"rating"`
	ViewCount int64   `json:"viewCount"`
	Deleted   bool    `json:"deleted"`
}

type cacheEntry struct {
	data      *VoteData
	expiresAt time.Time
}

// Client handles interaction with the Return YouTube Dislike API.
type Client struct {
	httpClient *http.Client
	baseURL    string
	cache      map[string]cacheEntry
	mu         sync.RWMutex
}

// NewClient constructs an initialized RYD API client.
func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: DefaultTimeout,
			Transport: &http.Transport{
				MaxIdleConns:        50,
				MaxIdleConnsPerHost: 10,
				IdleConnTimeout:     60 * time.Second,
			},
		},
		baseURL: PrimaryURL,
		cache:   make(map[string]cacheEntry),
	}
}

// SetBaseURL overrides the API endpoint URL (primarily for testing).
func (c *Client) SetBaseURL(url string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.baseURL = url
}

// GetVotes fetches likes, dislikes, and calculates approval ratio for a YouTube video ID.
func (c *Client) GetVotes(ctx context.Context, videoID string) (*VoteData, error) {
	if videoID == "" {
		return nil, fmt.Errorf("video ID cannot be empty")
	}

	// 1. Check in-memory cache
	c.mu.RLock()
	entry, found := c.cache[videoID]
	c.mu.RUnlock()

	if found && time.Now().Before(entry.expiresAt) {
		return entry.data, nil
	}

	// 2. Fetch from RYD upstream API
	c.mu.RLock()
	base := c.baseURL
	c.mu.RUnlock()
	url := fmt.Sprintf("%s?videoId=%s", base, videoID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed creating RYD request: %w", err)
	}

	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("RYD request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, fmt.Errorf("video not found on RYD: %s", videoID)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("RYD API returned status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed reading RYD response: %w", err)
	}

	var raw rawRYDResponse
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("failed parsing RYD json: %w", err)
	}

	// 3. Calculate community like percentage ratio
	likePct := 100
	totalVotes := raw.Likes + raw.Dislikes
	if totalVotes > 0 {
		likePct = int(math.Round((float64(raw.Likes) / float64(totalVotes)) * 100))
		if likePct > 100 {
			likePct = 100
		} else if likePct < 0 {
			likePct = 0
		}
	}

	data := &VoteData{
		ID:             raw.ID,
		Likes:          raw.Likes,
		Dislikes:       raw.Dislikes,
		Rating:         math.Round(raw.Rating*10) / 10,
		ViewCount:      raw.ViewCount,
		LikePercentage: likePct,
	}

	// 4. Store in cache
	c.mu.Lock()
	c.cache[videoID] = cacheEntry{
		data:      data,
		expiresAt: time.Now().Add(CacheTTL),
	}
	c.mu.Unlock()

	return data, nil
}
