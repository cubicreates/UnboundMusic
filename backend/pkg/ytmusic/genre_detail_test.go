/*
 * Package: ytmusic
 * File: genre_detail_test.go
 * Purpose: Unit tests for InnerTube genre detail playlist shelf parser and high-res artwork upscaling.
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

func TestUpscaleThumbnailHighRes(t *testing.T) {
	raw := "https://lh3.googleusercontent.com/abc=w544-h544-s"
	got := UpscaleThumbnailHighRes(raw)
	expected := "https://lh3.googleusercontent.com/abc=w800-h800-l90-rj"
	if got != expected {
		t.Errorf("UpscaleThumbnailHighRes(%q) = %q, expected %q", raw, got, expected)
	}
}

func TestParseGenrePlaylistsPayload(t *testing.T) {
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
											"musicCarouselShelfRenderer": {
												"header": {
													"musicCarouselShelfBasicHeaderRenderer": {
														"title": { "runs": [ { "text": "Today's Hip-Hop Hits" } ] }
													}
												},
												"contents": [
													{
														"musicTwoRowItemRenderer": {
															"title": { "runs": [ { "text": "Rap Caviar Radio" } ] },
															"subtitle": { "runs": [ { "text": "Curated by YouTube Music" } ] },
															"thumbnailRenderer": {
																"musicThumbnailRenderer": {
																	"thumbnail": {
																		"thumbnails": [
																			{ "url": "https://lh3.googleusercontent.com/test=w544-h544" }
																		]
																	}
																}
															},
															"navigationEndpoint": {
																"watchEndpoint": {
																	"playlistId": "RDCLAK5uy_hiphop123"
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

	shelves, err := engine.ParseGenrePlaylistsJSON([]byte(mockJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing genre playlists: %v", err)
	}

	if len(shelves) != 1 {
		t.Fatalf("expected 1 shelf, got %d", len(shelves))
	}
	if shelves[0].Title != "Today's Hip-Hop Hits" {
		t.Errorf("expected 'Today's Hip-Hop Hits', got %q", shelves[0].Title)
	}
	if len(shelves[0].Items) != 1 {
		t.Fatalf("expected 1 item in shelf, got %d", len(shelves[0].Items))
	}
	item := shelves[0].Items[0]
	if item.Title != "Rap Caviar Radio" {
		t.Errorf("expected 'Rap Caviar Radio', got %q", item.Title)
	}
	if item.PlaylistID != "RDCLAK5uy_hiphop123" {
		t.Errorf("expected 'RDCLAK5uy_hiphop123', got %q", item.PlaylistID)
	}
	if item.ThumbnailURL != "https://lh3.googleusercontent.com/test=w800-h800-l90-rj" {
		t.Errorf("expected upscaled =w800-h800 thumbnail, got %q", item.ThumbnailURL)
	}
}

func TestFetchGenrePlaylistsOfflineFallback(t *testing.T) {
	failingClient := &http.Client{
		Transport: &failingTransport{},
	}

	engine := NewExploreEngine(nil)
	engine.SetHTTPClient(failingClient)

	shelves, err := engine.FetchGenrePlaylists(context.Background(), "default_hip-hop", "Hip-Hop", "US", "en")
	if err != nil {
		t.Fatalf("expected fallback shelves instead of error, got: %v", err)
	}
	if len(shelves) == 0 || len(shelves[0].Items) == 0 {
		t.Fatalf("expected non-empty fallback playlist shelves")
	}
}
