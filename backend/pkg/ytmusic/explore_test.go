/*
 * Package: ytmusic
 * File: explore_test.go
 * Purpose: Unit tests for InnerTube browse parsing, high-resolution thumbnail upscaling, and 4-hour SQLite caching.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary SQLite databases.
 */

package ytmusic

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

// TestThumbnailUrlUpscaling verifies thumbnail URLs are correctly upscaled to high resolution.
func TestThumbnailUrlUpscaling(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{
			input:    "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj",
			expected: "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
		},
		{
			input:    "https://lh3.googleusercontent.com/xyz=w226-h226",
			expected: "https://lh3.googleusercontent.com/xyz=w544-h544-l90-rj",
		},
		{
			input:    "https://lh3.googleusercontent.com/sample=s180",
			expected: "https://lh3.googleusercontent.com/sample=s544",
		},
		{
			input:    "https://example.com/cover.jpg",
			expected: "https://example.com/cover.jpg",
		},
	}

	for _, tc := range tests {
		got := UpscaleThumbnail(tc.input)
		if got != tc.expected {
			t.Errorf("UpscaleThumbnail(%q) = %q, expected %q", tc.input, got, tc.expected)
		}
	}
}

// TestParseChartsMockPayload tests extraction of titles, artists, and video IDs from InnerTube JSON.
func TestParseChartsMockPayload(t *testing.T) {
	mockJSON := `
	{
		"contents": {
			"singleColumnBrowseResultsRenderer": {
				"tabs": [
					{
						"tabRenderer": {
							"content": {
								"sectionListRenderer": {
									"contents": [
										{
											"musicCarouselShelfRenderer": {
												"contents": [
													{
														"musicResponsiveListItemRenderer": {
															"navigationEndpoint": {
																"watchEndpoint": {
																	"videoId": "dQw4w9WgXcQ"
																}
															},
															"flexColumns": [
																{
																	"musicResponsiveListItemFlexColumnRenderer": {
																		"text": {
																			"runs": [
																				{ "text": "Never Gonna Give You Up" }
																			]
																		}
																	}
																},
																{
																	"musicResponsiveListItemFlexColumnRenderer": {
																		"text": {
																			"runs": [
																				{ "text": "Rick Astley" },
																				{ "text": " • " },
																				{ "text": "Whenever You Need Somebody" }
																			]
																		}
																	}
																}
															],
															"thumbnail": {
																"musicThumbnailRenderer": {
																	"thumbnail": {
																		"thumbnails": [
																			{ "url": "https://lh3.googleusercontent.com/rick=w120-h120" }
																		]
																	}
																}
															}
														}
													},
													{
														"musicTwoRowItemRenderer": {
															"navigationEndpoint": {
																"watchEndpoint": {
																	"videoId": "9bZkp7q19f0"
																}
															},
															"title": {
																"runs": [
																	{ "text": "Gangnam Style" }
																]
															},
															"subtitle": {
																"runs": [
																	{ "text": "PSY" }
																]
															},
															"thumbnailRenderer": {
																"musicThumbnailRenderer": {
																	"thumbnail": {
																		"thumbnails": [
																			{ "url": "https://lh3.googleusercontent.com/psy=w226-h226" }
																		]
																	}
																}
															}
														}
													}
												]
											}
										}
									]
								}
							}
						}
					}
				]
			}
		}
	}`

	tracks, err := ParseBrowseTracks([]byte(mockJSON))
	if err != nil {
		t.Fatalf("ParseBrowseTracks failed: %v", err)
	}

	if len(tracks) != 2 {
		t.Fatalf("expected 2 parsed tracks, got %d", len(tracks))
	}

	// Track 1
	if tracks[0].ID != "dQw4w9WgXcQ" {
		t.Errorf("expected ID dQw4w9WgXcQ, got: %s", tracks[0].ID)
	}
	if tracks[0].Title != "Never Gonna Give You Up" {
		t.Errorf("expected title Never Gonna Give You Up, got: %s", tracks[0].Title)
	}
	if tracks[0].Artist != "Rick Astley" {
		t.Errorf("expected artist Rick Astley, got: %s", tracks[0].Artist)
	}
	if !strings.Contains(tracks[0].Thumbnail, "=w544-h544-l90-rj") {
		t.Errorf("expected upscaled thumbnail, got: %s", tracks[0].Thumbnail)
	}

	// Track 2
	if tracks[1].ID != "9bZkp7q19f0" {
		t.Errorf("expected ID 9bZkp7q19f0, got: %s", tracks[1].ID)
	}
	if tracks[1].Title != "Gangnam Style" {
		t.Errorf("expected title Gangnam Style, got: %s", tracks[1].Title)
	}
	if tracks[1].Artist != "PSY" {
		t.Errorf("expected artist PSY, got: %s", tracks[1].Artist)
	}
}

// TestExploreCacheTTL asserts that subsequent calls within TTL read directly from SQLite without network activity.
func TestExploreCacheTTL(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_explore_cache.db")

	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed opening test DB: %v", err)
	}
	defer db.Close()

	repo := database.NewRepository(db)

	var requestCount int32
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&requestCount, 1)
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `
		{
			"contents": {
				"musicResponsiveListItemRenderer": {
					"navigationEndpoint": {
						"watchEndpoint": { "videoId": "hit_song_1" }
					},
					"flexColumns": [
						{
							"musicResponsiveListItemFlexColumnRenderer": {
								"text": { "runs": [{ "text": "Billboard #1 Hit" }] }
							}
						},
						{
							"musicResponsiveListItemFlexColumnRenderer": {
								"text": { "runs": [{ "text": "Top Artist" }] }
							}
						}
					]
				}
			}
		}`)
	}))
	defer mockServer.Close()

	engine := NewExploreEngine(repo)

	// Inject custom transport routing to mockServer
	customClient := &http.Client{
		Transport: &testRoundTripper{targetURL: mockServer.URL},
	}
	engine.SetHTTPClient(customClient)

	ctx := context.Background()

	// First call: should hit mockServer
	tracks1, err := engine.FetchRegionalCharts(ctx, "US", "en")
	if err != nil {
		t.Fatalf("first FetchRegionalCharts failed: %v", err)
	}
	if len(tracks1) != 1 || tracks1[0].Title != "Billboard #1 Hit" {
		t.Fatalf("unexpected tracks from first call: %+v", tracks1)
	}
	if atomic.LoadInt32(&requestCount) != 1 {
		t.Errorf("expected 1 HTTP request on first fetch, got: %d", requestCount)
	}

	// Second call: should hit SQLite feed_cache, zero new HTTP requests
	tracks2, err := engine.FetchRegionalCharts(ctx, "US", "en")
	if err != nil {
		t.Fatalf("second FetchRegionalCharts failed: %v", err)
	}
	if len(tracks2) != 1 || tracks2[0].Title != "Billboard #1 Hit" {
		t.Fatalf("unexpected tracks from second call: %+v", tracks2)
	}
	if atomic.LoadInt32(&requestCount) != 1 {
		t.Errorf("expected request count to remain 1 due to SQLite cache hit, but got: %d", requestCount)
	}
}

// testRoundTripper redirects requests to mockServer URL.
type testRoundTripper struct {
	targetURL string
}

func (t *testRoundTripper) RoundTrip(req *http.Request) (*http.Response, error) {
	newReq := req.Clone(req.Context())
	newReq.URL.Scheme = "http"
	newReq.URL.Host = strings.TrimPrefix(t.targetURL, "http://")
	return http.DefaultTransport.RoundTrip(newReq)
}
