package server

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

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

// handleMusicSearch handles strictly Music-only catalog search with optional subtype filter (all, songs, artists, albums, playlists).
// Route: GET /api/v1/search/music?q=...&filter=...
func (s *Server) handleMusicSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	query := strings.TrimSpace(r.URL.Query().Get("q"))
	if query == "" {
		writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}

	filter := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("filter")))
	if filter == "" {
		filter = "all"
	}
	if r.URL.Query().Get("record") == "1" {
		s.recordPlaybackSearchSafe("music", query)
	}

	cacheKey := fmt.Sprintf("search:v1:music:%s:%s", filter, strings.ToLower(query))
	if s.repo != nil {
		if cachedBytes, ok := s.repo.GetCachedJSON(r.Context(), cacheKey); ok {
			var cachedTracks []models.Track
			if err := json.Unmarshal(cachedBytes, &cachedTracks); err == nil && len(cachedTracks) > 0 {
				writeJSON(w, http.StatusOK, map[string]interface{}{
					"query":  query,
					"domain": "music",
					"filter": filter,
					"cached": true,
					"count":  len(cachedTracks),
					"tracks": cachedTracks,
				})
				return
			}
		}
	}

	tracks, err := s.ytClient.SearchMusic(r.Context(), query, filter)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	SafeGo("music_search_track_cacher", func() {
		for _, t := range tracks {
			_ = s.repo.SaveTrack(context.Background(), &t)
		}
		if s.repo != nil && len(tracks) > 0 {
			if encoded, mErr := json.Marshal(tracks); mErr == nil {
				s.repo.SetCachedJSON(context.Background(), cacheKey, encoded, 30*time.Minute)
			}
		}
	})

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":  query,
		"domain": "music",
		"filter": filter,
		"cached": false,
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

// handleUnifiedSearch offloads Music (catalog/vibe/lyrics fallback) and Podcast search
// behind one backend endpoint for the unified Search screen.
// Route: GET /api/v1/search/unified?domain=music|podcasts&q=...&filter=...&gl=...&ai=...
func (s *Server) handleUnifiedSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	query := strings.TrimSpace(r.URL.Query().Get("q"))
	if query == "" {
		writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}

	domain := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("domain")))
	filter := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("filter")))
	if filter == "" {
		filter = "all"
	}
	if r.URL.Query().Get("record") == "1" {
		s.recordPlaybackSearchSafe(domain, query)
	}

	if domain == "podcasts" || domain == "podcast" {
		cacheKey := fmt.Sprintf("search:v1:podcasts:%s:%s", filter, strings.ToLower(query))
		if s.repo != nil {
			if cachedBytes, ok := s.repo.GetCachedJSON(r.Context(), cacheKey); ok {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write(cachedBytes)
				return
			}
		}
		episodes, shows, err := s.podcastEng.SearchPodcastsWithFilter(r.Context(), query, filter)
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		payload := map[string]interface{}{
			"domain":   "podcasts",
			"query":    query,
			"filter":   filter,
			"episodes": episodes,
			"shows":    shows,
			"count":    len(episodes),
		}
		if s.repo != nil && (len(episodes) > 0 || len(shows) > 0) {
			if encoded, mErr := json.Marshal(payload); mErr == nil {
				s.repo.SetCachedJSON(context.Background(), cacheKey, encoded, 30*time.Minute)
			}
		}
		writeJSON(w, http.StatusOK, payload)
		return
	}

	musicCacheKey := fmt.Sprintf("search:v1:unified_music:%s:%s", filter, strings.ToLower(query))
	if s.repo != nil {
		if cachedBytes, ok := s.repo.GetCachedJSON(r.Context(), musicCacheKey); ok {
			var cachedTracks []models.Track
			if err := json.Unmarshal(cachedBytes, &cachedTracks); err == nil && len(cachedTracks) > 0 {
				writeJSON(w, http.StatusOK, map[string]interface{}{
					"domain": "music",
					"query":  query,
					"filter": filter,
					"cached": true,
					"count":  len(cachedTracks),
					"tracks": cachedTracks,
				})
				return
			}
		}
	}

	// Music domain: strictly filter out podcasts & enrich sparse results on backend
	isPodcastTrack := func(t models.Track) bool {
		return strings.EqualFold(t.ItemType, "podcast") ||
			strings.HasPrefix(t.ID, "MPSP") ||
			strings.HasPrefix(t.BrowseID, "MPSP")
	}

	var results []models.Track
	if s.ytClient != nil {
		if fetched, err := s.ytClient.SearchMusic(r.Context(), query, filter); err == nil {
			for _, t := range fetched {
				if !isPodcastTrack(t) {
					results = append(results, t)
				}
			}
		}
		if len(results) == 0 {
			if fallback, err := s.ytClient.SearchWithCategory(r.Context(), query, "music"); err == nil {
				for _, t := range fallback {
					if !isPodcastTrack(t) {
						results = append(results, t)
					}
				}
			}
		}
	}

	if results == nil {
		results = []models.Track{}
	}

	SafeGo("unified_search_track_cacher", func() {
		for _, t := range results {
			_ = s.repo.SaveTrack(context.Background(), &t)
		}
		if s.repo != nil && len(results) > 0 {
			if encoded, mErr := json.Marshal(results); mErr == nil {
				s.repo.SetCachedJSON(context.Background(), musicCacheKey, encoded, 30*time.Minute)
			}
		}
	})

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"domain": "music",
		"query":  query,
		"filter": filter,
		"cached": false,
		"count":  len(results),
		"tracks": results,
	})
}

// handleSearchCascade executes the 4-stage intelligent search cascade.
// Route: GET /api/v1/search/cascade?q=...
func (s *Server) handleSearchCascade(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}
	result, err := s.ytClient.SearchCascade(r.Context(), q)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, result)
}

// handleSearchHistory manages persistent SQLite search history ("Previous Searches") per domain.
// Route: GET|POST|DELETE /api/v1/search/history
func (s *Server) handleSearchHistory(w http.ResponseWriter, r *http.Request) {
	if s.repo == nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{"history": []string{}, "count": 0})
		return
	}

	switch r.Method {
	case http.MethodGet:
		domain := r.URL.Query().Get("domain")
		prefix := r.URL.Query().Get("q")
		limit := 20
		if lStr := r.URL.Query().Get("limit"); lStr != "" {
			if parsed, err := strconv.Atoi(lStr); err == nil && parsed > 0 && parsed <= 50 {
				limit = parsed
			}
		}
		history, err := s.repo.GetSearchHistory(r.Context(), domain, prefix, limit)
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		if (domain == "" || strings.EqualFold(domain, "music")) &&
			s.accountSync != nil && s.accountSync.GetStatus().Connected && s.ytClient != nil {
			subCtx, cancel := context.WithTimeout(r.Context(), 3500*time.Millisecond)
			ytHistory, ytErr := s.ytClient.FetchAccountSearchHistory(subCtx, prefix)
			cancel()
			if ytErr == nil && len(ytHistory) > 0 {
				seen := make(map[string]bool, len(history)+len(ytHistory))
				merged := make([]string, 0, limit)
				for _, h := range history {
					k := strings.ToLower(strings.TrimSpace(h))
					if k != "" && !seen[k] {
						seen[k] = true
						merged = append(merged, h)
					}
				}
				for _, yh := range ytHistory {
					if len(merged) >= limit {
						break
					}
					k := strings.ToLower(strings.TrimSpace(yh))
					if k != "" && !seen[k] {
						seen[k] = true
						merged = append(merged, yh)
					}
				}
				history = merged
			}
		}
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"domain":  domain,
			"history": history,
			"count":   len(history),
		})
	case http.MethodPost:
		var req struct {
			Domain string `json:"domain"`
			Query  string `json:"query"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.Query) == "" {
			writeError(w, http.StatusBadRequest, "query is required")
			return
		}
		if err := s.repo.RecordSearchQuery(r.Context(), req.Domain, req.Query); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		history, _ := s.repo.GetSearchHistory(r.Context(), req.Domain, "", 20)
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"status":  "recorded",
			"history": history,
			"count":   len(history),
		})
	case http.MethodDelete:
		domain := r.URL.Query().Get("domain")
		query := strings.TrimSpace(r.URL.Query().Get("q"))
		if query != "" {
			_ = s.repo.DeleteSearchHistoryItem(r.Context(), domain, query)
		} else {
			_ = s.repo.ClearSearchHistory(r.Context(), domain)
		}
		history, _ := s.repo.GetSearchHistory(r.Context(), domain, "", 20)
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"status":  "deleted",
			"history": history,
			"count":   len(history),
		})
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// handleSearchHistoryDelete deletes a single item or clears all search history for a domain.
// Route: POST /api/v1/search/history/delete
func (s *Server) handleSearchHistoryDelete(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if s.repo == nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{"history": []string{}, "count": 0})
		return
	}
	var req struct {
		Domain   string `json:"domain"`
		Query    string `json:"query"`
		ClearAll bool   `json:"clear_all"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}
	if req.ClearAll || strings.TrimSpace(req.Query) == "" {
		_ = s.repo.ClearSearchHistory(r.Context(), req.Domain)
	} else {
		_ = s.repo.DeleteSearchHistoryItem(r.Context(), req.Domain, req.Query)
	}
	history, _ := s.repo.GetSearchHistory(r.Context(), req.Domain, "", 20)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "ok",
		"history": history,
		"count":   len(history),
	})
}
