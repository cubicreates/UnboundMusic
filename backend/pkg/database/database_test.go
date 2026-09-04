/*
 * Package: database
 * File: database_test.go
 * Purpose: Unit tests for SQLite database initialization, table migrations, and DAO operations.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using temporary SQLite databases.
 */

package database

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// TestDatabaseOpenAndMigrate validates SQLite schema migration on startup.
func TestDatabaseOpenAndMigrate(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_unbound.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	if _, err := os.Stat(dbPath); os.IsNotExist(err) {
		t.Errorf("database file was not created at %s", dbPath)
	}
}

// TestRepositoryTrackAndLyricsCRUD tests saving and reading tracks and timed lyrics from SQLite.
func TestRepositoryTrackAndLyricsCRUD(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_crud.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	// 1. Test Track Save & Get
	sampleTrack := &models.Track{
		ID:           "test_track_123",
		Title:        "DNA.",
		Artist:       "Kendrick Lamar",
		Album:        "DAMN.",
		DurationMs:   186000,
		Codec:        "opus",
		BitrateKbps:  160,
		IsLocal:      true,
		LocalPath:    "/Music/DAMN/02_DNA.mp3",
		ThumbnailURL: "https://example.com/art.jpg",
	}

	if err := repo.SaveTrack(ctx, sampleTrack); err != nil {
		t.Fatalf("SaveTrack failed: %v", err)
	}

	retrievedTrack, err := repo.GetTrack(ctx, "test_track_123")
	if err != nil {
		t.Fatalf("GetTrack failed: %v", err)
	}
	if retrievedTrack == nil {
		t.Fatalf("expected track, got nil")
	}

	if retrievedTrack.Title != "DNA." || retrievedTrack.Artist != "Kendrick Lamar" || !retrievedTrack.IsLocal {
		t.Errorf("retrieved track fields do not match inserted track: %+v", retrievedTrack)
	}

	// 2. Test Lyrics Save & Get
	sampleLyrics := &models.LyricsPayload{
		TrackID:      "test_track_123",
		Title:        "DNA.",
		Artist:       "Kendrick Lamar",
		PlainLyrics:  "I got loyalty, got royalty inside my DNA",
		IsWordSynced: true,
		Source:       "Genius + On-Device Aligner",
		Lines: []models.LyricLine{
			{
				Text:    "I got loyalty, got royalty inside my DNA",
				StartMs: 2500,
				EndMs:   6800,
				Syllables: []models.Syllable{
					{Text: "Loyalty", StartMs: 2500, EndMs: 3500},
					{Text: "Royalty", StartMs: 3500, EndMs: 4800},
					{Text: "DNA", StartMs: 4800, EndMs: 6800},
				},
			},
		},
	}

	if err := repo.SaveLyrics(ctx, sampleLyrics); err != nil {
		t.Fatalf("SaveLyrics failed: %v", err)
	}

	cachedLyrics, err := repo.GetLyrics(ctx, "test_track_123")
	if err != nil {
		t.Fatalf("GetLyrics failed: %v", err)
	}
	if cachedLyrics == nil {
		t.Fatalf("expected cached lyrics, got nil")
	}

	if len(cachedLyrics.Lines) != 1 || cachedLyrics.Lines[0].StartMs != 2500 {
		t.Errorf("cached lyrics mismatch: %+v", cachedLyrics)
	}

	// 3. Test Fingerprint Save & Lookup
	if err := repo.SaveFingerprint(ctx, "hash_abc_999", "/Music/DAMN/02_DNA.mp3", 186000); err != nil {
		t.Fatalf("SaveFingerprint failed: %v", err)
	}

	matchedPath, err := repo.GetPathByFingerprint(ctx, "hash_abc_999")
	if err != nil {
		t.Fatalf("GetPathByFingerprint failed: %v", err)
	}
	if matchedPath != "/Music/DAMN/02_DNA.mp3" {
		t.Errorf("fingerprint matched path mismatch: %s", matchedPath)
	}
}

// TestLocalTrackUpsertAndFetchBySource tests indexing local tracks across different source folders.
func TestLocalTrackUpsertAndFetchBySource(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_local_tracks.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	tracks := []*models.LocalTrack{
		{
			ID:           "loc_1",
			FilePath:     "/storage/emulated/0/Download/song1.mp3",
			Title:        "Download Song",
			Artist:       "Artist A",
			Album:        "Album A",
			DurationMs:   200000,
			Format:       "mp3",
			FileSize:     5000000,
			SourceFolder: "downloads",
			DateIndexed:  1725350000,
			MTime:        1725340000,
		},
		{
			ID:           "loc_2",
			FilePath:     "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20240902-WA0001.opus",
			Title:        "WhatsApp Audio 1",
			Artist:       "Unknown Artist",
			Album:        "",
			DurationMs:   150000,
			Format:       "opus",
			FileSize:     2500000,
			SourceFolder: "whatsapp",
			DateIndexed:  1725350010,
			MTime:        1725340010,
		},
		{
			ID:           "loc_3",
			FilePath:     "/storage/emulated/0/Telegram/Telegram Audio/audio.m4a",
			Title:        "Telegram Audio",
			Artist:       "Artist T",
			Album:        "Album T",
			DurationMs:   180000,
			Format:       "m4a",
			FileSize:     3500000,
			SourceFolder: "telegram",
			DateIndexed:  1725350020,
			MTime:        1725340020,
		},
	}

	for _, tr := range tracks {
		if err := repo.UpsertLocalTrack(ctx, tr); err != nil {
			t.Fatalf("UpsertLocalTrack failed for %s: %v", tr.ID, err)
		}
	}

	// 1. Verify WhatsApp source filter
	waTracks, err := repo.GetLocalTracksBySource(ctx, "whatsapp")
	if err != nil {
		t.Fatalf("GetLocalTracksBySource failed: %v", err)
	}
	if len(waTracks) != 1 || waTracks[0].Format != "opus" {
		t.Errorf("expected 1 whatsapp track with format opus, got: %+v", waTracks)
	}

	// 2. Verify all local tracks count
	allTracks, err := repo.GetAllLocalTracks(ctx)
	if err != nil {
		t.Fatalf("GetAllLocalTracks failed: %v", err)
	}
	if len(allTracks) != 3 {
		t.Errorf("expected 3 total local tracks, got: %d", len(allTracks))
	}

	// 3. Verify lookup by file path
	found, err := repo.GetLocalTrackByPath(ctx, "/storage/emulated/0/Download/song1.mp3")
	if err != nil {
		t.Fatalf("GetLocalTrackByPath failed: %v", err)
	}
	if found == nil || found.Title != "Download Song" {
		t.Errorf("expected Download Song, got: %+v", found)
	}

	// 4. Update track metadata via upsert
	tracks[0].Title = "Updated Download Song"
	if err := repo.UpsertLocalTrack(ctx, tracks[0]); err != nil {
		t.Fatalf("UpsertLocalTrack update failed: %v", err)
	}
	updated, err := repo.GetLocalTrackByPath(ctx, "/storage/emulated/0/Download/song1.mp3")
	if err != nil {
		t.Fatalf("GetLocalTrackByPath after update failed: %v", err)
	}
	if updated.Title != "Updated Download Song" {
		t.Errorf("expected title to be updated, got: %s", updated.Title)
	}
}

// TestFingerprintRecordUpsertAndLookup tests storing and retrieving acoustic metadata by Chromaprint hash.
func TestFingerprintRecordUpsertAndLookup(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_fingerprints.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	record := &models.FingerprintRecord{
		Hash:       "AQAAZEkUSUlCRVLwH0eO5vjx4xKOK_rh4_jhox...",
		FilePath:   "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-01.opus",
		Title:      "Blinding Lights",
		Artist:     "The Weeknd",
		Album:      "After Hours",
		DurationMs: 200000,
		Source:     "acoustid",
		UpdatedAt:  1725350000,
	}

	if err := repo.UpsertFingerprint(ctx, record); err != nil {
		t.Fatalf("UpsertFingerprint failed: %v", err)
	}

	fetched, err := repo.GetFingerprintByHash(ctx, record.Hash)
	if err != nil {
		t.Fatalf("GetFingerprintByHash failed: %v", err)
	}
	if fetched == nil {
		t.Fatalf("expected fingerprint record, got nil")
	}

	if fetched.Title != "Blinding Lights" || fetched.Artist != "The Weeknd" || fetched.Album != "After Hours" {
		t.Errorf("fingerprint metadata mismatch: %+v", fetched)
	}
}

// TestPlaybackEventsTelemetry tests recording offline listening actions and retrieving recent events.
func TestPlaybackEventsTelemetry(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_events.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	events := []*models.PlaybackEvent{
		{
			EventID:        "evt_1",
			TrackID:        "track_1",
			Title:          "Song One",
			Artist:         "Artist A",
			Album:          "Album A",
			Genre:          "Phonk",
			DurationSec:    180,
			ListenedSec:    180,
			CompletedRatio: 1.0,
			Timestamp:      1000,
		},
		{
			EventID:        "evt_2",
			TrackID:        "track_2",
			Title:          "Song Two",
			Artist:         "Artist B",
			Album:          "Album B",
			Genre:          "Lo-Fi",
			DurationSec:    200,
			ListenedSec:    160,
			CompletedRatio: 0.8,
			Timestamp:      2000,
		},
	}

	for _, ev := range events {
		if err := repo.RecordPlaybackEvent(ctx, ev); err != nil {
			t.Fatalf("RecordPlaybackEvent failed: %v", err)
		}
	}

	recent, err := repo.GetRecentPlaybackEvents(ctx, 10)
	if err != nil {
		t.Fatalf("GetRecentPlaybackEvents failed: %v", err)
	}
	if len(recent) != 2 {
		t.Fatalf("expected 2 events, got: %d", len(recent))
	}

	// Verify descending order by timestamp
	if recent[0].EventID != "evt_2" || recent[1].EventID != "evt_1" {
		t.Errorf("events not ordered by timestamp DESC: %+v", recent)
	}
}

// TestFeedCacheTTLAndExpiration tests setting and getting cached explore feeds with TTL expiration.
func TestFeedCacheTTLAndExpiration(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test_cache.db")

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	repo := NewRepository(db)
	ctx := context.Background()

	cacheKey := "charts:US:en"
	testPayload := `{"tracks":[{"id":"abc","title":"Billboard Hit"}]}`

	// 1. Cache with 10 second TTL
	if err := repo.SetFeedCache(ctx, cacheKey, testPayload, 10); err != nil {
		t.Fatalf("SetFeedCache failed: %v", err)
	}

	cached, err := repo.GetFeedCache(ctx, cacheKey)
	if err != nil {
		t.Fatalf("GetFeedCache failed: %v", err)
	}
	if cached != testPayload {
		t.Errorf("expected cached payload %s, got %s", testPayload, cached)
	}

	// 2. Cache with -1 second TTL (already expired)
	expiredKey := "charts:expired"
	if err := repo.SetFeedCache(ctx, expiredKey, testPayload, -1); err != nil {
		t.Fatalf("SetFeedCache with negative TTL failed: %v", err)
	}

	expiredResult, err := repo.GetFeedCache(ctx, expiredKey)
	if err != nil {
		t.Fatalf("GetFeedCache on expired key returned error: %v", err)
	}
	if expiredResult != "" {
		t.Errorf("expected empty string for expired cache key, got: %s", expiredResult)
	}
}
