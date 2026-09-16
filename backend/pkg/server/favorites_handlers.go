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
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
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
