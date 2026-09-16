/*
 * Package: server
 * File: playlists_handlers.go
 * Purpose: REST controllers on Server for user custom playlists (create, read, update, delete, tracks, reorder).
 * Subsystem: Localhost Daemon & Embedded Server API
 * Concurrency: Thread-safe HTTP handlers.
 */

package server

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

// handlePlaylistsRouter dispatches /api/v1/playlists requests by method and sub-path.
func (s *Server) handlePlaylistsRouter(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/api/v1/playlists")
	path = strings.TrimPrefix(path, "/")

	if path == "" {
		switch r.Method {
		case http.MethodGet:
			s.handleGetPlaylists(w, r)
		case http.MethodPost:
			s.handleCreatePlaylist(w, r)
		default:
			writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	parts := strings.Split(path, "/")
	playlistID := parts[0]

	if len(parts) == 1 {
		switch r.Method {
		case http.MethodGet:
			s.handleGetPlaylistByID(w, r, playlistID)
		case http.MethodPut:
			s.handleUpdatePlaylist(w, r, playlistID)
		case http.MethodDelete:
			s.handleDeletePlaylist(w, r, playlistID)
		default:
			writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		}
		return
	}

	subResource := parts[1]
	switch subResource {
	case "tracks":
		if len(parts) == 2 && r.Method == http.MethodPost {
			s.handleAddPlaylistTrack(w, r, playlistID)
			return
		}
		if len(parts) == 3 && r.Method == http.MethodDelete {
			pos, err := strconv.Atoi(parts[2])
			if err != nil {
				writeError(w, http.StatusBadRequest, "invalid track position")
				return
			}
			s.handleRemovePlaylistTrack(w, r, playlistID, pos)
			return
		}
	case "reorder":
		if r.Method == http.MethodPut || r.Method == http.MethodPost {
			s.handleReorderPlaylist(w, r, playlistID)
			return
		}
	}

	writeError(w, http.StatusNotFound, "route not found")
}

func (s *Server) handleGetPlaylists(w http.ResponseWriter, r *http.Request) {
	if s.repo == nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"playlists": []models.CustomPlaylist{},
			"count":     0,
		})
		return
	}

	playlists, err := s.repo.GetCustomPlaylists(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to query playlists: "+err.Error())
		return
	}
	if playlists == nil {
		playlists = []models.CustomPlaylist{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"playlists": playlists,
		"count":     len(playlists),
	})
}

func (s *Server) handleGetPlaylistByID(w http.ResponseWriter, r *http.Request, id string) {
	if s.repo == nil {
		writeError(w, http.StatusNotFound, "playlist not found")
		return
	}

	p, err := s.repo.GetCustomPlaylistByID(r.Context(), id)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to query playlist: "+err.Error())
		return
	}
	if p == nil {
		writeError(w, http.StatusNotFound, "playlist not found")
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"playlist": p,
	})
}

func (s *Server) handleCreatePlaylist(w http.ResponseWriter, r *http.Request) {
	var req createPlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if req.Title == "" {
		writeError(w, http.StatusBadRequest, "title is required")
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

	if err := s.repo.CreateCustomPlaylist(r.Context(), playlist); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to create playlist: "+err.Error())
		return
	}

	created, _ := s.repo.GetCustomPlaylistByID(r.Context(), id)
	writeJSON(w, http.StatusCreated, map[string]interface{}{
		"status":   "created",
		"playlist": created,
	})
}

func (s *Server) handleUpdatePlaylist(w http.ResponseWriter, r *http.Request, id string) {
	var req updatePlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if req.Title == "" {
		writeError(w, http.StatusBadRequest, "title cannot be empty")
		return
	}

	if err := s.repo.UpdateCustomPlaylistDetails(r.Context(), id, req.Title, req.Description, req.CoverURL); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to update playlist: "+err.Error())
		return
	}

	updated, _ := s.repo.GetCustomPlaylistByID(r.Context(), id)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "updated",
		"playlist": updated,
	})
}

func (s *Server) handleDeletePlaylist(w http.ResponseWriter, r *http.Request, id string) {
	if err := s.repo.DeleteCustomPlaylist(r.Context(), id); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to delete playlist: "+err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "deleted",
		"deleted": true,
	})
}

func (s *Server) handleAddPlaylistTrack(w http.ResponseWriter, r *http.Request, playlistID string) {
	var track models.PlaylistTrack
	if err := json.NewDecoder(r.Body).Decode(&track); err != nil {
		writeError(w, http.StatusBadRequest, "invalid track payload: "+err.Error())
		return
	}

	if track.ID == "" && track.Title == "" {
		writeError(w, http.StatusBadRequest, "track_id or title is required")
		return
	}

	if err := s.repo.AddTrackToCustomPlaylist(r.Context(), playlistID, &track); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to add track: "+err.Error())
		return
	}

	updated, _ := s.repo.GetCustomPlaylistByID(r.Context(), playlistID)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "track_added",
		"playlist": updated,
	})
}

func (s *Server) handleRemovePlaylistTrack(w http.ResponseWriter, r *http.Request, playlistID string, position int) {
	if err := s.repo.RemoveTrackFromCustomPlaylist(r.Context(), playlistID, position); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to remove track: "+err.Error())
		return
	}

	updated, _ := s.repo.GetCustomPlaylistByID(r.Context(), playlistID)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "track_removed",
		"playlist": updated,
	})
}

func (s *Server) handleReorderPlaylist(w http.ResponseWriter, r *http.Request, playlistID string) {
	var req reorderPlaylistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if err := s.repo.ReorderCustomPlaylistTracks(r.Context(), playlistID, req.FromIndex, req.ToIndex); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to reorder playlist tracks: "+err.Error())
		return
	}

	updated, _ := s.repo.GetCustomPlaylistByID(r.Context(), playlistID)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "reordered",
		"playlist": updated,
	})
}
