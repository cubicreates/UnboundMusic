/*
 * Package: ytmusic
 * File: genres.go
 * Purpose: Scrapes structured genre and mood discovery boards from YouTube Music InnerTube index (FEmusic_moods_and_genres).
 *          Provides 7-day SQLite cache, deterministic color fallbacks, and zero-data offline resilience.
 * Subsystem: Music Discovery & Explore Feeds
 * Concurrency: Thread-safe HTTP and SQLite operations.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
)

// GenreItem represents a clickable mood or genre card.
type GenreItem struct {
	Title       string `json:"title"`
	StripeColor int64  `json:"stripe_color"`
	Params      string `json:"params"`
	BrowseID    string `json:"browse_id"`
}

// GenreSection groups genre items into categories (e.g., "Moods & moments", "Genres").
type GenreSection struct {
	Title string      `json:"title"`
	Items []GenreItem `json:"items"`
}

// PlaylistItem represents a playlist or album card inside a genre shelf.
type PlaylistItem struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	Subtitle     string `json:"subtitle"`
	ThumbnailURL string `json:"thumbnail_url"`
	PlaylistID   string `json:"playlist_id"`
}

// PlaylistShelf represents a titled shelf of playlists in GenreDetailScreen.
type PlaylistShelf struct {
	Title string         `json:"title"`
	Items []PlaylistItem `json:"items"`
}

// DeterministicStripeColor hashes a category title to map consistently to Unbound's technical palette.
func DeterministicStripeColor(title string) int64 {
	palette := []int64{
		0xFF4CD6FB, // Unbound Cyan
		0xFFD67BFF, // Neon Violet
		0xFF00FF66, // Matrix Emerald
		0xFFFFB74D, // Amber
		0xFFFF5722, // Sunset Orange
		0xFF9C27B0, // Deep Purple
		0xFF00E676, // Mint
		0xFF2979FF, // Electric Blue
	}
	var hash uint32 = 2166136261
	for i := 0; i < len(title); i++ {
		hash ^= uint32(title[i])
		hash *= 16777619
	}
	idx := int(hash % uint32(len(palette)))
	return palette[idx]
}

// GetDefaultGenreSections returns the static 12 global core genres for instant offline resilience.
func GetDefaultGenreSections() []GenreSection {
	coreGenres := []string{
		"Hip-Hop", "Pop", "Rock", "Electronic", "R&B", "Lo-Fi",
		"Metal", "Indie", "Classical", "Jazz", "Country", "Ambient",
	}
	items := make([]GenreItem, len(coreGenres))
	for i, name := range coreGenres {
		items[i] = GenreItem{
			Title:       name,
			StripeColor: DeterministicStripeColor(name),
			Params:      "default_" + strings.ToLower(strings.ReplaceAll(name, " ", "_")),
			BrowseID:    "FEmusic_moods_and_genres_category",
		}
	}
	return []GenreSection{
		{
			Title: "Genres",
			Items: items,
		},
	}
}

// ParseMoodsAndGenresJSON parses the InnerTube browse response for FEmusic_moods_and_genres.
func (e *ExploreEngine) ParseMoodsAndGenresJSON(respBytes []byte) ([]GenreSection, error) {
	var root map[string]any
	if err := json.Unmarshal(respBytes, &root); err != nil {
		return nil, fmt.Errorf("failed to parse browse response JSON: %w", err)
	}

	contents, _ := root["contents"].(map[string]any)
	singleColumn, _ := contents["singleColumnBrowseResultsRenderer"].(map[string]any)
	tabs, _ := singleColumn["tabs"].([]any)
	if len(tabs) == 0 {
		return nil, fmt.Errorf("no tabs found in browse response")
	}

	tab0, _ := tabs[0].(map[string]any)
	tabRenderer, _ := tab0["tabRenderer"].(map[string]any)
	content, _ := tabRenderer["content"].(map[string]any)
	sectionList, _ := content["sectionListRenderer"].(map[string]any)
	sectionContents, _ := sectionList["contents"].([]any)

	var sections []GenreSection

	for _, sec := range sectionContents {
		secMap, ok := sec.(map[string]any)
		if !ok {
			continue
		}

		grid, ok := secMap["gridRenderer"].(map[string]any)
		if !ok {
			continue
		}

		// Extract Section Title
		secTitle := "Explore"
		if header, ok := grid["header"].(map[string]any); ok {
			if gridHeader, ok := header["gridHeaderRenderer"].(map[string]any); ok {
				if titleObj, ok := gridHeader["title"].(map[string]any); ok {
					if runs, ok := titleObj["runs"].([]any); ok && len(runs) > 0 {
						if r0, ok := runs[0].(map[string]any); ok {
							if t, ok := r0["text"].(string); ok {
								secTitle = t
							}
						}
					}
				}
			}
		}

		// Extract Navigation Items
		itemsArray, _ := grid["items"].([]any)
		var genreItems []GenreItem

		for _, item := range itemsArray {
			itemMap, ok := item.(map[string]any)
			if !ok {
				continue
			}

			btn, ok := itemMap["musicNavigationButtonRenderer"].(map[string]any)
			if !ok {
				continue
			}

			var genreTitle string
			if btnText, ok := btn["buttonText"].(map[string]any); ok {
				if runs, ok := btnText["runs"].([]any); ok && len(runs) > 0 {
					if r0, ok := runs[0].(map[string]any); ok {
						if t, ok := r0["text"].(string); ok {
							genreTitle = t
						}
					}
				}
			}

			if genreTitle == "" {
				continue
			}

			var stripeColor int64
			if solid, ok := btn["solid"].(map[string]any); ok {
				if colorVal, ok := solid["leftStripeColor"]; ok {
					switch c := colorVal.(type) {
					case float64:
						stripeColor = int64(c)
					case int64:
						stripeColor = c
					}
				}
			}

			if stripeColor == 0 {
				stripeColor = DeterministicStripeColor(genreTitle)
			}

			var params string
			var browseID string
			if clickCmd, ok := btn["clickCommand"].(map[string]any); ok {
				if browseEndpoint, ok := clickCmd["browseEndpoint"].(map[string]any); ok {
					if p, ok := browseEndpoint["params"].(string); ok {
						params = p
					}
					if b, ok := browseEndpoint["browseId"].(string); ok {
						browseID = b
					}
				}
			}

			genreItems = append(genreItems, GenreItem{
				Title:       genreTitle,
				StripeColor: stripeColor,
				Params:      params,
				BrowseID:    browseID,
			})
		}

		if len(genreItems) > 0 {
			sections = append(sections, GenreSection{
				Title: secTitle,
				Items: genreItems,
			})
		}
	}

	return sections, nil
}

// FetchMoodsAndGenres retrieves structured genre and mood discovery boards with 7-day SQLite cache.
func (e *ExploreEngine) FetchMoodsAndGenres(ctx context.Context, countryCode, langCode string) ([]GenreSection, error) {
	if countryCode == "" {
		countryCode = "US"
	}
	if langCode == "" {
		langCode = "en"
	}

	cacheKey := fmt.Sprintf("moods_and_genres:%s:%s", strings.ToUpper(countryCode), strings.ToLower(langCode))

	// 1. Check SQLite 7-day cache
	if e.repo != nil {
		cachedData, err := e.repo.GetFeedCache(ctx, cacheKey)
		if err == nil && cachedData != "" {
			var sections []GenreSection
			if err := json.Unmarshal([]byte(cachedData), &sections); err == nil && len(sections) > 0 {
				return sections, nil
			}
		}
	}

	// 2. Fetch live from InnerTube
	respBytes, err := executeBrowseRequest(ctx, e.httpClient, "FEmusic_moods_and_genres", countryCode, langCode)
	if err != nil {
		// Offline fallback: try stale cache first
		if e.repo != nil {
			cachedData, cacheErr := e.repo.GetFeedCache(ctx, cacheKey)
			if cacheErr == nil && cachedData != "" {
				var sections []GenreSection
				if json.Unmarshal([]byte(cachedData), &sections) == nil && len(sections) > 0 {
					return sections, nil
				}
			}
		}
		// Return static 12 core genres fallback
		return GetDefaultGenreSections(), nil
	}

	sections, err := e.ParseMoodsAndGenresJSON(respBytes)
	if err != nil || len(sections) == 0 {
		return GetDefaultGenreSections(), nil
	}

	// 3. Cache into SQLite with 7-day TTL (604,800 seconds)
	if e.repo != nil {
		if data, err := json.Marshal(sections); err == nil {
			_ = e.repo.SetFeedCache(ctx, cacheKey, string(data), 604800)
		}
	}

	return sections, nil
}
