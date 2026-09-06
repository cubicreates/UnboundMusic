/*
 * Package: ytmusic
 * File: innertube.go
 * Purpose: Low-level HTTP communication with YouTube Music InnerTube browse endpoints.
 * Subsystem: Core Scraper Engine
 * Concurrency: Thread-safe HTTP operations with connection pooling.
 */

package ytmusic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const (
	// BrowseEndpointURL is the public InnerTube browse API endpoint.
	BrowseEndpointURL = "https://music.youtube.com/youtubei/v1/browse"

	// DefaultBrowseUserAgent mimics a desktop Chrome browser.
	DefaultBrowseUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

// BrowsePayload defines the unauthenticated JSON body sent to InnerTube browse endpoints.
type BrowsePayload struct {
	Context  BrowseContext `json:"context"`
	BrowseID string        `json:"browseId"`
}

// BrowseContext defines the client identity block for InnerTube.
type BrowseContext struct {
	Client BrowseClient `json:"client"`
}

// BrowseClient contains device and geo localization fields.
type BrowseClient struct {
	ClientName    string `json:"clientName"`
	ClientVersion string `json:"clientVersion"`
	Gl            string `json:"gl"`
	Hl            string `json:"hl"`
}

// executeBrowseRequest performs an unauthenticated POST request to the InnerTube browse endpoint.
func executeBrowseRequest(ctx context.Context, client *http.Client, browseID, countryCode, langCode string) ([]byte, error) {
	if countryCode == "" {
		countryCode = "US"
	}
	if langCode == "" {
		langCode = "en"
	}

	payload := BrowsePayload{
		Context: BrowseContext{
			Client: BrowseClient{
				ClientName:    "WEB_REMIX",
				ClientVersion: "1.20240101.01.00",
				Gl:            countryCode,
				Hl:            langCode,
			},
		},
		BrowseID: browseID,
	}

	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal browse payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, BrowseEndpointURL, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, fmt.Errorf("failed to create browse request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", DefaultBrowseUserAgent)
	req.Header.Set("Origin", "https://music.youtube.com")
	req.Header.Set("Referer", "https://music.youtube.com/")

	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second}
	}

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("browse HTTP request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("browse API error status %d: %s", resp.StatusCode, string(respBody))
	}

	return io.ReadAll(resp.Body)
}
