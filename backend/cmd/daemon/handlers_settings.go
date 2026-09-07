/*
 * Package: main
 * File: handlers_settings.go
 * Purpose: REST controller handlers for application configuration, DSP parameters, user EQ presets, and storage cache purging.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handlers.
 */

package main

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

type setSettingRequest struct {
	Key   string `json:"key"`
	Value string `json:"value"`
}

// HandleGetSettings returns all stored application configuration key-value pairs.
// Route: GET /api/v1/settings
func (d *Daemon) HandleGetSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	settings, err := d.repo.GetAllSettings(r.Context())
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to retrieve settings: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"settings": settings,
	})
}

// HandleSetSetting updates or stores an application configuration key-value pair.
// Route: POST /api/v1/settings
func (d *Daemon) HandleSetSetting(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req setSettingRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Key == "" {
		d.writeError(w, http.StatusBadRequest, "Invalid request: missing key payload")
		return
	}

	if err := d.repo.SetSetting(r.Context(), req.Key, req.Value); err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to save setting: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status": "ok",
		"key":    req.Key,
		"value":  req.Value,
	})
}

// HandleGetEqPresets retrieves all saved user equalizer curves and spatial settings.
// Route: GET /api/v1/eq/presets
func (d *Daemon) HandleGetEqPresets(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	presets, err := d.repo.GetUserEqPresets(r.Context())
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to load presets: "+err.Error())
		return
	}

	if presets == nil {
		presets = []database.UserEqPreset{}
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"presets": presets,
	})
}

// HandleSaveEqPreset creates or updates a custom equalizer curve.
// Route: POST /api/v1/eq/presets
func (d *Daemon) HandleSaveEqPreset(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var p database.UserEqPreset
	if err := json.NewDecoder(r.Body).Decode(&p); err != nil || p.Name == "" {
		d.writeError(w, http.StatusBadRequest, "Invalid request: missing preset name")
		return
	}

	if err := d.repo.SaveUserEqPreset(r.Context(), p); err != nil {
		d.writeError(w, http.StatusInternalServerError, "Failed to save preset: "+err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status": "ok",
		"name":   p.Name,
	})
}

// HandlePurgeCache deletes temporary artwork, streaming cache buffers, and unpinned chunks.
// Route: POST /api/v1/storage/purge_cache
func (d *Daemon) HandlePurgeCache(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var freedBytes int64 = 0

	// Common Android and local cache directories
	cacheDirs := []string{
		filepath.Join(os.TempDir(), "unbound_cache"),
		"/storage/emulated/0/Unbound/cache",
		filepath.Join(os.TempDir(), "unbound_stream"),
	}

	for _, dir := range cacheDirs {
		_ = filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
			if err == nil && !info.IsDir() {
				freedBytes += info.Size()
				_ = os.Remove(path)
			}
			return nil
		})
	}

	freedMB := float64(freedBytes) / (1024 * 1024)

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":      "ok",
		"freed_bytes": freedBytes,
		"freed_mb":    freedMB,
	})
}
