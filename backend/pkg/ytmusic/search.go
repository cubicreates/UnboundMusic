/*
 * Package: ytmusic
 * File: search.go
 * Purpose: Executes and parses YouTube Music search queries into standardized Track models.
 * Subsystem: Core Scraper Engine
 * Concurrency: Thread-safe; multiple queries can execute concurrently.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// SearchRequestBody models the JSON envelope sent to /youtubei/v1/search.
type SearchRequestBody struct {
	Context ClientContext `json:"context"`
	Query   string        `json:"query"`
	Params  string        `json:"params,omitempty"`
}

// Search executes a search query against YouTube Music and parses matching tracks.
func (c *Client) Search(ctx context.Context, query string) ([]models.Track, error) {
	if strings.TrimSpace(query) == "" {
		return nil, fmt.Errorf("search query cannot be empty")
	}

	cfg := ConfigWebRemix
	body := SearchRequestBody{
		Context: c.buildContext(cfg),
		Query:   query,
		Params:  "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D", // Filter exclusively for pure audio song tracks
	}

	respBytes, err := c.post(ctx, "search", body, cfg)
	if err != nil {
		return nil, fmt.Errorf("search request failed: %w", err)
	}

	return parseSearchResponse(respBytes)
}

// parseSearchResponse traverses the YouTube Music search JSON tree to extract track items.
func parseSearchResponse(data []byte) ([]models.Track, error) {
	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("failed to parse search JSON: %w", err)
	}

	var tracks []models.Track

	contents, ok := raw["contents"].(map[string]any)
	if !ok {
		return tracks, nil
	}

	tabbed, ok := contents["tabbedSearchResultsRenderer"].(map[string]any)
	if !ok {
		return tracks, nil
	}

	tabs, ok := tabbed["tabs"].([]any)
	if !ok || len(tabs) == 0 {
		return tracks, nil
	}

	tabContent, ok := tabs[0].(map[string]any)["tabRenderer"].(map[string]any)["content"].(map[string]any)
	if !ok {
		return tracks, nil
	}

	sectionList, ok := tabContent["sectionListRenderer"].(map[string]any)["contents"].([]any)
	if !ok {
		return tracks, nil
	}

	for _, section := range sectionList {
		secMap, ok := section.(map[string]any)
		if !ok {
			continue
		}

		musicShelf, ok := secMap["musicShelfRenderer"].(map[string]any)
		if !ok {
			continue
		}

		shelfContents, ok := musicShelf["contents"].([]any)
		if !ok {
			continue
		}

		for _, item := range shelfContents {
			itemMap, ok := item.(map[string]any)
			if !ok {
				continue
			}

			track := extractTrackFromResponsiveItem(itemMap)
			if track != nil && track.ID != "" {
				tracks = append(tracks, *track)
			}
		}
	}

	return tracks, nil
}

// extractTrackFromResponsiveItem parses a single musicResponsiveListItemRenderer into a Track model.
func extractTrackFromResponsiveItem(item map[string]any) *models.Track {
	responsive, ok := item["musicResponsiveListItemRenderer"].(map[string]any)
	if !ok {
		return nil
	}

	track := &models.Track{}

	// Extract Playlist Item Data / Video ID
	if plData, ok := responsive["playlistItemData"].(map[string]any); ok {
		if vid, ok := plData["videoId"].(string); ok {
			track.ID = vid
		}
	}

	// Fallback to overlay or watchEndpoint if playlistItemData is missing
	if track.ID == "" {
		if overlay, ok := responsive["overlay"].(map[string]any); ok {
			if btn, ok := overlay["musicItemThumbnailOverlayRenderer"].(map[string]any); ok {
				if playBtn, ok := btn["content"].(map[string]any)["musicPlayButtonRenderer"].(map[string]any); ok {
					if nav, ok := playBtn["playNavigationEndpoint"].(map[string]any)["watchEndpoint"].(map[string]any); ok {
						if vid, ok := nav["videoId"].(string); ok {
							track.ID = vid
						}
					}
				}
			}
		}
	}

	// Extract Flex Columns (Title, Artist, Album, Duration)
	flexColumns, ok := responsive["flexColumns"].([]any)
	if ok && len(flexColumns) > 0 {
		// Column 0: Title
		if col0, ok := flexColumns[0].(map[string]any)["musicResponsiveListItemFlexColumnRenderer"].(map[string]any); ok {
			track.Title = extractRunsText(col0["text"])
		}

		// Column 1: Artist, Album, Duration
		if len(flexColumns) > 1 {
			if col1, ok := flexColumns[1].(map[string]any)["musicResponsiveListItemFlexColumnRenderer"].(map[string]any); ok {
				runs := extractRunsList(col1["text"])
				if len(runs) > 0 {
					track.Artist = runs[0]
				}
				if len(runs) > 1 {
					track.Album = runs[1]
				}
				if len(runs) > 2 {
					track.DurationMs = parseDurationToMs(runs[len(runs)-1])
				}
			}
		}
	}

	// Extract Thumbnail
	if thumbnails, ok := responsive["thumbnail"].(map[string]any)["musicThumbnailRenderer"].(map[string]any)["thumbnail"].(map[string]any)["thumbnails"].([]any); ok && len(thumbnails) > 0 {
		if lastThumb, ok := thumbnails[len(thumbnails)-1].(map[string]any); ok {
			if url, ok := lastThumb["url"].(string); ok {
				track.ThumbnailURL = url
			}
		}
	}

	return track
}

// extractRunsText flattens text runs into a single string.
func extractRunsText(textObj any) string {
	textMap, ok := textObj.(map[string]any)
	if !ok {
		return ""
	}
	runs, ok := textMap["runs"].([]any)
	if !ok || len(runs) == 0 {
		return ""
	}
	var sb strings.Builder
	for _, r := range runs {
		if rMap, ok := r.(map[string]any); ok {
			if text, ok := rMap["text"].(string); ok {
				sb.WriteString(text)
			}
		}
	}
	return sb.String()
}

// extractRunsList extracts distinct runs separated by bullet points or delimiters.
func extractRunsList(textObj any) []string {
	textMap, ok := textObj.(map[string]any)
	if !ok {
		return nil
	}
	runs, ok := textMap["runs"].([]any)
	if !ok {
		return nil
	}
	var result []string
	for _, r := range runs {
		if rMap, ok := r.(map[string]any); ok {
			if text, ok := rMap["text"].(string); ok {
				trimmed := strings.TrimSpace(text)
				if trimmed != "" && trimmed != "•" && trimmed != "&" {
					result = append(result, trimmed)
				}
			}
		}
	}
	return result
}

// parseDurationToMs converts "3:45" or "1:15:30" string into milliseconds.
func parseDurationToMs(durationStr string) int64 {
	parts := strings.Split(strings.TrimSpace(durationStr), ":")
	var totalSeconds int64
	for _, part := range parts {
		var sec int64
		fmt.Sscanf(part, "%d", &sec)
		totalSeconds = totalSeconds*60 + sec
	}
	return totalSeconds * 1000
}
