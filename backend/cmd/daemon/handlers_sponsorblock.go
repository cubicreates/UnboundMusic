/*
 * Package: main
 * File: handlers_sponsorblock.go
 * Purpose: REST endpoint for querying SponsorBlock music_offtopic skip segments for seamless playback.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handler.
 */

package main

import (
	"net/http"
	"strings"
)

// HandleGetSkipSegments serves GET /api/v1/track/skip_segments?video_id=...
func (d *Daemon) HandleGetSkipSegments(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	q := r.URL.Query()
	videoID := strings.TrimSpace(q.Get("video_id"))
	if videoID == "" {
		videoID = strings.TrimSpace(q.Get("id"))
	}

	if videoID == "" {
		d.writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if d.sponsorClient == nil {
		d.writeError(w, http.StatusServiceUnavailable, "sponsorblock client not configured")
		return
	}

	segments, err := d.sponsorClient.GetMusicSkipSegments(r.Context(), videoID)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, segments)
}
