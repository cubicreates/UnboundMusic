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
		HTML5Preference string `json:"html5Preference"`
	} `json:"contentPlaybackContext"`
}

// PlayerRequestBody models the JSON envelope sent to /youtubei/v1/player.
type PlayerRequestBody struct {
	Context         ClientContext   `json:"context"`
	VideoID         string          `json:"videoId"`
	PlaybackContext PlaybackContext `json:"playbackContext"`
	ContentCheckOk  bool            `json:"contentCheckOk"`
	RacyCheckOk     bool            `json:"racyCheckOk"`
}

var fallbackConfigs = []ClientConfig{
	ConfigIOS,
	ConfigWebRemix,
	ConfigTVHTML5Simply,
}

// GetStreamInfo queries YouTube's player API across client profiles until a valid pure audio stream is extracted.
func (c *Client) GetStreamInfo(ctx context.Context, videoID string) (*models.StreamInfo, error) {
	if strings.TrimSpace(videoID) == "" {
		return nil, fmt.Errorf("video ID cannot be empty")
	}

	var lastErr error
	for _, cfg := range fallbackConfigs {
		var pb PlaybackContext
		pb.ContentPlaybackContext.HTML5Preference = "HTML5_PREF_WANTS"

		body := PlayerRequestBody{
			Context:         c.buildContext(cfg),
			VideoID:         videoID,
			PlaybackContext: pb,
			ContentCheckOk:  true,
			RacyCheckOk:     true,
		}

		respBytes, err := c.post(ctx, "player", body, cfg)
		if err != nil {
			lastErr = err
			continue
		}

		info, err := parsePlayerResponse(videoID, respBytes)
		if err == nil && info != nil && info.StreamURL != "" {
			return info, nil
		}
		if err != nil {
			lastErr = err
		}
	}

	return nil, fmt.Errorf("failed to extract audio stream: %w", lastErr)
}

// parsePlayerResponse traverses adaptiveFormats to find the highest bitrate pure Opus or AAC audio stream.
func parsePlayerResponse(videoID string, data []byte) (*models.StreamInfo, error) {
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

	var bestFormat map[string]any
	bestBitrate := 0

	for _, f := range adaptiveFormats {
		fMap, ok := f.(map[string]any)
		if !ok {
			continue
		}

		mimeType, _ := fMap["mimeType"].(string)
		// Strictly filter for audio-only streams (ignore all video formats)
		if !strings.HasPrefix(mimeType, "audio/") {
			continue
		}

		bitrate := ParseBitrate(fMap["bitrate"])
		if bitrate > bestBitrate {
			bestBitrate = bitrate
			bestFormat = fMap
		}
	}

	if bestFormat == nil {
		return nil, fmt.Errorf("no pure audio format found in streamingData")
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

	return &models.StreamInfo{
		VideoID:       videoID,
		StreamURL:     decipheredURL,
		Codec:         codec,
		BitrateKbps:   bestBitrate,
		SampleRate:    sampleRate,
		ContentLength: contentLength,
		DurationMs:    durationMs,
		ExpiresAt:     expiresAt,
		AudioChannels: channels,
	}, nil
}
