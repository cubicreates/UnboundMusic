package ytmusic

import (
	"context"
	"strings"
	"testing"
)

func TestParseMusicResponsiveItem(t *testing.T) {
	rawItem := map[string]interface{}{
		"flexColumns": []interface{}{
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{
								"text": "HUMBLE.",
								"navigationEndpoint": map[string]interface{}{
									"watchEndpoint": map[string]interface{}{
										"videoId": "tvTRZJ-4EyI",
									},
								},
							},
						},
					},
				},
			},
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{"text": "Kendrick Lamar"},
							map[string]interface{}{"text": " • "},
							map[string]interface{}{"text": "DAMN."},
						},
					},
				},
			},
		},
		"thumbnail": map[string]interface{}{
			"musicThumbnailRenderer": map[string]interface{}{
				"thumbnail": map[string]interface{}{
					"thumbnails": []interface{}{
						map[string]interface{}{
							"url": "https://lh3.googleusercontent.com/test=w120-h120-l90-rj",
						},
					},
				},
			},
		},
	}

	track, ok := parseMusicResponsiveItem(rawItem)
	if !ok {
		t.Fatalf("expected successful track parsing")
	}
	if track.ID != "tvTRZJ-4EyI" || track.Title != "HUMBLE." || track.Artist != "Kendrick Lamar" {
		t.Errorf("unexpected track parsed: %+v", track)
	}
	if !strings.Contains(track.ThumbnailURL, "=w800-h800") {
		t.Errorf("expected high-res thumbnail URL, got %s", track.ThumbnailURL)
	}
}

func TestParseMusicResponsiveItemWithPlaylistItemData(t *testing.T) {
	rawItem := map[string]interface{}{
		"playlistItemData": map[string]interface{}{
			"videoId": "dQw4w9WgXcQ",
		},
		"flexColumns": []interface{}{
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{
								"text": "Never Gonna Give You Up",
							},
						},
					},
				},
			},
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{"text": "Rick Astley"},
						},
					},
				},
			},
		},
	}

	track, ok := parseMusicResponsiveItem(rawItem)
	if !ok {
		t.Fatalf("expected successful track parsing with playlistItemData")
	}
	if track.ID != "dQw4w9WgXcQ" || track.Title != "Never Gonna Give You Up" || track.Artist != "Rick Astley" {
		t.Errorf("unexpected track parsed: %+v", track)
	}
}

func TestParseMusicResponsiveItemWithOverlay(t *testing.T) {
	rawItem := map[string]interface{}{
		"overlay": map[string]interface{}{
			"musicItemThumbnailOverlayRenderer": map[string]interface{}{
				"content": map[string]interface{}{
					"musicPlayButtonRenderer": map[string]interface{}{
						"playNavigationEndpoint": map[string]interface{}{
							"watchEndpoint": map[string]interface{}{
								"videoId": "9bZkp7q19f0",
							},
						},
					},
				},
			},
		},
		"flexColumns": []interface{}{
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{
								"text": "Gangnam Style",
							},
						},
					},
				},
			},
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{"text": "PSY"},
						},
					},
				},
			},
		},
	}

	track, ok := parseMusicResponsiveItem(rawItem)
	if !ok {
		t.Fatalf("expected successful track parsing with overlay")
	}
	if track.ID != "9bZkp7q19f0" || track.Title != "Gangnam Style" || track.Artist != "PSY" {
		t.Errorf("unexpected track parsed: %+v", track)
	}
}

func TestParseHomeShelvesFromJSON(t *testing.T) {
	mockJSON := map[string]interface{}{
		"contents": map[string]interface{}{
			"singleColumnBrowseResultsRenderer": map[string]interface{}{
				"tabs": []interface{}{
					map[string]interface{}{
						"tabRenderer": map[string]interface{}{
							"content": map[string]interface{}{
								"sectionListRenderer": map[string]interface{}{
									"contents": []interface{}{
										map[string]interface{}{
											"musicCarouselShelfRenderer": map[string]interface{}{
												"header": map[string]interface{}{
													"musicCarouselShelfBasicHeaderRenderer": map[string]interface{}{
														"title": map[string]interface{}{
															"runs": []interface{}{
																map[string]interface{}{"text": "Mixed for you"},
															},
														},
														"strapline": map[string]interface{}{
															"runs": []interface{}{
																map[string]interface{}{"text": "FOR COZY DAYS AND ENDLESS CUPS OF TEA"},
															},
														},
													},
												},
												"contents": []interface{}{
													map[string]interface{}{
														"musicResponsiveListItemRenderer": map[string]interface{}{
															"playlistItemData": map[string]interface{}{
																"videoId": "test_video_123",
															},
															"flexColumns": []interface{}{
																map[string]interface{}{
																	"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
																		"text": map[string]interface{}{
																			"runs": []interface{}{
																				map[string]interface{}{"text": "Starboy"},
																			},
																		},
																	},
																},
																map[string]interface{}{
																	"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
																		"text": map[string]interface{}{
																			"runs": []interface{}{
																				map[string]interface{}{"text": "The Weeknd"},
																			},
																		},
																	},
																},
															},
														},
													},
												},
											},
										},
									},
								},
							},
						},
					},
				},
			},
		},
	}

	shelves := parseHomeShelvesFromJSON(mockJSON)
	if len(shelves) == 0 {
		t.Fatalf("expected at least 1 shelf parsed from mock JSON")
	}

	s0 := shelves[0]
	if s0.Title != "Mixed for you" {
		t.Errorf("expected Title 'Mixed for you', got %q", s0.Title)
	}
	if s0.Subtitle != "FOR COZY DAYS AND ENDLESS CUPS OF TEA" {
		t.Errorf("expected Subtitle 'FOR COZY DAYS AND ENDLESS CUPS OF TEA', got %q", s0.Subtitle)
	}
	if len(s0.Tracks) != 1 {
		t.Fatalf("expected 1 track in shelf, got %d", len(s0.Tracks))
	}
	if s0.Tracks[0].ID != "test_video_123" || s0.Tracks[0].Title != "Starboy" {
		t.Errorf("unexpected track parsed in shelf: %+v", s0.Tracks[0])
	}
}

func TestParseMusicResponsiveItemWithSignedInTypeBadge(t *testing.T) {
	rawItem := map[string]interface{}{
		"flexColumns": []interface{}{
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{
								"text": "Him & I",
								"navigationEndpoint": map[string]interface{}{
									"watchEndpoint": map[string]interface{}{
										"videoId": "SA7AIacke-4",
									},
								},
							},
						},
					},
				},
			},
			map[string]interface{}{
				"musicResponsiveListItemFlexColumnRenderer": map[string]interface{}{
					"text": map[string]interface{}{
						"runs": []interface{}{
							map[string]interface{}{"text": "Song"},
							map[string]interface{}{"text": " • "},
							map[string]interface{}{"text": "G-Eazy"},
							map[string]interface{}{"text": " & "},
							map[string]interface{}{"text": "Halsey"},
							map[string]interface{}{"text": " • "},
							map[string]interface{}{"text": "The Beautiful & Damned"},
							map[string]interface{}{"text": " • "},
							map[string]interface{}{"text": "4:28"},
						},
					},
				},
			},
		},
	}

	track, ok := parseMusicResponsiveItem(rawItem)
	if !ok {
		t.Fatalf("expected successful track parsing")
	}
	if track.Artist != "G-Eazy & Halsey" {
		t.Errorf("expected artist 'G-Eazy & Halsey', got '%s'", track.Artist)
	}
	if track.Album != "The Beautiful & Damned" {
		t.Errorf("expected album 'The Beautiful & Damned', got '%s'", track.Album)
	}
	if track.DurationMs != 268000 {
		t.Errorf("expected duration 268000, got %d", track.DurationMs)
	}
}

func TestParseMusicTwoRowItemRendererWithSignedInTypeBadge(t *testing.T) {
	rawItem := map[string]interface{}{
		"navigationEndpoint": map[string]interface{}{
			"watchEndpoint": map[string]interface{}{
				"videoId": "SA7AIacke-4",
			},
		},
		"title": map[string]interface{}{
			"runs": []interface{}{
				map[string]interface{}{"text": "Him & I"},
			},
		},
		"subtitle": map[string]interface{}{
			"runs": []interface{}{
				map[string]interface{}{"text": "Song"},
				map[string]interface{}{"text": " • "},
				map[string]interface{}{"text": "G-Eazy"},
			},
		},
	}

	track, ok := parseMusicTwoRowItemRenderer(rawItem)
	if !ok {
		t.Fatalf("expected successful track parsing")
	}
	if track.Artist != "G-Eazy" {
		t.Errorf("expected artist 'G-Eazy', got '%s'", track.Artist)
	}
}

func TestParsePlaylistOrAlbumResponse(t *testing.T) {
	mockJSON := []byte(`{
		"header": {
			"musicResponsiveHeaderRenderer": {
				"title": {
					"runs": [{"text": "After Hours"}]
				},
				"straplineTextOne": {
					"runs": [{"text": "The Weeknd"}]
				},
				"subtitle": {
					"runs": [
						{"text": "Album"},
						{"text": " • "},
						{"text": "2020"}
					]
				},
				"thumbnail": {
					"musicThumbnailRenderer": {
						"thumbnail": {
							"thumbnails": [{"url": "https://lh3.googleusercontent.com/test=w120-h120"}]
						}
					}
				},
				"secondSubtitle": {
					"runs": [{"text": "14 songs, 56 minutes"}]
				}
			}
		},
		"contents": {
			"singleColumnBrowseResultsRenderer": {
				"tabs": [{
					"tabRenderer": {
						"content": {
							"sectionListRenderer": {
								"contents": [{
									"musicShelfRenderer": {
										"contents": [
											{
												"musicResponsiveListItemRenderer": {
													"flexColumns": [
														{
															"musicResponsiveListItemFlexColumnRenderer": {
																"text": {
																	"runs": [{
																		"text": "Blinding Lights",
																		"navigationEndpoint": {
																			"watchEndpoint": {
																				"videoId": "4NRXx6U8ABQ"
																			}
																		}
																	}]
																}
															}
														},
														{
															"musicResponsiveListItemFlexColumnRenderer": {
																"text": {
																	"runs": [
																		{"text": "The Weeknd"},
																		{"text": " • "},
																		{"text": "After Hours"}
																	]
																}
															}
														}
													]
												}
											}
										]
									}
								}]
							}
						}
					}
				}]
			}
		}
	}`)

	album, err := parsePlaylistOrAlbumResponse("MPREb_test123", mockJSON)
	if err != nil {
		t.Fatalf("expected successful album parsing, got: %v", err)
	}

	if album.Title != "After Hours" {
		t.Errorf("expected title 'After Hours', got %q", album.Title)
	}
	if album.Subtitle != "The Weeknd" {
		t.Errorf("expected subtitle 'The Weeknd', got %q", album.Subtitle)
	}
	if !album.IsAlbum {
		t.Errorf("expected isAlbum = true, got false")
	}
	if album.TrackCount != 1 {
		t.Errorf("expected track count 1, got %d", album.TrackCount)
	}
	if len(album.Tracks) != 1 || album.Tracks[0].Title != "Blinding Lights" {
		t.Errorf("unexpected tracks parsed: %+v", album.Tracks)
	}
	if !strings.Contains(album.ThumbnailURL, "=w800-h800") {
		t.Errorf("expected upscaled thumbnail, got %s", album.ThumbnailURL)
	}
}

func TestParsePlaylistOrAlbum_PlaylistVideoRenderer(t *testing.T) {
	mockJSON := []byte(`{
		"header": {
			"musicDetailHeaderRenderer": {
				"title": { "runs": [{ "text": "Top Global Hits" }] },
				"subtitle": { "runs": [{ "text": "YouTube Music" }] }
			}
		},
		"contents": {
			"singleColumnBrowseResultsRenderer": {
				"tabs": [{
					"tabRenderer": {
						"content": {
							"sectionListRenderer": {
								"contents": [{
									"playlistVideoListRenderer": {
										"contents": [
											{
												"playlistVideoRenderer": {
													"videoId": "kJQP7kiw5Fk",
													"title": { "runs": [{ "text": "Despacito" }] },
													"shortBylineText": { "runs": [{ "text": "Luis Fonsi" }] },
													"lengthSeconds": "288"
												}
											},
											{
												"playlistVideoRenderer": {
													"videoId": "JGwWNGJdvx8",
													"title": { "runs": [{ "text": "Shape of You" }] },
													"shortBylineText": { "runs": [{ "text": "Ed Sheeran" }] },
													"lengthSeconds": "264"
												}
											}
										]
									}
								}]
							}
						}
					}
				}]
			}
		}
	}`)

	playlist, err := parsePlaylistOrAlbumResponse("VLPL_test456", mockJSON)
	if err != nil {
		t.Fatalf("expected successful playlist parsing, got: %v", err)
	}

	if playlist.Title != "Top Global Hits" {
		t.Errorf("expected title 'Top Global Hits', got %q", playlist.Title)
	}
	if playlist.TrackCount != 2 {
		t.Errorf("expected 2 tracks, got %d", playlist.TrackCount)
	}
	if len(playlist.Tracks) != 2 {
		t.Fatalf("expected 2 tracks parsed, got %d", len(playlist.Tracks))
	}
	if playlist.Tracks[0].ID != "kJQP7kiw5Fk" || playlist.Tracks[0].Title != "Despacito" {
		t.Errorf("unexpected track 0: %+v", playlist.Tracks[0])
	}
	if playlist.Tracks[1].ID != "JGwWNGJdvx8" || playlist.Tracks[1].Title != "Shape of You" {
		t.Errorf("unexpected track 1: %+v", playlist.Tracks[1])
	}
}

func TestClient_WithDisableAuth(t *testing.T) {
	ctx := context.Background()
	// Standard browse endpoint is account-bound
	if !shouldAttachAuth(ctx, "browse") {
		t.Errorf("expected browse to be account-bound by default")
	}

	// WithDisableAuth forces it to unauthenticated guest request
	guestCtx := WithDisableAuth(ctx)
	if shouldAttachAuth(guestCtx, "browse") {
		t.Errorf("expected WithDisableAuth to omit auth on browse endpoint")
	}

	// player and search are unauthenticated guest by default
	if shouldAttachAuth(ctx, "player") {
		t.Errorf("expected player to be unauthenticated by default")
	}
	if shouldAttachAuth(ctx, "search") {
		t.Errorf("expected search to be unauthenticated by default")
	}
}
