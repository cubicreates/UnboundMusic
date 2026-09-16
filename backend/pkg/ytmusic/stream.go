/*
 * Package: ytmusic
 * File: stream.go
 * Purpose: Extracts direct pure audio playback stream URLs (Opus 160kbps / AAC 256kbps) from Innertube player endpoint with multi-client failover.
 * Subsystem: Core Scraper Engine
 * Concurrency: Thread-safe; handles concurrent stream resolution requests.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// PlaybackContext defines HTML5 playback preferences.
type PlaybackContext struct {
	ContentPlaybackContext struct {
		HTML5Preference    string `json:"html5Preference"`
		SignatureTimestamp int    `json:"signatureTimestamp,omitempty"`
	} `json:"contentPlaybackContext"`
}

// ServiceIntegrityDimensions allows passing poToken when provided.
type ServiceIntegrityDimensions struct {
	PoToken string `json:"poToken,omitempty"`
}

// PlayerRequestBody models the JSON envelope sent to /youtubei/v1/player.
type PlayerRequestBody struct {
	Context                    ClientContext               `json:"context"`
	VideoID                    string                      `json:"videoId"`
	PlaybackContext            PlaybackContext             `json:"playbackContext"`
	ContentCheckOk             bool                        `json:"contentCheckOk"`
	RacyCheckOk                bool                        `json:"racyCheckOk"`
	ServiceIntegrityDimensions *ServiceIntegrityDimensions `json:"serviceIntegrityDimensions,omitempty"`
}

var fallbackConfigs = []ClientConfig{
	ConfigVisionOS,
	ConfigAndroid,
	ConfigIOS,
	ConfigWebRemix,
}

// GetStreamInfo queries YouTube's player API across client profiles until a valid pure audio stream is extracted.
func (c *Client) GetStreamInfo(ctx context.Context, videoID string) (*models.StreamInfo, error) {
	return c.GetStreamInfoWithQuality(ctx, videoID, "high")
}

// GetStreamInfoWithQuality queries YouTube's player API and returns an audio stream matching the requested quality (low, medium, high).
func (c *Client) GetStreamInfoWithQuality(ctx context.Context, videoID string, quality string) (*models.StreamInfo, error) {
	if strings.TrimSpace(videoID) == "" {
		return nil, fmt.Errorf("video ID cannot be empty")
	}

	sigTimestamp := int(time.Now().Unix() / 86400)

	var lastErr error
	for _, cfg := range fallbackConfigs {
		var pb PlaybackContext
		pb.ContentPlaybackContext.HTML5Preference = "HTML5_PREF_WANTS"
		pb.ContentPlaybackContext.SignatureTimestamp = sigTimestamp

		var sid *ServiceIntegrityDimensions
		if poToken := c.GetPoToken(); poToken != "" {
			sid = &ServiceIntegrityDimensions{PoToken: poToken}
		}

		body := PlayerRequestBody{
			Context:                    c.buildContext(cfg),
			VideoID:                    videoID,
			PlaybackContext:            pb,
			ContentCheckOk:             true,
			RacyCheckOk:                true,
			ServiceIntegrityDimensions: sid,
		}

		respBytes, err := c.post(ctx, "player", body, cfg)
		if err != nil {
			lastErr = err
			continue
		}

		info, err := parsePlayerResponseWithQuality(videoID, respBytes, quality)
		if err == nil && info != nil && info.StreamURL != "" {
			return info, nil
		}
		if err != nil {
			lastErr = err
		}
	}

	// Fallback for authenticated users (e.g. age-restricted content):
	// If all unencumbered guest configs failed and user has credentials, retry with authentication.
	if c.HasCredentials() {
		authCtx := WithForceAuth(ctx)
		for _, cfg := range fallbackConfigs {
			var pb PlaybackContext
			pb.ContentPlaybackContext.HTML5Preference = "HTML5_PREF_WANTS"
			pb.ContentPlaybackContext.SignatureTimestamp = sigTimestamp

			var sid *ServiceIntegrityDimensions
			if poToken := c.GetPoToken(); poToken != "" {
				sid = &ServiceIntegrityDimensions{PoToken: poToken}
			}

			body := PlayerRequestBody{
				Context:                    c.buildContext(cfg),
				VideoID:                    videoID,
				PlaybackContext:            pb,
				ContentCheckOk:             true,
				RacyCheckOk:                true,
				ServiceIntegrityDimensions: sid,
			}

			respBytes, err := c.post(authCtx, "player", body, cfg)
			if err != nil {
				lastErr = err
				continue
			}

			info, err := parsePlayerResponseWithQuality(videoID, respBytes, quality)
			if err == nil && info != nil && info.StreamURL != "" {
				return info, nil
			}
			if err != nil {
				lastErr = err
			}
		}
	}

	return nil, fmt.Errorf("failed to extract audio stream: %w", lastErr)
}

// parsePlayerResponse traverses adaptiveFormats to find the highest bitrate pure Opus or AAC audio stream.
func parsePlayerResponse(videoID string, data []byte) (*models.StreamInfo, error) {
	return parsePlayerResponseWithQuality(videoID, data, "high")
}

// parsePlayerResponseWithQuality traverses adaptiveFormats to find the target quality pure Opus or AAC audio stream.
func parsePlayerResponseWithQuality(videoID string, data []byte, quality string) (*models.StreamInfo, error) {
	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("failed to parse player response JSON: %w", err)
	}

	streamingData, ok := raw["streamingData"].(map[string]any)
	if !ok {
		if playability, ok := raw["playabilityStatus"].(map[string]any); ok {
			status, _ := playability["status"].(string)
			reason, _ := playability["reason"].(string)
			return nil, fmt.Errorf("player status %s: %s", status, reason)
		}
		return nil, fmt.Errorf("response does not contain streamingData")
	}

	adaptiveFormats, ok := streamingData["adaptiveFormats"].([]any)
	if !ok || len(adaptiveFormats) == 0 {
		return nil, fmt.Errorf("no adaptiveFormats found for video %s", videoID)
	}

	var audioFormats []map[string]any
	for _, f := range adaptiveFormats {
		fMap, ok := f.(map[string]any)
		if !ok {
			continue
		}
		mimeType, _ := fMap["mimeType"].(string)
		if strings.HasPrefix(mimeType, "audio/") {
			audioFormats = append(audioFormats, fMap)
		}
	}

	if len(audioFormats) == 0 {
		return nil, fmt.Errorf("no pure audio format found in streamingData")
	}

	var bestFormat map[string]any
	bestBitrate := 0

	switch strings.ToLower(strings.TrimSpace(quality)) {
	case "low":
		// Low quality: data saver ~48kbps opus. Pick the format with lowest bitrate or closest to 48-50kbps.
		minBitrate := int(^uint(0) >> 1)
		for _, fMap := range audioFormats {
			br := ParseBitrate(fMap["bitrate"])
			if br > 0 && br < minBitrate {
				minBitrate = br
				bestFormat = fMap
				bestBitrate = br
			}
		}
	case "medium":
		// Medium quality: balanced ~128kbps-160kbps opus.
		targetBr := 140000
		bestDiff := int(^uint(0) >> 1)
		for _, fMap := range audioFormats {
			br := ParseBitrate(fMap["bitrate"])
			diff := br - targetBr
			if diff < 0 {
				diff = -diff
			}
			if diff < bestDiff {
				bestDiff = diff
				bestFormat = fMap
				bestBitrate = br
			}
		}
	default:
		// High quality: maximum audio bitrate available
		for _, fMap := range audioFormats {
			br := ParseBitrate(fMap["bitrate"])
			if br > bestBitrate {
				bestBitrate = br
				bestFormat = fMap
			}
		}
	}

	if bestFormat == nil && len(audioFormats) > 0 {
		bestFormat = audioFormats[0]
		bestBitrate = ParseBitrate(bestFormat["bitrate"])
	}

	rawURL, _ := bestFormat["url"].(string)
	sigCipher, _ := bestFormat["signatureCipher"].(string)
	cipher, _ := bestFormat["cipher"].(string)

	decipheredURL, err := DecipherURL(rawURL, sigCipher, cipher)
	if err != nil {
		return nil, fmt.Errorf("failed to decipher stream URL: %w", err)
	}

	mimeType, _ := bestFormat["mimeType"].(string)
	codec := "opus"
	if strings.Contains(mimeType, "mp4a") {
		codec = "aac"
	}

	var contentLength int64
	if clStr, ok := bestFormat["contentLength"].(string); ok {
		contentLength, _ = strconv.ParseInt(clStr, 10, 64)
	}

	var sampleRate int
	if srStr, ok := bestFormat["audioSampleRate"].(string); ok {
		sampleRate, _ = strconv.Atoi(srStr)
	}

	var channels int
	if ch, ok := bestFormat["audioChannels"].(float64); ok {
		channels = int(ch)
	}

	var durationMs int64
	if durStr, ok := bestFormat["approxDurationMs"].(string); ok {
		durationMs, _ = strconv.ParseInt(durStr, 10, 64)
	}
	if durationMs == 0 && contentLength > 0 && bestBitrate > 0 {
		durationMs = (contentLength * 8) / int64(bestBitrate)
	}

	expiresAt := time.Now().Add(6 * time.Hour)

	// Apply N-transform to prevent YouTube 40kbps stream throttling
	finalStreamURL := applyNTransform(decipheredURL)

	return &models.StreamInfo{
		VideoID:       videoID,
		StreamURL:     finalStreamURL,
		Codec:         codec,
		BitrateKbps:   bestBitrate,
		SampleRate:    sampleRate,
		ContentLength: contentLength,
		DurationMs:    durationMs,
		ExpiresAt:     expiresAt,
		AudioChannels: channels,
	}, nil
}
