/*
 * Package: ytmusic
 * File: explore.go
 * Purpose: Public YouTube Music explore and chart scraper with 4-hour SQLite feed caching.
 * Subsystem: Core Scraper Engine
 * Concurrency: Thread-safe scraper operations.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

const (
	// FeedCacheTTLSeconds is 4 hours (14,400 seconds).
	FeedCacheTTLSeconds = int64(14400)
)

var (
	reDim  = regexp.MustCompile(`=w\d+-h\d+([a-zA-Z0-9_-]*)`)
	reSize = regexp.MustCompile(`=s\d+([a-zA-Z0-9_-]*)`)
)

// UpscaleThumbnail transforms low-res thumbnail dimensions into 544x544 high-resolution art.
func UpscaleThumbnail(rawURL string) string {
	if rawURL == "" {
		return ""
	}
	if reDim.MatchString(rawURL) {
		return reDim.ReplaceAllString(rawURL, "=w544-h544-l90-rj")
	}
	if reSize.MatchString(rawURL) {
		return reSize.ReplaceAllString(rawURL, "=s544")
	}
	return rawURL
}

// ExploreEngine coordinates public explore feeds, regional charts, and SQLite caching.
type ExploreEngine struct {
	repo       *database.Repository
	httpClient *http.Client
}

// NewExploreEngine instantiates a new explore engine.
func NewExploreEngine(repo *database.Repository) *ExploreEngine {
	return &ExploreEngine{
		repo: repo,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// SetHTTPClient configures a custom HTTP client (useful for unit test mocking).
func (e *ExploreEngine) SetHTTPClient(client *http.Client) {
	e.httpClient = client
}

// FetchRegionalCharts retrieves top trending songs for a region, checking SQLite feed_cache first.
func (e *ExploreEngine) FetchRegionalCharts(ctx context.Context, countryCode, langCode string) ([]models.TrackItem, error) {
	if countryCode == "" {
		countryCode = "US"
	}
	if langCode == "" {
		langCode = "en"
	}

	cacheKey := fmt.Sprintf("charts:%s:%s", strings.ToUpper(countryCode), strings.ToLower(langCode))

	// 1. Check SQLite feed_cache
	if e.repo != nil {
		cachedData, err := e.repo.GetFeedCache(ctx, cacheKey)
		if err == nil && cachedData != "" {
			var tracks []models.TrackItem
			if err := json.Unmarshal([]byte(cachedData), &tracks); err == nil && len(tracks) > 0 {
				return tracks, nil
			}
		}
	}

	// 2. Fetch live from InnerTube
	respBytes, err := executeBrowseRequest(ctx, e.httpClient, "FEmusic_charts", countryCode, langCode)
	if err != nil {
		// Offline fallback: try to serve stale cache if network failed
		if e.repo != nil {
			cachedData, cacheErr := e.repo.GetFeedCache(ctx, cacheKey)
			if cacheErr == nil && cachedData != "" {
				var tracks []models.TrackItem
				if json.Unmarshal([]byte(cachedData), &tracks) == nil && len(tracks) > 0 {
					return tracks, nil
				}
			}
		}
		return nil, fmt.Errorf("failed fetching regional charts from InnerTube: %w", err)
	}

	// 3. Parse response: extract songs or unpack Top 100 chart playlist
	parsedItems, err := ParseBrowseTracks(respBytes)
	if err != nil {
		parsedItems = nil
	}

	var tracks []models.TrackItem
	for _, it := range parsedItems {
		// Valid playable YouTube tracks have video IDs (not playlist containers)
		if it.ID != "" && !strings.HasPrefix(it.ID, "VL") && !strings.HasPrefix(it.ID, "PL") && !strings.HasPrefix(it.ID, "MPRE") {
			tracks = append(tracks, it)
		}
	}

	// If the browse feed contained playlist containers and no direct tracks, unpack the top chart playlist
	if len(tracks) == 0 {
		for _, it := range parsedItems {
			if strings.HasPrefix(it.ID, "VLPL") || strings.HasPrefix(it.ID, "VLOL") || strings.HasPrefix(it.ID, "PL") {
				unpacked, err := e.FetchBrowse(ctx, it.ID, countryCode, langCode)
				if err == nil && len(unpacked) > 0 {
					tracks = unpacked
					break
				}
			}
		}
	}

	// Fallback to well-known Billboard / Hot 100 regional playlist IDs if needed
	if len(tracks) == 0 {
		knownPlaylists := []string{
			"VLPL4fGSI1pDJn40WjZ6utkIuj2rNg-7iGsq", // Top 100 Music Videos
			"VLPL4fGSI1pDJn5oibdgJt8Hy0-dr2B7kSs2", // Daily Top Music Videos
			"VLPL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4_", // Global / US Hot 100
		}
		for _, plID := range knownPlaylists {
			unpacked, err := e.FetchBrowse(ctx, plID, countryCode, langCode)
			if err == nil && len(unpacked) > 0 {
				tracks = unpacked
				break
			}
		}
	}

	if len(tracks) == 0 {
		return nil, fmt.Errorf("no playable chart tracks found in regional browse")
	}

	// 4. Save to SQLite feed_cache
	if e.repo != nil && len(tracks) > 0 {
		if dataBytes, err := json.Marshal(tracks); err == nil {
			_ = e.repo.SetFeedCache(ctx, cacheKey, string(dataBytes), FeedCacheTTLSeconds)
		}
	}

	return tracks, nil
}

// FetchNewReleases retrieves new album releases and tracks, checking SQLite feed_cache first.
func (e *ExploreEngine) FetchNewReleases(ctx context.Context, countryCode, langCode string) ([]models.TrackItem, error) {
	if countryCode == "" {
		countryCode = "US"
	}
	if langCode == "" {
		langCode = "en"
	}

	cacheKey := fmt.Sprintf("explore:%s:%s", strings.ToUpper(countryCode), strings.ToLower(langCode))

	// 1. Check SQLite feed_cache
	if e.repo != nil {
		cachedData, err := e.repo.GetFeedCache(ctx, cacheKey)
		if err == nil && cachedData != "" {
			var tracks []models.TrackItem
			if err := json.Unmarshal([]byte(cachedData), &tracks); err == nil && len(tracks) > 0 {
				return tracks, nil
			}
		}
	}

	// 2. Fetch live from InnerTube
	respBytes, err := executeBrowseRequest(ctx, e.httpClient, "FEmusic_explore", countryCode, langCode)
	if err != nil {
		// Offline fallback
		if e.repo != nil {
			cachedData, cacheErr := e.repo.GetFeedCache(ctx, cacheKey)
			if cacheErr == nil && cachedData != "" {
				var tracks []models.TrackItem
				if json.Unmarshal([]byte(cachedData), &tracks) == nil && len(tracks) > 0 {
					return tracks, nil
				}
			}
		}
		return nil, fmt.Errorf("failed fetching new releases from InnerTube: %w", err)
	}

	// 3. Parse response
	tracks, err := ParseBrowseTracks(respBytes)
	if err != nil {
		return nil, err
	}

	// 4. Save to SQLite feed_cache
	if e.repo != nil && len(tracks) > 0 {
		if dataBytes, err := json.Marshal(tracks); err == nil {
			_ = e.repo.SetFeedCache(ctx, cacheKey, string(dataBytes), FeedCacheTTLSeconds)
		}
	}

	return tracks, nil
}

// FetchBrowse executes an unauthenticated browse query for any given browseID with 4-hour SQLite caching.
func (e *ExploreEngine) FetchBrowse(ctx context.Context, browseID, countryCode, langCode string) ([]models.TrackItem, error) {
	if countryCode == "" {
		countryCode = "US"
	}
	if langCode == "" {
		langCode = "en"
	}

	cacheKey := fmt.Sprintf("browse:%s:%s:%s", browseID, strings.ToUpper(countryCode), strings.ToLower(langCode))

	// 1. Check SQLite feed_cache
	if e.repo != nil {
		cachedData, err := e.repo.GetFeedCache(ctx, cacheKey)
		if err == nil && cachedData != "" {
			var tracks []models.TrackItem
			if err := json.Unmarshal([]byte(cachedData), &tracks); err == nil && len(tracks) > 0 {
				return tracks, nil
			}
		}
	}

	// 2. Fetch live from InnerTube
	respBytes, err := executeBrowseRequest(ctx, e.httpClient, browseID, countryCode, langCode)
	if err != nil {
		// Offline fallback
		if e.repo != nil {
			cachedData, cacheErr := e.repo.GetFeedCache(ctx, cacheKey)
			if cacheErr == nil && cachedData != "" {
				var tracks []models.TrackItem
				if json.Unmarshal([]byte(cachedData), &tracks) == nil && len(tracks) > 0 {
					return tracks, nil
				}
			}
		}
		return nil, fmt.Errorf("failed fetching browse %s from InnerTube: %w", browseID, err)
	}

	// 3. Parse response
	tracks, err := ParseBrowseTracks(respBytes)
	if err != nil {
		return nil, err
	}

	// 4. Save to SQLite feed_cache
	if e.repo != nil && len(tracks) > 0 {
		if dataBytes, err := json.Marshal(tracks); err == nil {
			_ = e.repo.SetFeedCache(ctx, cacheKey, string(dataBytes), FeedCacheTTLSeconds)
		}
	}

	return tracks, nil
}

// ParseBrowseTracks traverses InnerTube browse JSON and extracts normalized models.TrackItem entries.
func ParseBrowseTracks(jsonData []byte) ([]models.TrackItem, error) {
	var root map[string]any
	if err := json.Unmarshal(jsonData, &root); err != nil {
		return nil, fmt.Errorf("invalid InnerTube JSON: %w", err)
	}

	var tracks []models.TrackItem
	seenIDs := make(map[string]bool)

	// Helper to extract tracks from musicResponsiveListItemRenderer
	extractResponsiveItem := func(renderer map[string]any) *models.TrackItem {
		var item models.TrackItem
		item.Source = "youtube"

		// Extract videoId
		if nav, ok := renderer["navigationEndpoint"].(map[string]any); ok {
			if watch, ok := nav["watchEndpoint"].(map[string]any); ok {
				if vid, ok := watch["videoId"].(string); ok {
					item.ID = vid
				}
			}
		}
		if item.ID == "" {
			if overlay, ok := renderer["overlay"].(map[string]any); ok {
				if thumbOverlay, ok := overlay["musicItemThumbnailOverlayRenderer"].(map[string]any); ok {
					if content, ok := thumbOverlay["content"].(map[string]any); ok {
						if btn, ok := content["musicPlayButtonRenderer"].(map[string]any); ok {
							if playNav, ok := btn["playNavigationEndpoint"].(map[string]any); ok {
								if watch, ok := playNav["watchEndpoint"].(map[string]any); ok {
									if vid, ok := watch["videoId"].(string); ok {
										item.ID = vid
									}
								}
							}
						}
					}
				}
			}
		}

		if item.ID == "" {
			return nil
		}

		// Extract flexColumns
		if cols, ok := renderer["flexColumns"].([]any); ok {
			// Column 0: Title
			if len(cols) > 0 {
				if col0, ok := cols[0].(map[string]any); ok {
					if flexCol, ok := col0["musicResponsiveListItemFlexColumnRenderer"].(map[string]any); ok {
						if textObj, ok := flexCol["text"].(map[string]any); ok {
							if runs, ok := textObj["runs"].([]any); ok && len(runs) > 0 {
								if r0, ok := runs[0].(map[string]any); ok {
									if t, ok := r0["text"].(string); ok {
										item.Title = t
									}
								}
							}
						}
					}
				}
			}

			// Column 1: Artists & Album
			if len(cols) > 1 {
				if col1, ok := cols[1].(map[string]any); ok {
					if flexCol, ok := col1["musicResponsiveListItemFlexColumnRenderer"].(map[string]any); ok {
						if textObj, ok := flexCol["text"].(map[string]any); ok {
							if runs, ok := textObj["runs"].([]any); ok {
								for _, r := range runs {
									if rMap, ok := r.(map[string]any); ok {
										if txt, ok := rMap["text"].(string); ok {
											txt = strings.TrimSpace(txt)
											if txt != "" && txt != "•" && txt != "," && txt != "&" {
												item.Artists = append(item.Artists, txt)
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		if len(item.Artists) > 0 {
			item.Artist = item.Artists[0]
		}
		if item.Title == "" {
			return nil
		}

		// Extract thumbnail
		if thumbObj, ok := renderer["thumbnail"].(map[string]any); ok {
			if musicThumb, ok := thumbObj["musicThumbnailRenderer"].(map[string]any); ok {
				if thumb, ok := musicThumb["thumbnail"].(map[string]any); ok {
					if thumbs, ok := thumb["thumbnails"].([]any); ok && len(thumbs) > 0 {
						if lastThumb, ok := thumbs[len(thumbs)-1].(map[string]any); ok {
							if u, ok := lastThumb["url"].(string); ok {
								item.Thumbnail = UpscaleThumbnail(u)
							}
						}
					}
				}
			}
		}

		if item.Thumbnail == "" && item.ID != "" && len(item.ID) == 11 && !strings.HasPrefix(item.ID, "local:") {
			item.Thumbnail = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", item.ID)
		}

		return &item
	}

	// Helper to extract tracks from musicTwoRowItemRenderer
	extractTwoRowItem := func(renderer map[string]any) *models.TrackItem {
		var item models.TrackItem
		item.Source = "youtube"

		if nav, ok := renderer["navigationEndpoint"].(map[string]any); ok {
			if watch, ok := nav["watchEndpoint"].(map[string]any); ok {
				if vid, ok := watch["videoId"].(string); ok {
					item.ID = vid
				}
			}
			if item.ID == "" {
				if browse, ok := nav["browseEndpoint"].(map[string]any); ok {
					if bid, ok := browse["browseId"].(string); ok {
						item.ID = bid
					}
				}
			}
		}

		if item.ID == "" {
			return nil
		}

		// Title
		if titleObj, ok := renderer["title"].(map[string]any); ok {
			if runs, ok := titleObj["runs"].([]any); ok && len(runs) > 0 {
				if r0, ok := runs[0].(map[string]any); ok {
					if t, ok := r0["text"].(string); ok {
						item.Title = t
					}
				}
			}
		}

		// Subtitle (Artist)
		if subObj, ok := renderer["subtitle"].(map[string]any); ok {
			if runs, ok := subObj["runs"].([]any); ok {
				for _, r := range runs {
					if rMap, ok := r.(map[string]any); ok {
						if txt, ok := rMap["text"].(string); ok {
							txt = strings.TrimSpace(txt)
							if txt != "" && txt != "•" && txt != "," {
								item.Artists = append(item.Artists, txt)
							}
						}
					}
				}
			}
		}

		if len(item.Artists) > 0 {
			item.Artist = item.Artists[0]
		}
		if item.Title == "" {
			return nil
		}

		// Thumbnail
		if thumbRenderer, ok := renderer["thumbnailRenderer"].(map[string]any); ok {
			if musicThumb, ok := thumbRenderer["musicThumbnailRenderer"].(map[string]any); ok {
				if thumb, ok := musicThumb["thumbnail"].(map[string]any); ok {
					if thumbs, ok := thumb["thumbnails"].([]any); ok && len(thumbs) > 0 {
						if lastThumb, ok := thumbs[len(thumbs)-1].(map[string]any); ok {
							if u, ok := lastThumb["url"].(string); ok {
								item.Thumbnail = UpscaleThumbnail(u)
							}
						}
					}
				}
			}
		}

		if item.Thumbnail == "" && item.ID != "" && len(item.ID) == 11 && !strings.HasPrefix(item.ID, "local:") {
			item.Thumbnail = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", item.ID)
		}

		return &item
	}

	// Recursive traverse to find item renderers across shelves
	var walk func(node any)
	walk = func(node any) {
		switch val := node.(type) {
		case map[string]any:
			if respItem, ok := val["musicResponsiveListItemRenderer"].(map[string]any); ok {
				if track := extractResponsiveItem(respItem); track != nil && !seenIDs[track.ID] {
					seenIDs[track.ID] = true
					tracks = append(tracks, *track)
				}
			}
			if twoRowItem, ok := val["musicTwoRowItemRenderer"].(map[string]any); ok {
				if track := extractTwoRowItem(twoRowItem); track != nil && !seenIDs[track.ID] {
					seenIDs[track.ID] = true
					tracks = append(tracks, *track)
				}
			}
			for _, v := range val {
				walk(v)
			}
		case []any:
			for _, elem := range val {
				walk(elem)
			}
		}
	}

	walk(root)
	return tracks, nil
}
