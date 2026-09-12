/*
 * Package: ytmusic
 * File: transcript.go
 * Purpose: Extracts timestamped timed-text captions/subtitles from YouTube videos for kinetic lyrics.
 * Subsystem: Lyrics & Transcript Extractor
 * Concurrency: Concurrent safe methods using context.Context.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// LyricLine encapsulates a timed subtitle line from a transcript.
type LyricLine struct {
	StartMs int64  `json:"start_ms"`
	EndMs   int64  `json:"end_ms"`
	Text    string `json:"text"`
}

// parseTranscriptJSON parses InnerTube transcript JSON into sorted LyricLine slices.
func parseTranscriptJSON(data []byte) ([]LyricLine, error) {
	var root map[string]interface{}
	if err := json.Unmarshal(data, &root); err != nil {
		return nil, fmt.Errorf("failed to parse transcript json: %w", err)
	}

	var lines []LyricLine

	var traverse func(val interface{})
	traverse = func(val interface{}) {
		switch v := val.(type) {
		case map[string]interface{}:
			if seg, ok := v["transcriptSegmentRenderer"].(map[string]interface{}); ok {
				var line LyricLine
				if startStr, ok := seg["startMs"].(string); ok {
					line.StartMs, _ = strconv.ParseInt(startStr, 10, 64)
				}
				if endStr, ok := seg["endMs"].(string); ok {
					line.EndMs, _ = strconv.ParseInt(endStr, 10, 64)
				}
				if snippet, ok := seg["snippet"].(map[string]interface{}); ok {
					if runs, ok := snippet["runs"].([]interface{}); ok {
						var textParts []string
						for _, r := range runs {
							if rm, ok := r.(map[string]interface{}); ok {
								if t, ok := rm["text"].(string); ok {
									textParts = append(textParts, t)
								}
							}
						}
						line.Text = strings.Join(textParts, "")
					}
				}
				if line.Text != "" {
					lines = append(lines, line)
				}
			}
			for _, child := range v {
				traverse(child)
			}
		case []interface{}:
			for _, child := range v {
				traverse(child)
			}
		}
	}

	traverse(root)
	return lines, nil
}

// GetTranscript fetches timed transcript text for a video from YouTube's InnerTube API.
func (c *Client) GetTranscript(ctx context.Context, videoID string) ([]LyricLine, error) {
	body := map[string]interface{}{
		"context": c.buildContext(ConfigWeb),
		"params":  fmt.Sprintf("\x0a\x0b%s", videoID),
	}

	respBytes, err := c.post(ctx, "get_transcript", body, ConfigWeb)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch transcript for video %s: %w", videoID, err)
	}

	return parseTranscriptJSON(respBytes)
}
