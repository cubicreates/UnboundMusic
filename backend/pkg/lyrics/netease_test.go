/*
 * Package: lyrics
 * File: netease_test.go
 * Purpose: Unit tests for NetEase Cloud Music search and synchronized LRC parsing.
 * Subsystem: Test Suite
 * Concurrency: Tests execute HTTP requests against mock test servers.
 */

package lyrics

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestNetEaseClient_Fetch(t *testing.T) {
	searchJSON := `{
		"result": {
			"songs": [
				{
					"id": 1001,
					"name": "DNA",
					"duration": 186000,
					"artists": [{"name": "Kendrick Lamar"}]
				}
			]
		}
	}`

	lyricJSON := `{
		"lrc": {
			"version": 1,
			"lyric": "[00:05.12]I got, I got, I got, I got\n[00:10.55]Loyalty, got royalty inside my DNA\n[00:15.80]Cocaine quarter piece"
		}
	}`

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if strings.Contains(r.URL.Path, "/search/get/web") {
			_, _ = w.Write([]byte(searchJSON))
		} else if strings.Contains(r.URL.Path, "/song/lyric") {
			_, _ = w.Write([]byte(lyricJSON))
		} else {
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	client := &NetEaseClient{
		httpClient: server.Client(),
		baseURL:    server.URL,
	}

	payload, err := client.Fetch(context.Background(), "DNA", "Kendrick Lamar", 186)
	if err != nil {
		t.Fatalf("NetEase Fetch failed: %v", err)
	}

	if payload.Title != "DNA" {
		t.Errorf("expected title DNA, got %s", payload.Title)
	}
	if len(payload.Lines) != 3 {
		t.Fatalf("expected 3 lines, got %d", len(payload.Lines))
	}

	line1 := payload.Lines[0]
	if line1.StartMs != 5120 {
		t.Errorf("expected line 1 startMs 5120, got %d", line1.StartMs)
	}
	if line1.Text != "I got, I got, I got, I got" {
		t.Errorf("unexpected text for line 1: %q", line1.Text)
	}

	line2 := payload.Lines[1]
	if line2.StartMs != 10550 {
		t.Errorf("expected line 2 startMs 10550, got %d", line2.StartMs)
	}
}

func TestNetEaseClient_Instrumental(t *testing.T) {
	searchJSON := `{
		"result": {
			"songs": [{"id": 2002, "name": "Guitar Solo", "duration": 120000}]
		}
	}`

	lyricJSON := `{"nolyric": true}`

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if strings.Contains(r.URL.Path, "/search") {
			_, _ = w.Write([]byte(searchJSON))
		} else {
			_, _ = w.Write([]byte(lyricJSON))
		}
	}))
	defer server.Close()

	client := &NetEaseClient{
		httpClient: server.Client(),
		baseURL:    server.URL,
	}

	payload, err := client.Fetch(context.Background(), "Guitar Solo", "Artist", 120)
	if err != nil {
		t.Fatalf("Fetch failed: %v", err)
	}

	if !payload.Instrumental {
		t.Error("expected track to be marked instrumental")
	}
}
