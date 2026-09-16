/*
 * Package: main
 * File: handlers_playlists.go
 * Purpose: REST controllers for user custom playlists (create, read, update, delete, tracks, reorder).
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/google/uuid"
)

type createPlaylistRequest struct {
	ID          string                 `json:"id"`
	Title       string                 `json:"title"`
	Description string                 `json:"description"`
	CoverURL    string                 `json:"cover_url"`
	Tracks      []models.PlaylistTrack `json:"tracks"`
}

type updatePlaylistRequest struct {
	Title       string `json:"title"`
	Description string `json:"description"`
	CoverURL    string `json:"cover_url"`
}

type reorderPlaylistRequest struct {
	FromIndex int `json:"from_index"`
	ToIndex   int `json:"to_index"`
}

// HandlePlaylistsRouter dispatches /api/v1/playlists requests by method and sub-path.
func (d *Daemon) HandlePlaylistsRouter(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/api/v1/playlists")
	path = strings.TrimPrefix(path, "/")

	if path == "" {
		switch r.Method {
		case http.MethodGet:
			d.handleGetPlaylists(w, r)
		case http.MethodPost:
			d.handleCreatePlaylist(w, r)
		default:
			d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	parts := strings.Split(path, "/")
	playlistID := parts[0]

	if len(parts) == 1 {
		switch r.Method {
		case http.MethodGet:
			d.handleGetPlaylistByID(w, r, playlistID)
		case http.MethodPut:
			d.handleUpdatePlaylist(w, r, playlistID)
		case http.MethodDelete:
			d.handleDeletePlaylist(w, r, playlistID)
		default:
			d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	subResource := parts[1]
	switch subResource {
	case "tracks":
		if len(parts) == 2 && r.Method == http.MethodPost {
			d.handleAddPlaylistTrack(w, r, playlistID)
			return
		}
		if len(parts) == 3 && r.Method == http.MethodDelete {
			pos, err := strconv.Atoi(parts[2])
			if err != nil {
				d.writeError(w, http.StatusBadRequest, "invalid track position")
				return
			}
			d.handleRemovePlaylistTrack(w, r, playlistID, pos)
			return
		}
	case "reorder":
		if r.Method == http.MethodPut || r.Method == http.MethodPost {
			d.handleReorderPlaylist(w, r, playlistID)
			return
		}
	}

	d.writeError(w, http.StatusNotFound, "route not found")
}

func (d *Daemon) handleGetPlaylists(w http.ResponseWriter, r *http.Request) {
	if d.repo == nil {
		d.writeJSON(w, http.StatusOK, map[string]interface{}{"playlists": []models.CustomPlaylist{}})
		return
	}

	playlists, err := d.repo.GetCustomPlaylists(r.Context())
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to query playlists: "+err.Error())
		return
	}
	if playlists == nil {
		playlists = []models.CustomPlaylist{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"playlists": playlists,
		"count":     len(playlists),
	})
}

func (d *Daemon) handleGetPlaylistByID(w http.ResponseWriter, r *http.Request, id string) {
	if d.repo == nil {
		d.writeError(w, http.StatusNotFound, "playlist not found")
		return
	}

	p, err := d.repo.GetCustomPlaylistByID(r.Context(), id)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to query playlist: "+err.Error())
		return
	}
	if p == nil {
		d.writeError(w, http.StatusNotFound, "playlist not found")
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"playlist": p,
	})
}

func (d *Daemon) handleCreatePlaylist(w http.ResponseWriter, r *http.Request) {
	var req createPlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if req.Title == "" {
		d.writeError(w, http.StatusBadRequest, "title is required")
		return
	}

	id := req.ID
	if id == "" {
		id = "pl_" + uuid.New().String()
	}

	playlist := &models.CustomPlaylist{
		ID:          id,
		Title:       req.Title,
		Description: req.Description,
		CoverURL:    req.CoverURL,
		Tracks:      req.Tracks,
	}

	if err := d.repo.CreateCustomPlaylist(r.Context(), playlist); err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to create playlist: "+err.Error())
		return
	}

	created, _ := d.repo.GetCustomPlaylistByID(r.Context(), id)
	d.writeJSON(w, http.StatusCreated, map[string]interface{}{
		"status":   "created",
		"playlist": created,
	})
}

func (d *Daemon) handleUpdatePlaylist(w http.ResponseWriter, r *http.Request, id string) {
	var req updatePlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if err := d.repo.UpdateCustomPlaylistDetails(r.Context(), id, req.Title, req.Description, req.CoverURL); err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to update playlist: "+err.Error())
		return
	}

	updated, _ := d.repo.GetCustomPlaylistByID(r.Context(), id)
	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "updated",
		"playlist": updated,
	})
}

func (d *Daemon) handleDeletePlaylist(w http.ResponseWriter, r *http.Request, id string) {
	if err := d.repo.DeleteCustomPlaylist(r.Context(), id); err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to delete playlist: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "deleted",
		"id":      id,
		"message": "playlist deleted successfully",
	})
}

func (d *Daemon) handleAddPlaylistTrack(w http.ResponseWriter, r *http.Request, id string) {
	var track models.PlaylistTrack
	if err := json.NewDecoder(r.Body).Decode(&track); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid track payload: "+err.Error())
		return
	}

	if track.ID == "" {
		d.writeError(w, http.StatusBadRequest, "track id is required")
		return
	}

	if err := d.repo.AddTrackToCustomPlaylist(r.Context(), id, &track); err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to add track: "+err.Error())
		return
	}

	updated, _ := d.repo.GetCustomPlaylistByID(r.Context(), id)
	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "track_added",
		"playlist": updated,
	})
}

func (d *Daemon) handleRemovePlaylistTrack(w http.ResponseWriter, r *http.Request, id string, pos int) {
	if err := d.repo.RemoveTrackFromCustomPlaylist(r.Context(), id, pos); err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to remove track: "+err.Error())
		return
	}

	updated, _ := d.repo.GetCustomPlaylistByID(r.Context(), id)
	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "track_removed",
		"playlist": updated,
	})
}

func (d *Daemon) handleReorderPlaylist(w http.ResponseWriter, r *http.Request, id string) {
	var req reorderPlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid reorder payload: "+err.Error())
		return
	}

	if err := d.repo.ReorderCustomPlaylistTracks(r.Context(), id, req.FromIndex, req.ToIndex); err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to reorder tracks: "+err.Error())
		return
	}

	updated, _ := d.repo.GetCustomPlaylistByID(r.Context(), id)
	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "tracks_reordered",
		"playlist": updated,
	})
}
