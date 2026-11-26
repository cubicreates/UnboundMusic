/*
 * Package: main
 * File: handlers_lyrics.go
 * Purpose: REST endpoint for 3-tier synchronized lyrics retrieval with phonetic Romanization and SQLite caching.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handler.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
)

// HandleLyricsRecognize serves POST/GET /api/v1/recognize/lyrics returning multiple ranked candidate songs.
func (d *Daemon) HandleLyricsRecognize(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var query, mode string
	limit := 8

	if r.Method == http.MethodGet {
		query = strings.TrimSpace(r.URL.Query().Get("q"))
		if query == "" {
			query = strings.TrimSpace(r.URL.Query().Get("query"))
		}
		mode = strings.TrimSpace(r.URL.Query().Get("mode"))
		if lStr := r.URL.Query().Get("limit"); lStr != "" {
			if l, err := strconv.Atoi(lStr); err == nil && l > 0 {
				limit = l
			}
		}
	} else {
		type LyricsRecReq struct {
			Query string `json:"query"`
			Mode  string `json:"mode"`
			Limit int    `json:"limit"`
		}
		var req LyricsRecReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			d.writeError(w, http.StatusBadRequest, "invalid request payload: "+err.Error())
			return
		}
		query = strings.TrimSpace(req.Query)
		mode = strings.TrimSpace(req.Mode)
		if req.Limit > 0 {
			limit = req.Limit
		}
	}

	if query == "" {
		d.writeError(w, http.StatusBadRequest, "query parameter is required")
		return
	}

	if d.lyricsRec == nil {
		d.writeError(w, http.StatusServiceUnavailable, "lyrics recognizer not configured")
		return
	}

	resp, err := d.lyricsRec.Recognize(r.Context(), query, mode, limit)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, resp)
}

// HandleGetLyrics serves GET /api/v1/lyrics?track_id=...&title=...&artist=...&duration=...
func (d *Daemon) HandleGetLyrics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	q := r.URL.Query()
	trackID := strings.TrimSpace(q.Get("track_id"))
	if trackID == "" {
		trackID = strings.TrimSpace(q.Get("id"))
	}
	title := strings.TrimSpace(q.Get("title"))
	artist := strings.TrimSpace(q.Get("artist"))
	durationStr := strings.TrimSpace(q.Get("duration"))

	durationSec := 0
	if durationStr != "" {
		if val, err := strconv.Atoi(durationStr); err == nil {
			durationSec = val
		}
	}

	if trackID == "" && title == "" {
		d.writeError(w, http.StatusBadRequest, "track_id or title is required")
		return
	}

	if d.lyricsAgg == nil {
		d.writeError(w, http.StatusServiceUnavailable, "lyrics aggregator not configured")
		return
	}

	payload, err := d.lyricsAgg.GetLyrics(r.Context(), trackID, title, artist, durationSec)
	if err != nil {
		d.writeError(w, http.StatusNotFound, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, payload)
}
