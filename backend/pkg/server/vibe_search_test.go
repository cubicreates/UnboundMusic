package server

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
)

func TestHandleVibeSearchConversational(t *testing.T) {
	tempDir := t.TempDir()
	srv, err := NewServer(Config{
		Port:           45792,
		DatabasePath:   filepath.Join(tempDir, "test_vibe.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	})
	if err != nil {
		t.Fatalf("failed initializing server: %v", err)
	}
	defer srv.Shutdown(context.Background())
	defer srv.db.Close()

	payload := map[string]string{
		"prompt": "Hey I am feeling Sad play some music",
	}
	body, _ := json.Marshal(payload)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/search/vibe", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	srv.handleVibeSearch(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got: %d (%s)", w.Code, w.Body.String())
	}

	var resp struct {
		VibeResult struct {
			MoodTags       []string `json:"mood_tags"`
			EnergyLevel    string   `json:"energy_level"`
			SearchKeywords []string `json:"search_keywords"`
		} `json:"vibe_result"`
		RadioTracks []any `json:"radio_tracks"`
	}

	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed decoding vibe search response: %v", err)
	}

	foundMelancholic := false
	for _, m := range resp.VibeResult.MoodTags {
		if m == "Melancholic" {
			foundMelancholic = true
			break
		}
	}
	if !foundMelancholic {
		t.Errorf("expected Melancholic mood tag, got: %v", resp.VibeResult.MoodTags)
	}

	if len(resp.VibeResult.SearchKeywords) == 0 {
		t.Fatalf("expected search keywords, got empty slice")
	}
	// Under regional curation, search keywords must include curated songs instead of raw generic phrases
	hasCuratedSadSong := false
	for _, kw := range resp.VibeResult.SearchKeywords {
		if strings.Contains(kw, "Channa Mereya") || strings.Contains(kw, "Agar Tum Saath Ho") {
			hasCuratedSadSong = true
			break
		}
	}
	if !hasCuratedSadSong {
		t.Errorf("expected regional curated sad songs in search keywords, got: %v", resp.VibeResult.SearchKeywords)
	}
}

func TestHandleVibeSearchRegionalVictory(t *testing.T) {
	tempDir := t.TempDir()
	srv, err := NewServer(Config{
		Port:           45793,
		DatabasePath:   filepath.Join(tempDir, "test_vibe_victory.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	})
	if err != nil {
		t.Fatalf("failed initializing server: %v", err)
	}
	defer srv.Shutdown(context.Background())
	defer srv.db.Close()

	payload := map[string]string{
		"prompt": "Victory Songs",
		"region": "IN",
	}
	body, _ := json.Marshal(payload)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/search/vibe", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	srv.handleVibeSearch(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got: %d (%s)", w.Code, w.Body.String())
	}

	var resp struct {
		VibeResult struct {
			MoodTags       []string `json:"mood_tags"`
			EnergyLevel    string   `json:"energy_level"`
			SearchKeywords []string `json:"search_keywords"`
			Region         string   `json:"region"`
		} `json:"vibe_result"`
		RadioTracks []any `json:"radio_tracks"`
	}

	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed decoding vibe search response: %v", err)
	}

	if resp.VibeResult.Region != "IN" {
		t.Errorf("expected Region IN, got: %s", resp.VibeResult.Region)
	}

	hasChakDe := false
	for _, kw := range resp.VibeResult.SearchKeywords {
		if strings.Contains(kw, "Chak De India") || strings.Contains(kw, "Kar Har Maidaan Fateh") {
			hasChakDe = true
			break
		}
	}
	if !hasChakDe {
		t.Errorf("expected Indian victory anthems like Chak De India in search keywords, got: %v", resp.VibeResult.SearchKeywords)
	}
}
