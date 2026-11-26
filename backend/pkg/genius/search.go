/*
 * Package: genius
 * File: search.go
 * Purpose: Searches genius.com for song metadata and retrieves song page URLs without requiring API tokens.
 * Subsystem: FOSS Lyrics Engine
 * Concurrency: Thread-safe; multiple search queries can run concurrently.
 */

package genius

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
)

// SongHit represents a parsed search match from Genius search.
type SongHit struct {
	ID           int64  `json:"id"`
	Title        string `json:"title"`
	Artist       string `json:"artist"`
	Path         string `json:"path"`
	URL          string `json:"url"`
	ThumbnailURL string `json:"thumbnail_url"`
	MatchedLyric string `json:"matched_lyric,omitempty"`
	SectionType  string `json:"section_type,omitempty"`
}

// SearchSong queries genius.com for matching song titles and artist names.
func (c *Client) SearchSong(ctx context.Context, title, artist string) (*SongHit, error) {
	query := strings.TrimSpace(title)
	if artist != "" {
		query = fmt.Sprintf("%s %s", query, strings.TrimSpace(artist))
	}

	if query == "" {
		return nil, fmt.Errorf("search query cannot be empty")
	}

	searchURL := buildGeniusSearchURL(query)
	body, err := c.get(ctx, searchURL)
	if err != nil {
		return nil, fmt.Errorf("genius search request failed: %w", err)
	}

	return parseSearchResponse(body)
}

// SearchSongsMulti queries genius.com and returns up to limit candidate song hits across lyric, top_hit, and song sections.
func (c *Client) SearchSongsMulti(ctx context.Context, query string, limit int) ([]SongHit, error) {
	trimmed := strings.TrimSpace(query)
	if trimmed == "" {
		return nil, fmt.Errorf("search query cannot be empty")
	}
	if limit <= 0 {
		limit = 8
	}

	searchURL := buildGeniusSearchURL(trimmed)
	body, err := c.get(ctx, searchURL)
	if err != nil {
		return nil, fmt.Errorf("genius multi search request failed: %w", err)
	}

	return parseMultiSearchResponse(body, limit)
}

func parseMultiSearchResponse(data []byte, limit int) ([]SongHit, error) {
	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("failed to parse genius search JSON: %w", err)
	}

	response, ok := raw["response"].(map[string]any)
	if !ok {
		return nil, fmt.Errorf("invalid genius response envelope")
	}

	sections, ok := response["sections"].([]any)
	if !ok || len(sections) == 0 {
		return nil, nil
	}

	var results []SongHit
	seenIDs := make(map[int64]bool)
	seenKeys := make(map[string]bool)

	for _, sec := range sections {
		secMap, ok := sec.(map[string]any)
		if !ok {
			continue
		}

		secType, _ := secMap["type"].(string)
		if secType != "lyric" && secType != "top_hit" && secType != "song" {
			continue
		}

		hits, ok := secMap["hits"].([]any)
		if !ok || len(hits) == 0 {
			continue
		}

		for _, hit := range hits {
			hitMap, ok := hit.(map[string]any)
			if !ok {
				continue
			}

			result, ok := hitMap["result"].(map[string]any)
			if !ok {
				continue
			}

			parsedHit := extractSongHit(result)
			if parsedHit == nil || (parsedHit.Title == "" && parsedHit.Path == "") {
				continue
			}
			parsedHit.SectionType = secType

			// Extract lyric highlight snippet when present
			if highlights, ok := hitMap["highlights"].([]any); ok && len(highlights) > 0 {
				if h0, ok := highlights[0].(map[string]any); ok {
					if val, ok := h0["value"].(string); ok {
						cleaned := strings.Join(strings.Fields(strings.ReplaceAll(val, "\n", " ")), " ")
						parsedHit.MatchedLyric = strings.TrimSpace(cleaned)
					}
				}
			}

			normKey := strings.ToLower(strings.TrimSpace(parsedHit.Title)) + "::" + strings.ToLower(strings.TrimSpace(parsedHit.Artist))
			if parsedHit.ID != 0 && seenIDs[parsedHit.ID] {
				continue
			}
			if seenKeys[normKey] {
				continue
			}
			if parsedHit.ID != 0 {
				seenIDs[parsedHit.ID] = true
			}
			seenKeys[normKey] = true
			results = append(results, *parsedHit)
			if len(results) >= limit {
				return results, nil
			}
		}
	}

	return results, nil
}

// parseSearchResponse traverses the Genius multi-search JSON envelope to extract the top song hit.
func parseSearchResponse(data []byte) (*SongHit, error) {
	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return nil, fmt.Errorf("failed to parse genius search JSON: %w", err)
	}

	response, ok := raw["response"].(map[string]any)
	if !ok {
		return nil, fmt.Errorf("invalid genius response envelope")
	}

	sections, ok := response["sections"].([]any)
	if !ok || len(sections) == 0 {
		return nil, fmt.Errorf("no search sections returned from genius")
	}

	for _, sec := range sections {
		secMap, ok := sec.(map[string]any)
		if !ok {
			continue
		}

		secType, _ := secMap["type"].(string)
		if secType != "song" && secType != "top_hit" {
			continue
		}

		hits, ok := secMap["hits"].([]any)
		if !ok || len(hits) == 0 {
			continue
		}

		for _, hit := range hits {
			hitMap, ok := hit.(map[string]any)
			if !ok {
				continue
			}

			result, ok := hitMap["result"].(map[string]any)
			if !ok {
				continue
			}

			parsedHit := extractSongHit(result)
			if parsedHit != nil && parsedHit.Path != "" {
				return parsedHit, nil
			}
		}
	}

	return nil, fmt.Errorf("no matching song found on genius")
}

// extractSongHit maps a JSON result dictionary to a SongHit entity.
func extractSongHit(res map[string]any) *SongHit {
	path, _ := res["path"].(string)
	title, _ := res["title"].(string)
	songURL, _ := res["url"].(string)
	thumb, _ := res["song_art_image_thumbnail_url"].(string)

	var id int64
	if num, ok := res["id"].(float64); ok {
		id = int64(num)
	}

	artistName := ""
	if primaryArtist, ok := res["primary_artist"].(map[string]any); ok {
		artistName, _ = primaryArtist["name"].(string)
	}

	if path == "" && songURL != "" {
		path = songURL
	}

	return &SongHit{
		ID:           id,
		Title:        title,
		Artist:       artistName,
		Path:         path,
		URL:          songURL,
		ThumbnailURL: thumb,
	}
}
