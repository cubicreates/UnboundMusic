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

	// 1. First attempt: unconstrained search to capture Top Result card (e.g. Adele - Hello)
	tracks, err := c.searchWithFilter(ctx, query, "")
	if err == nil && len(tracks) >= 5 {
		return tracks, nil
	}

	// 2. Secondary enrichment: search with explicit Song filter
	songTracks, songErr := c.searchWithFilter(ctx, query, FilterSong)
	if songErr == nil && len(songTracks) > 0 {
		seen := make(map[string]bool)
		var combined []models.Track
		for _, t := range tracks {
			if !seen[t.ID] {
				seen[t.ID] = true
				combined = append(combined, t)
			}
		}
		for _, t := range songTracks {
			if !seen[t.ID] {
				seen[t.ID] = true
				combined = append(combined, t)
			}
		}
		return combined, nil
	}

	if err != nil {
		return nil, err
	}
	return tracks, nil
}

// SearchWithCategory executes a search query targeting a specific category: "music", "podcast", or "all".
func (c *Client) SearchWithCategory(ctx context.Context, query, category string) ([]models.Track, error) {
	trimmed := strings.TrimSpace(query)
	if trimmed == "" {
		return nil, fmt.Errorf("search query cannot be empty")
	}

	cat := strings.ToLower(strings.TrimSpace(category))
	switch cat {
	case "music":
		// 1. Query with strict Song filter
		tracks, err := c.searchWithFilter(ctx, trimmed, FilterSong)
		if err != nil || len(tracks) == 0 {
			// Fallback: regular search but strictly filtered
			tracks, _ = c.searchWithFilter(ctx, trimmed, "")
		}
		// Strict music filter: remove non-music videos, vlogs, reviews, long video essays, podcasts, ads
		var filtered []models.Track
		nonMusicKeywords := []string{
			"video essay", "reaction", "review", "vlog", "gameplay",
			"walkthrough", "trailer", "unboxing", "full episode", "podcast",
			"news", "press conference", "audiobook",
		}
		for _, t := range tracks {
			lowerTitle := strings.ToLower(t.Title)
			lowerArtist := strings.ToLower(t.Artist)

			isNonMusic := false
			for _, kw := range nonMusicKeywords {
				if strings.Contains(lowerTitle, kw) || strings.Contains(lowerArtist, kw) {
					isNonMusic = true
					break
				}
			}
			if isNonMusic {
				continue
			}

			// Filter out videos longer than 20 minutes (1,200,000 ms)
			if t.DurationMs > 1200000 {
				continue
			}

			filtered = append(filtered, t)
		}
		if len(filtered) > 0 {
			return filtered, nil
		}
		return tracks, nil

	case "podcast", "podcasts":
		tracks, err := c.searchWithFilter(ctx, trimmed, FilterPodcast)
		if err == nil && len(tracks) > 0 {
			return tracks, nil
		}
		// Fallback to podcast query
		podcastQuery := fmt.Sprintf("%s podcast", trimmed)
		return c.searchWithFilter(ctx, podcastQuery, "")

	case "album", "albums":
		tracks, err := c.searchWithFilter(ctx, trimmed, FilterAlbum)
		if err == nil && len(tracks) > 0 {
			return tracks, nil
		}
		return c.Search(ctx, query)

	case "artist", "artists":
		tracks, err := c.searchWithFilter(ctx, trimmed, FilterArtist)
		if err == nil && len(tracks) > 0 {
			return tracks, nil
		}
		return c.Search(ctx, query)

	case "playlist", "playlists":
		tracks, err := c.searchWithFilter(ctx, trimmed, FilterCommunityPlaylist)
		if err == nil && len(tracks) > 0 {
			return tracks, nil
		}
		return c.Search(ctx, query)

	case "song", "songs":
		tracks, err := c.searchWithFilter(ctx, trimmed, FilterSong)
		if err == nil && len(tracks) > 0 {
			return tracks, nil
		}
		return c.Search(ctx, query)

	default:
		return c.Search(ctx, query)
	}
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

	// [STAGE 3] Broad Community Audio Sweep
	broadQuery := fmt.Sprintf("%s audio", trimmed)
	tracks, err = c.searchWithFilter(ctx, broadQuery, "")
	if err == nil && len(tracks) > 0 {
		return &CascadeSearchResult{
			Query:        trimmed,
			StageReached: 3,
			StageName:    "Broad Audio Sweep",
			Tracks:       tracks,
		}, nil
	}

	// [STAGE 4] Graceful empty result
	return &CascadeSearchResult{
		Query:        trimmed,
		StageReached: 4,
		StageName:    "No Results",
		Tracks:       []models.Track{},
	}, nil
}

// parseSearchResponse traverses InnerTube JSON, extracting top result cards and tracks from shelves.
func parseSearchResponse(data []byte) ([]models.Track, error) {
	var root map[string]any
	if err := json.Unmarshal(data, &root); err != nil {
		return nil, fmt.Errorf("failed to parse search JSON: %w", err)
	}

	var tracks []models.Track
	seenIDs := make(map[string]bool)

	tabbed, ok := root["contents"].(map[string]any)["tabbedSearchResultsRenderer"].(map[string]any)
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

		// 1. Extract Top Result card (musicCardShelfRenderer)
		if cardShelf, ok := secMap["musicCardShelfRenderer"].(map[string]any); ok {
			cardTrack := extractTrackFromCardShelf(cardShelf)
			if cardTrack != nil && cardTrack.ID != "" && !seenIDs[cardTrack.ID] {
				seenIDs[cardTrack.ID] = true
				tracks = append(tracks, *cardTrack)
			}
		}

		// 2. Extract Shelf contents (musicShelfRenderer)
		if musicShelf, ok := secMap["musicShelfRenderer"].(map[string]any); ok {
			if shelfContents, ok := musicShelf["contents"].([]any); ok {
				for _, item := range shelfContents {
					if itemMap, ok := item.(map[string]any); ok {
						track := extractTrackFromResponsiveItem(itemMap)
						if track != nil && track.ID != "" && !seenIDs[track.ID] {
							seenIDs[track.ID] = true
							tracks = append(tracks, *track)
						}
					}
				}
			}
		}

		// 3. Extract Item section contents (itemSectionRenderer)
		if itemSec, ok := secMap["itemSectionRenderer"].(map[string]any); ok {
			if secContents, ok := itemSec["contents"].([]any); ok {
				for _, item := range secContents {
					if itemMap, ok := item.(map[string]any); ok {
						track := extractTrackFromResponsiveItem(itemMap)
						if track != nil && track.ID != "" && !seenIDs[track.ID] {
							seenIDs[track.ID] = true
							tracks = append(tracks, *track)
						}
					}
				}
			}
		}
	}

	return tracks, nil
}

// extractTrackFromCardShelf parses a musicCardShelfRenderer (Top Result card) into a Track model.
func extractTrackFromCardShelf(card map[string]any) *models.Track {
	track := &models.Track{
		ItemType: "song",
	}

	// Extract Title and Video ID / Browse ID from title runs
	if titleObj, ok := card["title"].(map[string]any); ok {
		track.Title = extractRunsText(titleObj)
		if runs, ok := titleObj["runs"].([]any); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]any); ok {
				if nav, ok := r0["navigationEndpoint"].(map[string]any); ok {
					if watch, ok := nav["watchEndpoint"].(map[string]any); ok {
						if vid, ok := watch["videoId"].(string); ok {
							track.ID = vid
						}
					}
					if browse, ok := nav["browseEndpoint"].(map[string]any); ok {
						if bId, ok := browse["browseId"].(string); ok {
							track.BrowseID = bId
							if strings.HasPrefix(bId, "MPREb_") {
								track.ItemType = "album"
							} else if strings.HasPrefix(bId, "VL") || strings.HasPrefix(bId, "PL") {
								track.ItemType = "playlist"
							} else if strings.HasPrefix(bId, "UC") {
								track.ItemType = "artist"
							}
						}
					}
				}
			}
		}
	}

	// Fallback to buttons for Video ID or Playlist ID
	if track.ID == "" {
		if btns, ok := card["buttons"].([]any); ok {
			for _, btn := range btns {
				if bMap, ok := btn.(map[string]any); ok {
					if btnRen, ok := bMap["buttonRenderer"].(map[string]any); ok {
						if cmd, ok := btnRen["command"].(map[string]any); ok {
							if watch, ok := cmd["watchEndpoint"].(map[string]any); ok {
								if vid, ok := watch["videoId"].(string); ok {
									track.ID = vid
									break
								}
							}
							if watchPL, ok := cmd["watchPlaylistEndpoint"].(map[string]any); ok {
								if pId, ok := watchPL["playlistId"].(string); ok && track.BrowseID == "" {
									track.BrowseID = pId
								}
							}
						}
					}
				}
			}
		}
	}

	// Extract Artist, Album, Year, and Item Type from subtitle runs
	if subObj, ok := card["subtitle"].(map[string]any); ok {
		runs := extractRunsList(subObj)
		for _, r := range runs {
			r = strings.TrimSpace(r)
			if r == "" || r == "•" {
				continue
			}
			if strings.EqualFold(r, "Album") || strings.EqualFold(r, "EP") {
				track.ItemType = "album"
				continue
			}
			if strings.EqualFold(r, "Playlist") {
				track.ItemType = "playlist"
				continue
			}
			if strings.EqualFold(r, "Artist") {
				track.ItemType = "artist"
				continue
			}
			if strings.EqualFold(r, "Song") || strings.EqualFold(r, "Video") {
				continue
			}
			// Check for 4-digit release year
			if len(r) == 4 && r[0] >= '1' && r[0] <= '2' && r[1] >= '0' && r[1] <= '9' {
				track.Year = r
				continue
			}
			if track.Artist == "" {
				track.Artist = r
			} else if track.Album == "" && !strings.Contains(r, ":") {
				track.Album = r
			}
		}
	}

	if track.ItemType == "album" || track.ItemType == "playlist" || track.ItemType == "artist" {
		if track.ID == "" && track.BrowseID != "" {
			track.ID = track.BrowseID
		}
	}

	if track.ID == "" {
		return nil
	}

	// Extract Thumbnail: YouTube Music first, fallback to YouTube video thumbnail
	track.ThumbnailURL = ExtractThumbnail(card, track.ID)
	if track.ItemType == "album" && strings.Contains(track.ThumbnailURL, "=w") {
		track.ThumbnailURL = strings.Split(track.ThumbnailURL, "=")[0] + "=w800-h800"
	}

	return track
}

// extractTrackFromResponsiveItem parses a single musicResponsiveListItemRenderer into a Track model.
func extractTrackFromResponsiveItem(item map[string]any) *models.Track {
	responsive, ok := item["musicResponsiveListItemRenderer"].(map[string]any)
	if !ok {
		return nil
	}

	track := &models.Track{
		ItemType: "song",
	}

	// Check item-level navigationEndpoint (for album, playlist, or artist browse endpoints)
	if nav, ok := responsive["navigationEndpoint"].(map[string]any); ok {
		if browse, ok := nav["browseEndpoint"].(map[string]any); ok {
			if bId, ok := browse["browseId"].(string); ok {
				track.BrowseID = bId
				if strings.HasPrefix(bId, "MPREb_") {
					track.ItemType = "album"
				} else if strings.HasPrefix(bId, "VL") || strings.HasPrefix(bId, "PL") {
					track.ItemType = "playlist"
				} else if strings.HasPrefix(bId, "UC") {
					track.ItemType = "artist"
				}
			}
			if configs, ok := browse["browseEndpointContextSupportedConfigs"].(map[string]any); ok {
				if musicConfig, ok := configs["browseEndpointContextMusicConfig"].(map[string]any); ok {
					if pageType, ok := musicConfig["pageType"].(string); ok {
						if pageType == "MUSIC_PAGE_TYPE_ALBUM" || pageType == "MUSIC_PAGE_TYPE_AUDIOBOOK" {
							track.ItemType = "album"
						} else if pageType == "MUSIC_PAGE_TYPE_PLAYLIST" {
							track.ItemType = "playlist"
						} else if pageType == "MUSIC_PAGE_TYPE_ARTIST" {
							track.ItemType = "artist"
						}
					}
				}
			}
		}
	}

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
					if nav, ok := playBtn["playNavigationEndpoint"].(map[string]any); ok {
						if watch, ok := nav["watchEndpoint"].(map[string]any); ok {
							if vid, ok := watch["videoId"].(string); ok {
								track.ID = vid
							}
						}
						if watchPL, ok := nav["watchPlaylistEndpoint"].(map[string]any); ok {
							if pId, ok := watchPL["playlistId"].(string); ok && track.BrowseID == "" {
								track.BrowseID = pId
							}
						}
					}
				}
			}
		}
	}

	// Extract Flex Columns (Title, Artist, Album, Duration, Type, Year)
	flexColumns, ok := responsive["flexColumns"].([]any)
	if ok && len(flexColumns) > 0 {
		// Column 0: Title & possible browseId
		if col0, ok := flexColumns[0].(map[string]any)["musicResponsiveListItemFlexColumnRenderer"].(map[string]any); ok {
			track.Title = extractRunsText(col0["text"])
			if col0Text, ok := col0["text"].(map[string]any); ok {
				if runs, ok := col0Text["runs"].([]any); ok && len(runs) > 0 {
					if r0, ok := runs[0].(map[string]any); ok {
						if nav, ok := r0["navigationEndpoint"].(map[string]any); ok {
							if browse, ok := nav["browseEndpoint"].(map[string]any); ok {
								if bId, ok := browse["browseId"].(string); ok {
									if track.BrowseID == "" {
										track.BrowseID = bId
									}
									if strings.HasPrefix(bId, "MPREb_") {
										track.ItemType = "album"
									} else if strings.HasPrefix(bId, "VL") || strings.HasPrefix(bId, "PL") {
										track.ItemType = "playlist"
									} else if strings.HasPrefix(bId, "UC") {
										track.ItemType = "artist"
									}
								}
							}
						}
					}
				}
			}
		}

		// Column 1: Artist, Album, Duration, Type, Year
		if len(flexColumns) > 1 {
			if col1, ok := flexColumns[1].(map[string]any)["musicResponsiveListItemFlexColumnRenderer"].(map[string]any); ok {
				rawRuns := extractRunsList(col1["text"])
				var meaningfulRuns []string
				for _, r := range rawRuns {
					r = strings.TrimSpace(r)
					if r == "" || r == "•" || r == "," {
						continue
					}
					if strings.EqualFold(r, "Album") || strings.EqualFold(r, "EP") || strings.EqualFold(r, "Single") {
						if strings.EqualFold(r, "Album") || strings.EqualFold(r, "EP") {
							track.ItemType = "album"
						}
						continue
					}
					if strings.EqualFold(r, "Playlist") {
						track.ItemType = "playlist"
						continue
					}
					if strings.EqualFold(r, "Artist") {
						track.ItemType = "artist"
						continue
					}
					if strings.EqualFold(r, "Song") || strings.EqualFold(r, "Video") {
						continue
					}
					// Check for 4-digit release year
					if len(r) == 4 && r[0] >= '1' && r[0] <= '2' && r[1] >= '0' && r[1] <= '9' {
						track.Year = r
						continue
					}
					meaningfulRuns = append(meaningfulRuns, r)
				}
				if len(meaningfulRuns) > 0 {
					track.Artist = meaningfulRuns[0]
				}
				if len(meaningfulRuns) > 1 {
					last := meaningfulRuns[len(meaningfulRuns)-1]
					if strings.Contains(last, ":") {
						track.DurationMs = parseDurationToMs(last)
						if len(meaningfulRuns) > 2 {
							track.Album = meaningfulRuns[1]
						}
					} else {
						track.Album = meaningfulRuns[1]
					}
				}
			}
		}
	}

	// For albums, playlists, and artists without videoId, use BrowseID as ID
	if track.ItemType == "album" || track.ItemType == "playlist" || track.ItemType == "artist" {
		if track.ID == "" && track.BrowseID != "" {
			track.ID = track.BrowseID
		}
	}

	if track.ID == "" {
		return nil
	}

	// Extract Thumbnail: YouTube Music first, fallback to YouTube video thumbnail
	track.ThumbnailURL = ExtractThumbnail(responsive, track.ID)
	if track.ItemType == "album" && strings.Contains(track.ThumbnailURL, "=w") {
		track.ThumbnailURL = strings.Split(track.ThumbnailURL, "=")[0] + "=w800-h800"
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

// ExtractThumbnail extracts high-res square thumbnail from YouTube Music structures,
// falling back to standard YouTube video thumbnails only if YouTube Music artwork is absent.
func ExtractThumbnail(obj map[string]any, fallbackVideoID string) string {
	if obj == nil {
		if fallbackVideoID != "" && len(fallbackVideoID) == 11 && !strings.HasPrefix(fallbackVideoID, "local:") {
			return fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", fallbackVideoID)
		}
		return ""
	}

	// 1. Check thumbnailRenderer or thumbnail container
	for _, key := range []string{"thumbnail", "thumbnailRenderer"} {
		if sub, ok := obj[key].(map[string]any); ok {
			for _, subKey := range []string{"musicThumbnailRenderer", "croppedSquareThumbnailRenderer"} {
				if renderer, ok := sub[subKey].(map[string]any); ok {
					if tObj, ok := renderer["thumbnail"].(map[string]any); ok {
						if thumbs, ok := tObj["thumbnails"].([]any); ok && len(thumbs) > 0 {
							if lastThumb, ok := thumbs[len(thumbs)-1].(map[string]any); ok {
								if u, ok := lastThumb["url"].(string); ok && u != "" {
									return UpscaleThumbnail(u)
								}
							}
						}
					}
				}
			}
			// Direct thumbnails array in sub
			if thumbs, ok := sub["thumbnails"].([]any); ok && len(thumbs) > 0 {
				if lastThumb, ok := thumbs[len(thumbs)-1].(map[string]any); ok {
					if u, ok := lastThumb["url"].(string); ok && u != "" {
						return UpscaleThumbnail(u)
					}
				}
			}
		}
	}

	// 2. Direct thumbnails array in root obj
	if thumbs, ok := obj["thumbnails"].([]any); ok && len(thumbs) > 0 {
		if lastThumb, ok := thumbs[len(thumbs)-1].(map[string]any); ok {
			if u, ok := lastThumb["url"].(string); ok && u != "" {
				return UpscaleThumbnail(u)
			}
		}
	}

	// 3. Fallback: YouTube standard video thumbnail
	if fallbackVideoID != "" && len(fallbackVideoID) == 11 && !strings.HasPrefix(fallbackVideoID, "local:") {
		return fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", fallbackVideoID)
	}

	return ""
}

