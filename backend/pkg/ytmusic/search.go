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

// Protobuf Search Filter Constants
const (
	FilterSong              = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
	FilterAlbum             = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
	FilterArtist            = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"
	FilterPodcast           = "EgWKAQJQAWoKEAkQChAFEAMQBA%3D%3D"
	FilterCommunityPlaylist = "EgeKAQQoAEABagoQAxAEEAoQCRAF"
)

// CascadeSearchResult models the output of the 4-stage search engine pipeline.
type CascadeSearchResult struct {
	Query        string         `json:"query"`
	StageReached int            `json:"stage_reached"`
	StageName    string         `json:"stage_name"`
	Tracks       []models.Track `json:"tracks"`
}

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

	return c.searchWithFilter(ctx, query, "EgWKAQIIAWoQEAMQBBAJEAoQBRAREBAQFQ%3D%3D")
}

// searchWithFilter queries the InnerTube search endpoint with optional protobuf filter tokens.
func (c *Client) searchWithFilter(ctx context.Context, query, filterParams string) ([]models.Track, error) {
	cfg := ConfigWebRemix
	body := SearchRequestBody{
		Context: c.buildContext(cfg),
		Query:   query,
		Params:  filterParams,
	}

	respBytes, err := c.post(ctx, "search", body, cfg)
	if err != nil {
		return nil, fmt.Errorf("search request failed: %w", err)
	}

	return parseSearchResponse(respBytes)
}

// SearchCascade executes the intelligent 4-stage search engine cascade:
// Stage 1: Official studio release query (FilterSong)
// Stage 2: Fan lyric and audio video query
// Stage 3: Broad community audio sweep
// Stage 4: Clean "No Results" state without destructive loops
func (c *Client) SearchCascade(ctx context.Context, query string) (*CascadeSearchResult, error) {
	trimmed := strings.TrimSpace(query)
	if trimmed == "" {
		return nil, fmt.Errorf("search query cannot be empty")
	}

	// [STAGE 1] Official Studio Release Query (FilterSong)
	tracks, err := c.searchWithFilter(ctx, trimmed, FilterSong)
	if err == nil && len(tracks) > 0 {
		return &CascadeSearchResult{
			Query:        trimmed,
			StageReached: 1,
			StageName:    "Official Studio Release",
			Tracks:       tracks,
		}, nil
	}

	// [STAGE 2] Fan-Made Lyric & Audio Video Query
	fanQuery := fmt.Sprintf("%s lyric video", trimmed)
	tracks, err = c.searchWithFilter(ctx, fanQuery, "")
	if err == nil && len(tracks) > 0 {
		return &CascadeSearchResult{
			Query:        trimmed,
			StageReached: 2,
			StageName:    "Fan Lyric & Community Audio",
			Tracks:       tracks,
		}, nil
	}

	// [STAGE 3] Broad YouTube Audio Sweep
	cleanQuery := strings.Map(func(r rune) rune {
		if strings.ContainsRune("!@#$%^&*()_+-=[]{};':\",.<>/?\\|", r) {
			return ' '
		}
		return r
	}, trimmed)
	tracks, err = c.searchWithFilter(ctx, cleanQuery, "")
	if err == nil && len(tracks) > 0 {
		return &CascadeSearchResult{
			Query:        trimmed,
			StageReached: 3,
			StageName:    "Broad Community Audio",
			Tracks:       tracks,
		}, nil
	}

	// [STAGE 4] Clean "No Results" State (Zero Destructive Loop)
	return &CascadeSearchResult{
		Query:        trimmed,
		StageReached: 4,
		StageName:    "No Results",
		Tracks:       []models.Track{},
	}, nil
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
