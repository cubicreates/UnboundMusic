/*
 * Package: server
 * File: handlers_podcasts.go
 * Purpose: Dedicated HTTP handlers for Podcast discovery, podcast-only search, show browsing, and resume tracking.
 * Subsystem: Podcasts & Spoken Audio API
 */

package server

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/podcasts"
)

// handlePodcastDiscover serves curated podcast shows and episodes for a podcast category.
// Route: GET /api/v1/podcasts/discover?category=...&gl=...
func (s *Server) handlePodcastDiscover(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	category := strings.TrimSpace(r.URL.Query().Get("category"))
	country := strings.TrimSpace(r.URL.Query().Get("gl"))
	if country == "" {
		country = strings.TrimSpace(r.URL.Query().Get("country"))
	}

	feed, err := s.podcastEng.DiscoverPodcasts(r.Context(), category, country)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, feed)
}

// handlePodcastSearch searches exclusively within podcast episodes and shows.
// Route: GET /api/v1/podcasts/search?q=...&filter=... (also mounted at /api/v1/search/podcasts)
func (s *Server) handlePodcastSearch(w http.ResponseWriter, r *http.Request) {
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

	episodes, shows, err := s.podcastEng.SearchPodcastsWithFilter(r.Context(), query, filter)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"query":    query,
		"domain":   "podcasts",
		"filter":   filter,
		"episodes": episodes,
		"shows":    shows,
		"count":    len(episodes),
	})
}

// handlePodcastResume records or retrieves exact millisecond resume positions for podcast episodes.
// Route: POST /api/v1/podcasts/resume or GET /api/v1/podcasts/resume?episode_id=...
func (s *Server) handlePodcastResume(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		epID := strings.TrimSpace(r.URL.Query().Get("episode_id"))
		if epID == "" {
			writeError(w, http.StatusBadRequest, "episode_id is required")
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"episode_id":  epID,
			"position_ms": s.podcastEng.GetResumePosition(epID),
		})
	case http.MethodPost:
		type ResumeReq struct {
			EpisodeID    string `json:"episode_id"`
			PositionMs   int64  `json:"position_ms"`
			Title        string `json:"title,omitempty"`
			ShowTitle    string `json:"show_title,omitempty"`
			Author       string `json:"author,omitempty"`
			ThumbnailURL string `json:"thumbnail_url,omitempty"`
			DurationMs   int64  `json:"duration_ms,omitempty"`
		}
		var req ResumeReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.EpisodeID) == "" {
			writeError(w, http.StatusBadRequest, "valid 'episode_id' is required")
			return
		}
		s.podcastEng.SaveResumePosition(req.EpisodeID, req.PositionMs)
		if strings.TrimSpace(req.Title) != "" {
			s.podcastEng.RecordEpisodeMetadata(r.Context(), podcasts.PodcastEpisode{
				ID:           req.EpisodeID,
				ShowID:       req.ShowTitle,
				ShowTitle:    req.ShowTitle,
				Author:       req.Author,
				Title:        req.Title,
				DurationMs:   req.DurationMs,
				ThumbnailURL: req.ThumbnailURL,
				ResumePosMs:  req.PositionMs,
			})
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"status":      "SAVED",
			"episode_id":  req.EpisodeID,
			"position_ms": req.PositionMs,
		})
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// handlePodcastLibrary returns followed shows, saved episodes, and in-progress episodes.
// Route: GET /api/v1/podcasts/library
func (s *Server) handlePodcastLibrary(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	state := s.podcastEng.GetLibraryState(r.Context())
	writeJSON(w, http.StatusOK, state)
}

// handlePodcastFollow toggles follow state for a podcast show.
// Route: POST /api/v1/podcasts/library/follow
func (s *Server) handlePodcastFollow(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	var show podcasts.PodcastShow
	if err := json.NewDecoder(r.Body).Decode(&show); err != nil {
		writeError(w, http.StatusBadRequest, "invalid show payload")
		return
	}
	followed, err := s.podcastEng.ToggleFollowShow(r.Context(), show)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"followed": followed,
		"show_id":  show.ID,
	})
}

// handlePodcastSaveEpisode toggles saved/bookmarked state for a podcast episode.
// Route: POST /api/v1/podcasts/library/save
func (s *Server) handlePodcastSaveEpisode(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	var ep podcasts.PodcastEpisode
	if err := json.NewDecoder(r.Body).Decode(&ep); err != nil {
		writeError(w, http.StatusBadRequest, "invalid episode payload")
		return
	}
	saved, err := s.podcastEng.ToggleSaveEpisode(r.Context(), ep)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"saved":      saved,
		"episode_id": ep.ID,
	})
}
