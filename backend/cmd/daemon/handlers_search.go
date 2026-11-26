/*
 * Package: main
 * File: handlers_search.go
 * Purpose: REST controllers for standard YouTube Music catalog search and the 4-stage intelligent search cascade.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"net/http"
	"strings"
)

// HandleSearch executes a standard YouTube Music search with optional category type.
// Route: GET /api/v1/search?q=...&type=...
func (d *Daemon) HandleSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		d.writeError(w, http.StatusBadRequest, "Missing search query parameter 'q'")
		return
	}

	searchType := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("type")))
	tracks, err := d.ytClient.SearchWithCategory(r.Context(), q, searchType)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":  q,
		"type":   searchType,
		"tracks": tracks,
		"count":  len(tracks),
	})
}

// HandleMusicSearch executes a strictly Music-only search (all, songs, artists, albums, playlists).
// Route: GET /api/v1/search/music?q=...&filter=...
func (d *Daemon) HandleMusicSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		d.writeError(w, http.StatusBadRequest, "Missing search query parameter 'q'")
		return
	}

	filter := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("filter")))
	if filter == "" {
		filter = "all"
	}

	tracks, err := d.ytClient.SearchMusic(r.Context(), q, filter)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":  q,
		"domain": "music",
		"filter": filter,
		"tracks": tracks,
		"count":  len(tracks),
	})
}

// HandleSearchCascade executes the 4-stage intelligent search cascade.
// Route: GET /api/v1/search/cascade?q=...
func (d *Daemon) HandleSearchCascade(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	q := r.URL.Query().Get("q")
	if q == "" {
		d.writeError(w, http.StatusBadRequest, "Missing search query parameter 'q'")
		return
	}

	result, err := d.ytClient.SearchCascade(r.Context(), q)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, result)
}
