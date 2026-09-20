/*
 * Package: main
 * File: handlers_ai.go
 * Purpose: REST controller for single-shot natural language vibe searches powered by edge AI.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handler executing non-blocking on-device inference.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// VibeSearchRequestBody represents the incoming natural language query payload.
type VibeSearchRequestBody struct {
	Prompt   string `json:"prompt"`
	Region   string `json:"region"`
	Language string `json:"language"`
}

// HandleVibeSearch parses a vibe prompt into structured tags and resolves matching radio tracks.
// Route: POST /api/v1/search/vibe
func (d *Daemon) HandleVibeSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req VibeSearchRequestBody
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	prompt := strings.TrimSpace(req.Prompt)
	if prompt == "" {
		d.writeError(w, http.StatusBadRequest, "prompt cannot be empty")
		return
	}

	region := strings.ToUpper(strings.TrimSpace(req.Region))
	if region == "" {
		region = strings.ToUpper(strings.TrimSpace(r.Header.Get("X-Unbound-Region")))
	}
	if len(region) < 2 || len(region) > 3 {
		region = "IN"
	}
	language := strings.ToLower(strings.TrimSpace(req.Language))
	if language == "" {
		language = "en"
	}

	vibeResult, err := d.aiRunner.ParseVibeQuery(r.Context(), prompt, region)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to parse vibe prompt: "+err.Error())
		return
	}

	// Resolve matching radio tracks using search keywords or regional fallback
	var radioTracks []models.TrackItem

	if len(vibeResult.SearchKeywords) > 0 && d.ytClient != nil {
		queriesToSearch := vibeResult.SearchKeywords
		if len(queriesToSearch) > 3 {
			queriesToSearch = queriesToSearch[:3]
		}
		for _, searchQuery := range queriesToSearch {
			tracks, searchErr := d.ytClient.Search(r.Context(), searchQuery)
			if searchErr == nil && len(tracks) > 0 {
				for _, t := range tracks {
					radioTracks = append(radioTracks, models.TrackItem{
						ID:         t.ID,
						Title:      t.Title,
						Artist:     t.Artist,
						Artists:    []string{t.Artist},
						Album:      t.Album,
						DurationMs: t.DurationMs,
						Thumbnail:  t.ThumbnailURL,
						Source:     "youtube",
					})
				}
			}
			if len(radioTracks) >= 8 {
				break
			}
		}
	}

	// Fallback to explore charts if search yields zero tracks or is offline
	if len(radioTracks) == 0 && d.exploreEng != nil {
		fallback, _ := d.exploreEng.FetchRegionalCharts(r.Context(), region, language)
		if len(fallback) > 0 {
			if len(fallback) > 10 {
				radioTracks = fallback[:10]
			} else {
				radioTracks = fallback
			}
		}
	}

	if radioTracks == nil {
		radioTracks = []models.TrackItem{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"vibe_result":  vibeResult,
		"radio_tracks": radioTracks,
	})
}
