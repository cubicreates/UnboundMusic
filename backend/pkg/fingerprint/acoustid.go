/*
 * Package: fingerprint
 * File: acoustid.go
 * Purpose: Queries AcoustID web service with rate limiting (3 qps) to resolve MusicBrainz metadata.
 * Subsystem: Acoustic Fingerprinting Engine
 * Concurrency: Thread-safe; rate limiter synchronizes concurrent outbound requests.
 */

package fingerprint

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Default AcoustID configuration constants
const (
	DefaultAcoustIDEndpoint  = "https://api.acoustid.org/v2/lookup"
	DefaultAcoustIDClientKey = "8XaBELgH"
	DefaultUserAgent         = "UnboundMusic/1.0 (Android; open-source)"
	MinConfidenceScore       = 0.70
)

// AcoustIDEndpoint can be modified in tests to point to a test HTTP server.
var (
	AcoustIDEndpoint  = DefaultAcoustIDEndpoint
	AcoustIDClientKey = DefaultAcoustIDClientKey
)

// GetAcoustIDClientKey returns the active client key, checking ACOUSTID_API_KEY env var first.
func GetAcoustIDClientKey() string {
	if envKey := strings.TrimSpace(os.Getenv("ACOUSTID_API_KEY")); envKey != "" {
		return envKey
	}
	if AcoustIDClientKey != "" {
		return AcoustIDClientKey
	}
	return DefaultAcoustIDClientKey
}

// MusicBrainzMeta encapsulates resolved track metadata from AcoustID / MusicBrainz.
type MusicBrainzMeta struct {
	Title       string   `json:"title"`
	Artist      string   `json:"artist"`
	Artists     []string `json:"artists"`
	Album       string   `json:"album"`
	Score       float64  `json:"score"`
	RecordingID string   `json:"recording_id"`
}

// acoustIDResponse represents the JSON response schema returned by api.acoustid.org.
type acoustIDResponse struct {
	Status  string           `json:"status"`
	Error   *acoustIDError   `json:"error,omitempty"`
	Results []acoustIDResult `json:"results"`
}

type acoustIDError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

type acoustIDResult struct {
	ID         string              `json:"id"`
	Score      float64             `json:"score"`
	Recordings []acoustIDRecording `json:"recordings"`
}

type acoustIDRecording struct {
	ID            string                 `json:"id"`
	Title         string                 `json:"title"`
	Artists       []acoustIDArtist       `json:"artists"`
	ReleaseGroups []acoustIDReleaseGroup `json:"releasegroups"`
}

type acoustIDArtist struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type acoustIDReleaseGroup struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Type  string `json:"type"`
}

// RateLimiter enforces a maximum rate of requests (3 queries per second for AcoustID).
type RateLimiter struct {
	interval time.Duration
	mu       sync.Mutex
	lastCall time.Time
}

// NewRateLimiter creates a new limiter with the specified maximum queries per second.
func NewRateLimiter(qps float64) *RateLimiter {
	if qps <= 0 {
		qps = 3.0
	}
	interval := time.Duration(float64(time.Second) / qps)
	return &RateLimiter{
		interval: interval,
	}
}

// Wait blocks until the next request slot is available or until ctx is cancelled.
func (r *RateLimiter) Wait(ctx context.Context) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	now := time.Now()
	nextAvailable := r.lastCall.Add(r.interval)

	if now.Before(nextAvailable) {
		sleepDuration := nextAvailable.Sub(now)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(sleepDuration):
		}
		r.lastCall = time.Now()
	} else {
		r.lastCall = now
	}

	return nil
}

// globalLimiter throttles outbound AcoustID web service requests to 3 QPS.
var globalLimiter = NewRateLimiter(3.0)

// LookupAcoustID queries api.acoustid.org with the given duration and Chromaprint hash.
// Outbound traffic is rate-limited to 3 queries per second.
func LookupAcoustID(ctx context.Context, duration float64, fingerprint string) (*MusicBrainzMeta, error) {
	if strings.TrimSpace(fingerprint) == "" {
		return nil, errors.New("fingerprint string must not be empty")
	}

	// Enforce 3 QPS rate limit
	if err := globalLimiter.Wait(ctx); err != nil {
		return nil, fmt.Errorf("rate limiter wait cancelled: %w", err)
	}

	durationSec := int(math.Round(duration))
	if durationSec <= 0 {
		durationSec = 1
	}

	params := url.Values{}
	params.Set("client", GetAcoustIDClientKey())
	params.Set("meta", "recordings+releasegroups+compress")
	params.Set("duration", strconv.Itoa(durationSec))
	params.Set("fingerprint", fingerprint)

	reqURL := fmt.Sprintf("%s?%s", AcoustIDEndpoint, params.Encode())

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create AcoustID HTTP request: %w", err)
	}
	req.Header.Set("User-Agent", DefaultUserAgent)

	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("AcoustID network lookup failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return nil, fmt.Errorf("AcoustID returned HTTP %d: %s", resp.StatusCode, string(bodyBytes))
	}

	bodyBytes, err := io.ReadAll(io.LimitReader(resp.Body, 10*1024*1024))
	if err != nil {
		return nil, fmt.Errorf("failed to read AcoustID response body: %w", err)
	}

	return ParseAcoustIDResponse(bodyBytes)
}

// ParseAcoustIDResponse parses the AcoustID JSON payload and extracts the best MusicBrainz recording.
// If no result reaches the minimum confidence threshold (0.70), it returns a meta object with Score < 0.70.
func ParseAcoustIDResponse(data []byte) (*MusicBrainzMeta, error) {
	var resp acoustIDResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("failed to decode AcoustID response JSON: %w", err)
	}

	if resp.Status != "ok" {
		if resp.Error != nil {
			return nil, fmt.Errorf("AcoustID API error (%d): %s", resp.Error.Code, resp.Error.Message)
		}
		return nil, fmt.Errorf("AcoustID API returned non-ok status: %s", resp.Status)
	}

	if len(resp.Results) == 0 {
		return &MusicBrainzMeta{Score: 0.0}, nil
	}

	// Find the top result with highest score and valid recordings
	var bestResult *acoustIDResult
	for i := range resp.Results {
		res := &resp.Results[i]
		if bestResult == nil || res.Score > bestResult.Score {
			bestResult = res
		}
	}

	if bestResult == nil || len(bestResult.Recordings) == 0 {
		score := 0.0
		if bestResult != nil {
			score = bestResult.Score
		}
		return &MusicBrainzMeta{Score: score}, nil
	}

	// Pick the first recording in the highest-scoring result
	rec := bestResult.Recordings[0]

	var artistNames []string
	for _, a := range rec.Artists {
		trimmed := strings.TrimSpace(a.Name)
		if trimmed != "" {
			artistNames = append(artistNames, trimmed)
		}
	}

	primaryArtist := ""
	if len(artistNames) > 0 {
		primaryArtist = strings.Join(artistNames, ", ")
	}

	albumTitle := ""
	if len(rec.ReleaseGroups) > 0 {
		albumTitle = strings.TrimSpace(rec.ReleaseGroups[0].Title)
	}

	return &MusicBrainzMeta{
		Title:       strings.TrimSpace(rec.Title),
		Artist:      primaryArtist,
		Artists:     artistNames,
		Album:       albumTitle,
		Score:       bestResult.Score,
		RecordingID: rec.ID,
	}, nil
}
