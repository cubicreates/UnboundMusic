/*
 * Package: main
 * File: handlers_radio.go
 * Purpose: HTTP controller endpoints for Magic Serendipity Radio, taste telemetry, and taste profile.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// MagicRadioRequest encapsulates parameters for generating a serendipity radio queue.
type MagicRadioRequest struct {
	LocalHour   int    `json:"local_hour,omitempty"`
	SeedTrackID string `json:"seed_track_id,omitempty"`
}

// TasteEventRequest defines the JSON payload for logging physical listening telemetry.
type TasteEventRequest struct {
	TrackID    string `json:"track_id"`
	Title      string `json:"title"`
	ArtistID   string `json:"artist_id,omitempty"`
	ArtistName string `json:"artist_name"`
	Genre      string `json:"genre,omitempty"`
	DurationMs int64  `json:"duration_ms"`
	ListenedMs int64  `json:"listened_ms"`
	EventType  string `json:"event_type,omitempty"`
}

// HandleMagicRadio handles POST /api/v1/radio/magic requests.
func (d *Daemon) HandleMagicRadio(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req MagicRadioRequest
	if r.Body != nil && r.ContentLength > 0 {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.LocalHour <= 0 {
		req.LocalHour = time.Now().Hour()
	}

	if d.radioGen == nil {
		d.writeError(w, http.StatusInternalServerError, "radio generator not initialized")
		return
	}

	resp, err := d.radioGen.GenerateMagicRadio(r.Context(), req.LocalHour, req.SeedTrackID)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, resp)
}

// HandleRecordTasteEvent handles POST /api/v1/analytics/taste_event requests.
func (d *Daemon) HandleRecordTasteEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req TasteEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "invalid json payload: "+err.Error())
		return
	}

	if req.TrackID == "" || req.ArtistName == "" {
		d.writeError(w, http.StatusBadRequest, "track_id and artist_name are required")
		return
	}

	delta, eventType := database.CalculateScoreDelta(req.DurationMs, req.ListenedMs, models.TasteEventType(req.EventType))

	event := models.TasteEvent{
		TrackID:    req.TrackID,
		Title:      req.Title,
		ArtistID:   req.ArtistID,
		ArtistName: req.ArtistName,
		Genre:      req.Genre,
		DurationMs: req.DurationMs,
		ListenedMs: req.ListenedMs,
		ScoreDelta: delta,
		EventType:  eventType,
		Timestamp:  time.Now().Unix(),
	}

	if d.repo != nil {
		if err := d.repo.RecordTasteEvent(event); err != nil {
			d.writeError(w, http.StatusInternalServerError, "failed recording taste event: "+err.Error())
			return
		}
	}

	d.writeJSON(w, http.StatusOK, map[string]any{
		"status":      "recorded",
		"score_delta": delta,
		"event_type":  string(eventType),
	})
}

// HandleGetTasteProfile handles GET /api/v1/taste/profile requests.
func (d *Daemon) HandleGetTasteProfile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	limit := 10
	if lStr := r.URL.Query().Get("limit"); lStr != "" {
		if parsed, err := strconv.Atoi(lStr); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	if d.repo == nil {
		d.writeError(w, http.StatusInternalServerError, "database repository not initialized")
		return
	}

	topArtists, err := d.repo.GetTopAffinityArtists(limit)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "failed querying top artists: "+err.Error())
		return
	}

	diversityScore, _ := d.repo.GetTasteDiversityScore()

	d.writeJSON(w, http.StatusOK, map[string]any{
		"top_artists":           topArtists,
		"taste_diversity_score": diversityScore,
	})
}
