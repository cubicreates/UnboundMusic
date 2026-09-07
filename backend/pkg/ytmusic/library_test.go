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
