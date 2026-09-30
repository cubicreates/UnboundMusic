package server

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// handleSearch handles catalog search queries with optional category filtering (all, music, podcast).
func (s *Server) handleSearch(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if strings.TrimSpace(query) == "" {
		writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}

	searchType := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("type")))
	tracks, err := s.ytClient.SearchWithCategory(r.Context(), query, searchType)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	SafeGo("search_track_cacher", func() {
		for _, t := range tracks {
			_ = s.repo.SaveTrack(context.Background(), &t)
		}
	})

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":  query,
		"type":   searchType,
		"count":  len(tracks),
		"tracks": tracks,
	})
}

// handleVibeSearch parses a natural language vibe prompt and resolves matching radio tracks.
// Route: POST /api/v1/search/vibe
func (s *Server) handleVibeSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	type VibeSearchRequestBody struct {
		Prompt   string `json:"prompt"`
		Region   string `json:"region"`
		Language string `json:"language"`
	}

	var req VibeSearchRequestBody
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	prompt := strings.TrimSpace(req.Prompt)
	if prompt == "" {
		writeError(w, http.StatusBadRequest, "prompt cannot be empty")
		return
	}

	region := strings.ToUpper(strings.TrimSpace(req.Region))
	if region == "" {
		region = strings.ToUpper(strings.TrimSpace(r.Header.Get("X-Unbound-Region")))
	}
	if region == "" {
		region = strings.ToUpper(strings.TrimSpace(r.Header.Get("CF-IPCountry")))
	}
	if region == "" {
		region = strings.ToUpper(strings.TrimSpace(r.Header.Get("X-Country-Code")))
	}
	// Sanitize to valid 2-letter or 3-letter country code
	if len(region) < 2 || len(region) > 3 {
		region = "IN"
	}

	vibeResult, err := s.aiRunner.ParseVibeQuery(r.Context(), prompt, region)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to parse vibe prompt: "+err.Error())
		return
	}

	var radioTracks []models.TrackItem
	seenIDs := make(map[string]bool)

	if len(vibeResult.SearchKeywords) > 0 && s.ytClient != nil {
		queriesToSearch := vibeResult.SearchKeywords
		if len(queriesToSearch) > 3 {
			queriesToSearch = queriesToSearch[:3]
		}
		for _, searchQuery := range queriesToSearch {
			tracks, searchErr := s.ytClient.SearchWithCategory(r.Context(), searchQuery, "song")
			if searchErr == nil && len(tracks) > 0 {
				for _, t := range tracks {
					// Strictly ensure only songs are included (reject albums, artists, playlists, browse IDs)
					if t.ItemType == "album" || t.ItemType == "artist" || t.ItemType == "playlist" ||
						strings.HasPrefix(t.ID, "UC") || strings.HasPrefix(t.ID, "MPREb_") ||
						strings.HasPrefix(t.ID, "VL") || strings.HasPrefix(t.ID, "PL") ||
						strings.HasPrefix(t.BrowseID, "UC") || strings.HasPrefix(t.BrowseID, "MPREb_") {
						continue
					}
					if !seenIDs[t.ID] {
						seenIDs[t.ID] = true
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
			}
			if len(radioTracks) >= 8 {
				break
			}
		}
	}

	// Fallback to regional explore charts if online search yields zero tracks or is offline
	if len(radioTracks) == 0 && s.ytExploreEng != nil {
		lang := strings.ToLower(strings.TrimSpace(req.Language))
		if lang == "" {
			lang = "en"
		}
		fallback, _ := s.ytExploreEng.FetchRegionalCharts(r.Context(), region, lang)
		if len(fallback) > 0 {
			if len(fallback) > 10 {
				radioTracks = fallback[:10]
			} else {
				radioTracks = fallback
			}
		}
	} else if len(radioTracks) == 0 && s.exploreEng != nil {
		charts, chartErr := s.exploreEng.GetTopCharts(r.Context(), region)
		if chartErr == nil && len(charts) > 0 {
			limit := 10
			if len(charts) < limit {
				limit = len(charts)
			}
			for i := 0; i < limit; i++ {
				c := charts[i]
				radioTracks = append(radioTracks, models.TrackItem{
					ID:        c.TrackID,
					Title:     c.Title,
					Artist:    c.Artist,
					Thumbnail: c.ThumbnailURL,
					Source:    "youtube",
				})
			}
		}
	}

	if radioTracks == nil {
		radioTracks = []models.TrackItem{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"vibe_result":  vibeResult,
		"radio_tracks": radioTracks,
	})
}
