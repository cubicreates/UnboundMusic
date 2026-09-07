/*
 * Package: main
 * File: handlers_genres.go
 * Purpose: REST controllers for Moods & Genres discovery boards and Genre Detail curated playlist shelves.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"net/http"

	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// HandleGetMoodsAndGenres returns structured genre and mood discovery boards.
// Route: GET /api/v1/explore/moods_genres?gl=US&hl=en
func (d *Daemon) HandleGetMoodsAndGenres(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	gl := r.URL.Query().Get("gl")
	if gl == "" {
		gl = "US"
	}
	hl := r.URL.Query().Get("hl")
	if hl == "" {
		hl = "en"
	}

	sections, err := d.exploreEng.FetchMoodsAndGenres(r.Context(), gl, hl)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to retrieve moods and genres: "+err.Error())
		return
	}

	if sections == nil {
		sections = []ytmusic.GenreSection{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"sections": sections,
	})
}

// HandleGetGenreDetail returns curated playlist shelves for a given category token.
// Route: GET /api/v1/explore/genre_detail?params=...&name=Hip-Hop&gl=US&hl=en
func (d *Daemon) HandleGetGenreDetail(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	params := r.URL.Query().Get("params")
	name := r.URL.Query().Get("name")
	if name == "" {
		name = "Genre"
	}

	gl := r.URL.Query().Get("gl")
	if gl == "" {
		gl = "US"
	}
	hl := r.URL.Query().Get("hl")
	if hl == "" {
		hl = "en"
	}

	shelves, err := d.exploreEng.FetchGenrePlaylists(r.Context(), params, name, gl, hl)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to retrieve genre playlists: "+err.Error())
		return
	}

	if shelves == nil {
		shelves = []ytmusic.PlaylistShelf{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"shelves": shelves,
	})
}
