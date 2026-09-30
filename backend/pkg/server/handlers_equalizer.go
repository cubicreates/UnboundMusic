package server

import (
	"net/http"
)

// handleAutoEqSearch searches available calibrated headphone profiles.
func (s *Server) handleAutoEqSearch(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	results := s.autoeq.SearchHeadphones(query)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":      query,
		"count":      len(results),
		"headphones": results,
	})
}

// handleAutoEqPreset returns 10-band or resampled equalization curve parameters for a headphone model.
func (s *Server) handleAutoEqPreset(w http.ResponseWriter, r *http.Request) {
	modelID := r.URL.Query().Get("id")
	bandsParam := r.URL.Query().Get("bands")

	var preset interface{}
	var err error

	if bandsParam == "5" {
		preset, err = s.autoeq.GetResampledPreset(modelID, []int{60, 230, 910, 3600, 14000})
	} else if bandsParam == "10" {
		preset, err = s.autoeq.GetResampledPreset(modelID, []int{31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000})
	} else {
		preset, err = s.autoeq.GetEQPreset(modelID)
	}

	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, preset)
}
