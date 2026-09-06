/*
 * Package: main
 * File: handlers_explore.go
 * Purpose: REST controllers for Regional Charts, 24-Hour Dayparting Mood Capsules, and Contextual Mood Radio.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"net/http"
	"strconv"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// HandleGetRegionalCharts returns the top 100 regional chart tracks for a given country code.
// Route: GET /api/v1/explore/charts?gl=US&hl=en
func (d *Daemon) HandleGetRegionalCharts(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
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

	tracks, err := d.exploreEng.FetchRegionalCharts(r.Context(), gl, hl)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	if tracks == nil {
		tracks = []models.TrackItem{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
	})
}

// HandleGetMoodCapsules returns situational mood capsules prioritized by temporal dayparting.
// Route: GET /api/v1/explore/moods?hour=18
func (d *Daemon) HandleGetMoodCapsules(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	hour := time.Now().Hour()
	if hourStr := r.URL.Query().Get("hour"); hourStr != "" {
		if parsedHour, err := strconv.Atoi(hourStr); err == nil {
			hour = parsedHour
		}
	}

	state := d.moodEng.GetDaypartingState(hour)
	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"active_window": state.ActiveWindow,
		"local_hour":    state.LocalHour,
		"capsules":      state.Capsules,
	})
}

// HandleGetMoodRadio returns a stream of tracks matching a specific mood capsule browseID.
// Route: GET /api/v1/explore/mood/radio?browse_id=FEmusic_moods_and_genres_category_workout&gl=US&hl=en
func (d *Daemon) HandleGetMoodRadio(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	browseID := r.URL.Query().Get("browse_id")
	gl := r.URL.Query().Get("gl")
	if gl == "" {
		gl = "US"
	}
	hl := r.URL.Query().Get("hl")
	if hl == "" {
		hl = "en"
	}

	tracks, err := d.moodEng.FetchMoodRadio(r.Context(), browseID, gl, hl)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	if tracks == nil {
		tracks = []models.TrackItem{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
	})
}
