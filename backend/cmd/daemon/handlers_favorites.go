/*
 * Package: main
 * File: handlers_favorites.go
 * Purpose: REST controllers for user favorited audio tracks across online & offline sources.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"encoding/json"
	"net/http"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// HandleFavoritesRouter routes /api/v1/favorites requests.
func (d *Daemon) HandleFavoritesRouter(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		d.handleGetFavorites(w, r)
	case http.MethodPost:
		d.handleToggleFavorite(w, r)
	default:
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (d *Daemon) handleGetFavorites(w http.ResponseWriter, r *http.Request) {
	if d.repo == nil {
		d.writeJSON(w, http.StatusOK, map[string]interface{}{"favorites": []models.UserFavorite{}, "count": 0})
		return
	}

	favs, err := d.repo.GetFavorites(r.Context())
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to query favorites: "+err.Error())
		return
	}
	if favs == nil {
		favs = []models.UserFavorite{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"favorites": favs,
		"count":     len(favs),
	})
}

func (d *Daemon) handleToggleFavorite(w http.ResponseWriter, r *http.Request) {
	var fav models.UserFavorite
	if err := json.NewDecoder(r.Body).Decode(&fav); err != nil || fav.TrackID == "" {
		d.writeError(w, http.StatusBadRequest, "invalid favorite payload: track_id required")
		return
	}

	isFav, err := d.repo.ToggleFavorite(r.Context(), &fav)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to toggle favorite: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "ok",
		"track_id": fav.TrackID,
		"favorite": isFav,
	})
}
