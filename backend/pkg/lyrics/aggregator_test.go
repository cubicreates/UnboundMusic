/*
 * Package: lyrics
 * File: aggregator_test.go
 * Purpose: Unit tests for 3-tier cascade lyrics aggregator and SQLite caching.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Standard Go testing framework.
 */

package lyrics

import (
	"context"
	"fmt"
	"sync"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// mockCache simulates SQLite caching in-memory.
type mockCache struct {
	mu    sync.Mutex
	store map[string]*models.LyricsPayload
}

func newMockCache() *mockCache {
	return &mockCache{
		store: make(map[string]*models.LyricsPayload),
	}
}

func (m *mockCache) GetLyrics(ctx context.Context, trackID string) (*models.LyricsPayload, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if item, ok := m.store[trackID]; ok {
		return item, nil
	}
	return nil, nil
}

func (m *mockCache) SaveLyrics(ctx context.Context, payload *models.LyricsPayload) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.store[payload.TrackID] = payload
	return nil
}

// mockLRCLIB simulates LRCLIB responses.
type mockLRCLIB struct {
	response *models.LyricsPayload
	err      error
}

func (m *mockLRCLIB) Fetch(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error) {
	if m.err != nil {
		return nil, m.err
	}
	return m.response, nil
}

// mockYT simulates YouTube transcript responses.
type mockYT struct {
	lines []ytmusic.LyricLine
	err   error
}

func (m *mockYT) GetTranscript(ctx context.Context, videoID string) ([]ytmusic.LyricLine, error) {
	if m.err != nil {
		return nil, m.err
	}
	return m.lines, nil
}

// mockGenius simulates Genius scraper responses.
type mockGenius struct {
	hit     *genius.SongHit
	payload *models.LyricsPayload
	err     error
}

func (m *mockGenius) SearchSong(ctx context.Context, title, artist string) (*genius.SongHit, error) {
	if m.err != nil {
		return nil, m.err
	}
	return m.hit, nil
}

func (m *mockGenius) FetchLyrics(ctx context.Context, hit *genius.SongHit) (*models.LyricsPayload, error) {
	if m.err != nil {
		return nil, m.err
	}
	return m.payload, nil
}

func TestAggregator_CacheHit(t *testing.T) {
	cache := newMockCache()
	cachedPayload := &models.LyricsPayload{
		TrackID: "cached_123",
		Title:   "Cached Song",
		Artist:  "Cached Artist",
		Lines: []models.LyricLine{
			{StartMs: 1000, EndMs: 2000, Text: "Already cached line"},
		},
		Source: "SQLite Cache",
	}
	_ = cache.SaveLyrics(context.Background(), cachedPayload)

	agg := NewAggregator(cache, nil, nil, nil)
	result, err := agg.GetLyrics(context.Background(), "cached_123", "Cached Song", "Cached Artist", 180)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result.Title != "Cached Song" || result.Source != "SQLite Cache" {
		t.Errorf("expected cached payload, got %+v", result)
	}
}

func TestAggregator_Tier1_LRCLIB_Success(t *testing.T) {
	cache := newMockCache()
	lrclib := &mockLRCLIB{
		response: &models.LyricsPayload{
			Title:  "Tokyo Drift",
			Artist: "Teriyaki Boyz",
			Lines: []models.LyricLine{
				{StartMs: 500, EndMs: 2500, Text: "ありがとう"},
			},
			Source: "LRCLIB Synced Lyrics",
		},
	}

	agg := NewAggregator(cache, lrclib, nil, nil)
	result, err := agg.GetLyrics(context.Background(), "yt_tokyo", "Tokyo Drift", "Teriyaki Boyz", 200)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(result.Lines) != 1 {
		t.Fatalf("expected 1 line, got %d", len(result.Lines))
	}

	// Verify Romanization was automatically applied
	if result.Lines[0].Romanized != "arigatou" {
		t.Errorf("expected romanized 'arigatou', got %q", result.Lines[0].Romanized)
	}

	// Verify it was cached in SQLite
	cached, _ := cache.GetLyrics(context.Background(), "yt_tokyo")
	if cached == nil {
		t.Errorf("expected payload to be cached in SQLite")
	}
}

// mockNetEase simulates NetEase Cloud Music responses.
type mockNetEase struct {
	payload *models.LyricsPayload
	err     error
}

func (m *mockNetEase) Fetch(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error) {
	if m.err != nil {
		return nil, m.err
	}
	return m.payload, nil
}

func TestAggregator_Tier2_NetEase_Success(t *testing.T) {
	cache := newMockCache()
	lrclib := &mockLRCLIB{err: fmt.Errorf("not found on lrclib")}
	netease := &mockNetEase{
		payload: &models.LyricsPayload{
			Title:  "DNA",
			Artist: "Kendrick Lamar",
			Lines: []models.LyricLine{
				{StartMs: 5000, EndMs: 10000, Text: "I got royalty inside my DNA"},
			},
			Source: "NetEase Cloud Music Synced LRC",
		},
	}

	agg := NewAggregator(cache, lrclib, nil, nil, netease)
	result, err := agg.GetLyrics(context.Background(), "yt_dna", "DNA", "Kendrick Lamar", 186)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result.Source != "NetEase Cloud Music Synced LRC" {
		t.Errorf("expected NetEase source, got %q", result.Source)
	}
	if len(result.Lines) != 1 {
		t.Fatalf("expected 1 line, got %d", len(result.Lines))
	}
}

func TestAggregator_Tier3_YouTubeFallback(t *testing.T) {
	cache := newMockCache()
	lrclib := &mockLRCLIB{err: fmt.Errorf("not found on lrclib")}
	netease := &mockNetEase{err: fmt.Errorf("netease not found")}
	yt := &mockYT{
		lines: []ytmusic.LyricLine{
			{StartMs: 1000, EndMs: 4000, Text: "사랑해 I love you"},
		},
	}

	agg := NewAggregator(cache, lrclib, yt, nil, netease)
	result, err := agg.GetLyrics(context.Background(), "kpop_vid", "Love Song", "K-Artist", 190)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result.Source != "YouTube InnerTube Captions" {
		t.Errorf("expected source to be YouTube Captions, got %q", result.Source)
	}

	if len(result.Lines) != 1 {
		t.Fatalf("expected 1 line, got %d", len(result.Lines))
	}

	if result.Lines[0].Romanized == "" {
		t.Errorf("expected romanized text for Korean characters")
	}
}

func TestAggregator_Tier4_GeniusFallback(t *testing.T) {
	cache := newMockCache()
	lrclib := &mockLRCLIB{err: fmt.Errorf("not found on lrclib")}
	netease := &mockNetEase{err: fmt.Errorf("netease not found")}
	yt := &mockYT{err: fmt.Errorf("no transcript on youtube")}
	g := &mockGenius{
		hit: &genius.SongHit{ID: 1, Title: "Rare Song", Artist: "Indie Singer"},
		payload: &models.LyricsPayload{
			Title:       "Rare Song",
			Artist:      "Indie Singer",
			PlainLyrics: "Just some acoustic lyrics here",
			Source:      "Genius Scraper",
		},
	}

	agg := NewAggregator(cache, lrclib, yt, g, netease)
	result, err := agg.GetLyrics(context.Background(), "indie_vid", "Rare Song", "Indie Singer", 150)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result.PlainLyrics != "Just some acoustic lyrics here" {
		t.Errorf("unexpected plain lyrics: %q", result.PlainLyrics)
	}
}

func TestAggregator_InstrumentalTrack(t *testing.T) {
	cache := newMockCache()
	lrclib := &mockLRCLIB{
		response: &models.LyricsPayload{
			Title:        "Interstellar Main Theme",
			Artist:       "Hans Zimmer",
			PlainLyrics:  "[Instrumental Track - Pure Audio]",
			Instrumental: true,
			Source:       "LRCLIB (Instrumental)",
		},
	}

	agg := NewAggregator(cache, lrclib, nil, nil)
	result, err := agg.GetLyrics(context.Background(), "zimmer_theme", "Interstellar Main Theme", "Hans Zimmer", 300)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if !result.Instrumental {
		t.Errorf("expected Instrumental to be true")
	}
	if result.PlainLyrics != "[Instrumental Track - Pure Audio]" {
		t.Errorf("unexpected plain lyrics: %q", result.PlainLyrics)
	}
}
