/*
 * Package: genius
 * File: genius_test.go
 * Purpose: Unit tests for Genius HTML parsing, LRCLIB sync extraction, and syllable tokenization.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing framework.
 */

package genius

import (
	"context"
	"strings"
	"testing"
)

// TestExtractLyricsFromHTML validates DOM extraction from mock Genius HTML fragments.
func TestExtractLyricsFromHTML(t *testing.T) {
	mockHTML := `
		<html>
			<body>
				<div data-lyrics-container="true">
					[Verse 1]<br/>
					I got loyalty, got royalty inside my DNA<br/>
					Cocaine quarter piece, got war and peace inside my DNA
				</div>
				<div data-lyrics-container="true">
					[Chorus]<br/>
					Tell me something, you motherfuckers can&#x27;t tell me nothing
				</div>
			</body>
		</html>
	`

	extracted, err := extractLyricsFromHTML(mockHTML)
	if err != nil {
		t.Fatalf("unexpected error parsing mock HTML: %v", err)
	}

	if !strings.Contains(extracted, "I got loyalty, got royalty inside my DNA") {
		t.Errorf("expected lyrics text missing from output")
	}

	if !strings.Contains(extracted, "can't tell me nothing") {
		t.Errorf("expected unescaped HTML entities in output")
	}
}

// TestParseLRCLyrics validates conversion of timestamped LRC strings to LyricLine slices.
func TestParseLRCLyrics(t *testing.T) {
	mockLRC := `
[00:15.30]First line of the song
[00:20.50]Second line with more words
[00:28.00]Third line ending
`
	lines := ParseLRCLyrics(mockLRC)
	if len(lines) != 3 {
		t.Fatalf("expected 3 lines, got %d", len(lines))
	}

	if lines[0].StartMs != 15300 {
		t.Errorf("line 0 StartMs = %d, expected 15300", lines[0].StartMs)
	}

	if lines[0].EndMs != 20500 {
		t.Errorf("line 0 EndMs = %d, expected 20500", lines[0].EndMs)
	}

	if len(lines[0].Syllables) != 5 {
		t.Errorf("line 0 expected 5 syllables/words, got %d", len(lines[0].Syllables))
	}
}

// TestLiveGeniusSearch validates live search and lyrics scraping on genius.com.
func TestLiveGeniusSearch(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping live network test in short mode")
	}

	client := NewClient()
	ctx := context.Background()

	hit, err := client.SearchSong(ctx, "Kendrick Lamar", "DNA")
	if err != nil {
		t.Fatalf("Genius search failed: %v", err)
	}

	if hit.Path == "" && hit.URL == "" {
		t.Fatalf("expected song path or URL from Genius hit")
	}

	lyrics, err := client.FetchLyrics(ctx, hit)
	if err != nil {
		t.Fatalf("Genius lyrics fetch failed: %v", err)
	}

	if len(lyrics.PlainLyrics) == 0 {
		t.Errorf("expected non-empty plain lyrics")
	}
}
