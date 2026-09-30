/*
 * Package: upstream
 * File: mirror.go
 * Purpose: Dynamic multi-provider upstream redundancy engine for YouTube streams and search.
 *          Provides automatic failover across Piped, Invidious, and InnerTube instances
 *          to protect against client cipher rotations, IP rate-limiting, and API breakages.
 * Subsystem: Upstream Resilience & Anti-Fragility
 * Concurrency: Thread-safe instance rotation with latency and failure score tracking.
 */

package upstream

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// InstanceType defines whether a mirror is Piped or Invidious.
type InstanceType string

const (
	TypePiped     InstanceType = "PIPED"
	TypeInvidious InstanceType = "INVIDIOUS"
)

// MirrorInstance represents an external mirror node with health tracking.
type MirrorInstance struct {
	BaseURL     string       `json:"base_url"`
	Type        InstanceType `json:"type"`
	FailedCount int          `json:"failed_count"`
	LastSuccess time.Time    `json:"last_success"`
	LastFailure time.Time    `json:"last_failure"`
	LatencyMs   int64        `json:"latency_ms"`
	IsActive    bool         `json:"is_active"`
}

// MirrorManager coordinates failover across public and self-hosted mirrors.
type MirrorManager struct {
	mu        sync.RWMutex
	client    *http.Client
	instances []*MirrorInstance
}

var (
	defaultManager     *MirrorManager
	defaultManagerOnce sync.Once
)

// DefaultMirrors contains high-availability fallback instances.
var DefaultMirrors = []*MirrorInstance{
	{BaseURL: "https://pipedapi.kavin.rocks", Type: TypePiped, IsActive: true},
	{BaseURL: "https://pipedapi.tokhmi.xyz", Type: TypePiped, IsActive: true},
	{BaseURL: "https://pipedapi.drgns.space", Type: TypePiped, IsActive: true},
	{BaseURL: "https://invidious.asir.dev", Type: TypeInvidious, IsActive: true},
	{BaseURL: "https://invidious.nerdvpn.de", Type: TypeInvidious, IsActive: true},
}

// GetDefaultMirrorManager returns the singleton mirror manager instance.
func GetDefaultMirrorManager() *MirrorManager {
	defaultManagerOnce.Do(func() {
		defaultManager = NewMirrorManager(DefaultMirrors)
	})
	return defaultManager
}

// NewMirrorManager initializes a new mirror manager.
func NewMirrorManager(instances []*MirrorInstance) *MirrorManager {
	copied := make([]*MirrorInstance, len(instances))
	for i, inst := range instances {
		cp := *inst
		copied[i] = &cp
	}
	return &MirrorManager{
		client: &http.Client{
			Timeout: 7 * time.Second,
		},
		instances: copied,
	}
}

// getHealthyInstances returns active instances sorted by lowest failure count and latency.
func (m *MirrorManager) getHealthyInstances() []*MirrorInstance {
	m.mu.RLock()
	defer m.mu.RUnlock()

	active := make([]*MirrorInstance, 0, len(m.instances))
	now := time.Now()

	for _, inst := range m.instances {
		// Temporary cooldown for failing instances (backoff 2 minutes per consecutive failure)
		if inst.FailedCount > 0 {
			cooldown := time.Duration(inst.FailedCount*2) * time.Minute
			if now.Sub(inst.LastFailure) < cooldown {
				continue
			}
		}
		active = append(active, inst)
	}

	// Sort by fewest failures, then lowest latency
	sort.SliceStable(active, func(i, j int) bool {
		if active[i].FailedCount != active[j].FailedCount {
			return active[i].FailedCount < active[j].FailedCount
		}
		return active[i].LatencyMs < active[j].LatencyMs
	})

	return active
}

// recordSuccess marks an instance as healthy and updates latency.
func (m *MirrorManager) recordSuccess(inst *MirrorInstance, elapsedMs int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	inst.FailedCount = 0
	inst.LastSuccess = time.Now()
	inst.LatencyMs = elapsedMs
}

// recordFailure increments failure count for an instance.
func (m *MirrorManager) recordFailure(inst *MirrorInstance) {
	m.mu.Lock()
	defer m.mu.Unlock()
	inst.FailedCount++
	inst.LastFailure = time.Now()
}

// FetchStreamFromMirror cascades across available mirrors to resolve direct audio stream URLs.
func (m *MirrorManager) FetchStreamFromMirror(ctx context.Context, videoID string) (*models.StreamInfo, error) {
	instances := m.getHealthyInstances()
	if len(instances) == 0 {
		return nil, fmt.Errorf("no healthy upstream mirrors available")
	}

	var lastErr error
	for _, inst := range instances {
		start := time.Now()
		var streamInfo *models.StreamInfo
		var err error

		if inst.Type == TypePiped {
			streamInfo, err = m.fetchPipedStream(ctx, inst.BaseURL, videoID)
		} else {
			streamInfo, err = m.fetchInvidiousStream(ctx, inst.BaseURL, videoID)
		}

		if err == nil && streamInfo != nil && streamInfo.StreamURL != "" {
			m.recordSuccess(inst, time.Since(start).Milliseconds())
			return streamInfo, nil
		}

		m.recordFailure(inst)
		lastErr = err
	}

	return nil, fmt.Errorf("all upstream mirrors failed for %s: %w", videoID, lastErr)
}

// fetchPipedStream queries a Piped REST instance for audio streams.
func (m *MirrorManager) fetchPipedStream(ctx context.Context, baseURL, videoID string) (*models.StreamInfo, error) {
	reqURL := fmt.Sprintf("%s/streams/%s", strings.TrimRight(baseURL, "/"), videoID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

	resp, err := m.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("piped mirror %s returned HTTP %d", baseURL, resp.StatusCode)
	}

	var payload struct {
		Title          string `json:"title"`
		Duration       int64  `json:"duration"`
		AudioStreams   []struct {
			URL         string `json:"url"`
			Bitrate     int    `json:"bitrate"`
			Codec       string `json:"codec"`
			Format      string `json:"format"`
			ContentLen  int64  `json:"contentLength"`
		} `json:"audioStreams"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return nil, err
	}

	if len(payload.AudioStreams) == 0 {
		return nil, fmt.Errorf("no audio streams found on piped mirror %s", baseURL)
	}

	// Select best audio stream (prefer Opus or highest bitrate)
	var bestStream *models.StreamInfo
	for _, as := range payload.AudioStreams {
		if as.URL == "" {
			continue
		}
		codec := strings.ToUpper(as.Codec)
		if codec == "" {
			codec = strings.ToUpper(as.Format)
		}
		bitrate := as.Bitrate / 1000
		if bitrate <= 0 {
			bitrate = 160
		}

		stream := &models.StreamInfo{
			StreamURL:     as.URL,
			Codec:         codec,
			BitrateKbps:   bitrate,
			DurationMs:    payload.Duration * 1000,
			ContentLength: as.ContentLen,
		}

		if bestStream == nil || strings.Contains(strings.ToLower(codec), "opus") || bitrate > bestStream.BitrateKbps {
			bestStream = stream
		}
	}

	if bestStream != nil {
		return bestStream, nil
	}

	return nil, fmt.Errorf("no valid stream found on piped mirror")
}

// fetchInvidiousStream queries an Invidious REST instance for audio streams.
func (m *MirrorManager) fetchInvidiousStream(ctx context.Context, baseURL, videoID string) (*models.StreamInfo, error) {
	reqURL := fmt.Sprintf("%s/api/v1/videos/%s?fields=title,lengthSeconds,adaptiveFormats", strings.TrimRight(baseURL, "/"), videoID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

	resp, err := m.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("invidious mirror %s returned HTTP %d", baseURL, resp.StatusCode)
	}

	var payload struct {
		Title           string `json:"title"`
		LengthSeconds   int64  `json:"lengthSeconds"`
		AdaptiveFormats []struct {
			URL         string `json:"url"`
			Bitrate     string `json:"bitrate"`
			Type        string `json:"type"`
			Encoding    string `json:"encoding"`
			ContentLen  string `json:"clen"`
		} `json:"adaptiveFormats"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return nil, err
	}

	for _, af := range payload.AdaptiveFormats {
		if strings.HasPrefix(af.Type, "audio/") && af.URL != "" {
			bitrate, _ := strconv.Atoi(af.Bitrate)
			clen, _ := strconv.ParseInt(af.ContentLen, 10, 64)
			codec := "OPUS"
			if strings.Contains(af.Type, "mp4") || strings.Contains(af.Type, "m4a") {
				codec = "AAC"
			}
			return &models.StreamInfo{
				StreamURL:     af.URL,
				Codec:         codec,
				BitrateKbps:   bitrate / 1000,
				DurationMs:    payload.LengthSeconds * 1000,
				ContentLength: clen,
			}, nil
		}
	}

	return nil, fmt.Errorf("no audio format found on invidious mirror")
}

// SearchTracksFromMirror performs resilient search across healthy mirrors when InnerTube search is down.
func (m *MirrorManager) SearchTracksFromMirror(ctx context.Context, query string) ([]models.Track, error) {
	instances := m.getHealthyInstances()
	if len(instances) == 0 {
		return nil, fmt.Errorf("no healthy upstream mirrors available for search")
	}

	var lastErr error
	for _, inst := range instances {
		if inst.Type == TypePiped {
			tracks, err := m.searchPiped(ctx, inst.BaseURL, query)
			if err == nil && len(tracks) > 0 {
				return tracks, nil
			}
			lastErr = err
		}
	}

	return nil, fmt.Errorf("all mirror search providers failed for query %q: %w", query, lastErr)
}

// searchPiped queries Piped search endpoint.
func (m *MirrorManager) searchPiped(ctx context.Context, baseURL, query string) ([]models.Track, error) {
	reqURL := fmt.Sprintf("%s/search?q=%s&filter=music_songs", strings.TrimRight(baseURL, "/"), url.QueryEscape(query))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}

	resp, err := m.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("piped search HTTP %d", resp.StatusCode)
	}

	var payload struct {
		Items []struct {
			URL          string `json:"url"`
			Title        string `json:"title"`
			UploaderName string `json:"uploaderName"`
			Duration     int64  `json:"duration"`
			Thumbnail    string `json:"thumbnail"`
		} `json:"items"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return nil, err
	}

	tracks := make([]models.Track, 0, len(payload.Items))
	for _, it := range payload.Items {
		// URL is typically /watch?v=VIDEO_ID
		videoID := strings.TrimPrefix(it.URL, "/watch?v=")
		if len(videoID) != 11 {
			continue
		}
		tracks = append(tracks, models.Track{
			ID:           videoID,
			Title:        it.Title,
			Artist:       it.UploaderName,
			DurationMs:   it.Duration * 1000,
			ThumbnailURL: it.Thumbnail,
			IsLocal:      false,
		})
	}

	return tracks, nil
}
