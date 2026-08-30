/*
 * Package: genius
 * File: client.go
 * Purpose: Provides high-performance HTTP communication with genius.com and LRCLIB APIs.
 * Subsystem: FOSS Lyrics Engine
 * Concurrency: Client is safe for concurrent access across multiple goroutines.
 */

package genius

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

const (
	// GeniusBaseURL is the root URL for genius.com web endpoints.
	GeniusBaseURL = "https://genius.com"

	// LRCLIBBaseURL is the base URL for the free community LRCLIB database.
	LRCLIBBaseURL = "https://lrclib.net/api"

	// UserAgent mimics a modern desktop Chrome browser for anonymous web scraping.
	UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

	// DefaultTimeout defines the HTTP timeout for lyrics and search network operations.
	DefaultTimeout = 12 * time.Second
)

// Client provides scraping and synchronization methods for lyrics and annotations.
type Client struct {
	httpClient *http.Client
}

// NewClient instantiates a new Genius & LRCLIB scraper client with connection pooling.
func NewClient() *Client {
	transport := &http.Transport{
		MaxIdleConns:        50,
		MaxIdleConnsPerHost: 10,
		IdleConnTimeout:     60 * time.Second,
		DisableCompression: false,
	}

	return &Client{
		httpClient: &http.Client{
			Transport: transport,
			Timeout:   DefaultTimeout,
		},
	}
}

// get executes a GET request against a target URL with browser headers and returns the raw response body.
func (c *Client) get(ctx context.Context, targetURL string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create GET request: %w", err)
	}

	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Cache-Control", "no-cache")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("HTTP GET request failed for %s: %w", targetURL, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("HTTP GET returned status %d for %s", resp.StatusCode, targetURL)
	}

	return io.ReadAll(resp.Body)
}

// buildGeniusSearchURL formats a search query string for the Genius search endpoint.
func buildGeniusSearchURL(query string) string {
	return fmt.Sprintf("%s/api/search/multi?q=%s", GeniusBaseURL, url.QueryEscape(query))
}
