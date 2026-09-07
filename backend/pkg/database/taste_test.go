/*
 * Package: database
 * File: taste_test.go
 * Purpose: Unit tests for on-device taste learning, exponential decay, skip ban, and artist affinity.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Thread-safe in-memory SQLite testing.
 */

package database

import (
	"math"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

func setupTestDB(t *testing.T) *Repository {
	t.Helper()
	db, err := Open(":memory:")
	if err != nil {
		t.Fatalf("Failed opening in-memory database: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	return NewRepository(db)
}

func TestTasteScoreDeltaCalculation(t *testing.T) {
	// 1. Completion > 85%
	delta, evType := CalculateScoreDelta(200000, 180000, "")
	if delta != 1.00 || evType != models.TasteEventComplete {
		t.Errorf("Expected +1.00 COMPLETE, got %.2f %s", delta, evType)
	}

	// 2. Fast skip < 15s
	delta, evType = CalculateScoreDelta(200000, 10000, "")
	if delta != -2.00 || evType != models.TasteEventFastSkip {
		t.Errorf("Expected -2.00 FAST_SKIP, got %.2f %s", delta, evType)
	}

	// 3. Mild skip 15-45s
	delta, evType = CalculateScoreDelta(200000, 30000, "")
	if delta != -0.50 || evType != models.TasteEventMildSkip {
		t.Errorf("Expected -0.50 MILD_SKIP, got %.2f %s", delta, evType)
	}

	// 4. Explicit loop
	delta, evType = CalculateScoreDelta(200000, 200000, models.TasteEventLoop)
	if delta != 2.50 || evType != models.TasteEventLoop {
		t.Errorf("Expected +2.50 LOOP, got %.2f %s", delta, evType)
	}
}

func TestTasteExponentialDecay(t *testing.T) {
	now := time.Now().Unix()
	initialScore := 10.0

	// 0 days elapsed -> exactly same score
	decayed0 := CalculateDecay(initialScore, now, now)
	if math.Abs(decayed0-initialScore) > 0.001 {
		t.Errorf("Expected %.2f, got %.2f", initialScore, decayed0)
	}

	// 7 days elapsed (half-life) -> exactly 50% score
	sevenDaysLater := now + int64(7*86400)
	decayed7 := CalculateDecay(initialScore, now, sevenDaysLater)
	if math.Abs(decayed7-5.0) > 0.05 {
		t.Errorf("Expected ~5.0 after 7 days, got %.2f", decayed7)
	}

	// 14 days elapsed -> ~25% score
	fourteenDaysLater := now + int64(14*86400)
	decayed14 := CalculateDecay(initialScore, now, fourteenDaysLater)
	if math.Abs(decayed14-2.5) > 0.05 {
		t.Errorf("Expected ~2.5 after 14 days, got %.2f", decayed14)
	}
}

func TestTasteFastSkipBanAndRedemption(t *testing.T) {
	repo := setupTestDB(t)
	now := time.Now().Unix()

	artistID := "artist_synthwave_01"
	artistName := "The Midnight"

	// 1. First fast skip (< 15s)
	err := repo.RecordTasteEvent(models.TasteEvent{
		TrackID:    "track_01",
		Title:      "Sunset",
		ArtistID:   artistID,
		ArtistName: artistName,
		DurationMs: 240000,
		ListenedMs: 5000,
		Timestamp:  now,
	})
	if err != nil {
		t.Fatalf("Failed recording taste event 1: %v", err)
	}

	top, err := repo.GetTopAffinityArtists(10)
	if err != nil || len(top) != 1 {
		t.Fatalf("Expected 1 artist, got %d (err: %v)", len(top), err)
	}
	if top[0].AffinityScore != -2.00 || top[0].IsBanned || top[0].FastSkipStreak != 1 {
		t.Errorf("Expected score -2.00, streak 1, banned=false; got %+v", top[0])
	}

	// 2. Second fast skip
	_ = repo.RecordTasteEvent(models.TasteEvent{
		TrackID:    "track_02",
		Title:      "Days of Thunder",
		ArtistID:   artistID,
		ArtistName: artistName,
		DurationMs: 240000,
		ListenedMs: 8000,
		Timestamp:  now + 10,
	})

	top, _ = repo.GetTopAffinityArtists(10)
	if len(top) != 1 || top[0].FastSkipStreak != 2 || top[0].IsBanned {
		t.Errorf("Expected streak 2, banned=false; got %+v", top[0])
	}

	// 3. Third fast skip -> triggers 14-day ban
	_ = repo.RecordTasteEvent(models.TasteEvent{
		TrackID:    "track_03",
		Title:      "Vampires",
		ArtistID:   artistID,
		ArtistName: artistName,
		DurationMs: 240000,
		ListenedMs: 7000,
		Timestamp:  now + 20,
	})

	// Banned artist must be excluded from GetTopAffinityArtists
	topAfterBan, _ := repo.GetTopAffinityArtists(10)
	if len(topAfterBan) != 0 {
		t.Errorf("Expected 0 active artists after 3-skip ban, got %d", len(topAfterBan))
	}

	// 4. Redemption play: Completed track (> 85%) lifts the ban immediately
	_ = repo.RecordTasteEvent(models.TasteEvent{
		TrackID:    "track_04",
		Title:      "Gloria",
		ArtistID:   artistID,
		ArtistName: artistName,
		DurationMs: 240000,
		ListenedMs: 230000, // > 85%
		Timestamp:  now + 30,
	})

	topAfterRedemption, _ := repo.GetTopAffinityArtists(10)
	if len(topAfterRedemption) != 1 {
		t.Fatalf("Expected artist to be unbanned after redemption play, got %d", len(topAfterRedemption))
	}
	if topAfterRedemption[0].IsBanned || topAfterRedemption[0].FastSkipStreak != 0 {
		t.Errorf("Expected banned=false and streak=0, got %+v", topAfterRedemption[0])
	}
}

func TestTasteDiversityScore(t *testing.T) {
	repo := setupTestDB(t)

	// Single artist -> default score
	_ = repo.RecordTasteEvent(models.TasteEvent{
		TrackID:    "t1",
		Title:      "Song 1",
		ArtistID:   "a1",
		ArtistName: "Artist 1",
		DurationMs: 200000,
		ListenedMs: 190000,
	})

	score, err := repo.GetTasteDiversityScore()
	if err != nil || score != 5.0 {
		t.Errorf("Expected 5.0 for single artist, got %.1f (err: %v)", score, err)
	}

	// Multiple artists with equal plays -> maximum diversity (10.0)
	for i := 2; i <= 5; i++ {
		_ = repo.RecordTasteEvent(models.TasteEvent{
			TrackID:    "t" + string(rune('0'+i)),
			Title:      "Song",
			ArtistID:   "a" + string(rune('0'+i)),
			ArtistName: "Artist",
			DurationMs: 200000,
			ListenedMs: 190000,
		})
	}

	score, err = repo.GetTasteDiversityScore()
	if err != nil || score < 9.0 {
		t.Errorf("Expected high diversity score (> 9.0) for balanced distribution, got %.1f", score)
	}
}
