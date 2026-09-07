/*
 * Package: ytmusic
 * File: genres_test.go
 * Purpose: Unit tests for InnerTube Moods and Genres parser, deterministic color fallback, and offline handling.
 * Subsystem: Core Scraper Engine Tests
 * Concurrency: Thread-safe test executions.
 */

package ytmusic

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestDeterministicStripeColor(t *testing.T) {
	c1 := DeterministicStripeColor("Hip-Hop")
	c2 := DeterministicStripeColor("Hip-Hop")
	if c1 != c2 {
		t.Errorf("expected deterministic color for same title, got %X != %X", c1, c2)
	}

	cRock := DeterministicStripeColor("Rock")
	if cRock == 0 {
		t.Errorf("expected non-zero color for Rock")
	}
}

func TestGetDefaultGenreSections(t *testing.T) {
	sections := GetDefaultGenreSections()
	if len(sections) == 0 {
		t.Fatalf("expected default genre sections, got 0")
	}
	if len(sections[0].Items) != 12 {
		t.Errorf("expected 12 default core genres, got %d", len(sections[0].Items))
	}
	foundHipHop := false
	for _, item := range sections[0].Items {
		if item.Title == "Hip-Hop" {
			foundHipHop = true
			if item.StripeColor == 0 {
				t.Errorf("expected non-zero stripe color for Hip-Hop")
			}
		}
	}
	if !foundHipHop {
		t.Errorf("expected Hip-Hop in default genres")
	}
}

func TestParseMoodsAndGenresPayload(t *testing.T) {
	mockJSON := `{
		"contents": {
			"singleColumnBrowseResultsRenderer": {
				"tabs": [
					{
						"tabRenderer": {
							"content": {
								"sectionListRenderer": {
									"contents": [
										{
											"gridRenderer": {
												"header": {
													"gridHeaderRenderer": {
														"title": { "runs": [ { "text": "Moods & moments" } ] }
													}
												},
												"items": [
													{
														"musicNavigationButtonRenderer": {
															"buttonText": { "runs": [ { "text": "Chill" } ] },
															"solid": { "leftStripeColor": 4283150160 },
															"clickCommand": {
																"browseEndpoint": {
																	"browseId": "FEmusic_moods_and_genres_category",
																	"params": "chill_params_123"
																}
															}
														}
													}
												]
											}
										},
										{
											"gridRenderer": {
												"header": {
													"gridHeaderRenderer": {
														"title": { "runs": [ { "text": "Genres" } ] }
													}
												},
												"items": [
													{
														"musicNavigationButtonRenderer": {
															"buttonText": { "runs": [ { "text": "Electronic" } ] },
															"solid": { "leftStripeColor": 4294924066 },
															"clickCommand": {
																"browseEndpoint": {
																	"browseId": "FEmusic_moods_and_genres_category",
																	"params": "electronic_params_456"
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

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(mockJSON))
	}))
	defer server.Close()

	engine := NewExploreEngine(nil)
	engine.SetHTTPClient(server.Client())

	sections, err := engine.ParseMoodsAndGenresJSON([]byte(mockJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing moods and genres: %v", err)
	}

	if len(sections) != 2 {
		t.Fatalf("expected 2 sections, got %d", len(sections))
	}
	if sections[0].Title != "Moods & moments" {
		t.Errorf("expected first section 'Moods & moments', got %q", sections[0].Title)
	}
	if len(sections[0].Items) != 1 || sections[0].Items[0].Title != "Chill" {
		t.Errorf("expected Chill in Moods & moments")
	}
	if sections[0].Items[0].Params != "chill_params_123" {
		t.Errorf("expected chill_params_123, got %q", sections[0].Items[0].Params)
	}
	if sections[1].Title != "Genres" {
		t.Errorf("expected second section 'Genres', got %q", sections[1].Title)
	}
	if len(sections[1].Items) != 1 || sections[1].Items[0].Title != "Electronic" {
		t.Errorf("expected Electronic in Genres")
	}
}

func TestFetchMoodsAndGenresOfflineFallback(t *testing.T) {
	// Client that fails network calls
	failingClient := &http.Client{
		Transport: &failingTransport{},
	}

	engine := NewExploreEngine(nil)
	engine.SetHTTPClient(failingClient)

	sections, err := engine.FetchMoodsAndGenres(context.Background(), "US", "en")
	if err != nil {
		t.Fatalf("expected fallback genres instead of error, got: %v", err)
	}
	if len(sections) == 0 || len(sections[0].Items) == 0 {
		t.Fatalf("expected non-empty fallback genre sections")
	}
}

type failingTransport struct{}

func (f *failingTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	return nil, context.DeadlineExceeded
}
