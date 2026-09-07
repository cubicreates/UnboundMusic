/*
 * Package: main
 * File: handlers_account.go
 * Purpose: REST controller handlers for YouTube Music account authentication, sync, status, and like mutations.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers handling concurrent requests.
 */

package main

import (
	"encoding/json"
	"net/http"
)

type syncRequest struct {
	Cookie string `json:"cookie"`
}

type trackLikeRequest struct {
	VideoID string `json:"video_id"`
	Like    bool   `json:"like"`
}

// HandleAccountSync authenticates user YouTube session cookies, saves them, and initiates sync.
// POST /api/v1/account/sync
func (d *Daemon) HandleAccountSync(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req syncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Cookie == "" {
		d.writeError(w, http.StatusBadRequest, "Invalid request: missing cookie payload")
		return
	}

	if err := d.syncer.ConnectAccount(r.Context(), req.Cookie); err != nil {
		d.writeError(w, http.StatusUnauthorized, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "ok",
		"message": "Account connected successfully",
		"data":    d.syncer.GetStatus(),
	})
}

// HandleAccountStatus returns current YouTube connection status, account name, and synced track count.
// GET /api/v1/account/status
func (d *Daemon) HandleAccountStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	status := d.syncer.GetStatus()
	d.writeJSON(w, http.StatusOK, status)
}

// HandleAccountDisconnect purges stored session cookies and cached synced library items.
// POST /api/v1/account/disconnect
func (d *Daemon) HandleAccountDisconnect(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if err := d.syncer.DisconnectAccount(r.Context()); err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to disconnect account: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "ok",
		"message": "Account disconnected successfully",
	})
}

// HandleGetAccountLiked returns all currently synced Liked Music tracks.
// GET /api/v1/account/liked
func (d *Daemon) HandleGetAccountLiked(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	tracks := d.syncer.GetLikedTracks()
	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
		"count":  len(tracks),
	})
}

// HandleToggleTrackLike dispatches like/removelike mutations to YouTube Music.
// POST /api/v1/track/like
func (d *Daemon) HandleToggleTrackLike(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req trackLikeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.VideoID == "" {
		d.writeError(w, http.StatusBadRequest, "Invalid request: missing video_id")
		return
	}

	var err error
	if req.Like {
		err = d.ytClient.LikeTrack(r.Context(), req.VideoID)
	} else {
		err = d.ytClient.UnlikeTrack(r.Context(), req.VideoID)
	}

	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to toggle track like: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "ok",
		"video_id": req.VideoID,
		"liked":    req.Like,
	})
}
