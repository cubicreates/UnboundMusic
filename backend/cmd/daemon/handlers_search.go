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
)

// HandleSearch executes a standard YouTube Music search.
// Route: GET /api/v1/search?q=...
func (d *Daemon) HandleSearch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	q := r.URL.Query().Get("q")
	if q == "" {
		d.writeError(w, http.StatusBadRequest, "Missing search query parameter 'q'")
		return
	}

	tracks, err := d.ytClient.Search(r.Context(), q)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
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
