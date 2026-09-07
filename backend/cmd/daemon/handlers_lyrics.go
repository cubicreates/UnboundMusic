/*
 * Package: main
 * File: handlers_lyrics.go
 * Purpose: REST endpoint for 3-tier synchronized lyrics retrieval with phonetic Romanization and SQLite caching.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handler.
 */

package main

import (
	"net/http"
	"strconv"
	"strings"
)

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
