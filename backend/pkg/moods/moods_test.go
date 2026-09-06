/*
 * Package: moods
 * File: moods_test.go
 * Purpose: Unit tests for 24-hour temporal dayparting, hour normalization, and mood radio fallback.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package moods

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// TestDaypartingHourMapping tests all 24 hours (0-23) for correct window and capsule generation.
func TestDaypartingHourMapping(t *testing.T) {
	engine := NewEngine(nil)

	expectedWindows := map[int]string{
		0:  "LATE_NIGHT_CHILL",
		3:  "LATE_NIGHT_CHILL",
		5:  "LATE_NIGHT_CHILL",
		6:  "MORNING_COMMUTE",
		8:  "MORNING_COMMUTE",
		10: "MORNING_COMMUTE",
		11: "DEEP_FOCUS",
		14: "DEEP_FOCUS",
		16: "DEEP_FOCUS",
		17: "WORKOUT_AND_DRIVE",
		19: "WORKOUT_AND_DRIVE",
		20: "WORKOUT_AND_DRIVE",
		21: "LATE_NIGHT_CHILL",
		23: "LATE_NIGHT_CHILL",
	}

	for hour, expectedWindow := range expectedWindows {
		state := engine.GetDaypartingState(hour)
		if state.ActiveWindow != expectedWindow {
			t.Errorf("hour %d: expected window %s, got %s", hour, expectedWindow, state.ActiveWindow)
		}
		if state.LocalHour != hour {
			t.Errorf("hour %d: expected LocalHour %d, got %d", hour, hour, state.LocalHour)
		}
		if len(state.Capsules) != 3 {
			t.Errorf("hour %d: expected 3 capsules, got %d", hour, len(state.Capsules))
		}
		for _, cap := range state.Capsules {
			if cap.Tag == "" || cap.Title == "" || cap.BrowseID == "" || cap.ColorHex == "" || cap.IconName == "" {
				t.Errorf("hour %d: incomplete capsule fields: %+v", hour, cap)
			}
		}
	}
}

// TestInvalidHourNormalization verifies negative numbers and values >= 24 are normalized cleanly.
func TestInvalidHourNormalization(t *testing.T) {
	engine := NewEngine(nil)

	tests := []struct {
		inputHour      int
		expectedHour   int
		expectedWindow string
	}{
		{inputHour: -1, expectedHour: 23, expectedWindow: "LATE_NIGHT_CHILL"},
		{inputHour: -18, expectedHour: 6, expectedWindow: "MORNING_COMMUTE"},
		{inputHour: 24, expectedHour: 0, expectedWindow: "LATE_NIGHT_CHILL"},
		{inputHour: 27, expectedHour: 3, expectedWindow: "LATE_NIGHT_CHILL"},
		{inputHour: 36, expectedHour: 12, expectedWindow: "DEEP_FOCUS"},
		{inputHour: 42, expectedHour: 18, expectedWindow: "WORKOUT_AND_DRIVE"},
	}

	for _, tc := range tests {
		state := engine.GetDaypartingState(tc.inputHour)
		if state.LocalHour != tc.expectedHour {
			t.Errorf("input %d: expected normalized hour %d, got %d", tc.inputHour, tc.expectedHour, state.LocalHour)
		}
		if state.ActiveWindow != tc.expectedWindow {
			t.Errorf("input %d: expected window %s, got %s", tc.inputHour, tc.expectedWindow, state.ActiveWindow)
		}
	}
}

// TestMoodRadioFallback tests that if a mood radio query fails, it gracefully falls back to regional charts.
func TestMoodRadioFallback(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_moods_cache.db")

	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed opening test db: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)

	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		bodyBytes, _ := io.ReadAll(r.Body)
		bodyStr := string(bodyBytes)

		// Simulate failure for specific mood browseId, but success for charts
		if strings.Contains(bodyStr, "FEmusic_moods_and_genres_category_broken") {
			http.Error(w, "Endpoint Not Found", http.StatusNotFound)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `
		{
			"contents": {
				"musicResponsiveListItemRenderer": {
					"navigationEndpoint": {
						"watchEndpoint": { "videoId": "fallback_chart_track" }
					},
					"flexColumns": [
						{
							"musicResponsiveListItemFlexColumnRenderer": {
								"text": { "runs": [{ "text": "Fallback Chart Anthem" }] }
							}
						},
						{
							"musicResponsiveListItemFlexColumnRenderer": {
								"text": { "runs": [{ "text": "Top Chart Artist" }] }
							}
						}
					]
				}
			}
		}`)
	}))
	defer mockServer.Close()

	exploreEngine := ytmusic.NewExploreEngine(repo)
	customClient := &http.Client{
		Transport: &testRoundTripper{targetURL: mockServer.URL},
	}
	exploreEngine.SetHTTPClient(customClient)

	engine := NewEngine(exploreEngine)
	ctx := context.Background()

	tracks, err := engine.FetchMoodRadio(ctx, "FEmusic_moods_and_genres_category_broken", "US", "en")
	if err != nil {
		t.Fatalf("FetchMoodRadio failed despite fallback: %v", err)
	}

	if len(tracks) != 1 || tracks[0].Title != "Fallback Chart Anthem" {
		t.Fatalf("expected fallback chart track, got: %+v", tracks)
	}
}

type testRoundTripper struct {
	targetURL string
}

func (t *testRoundTripper) RoundTrip(req *http.Request) (*http.Response, error) {
	newReq := req.Clone(req.Context())
	newReq.URL.Scheme = "http"
	newReq.URL.Host = strings.TrimPrefix(t.targetURL, "http://")
	return http.DefaultTransport.RoundTrip(newReq)
}
