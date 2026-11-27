/*
 * Package: server
 * File: favorites_handlers.go
 * Purpose: REST controllers on Server for user favorite tracks (toggle and retrieve).
 * Subsystem: Localhost Daemon & Embedded Server API
 * Concurrency: Thread-safe HTTP handlers.
 */

package server

import (
	"encoding/json"
	"log"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// handleFavoritesRouter dispatches /api/v1/favorites requests.
func (s *Server) handleFavoritesRouter(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/api/v1/favorites")
	path = strings.TrimPrefix(path, "/")

	if path == "" {
		switch r.Method {
		case http.MethodGet:
			s.handleGetFavorites(w, r)
		default:
			writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	if path == "toggle" && r.Method == http.MethodPost {
		s.handleToggleFavorite(w, r)
		return
	}

	writeError(w, http.StatusNotFound, "route not found")
}

func (s *Server) handleGetFavorites(w http.ResponseWriter, r *http.Request) {
	if s.repo == nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"favorites": []models.UserFavorite{},
			"count":     0,
		})
		return
	}

	favs, err := s.repo.GetFavorites(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to query favorites: "+err.Error())
		return
	}
	if favs == nil {
		favs = []models.UserFavorite{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"favorites": favs,
		"count":     len(favs),
	})
}

func (s *Server) handleToggleFavorite(w http.ResponseWriter, r *http.Request) {
	var fav models.UserFavorite
	if err := json.NewDecoder(r.Body).Decode(&fav); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if fav.TrackID == "" {
		writeError(w, http.StatusBadRequest, "track_id is required")
		return
	}

	isFav, err := s.repo.ToggleFavorite(r.Context(), &fav)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to toggle favorite: "+err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "ok",
		"track_id": fav.TrackID,
		"favorite": isFav,
	})
}

// handleTrackLike handles POST /api/v1/track/like to sync likes/unlikes to YouTube when signed in,
// while gracefully acknowledging local/guest favorites.
func (s *Server) handleTrackLike(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	type trackLikeReq struct {
		VideoID string `json:"video_id"`
		TrackID string `json:"track_id"`
		Like    bool   `json:"like"`
	}
	var req trackLikeReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	vid := strings.TrimSpace(req.VideoID)
	if vid == "" {
		vid = strings.TrimSpace(req.TrackID)
	}
	if vid == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if !ytmusic.IsValidYouTubeVideoID(vid) {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"status":         "local_only",
			"video_id":       vid,
			"liked":          req.Like,
			"synced_youtube": false,
		})
		return
	}

	if s.ytClient == nil || !s.ytClient.HasCredentials() {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"status":         "guest_local",
			"video_id":       vid,
			"liked":          req.Like,
			"synced_youtube": false,
		})
		return
	}

	var err error
	if req.Like {
		err = s.ytClient.LikeTrack(r.Context(), vid)
	} else {
		err = s.ytClient.UnlikeTrack(r.Context(), vid)
	}
	if err != nil {
		log.Printf("[Like] YouTube like mutation failed for %s (like=%v): %v", vid, req.Like, err)
		writeError(w, http.StatusBadGateway, "YouTube like sync failed: "+err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":         "ok",
		"video_id":       vid,
		"liked":          req.Like,
		"synced_youtube": true,
	})
}
