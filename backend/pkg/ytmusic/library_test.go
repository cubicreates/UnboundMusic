package ytmusic

import (
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

