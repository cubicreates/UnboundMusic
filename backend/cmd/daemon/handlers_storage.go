/*
 * Package: main
 * File: handlers_storage.go
 * Purpose: REST controllers for triggering POSIX filesystem crawls and retrieving indexed local tracks.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers with 503 write-lock protection against concurrent scans.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/storage"
)

// ScanRequestBody represents the JSON payload specifying directory roots to crawl.
type ScanRequestBody struct {
	Paths []string `json:"paths"`
}

// HandleTriggerStorageScan triggers the POSIX crawler and magic byte analyzer across requested paths.
// Route: POST /api/v1/storage/scan
func (d *Daemon) HandleTriggerStorageScan(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req ScanRequestBody
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}

	if len(req.Paths) == 0 {
		d.writeError(w, http.StatusBadRequest, "paths array must not be empty")
		return
	}

	// Protect against concurrent re-indexing collisions
	if !d.tryLockScan() {
		d.writeError(w, http.StatusServiceUnavailable, "storage scanner is already active")
		return
	}
	defer d.unlockScan()

	start := time.Now()
	totalScanned := 0
	totalAudio := 0
	totalNew := 0
	totalUnchanged := 0

	for _, p := range req.Paths {
		trimmed := strings.TrimSpace(p)
		if trimmed == "" {
			continue
		}

		// Infer source folder tag from path string
		sourceFolder := "music"
		lower := strings.ToLower(trimmed)
		if strings.Contains(lower, "whatsapp") {
			sourceFolder = "whatsapp"
		} else if strings.Contains(lower, "telegram") {
			sourceFolder = "telegram"
		} else if strings.Contains(lower, "download") {
			sourceFolder = "downloads"
		}

		report, err := storage.ScanDirectory(r.Context(), d.repo, trimmed, sourceFolder)
		if err != nil {
			// Skip unreadable path or log error cleanly without failing other paths
			continue
		}

		if report != nil {
			totalScanned += report.ScannedFiles
			totalAudio += report.AudioFilesFound
			totalNew += report.NewTracksIndexed
			totalUnchanged += report.UnchangedTracks
		}
	}

	elapsedMs := time.Since(start).Milliseconds()

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":             "completed",
		"scanned_files":      totalScanned,
		"audio_files_found":  totalAudio,
		"new_tracks_indexed": totalNew,
		"unchanged_tracks":   totalUnchanged,
		"elapsed_ms":         elapsedMs,
	})
}

// HandleGetLocalTracks retrieves indexed physical tracks from the local SQLite vault.
// Route: GET /api/v1/storage/tracks?source=whatsapp
func (d *Daemon) HandleGetLocalTracks(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	if d.repo == nil {
		d.writeJSON(w, http.StatusOK, map[string]interface{}{
			"tracks": []models.LocalTrack{},
		})
		return
	}

	source := strings.TrimSpace(strings.ToLower(r.URL.Query().Get("source")))

	var tracks []models.LocalTrack
	var err error

	if source == "" || source == "all" {
		tracks, err = d.repo.GetAllLocalTracks(r.Context())
	} else {
		tracks, err = d.repo.GetLocalTracksBySource(r.Context(), source)
	}

	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed to query local tracks: "+err.Error())
		return
	}

	if tracks == nil {
		tracks = []models.LocalTrack{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
	})
}
