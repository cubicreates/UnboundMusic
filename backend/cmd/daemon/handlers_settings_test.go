package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

func TestSettingsAndPurgeEndpoints(t *testing.T) {
	tempDir := t.TempDir()
	db, err := database.Open(filepath.Join(tempDir, "test.db"))
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()
	repo := database.NewRepository(db)
	d := NewDaemon(repo, nil, nil, nil, nil)
	handler := d.Routes()

	// 1. POST /api/v1/settings
	body, _ := json.Marshal(map[string]string{"key": "crossfade_seconds", "value": "6"})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/settings", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for POST /api/v1/settings, got %d", rec.Code)
	}

	// 2. GET /api/v1/settings
	req = httptest.NewRequest(http.MethodGet, "/api/v1/settings", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for GET /api/v1/settings, got %d", rec.Code)
	}
	var settingsResp map[string]interface{}
	json.NewDecoder(rec.Body).Decode(&settingsResp)
	settingsMap, ok := settingsResp["settings"].(map[string]interface{})
	if !ok || settingsMap["crossfade_seconds"] != "6" {
		t.Errorf("expected crossfade_seconds='6', got %+v", settingsResp)
	}

	// 3. POST /api/v1/eq/presets
	presetBody, _ := json.Marshal(map[string]interface{}{
		"name":             "Vocal Boost",
		"band_levels_json": "[0, 0, 1, 2, 4, 4, 3, 1, 0, 0]",
		"bass_boost":       200,
		"virtualizer":      100,
	})
	req = httptest.NewRequest(http.MethodPost, "/api/v1/eq/presets", bytes.NewReader(presetBody))
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for POST /api/v1/eq/presets, got %d", rec.Code)
	}

	// 4. GET /api/v1/eq/presets
	req = httptest.NewRequest(http.MethodGet, "/api/v1/eq/presets", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for GET /api/v1/eq/presets, got %d", rec.Code)
	}

	// 5. POST /api/v1/storage/purge_cache
	req = httptest.NewRequest(http.MethodPost, "/api/v1/storage/purge_cache", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for POST /api/v1/storage/purge_cache, got %d", rec.Code)
	}
	var purgeResp map[string]interface{}
	json.NewDecoder(rec.Body).Decode(&purgeResp)
	if _, ok := purgeResp["freed_bytes"]; !ok {
		t.Errorf("expected freed_bytes in purge response, got %+v", purgeResp)
	}
}
