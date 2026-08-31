/*
 * Package: lastfm
 * File: scrobbler.go
 * Purpose: Last.fm API 2.0 client supporting real-time Now Playing broadcasts, track scrobbling with MD5 api_sig, and Loved Track syncing.
 * Subsystem: Audiophile Integrations & Scrobbling
 * Concurrency: Thread-safe HTTP client with mutex locks for session token management.
 */

package lastfm

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	apiBaseURL = "https://ws.audioscrobbler.com/2.0/"
)

// Scrobbler coordinates requests to the Last.fm REST API.
type Scrobbler struct {
	mu         sync.RWMutex
	apiKey     string
	apiSecret  string
	sessionKey string
	httpClient *http.Client
}

// NewScrobbler initializes a new Last.fm scrobbler client.
func NewScrobbler(apiKey, apiSecret string) *Scrobbler {
	return &Scrobbler{
		apiKey:     apiKey,
		apiSecret:  apiSecret,
		httpClient: &http.Client{Timeout: 5 * time.Second},
	}
}

// SetSessionKey updates the authenticated user's session token.
func (s *Scrobbler) SetSessionKey(sessionKey string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.sessionKey = sessionKey
}

// UpdateNowPlaying notifies Last.fm that the user started listening to a song.
func (s *Scrobbler) UpdateNowPlaying(ctx context.Context, track, artist, album string, durationSec int) error {
	s.mu.RLock()
	apiKey := s.apiKey
	apiSecret := s.apiSecret
	sessionKey := s.sessionKey
	s.mu.RUnlock()

	if apiKey == "" || sessionKey == "" {
		return fmt.Errorf("last.fm is not configured or authenticated")
	}

	params := map[string]string{
		"method":  "track.updateNowPlaying",
		"track":   track,
		"artist":  artist,
		"api_key": apiKey,
		"sk":      sessionKey,
	}
	if album != "" {
		params["album"] = album
	}
	if durationSec > 0 {
		params["duration"] = fmt.Sprintf("%d", durationSec)
	}

	params["api_sig"] = generateSignature(params, apiSecret)
	params["format"] = "json"

	return s.postRequest(ctx, params)
}

// Scrobble records a completed play event (triggered after 50% or 4 minutes).
func (s *Scrobbler) Scrobble(ctx context.Context, track, artist, album string, timestamp time.Time) error {
	s.mu.RLock()
	apiKey := s.apiKey
	apiSecret := s.apiSecret
	sessionKey := s.sessionKey
	s.mu.RUnlock()

	if apiKey == "" || sessionKey == "" {
		return fmt.Errorf("last.fm is not configured or authenticated")
	}

	if timestamp.IsZero() {
		timestamp = time.Now()
	}

	params := map[string]string{
		"method":    "track.scrobble",
		"track":     track,
		"artist":    artist,
		"timestamp": fmt.Sprintf("%d", timestamp.Unix()),
		"api_key":   apiKey,
		"sk":        sessionKey,
	}
	if album != "" {
		params["album"] = album
	}

	params["api_sig"] = generateSignature(params, apiSecret)
	params["format"] = "json"

	return s.postRequest(ctx, params)
}

// LoveTrack marks or unmarks a track as loved on the user's Last.fm profile.
func (s *Scrobbler) LoveTrack(ctx context.Context, track, artist string, love bool) error {
	s.mu.RLock()
	apiKey := s.apiKey
	apiSecret := s.apiSecret
	sessionKey := s.sessionKey
	s.mu.RUnlock()

	if apiKey == "" || sessionKey == "" {
		return fmt.Errorf("last.fm is not configured or authenticated")
	}

	method := "track.love"
	if !love {
		method = "track.unlove"
	}

	params := map[string]string{
		"method":  method,
		"track":   track,
		"artist":  artist,
		"api_key": apiKey,
		"sk":      sessionKey,
	}

	params["api_sig"] = generateSignature(params, apiSecret)
	params["format"] = "json"

	return s.postRequest(ctx, params)
}

func (s *Scrobbler) postRequest(ctx context.Context, params map[string]string) error {
	data := url.Values{}
	for k, v := range params {
		data.Set(k, v)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, apiBaseURL, strings.NewReader(data.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("last.fm network error: %w", err)
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		var errResp struct {
			Message string `json:"message"`
			Error   int    `json:"error"`
		}
		_ = json.Unmarshal(body, &errResp)
		return fmt.Errorf("last.fm API error (%d): %s", errResp.Error, errResp.Message)
	}

	return nil
}

// generateSignature calculates the MD5 hash of alphabetically sorted key-value pairs plus secret.
func generateSignature(params map[string]string, secret string) string {
	keys := make([]string, 0, len(params))
	for k := range params {
		if k != "format" && k != "callback" {
			keys = append(keys, k)
		}
	}
	sort.Strings(keys)

	var sb strings.Builder
	for _, k := range keys {
		sb.WriteString(k)
		sb.WriteString(params[k])
	}
	sb.WriteString(secret)

	hasher := md5.New()
	hasher.Write([]byte(sb.String()))
	return hex.EncodeToString(hasher.Sum(nil))
}
