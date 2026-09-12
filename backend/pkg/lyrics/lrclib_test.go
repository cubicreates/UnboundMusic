package lyrics

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCleanTrackTitle(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"Him & I (Official Video)", "Him & I"},
		{"Starboy [Official Audio]", "Starboy"},
		{"Shape of You (Lyrics)", "Shape of You"},
		{"Blinding Lights (Official Music Video)", "Blinding Lights"},
		{"Stay - Visualizer", "Stay"},
		{"Hello", "Hello"},
	}

	for _, tc := range tests {
		actual := CleanTrackTitle(tc.input)
		if actual != tc.expected {
			t.Errorf("CleanTrackTitle(%q) = %q; want %q", tc.input, actual, tc.expected)
		}
	}
}

func TestLRCLIBClient_SearchFallback(t *testing.T) {
	// Setup mock server where /get returns 404, but /search returns synced results
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/get" {
			http.NotFound(w, r)
			return
		}
		if r.URL.Path == "/search" {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`[
				{
					"id": 999,
					"trackName": "Him & I",
					"artistName": "G-Eazy & Halsey",
					"albumName": "The Beautiful & Damned",
					"duration": 268,
					"instrumental": false,
					"syncedLyrics": "[00:10.50]Cross my heart, hope to die\n[00:14.20]To my lover, I'd never lie",
					"plainLyrics": "Cross my heart, hope to die\nTo my lover, I'd never lie"
				}
			]`))
			return
		}
		http.NotFound(w, r)
	}))
	defer server.Close()

	client := NewLRCLIBClient()
	client.baseURL = server.URL

	// Query with "Song" as artist (simulating signed-in YouTube Music bug)
	payload, err := client.Fetch(context.Background(), "Him & I (Official Video)", "Song", 268)
	if err != nil {
		t.Fatalf("expected successful search fallback, got error: %v", err)
	}

	if payload == nil || len(payload.Lines) != 2 {
		t.Fatalf("unexpected payload: %+v", payload)
	}

	if payload.Lines[0].Text != "Cross my heart, hope to die" {
		t.Errorf("unexpected first line text: %s", payload.Lines[0].Text)
	}
	if payload.Lines[0].StartMs != 10500 {
		t.Errorf("unexpected first line start ms: %d", payload.Lines[0].StartMs)
	}
}
