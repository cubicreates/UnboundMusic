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

// handleAutoEqPreset returns 10-band equalization curve parameters for a headphone model.
func (s *Server) handleAutoEqPreset(w http.ResponseWriter, r *http.Request) {
	modelID := r.URL.Query().Get("id")
	preset, err := s.autoeq.GetEQPreset(modelID)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, preset)
}
