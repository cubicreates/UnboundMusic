/*
 * Package: database
 * File: taste.go
 * Purpose: SQLite storage and mathematical scoring algorithms for on-device taste learning.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Thread-safe database operations utilizing SQLite transactions.
 */

package database

import (
	"database/sql"
	"fmt"
	"math"
	"sort"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

const (
	// TasteDecayHalfLifeDays specifies the 7-day half-life for musical taste recency decay.
	TasteDecayHalfLifeDays = 7.0

	// FastSkipThresholdMs defines < 15 seconds as a hard skip / dislike.
	FastSkipThresholdMs = int64(15000)

	// MildSkipThresholdMs defines 15 to 45 seconds as a mild skip / disinterest.
	MildSkipThresholdMs = int64(45000)

	// CompletionThresholdRatio defines >= 85% listened as strong positive affinity.
	CompletionThresholdRatio = 0.85

	// FastSkipBanStreak defines 3 consecutive fast skips without a completion triggers a 14-day ban.
	FastSkipBanStreak = 3

	// BanDurationSeconds defines 14 days in seconds.
	BanDurationSeconds = int64(14 * 86400)
)

// CalculateScoreDelta evaluates duration, listened time, and event type to produce the mathematical score delta.
func CalculateScoreDelta(durationMs, listenedMs int64, explicitType models.TasteEventType) (float64, models.TasteEventType) {
	if explicitType != "" {
		switch explicitType {
		case models.TasteEventLoop:
			return 2.50, models.TasteEventLoop
		case models.TasteEventVolumeUp:
			return 0.50, models.TasteEventVolumeUp
		case models.TasteEventSeekBack:
			return 1.50, models.TasteEventSeekBack
		case models.TasteEventQueueAdd:
			return 1.50, models.TasteEventQueueAdd
		case models.TasteEventFastSkip:
			return -2.00, models.TasteEventFastSkip
		case models.TasteEventMildSkip:
			return -0.50, models.TasteEventMildSkip
		case models.TasteEventComplete:
			return 1.00, models.TasteEventComplete
		}
	}

	var ratio float64
	if durationMs > 0 {
		ratio = float64(listenedMs) / float64(durationMs)
	}

	if ratio >= CompletionThresholdRatio {
		return 1.00, models.TasteEventComplete
	}

	if listenedMs < FastSkipThresholdMs {
		return -2.00, models.TasteEventFastSkip
	}

	if listenedMs < MildSkipThresholdMs {
		return -0.50, models.TasteEventMildSkip
	}

	return 1.00, models.TasteEventComplete
}

// CalculateDecay applies exponential recency decay to an affinity score over elapsed time.
func CalculateDecay(score float64, lastListenedTimestamp, currentTimestamp int64) float64 {
	if currentTimestamp <= lastListenedTimestamp {
		return score
	}

	deltaDays := float64(currentTimestamp-lastListenedTimestamp) / 86400.0
	lambda := math.Ln2 / TasteDecayHalfLifeDays
	decayFactor := math.Exp(-lambda * deltaDays)
	return score * decayFactor
}

// RecordTasteEvent saves an interaction event and updates the artist affinity score in SQLite.
func (r *Repository) RecordTasteEvent(event models.TasteEvent) error {
	now := event.Timestamp
	if now <= 0 {
		now = time.Now().Unix()
		event.Timestamp = now
	}

	delta, eventType := CalculateScoreDelta(event.DurationMs, event.ListenedMs, event.EventType)
	event.ScoreDelta = delta
	event.EventType = eventType
	if event.DurationMs > 0 {
		event.CompletionRatio = float64(event.ListenedMs) / float64(event.DurationMs)
	}

	artistID := event.ArtistID
	if artistID == "" {
		artistID = event.ArtistName
	}
	event.ArtistID = artistID

	tx, err := r.db.conn.Begin()
	if err != nil {
		return fmt.Errorf("failed beginning taste event transaction: %w", err)
	}
	defer tx.Rollback()

	// 1. Insert into taste_events log
	insertEventQuery := `
		INSERT INTO taste_events (
			track_id, title, artist_id, artist_name, genre,
			duration_ms, listened_ms, completion_ratio, score_delta,
			event_type, timestamp
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
	`
	_, err = tx.Exec(
		insertEventQuery,
		event.TrackID, event.Title, event.ArtistID, event.ArtistName, event.Genre,
		event.DurationMs, event.ListenedMs, event.CompletionRatio, event.ScoreDelta,
		string(event.EventType), event.Timestamp,
	)
	if err != nil {
		return fmt.Errorf("failed inserting taste event: %w", err)
	}

	// 2. Query current artist affinity record if it exists
	var currentScore float64
	var playCount, skipCount, fastSkipStreak, isBanned int
	var banUntilTimestamp, lastListenedTimestamp int64
	var found bool

	queryAffinity := `
		SELECT affinity_score, play_count, skip_count, fast_skip_streak, is_banned, ban_until_timestamp, last_listened_timestamp
		FROM artist_affinity
		WHERE artist_id = ?;
	`
	err = tx.QueryRow(queryAffinity, artistID).Scan(
		&currentScore, &playCount, &skipCount, &fastSkipStreak,
		&isBanned, &banUntilTimestamp, &lastListenedTimestamp,
	)
	if err == nil {
		found = true
	} else if err != sql.ErrNoRows {
		return fmt.Errorf("failed querying artist affinity: %w", err)
	}

	// 3. Apply state machine transitions
	if found {
		// Apply decay before accumulating new score delta
		decayedScore := CalculateDecay(currentScore, lastListenedTimestamp, now)
		newScore := decayedScore + delta

		if eventType == models.TasteEventFastSkip {
			skipCount++
			fastSkipStreak++
			if fastSkipStreak >= FastSkipBanStreak {
				isBanned = 1
				banUntilTimestamp = now + BanDurationSeconds
			}
		} else if eventType == models.TasteEventMildSkip {
			skipCount++
		} else {
			// Complete, loop, volume-up, or seek-back acts as positive redemption
			playCount++
			fastSkipStreak = 0
			isBanned = 0
			banUntilTimestamp = 0
		}

		updateQuery := `
			UPDATE artist_affinity SET
				artist_name = ?,
				affinity_score = ?,
				play_count = ?,
				skip_count = ?,
				fast_skip_streak = ?,
				is_banned = ?,
				ban_until_timestamp = ?,
				last_listened_timestamp = ?
			WHERE artist_id = ?;
		`
		_, err = tx.Exec(
			updateQuery,
			event.ArtistName, newScore, playCount, skipCount,
			fastSkipStreak, isBanned, banUntilTimestamp, now, artistID,
		)
		if err != nil {
			return fmt.Errorf("failed updating artist affinity: %w", err)
		}
	} else {
		// First-time record for this artist
		playCount = 0
		skipCount = 0
		fastSkipStreak = 0
		isBanned = 0
		banUntilTimestamp = 0

		if eventType == models.TasteEventFastSkip {
			skipCount = 1
			fastSkipStreak = 1
		} else if eventType == models.TasteEventMildSkip {
			skipCount = 1
		} else {
			playCount = 1
		}

		insertAffinity := `
			INSERT INTO artist_affinity (
				artist_id, artist_name, affinity_score, play_count,
				skip_count, fast_skip_streak, is_banned, ban_until_timestamp,
				last_listened_timestamp
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
		`
		_, err = tx.Exec(
			insertAffinity,
			artistID, event.ArtistName, delta, playCount,
			skipCount, fastSkipStreak, isBanned, banUntilTimestamp, now,
		)
		if err != nil {
			return fmt.Errorf("failed inserting initial artist affinity: %w", err)
		}
	}

	return tx.Commit()
}

// GetTopAffinityArtists retrieves non-banned artists sorted descending by their decayed affinity scores.
func (r *Repository) GetTopAffinityArtists(limit int) ([]models.ArtistAffinity, error) {
	if limit <= 0 {
		limit = 10
	}

	now := time.Now().Unix()

	query := `
		SELECT artist_id, artist_name, affinity_score, play_count, skip_count,
		       fast_skip_streak, is_banned, ban_until_timestamp, last_listened_timestamp
		FROM artist_affinity
		WHERE is_banned = 0 OR ban_until_timestamp <= ?;
	`
	rows, err := r.db.conn.Query(query, now)
	if err != nil {
		return nil, fmt.Errorf("failed querying top affinity artists: %w", err)
	}
	defer rows.Close()

	var list []models.ArtistAffinity
	for rows.Next() {
		var a models.ArtistAffinity
		var lastTs int64
		var isBannedInt int

		err := rows.Scan(
			&a.ArtistID, &a.ArtistName, &a.AffinityScore, &a.PlayCount, &a.SkipCount,
			&a.FastSkipStreak, &isBannedInt, &a.BanUntilTimestamp, &lastTs,
		)
		if err != nil {
			return nil, fmt.Errorf("failed scanning artist affinity row: %w", err)
		}

		a.IsBanned = isBannedInt == 1
		a.LastListenedAt = time.Unix(lastTs, 0)
		a.DecayedScore = CalculateDecay(a.AffinityScore, lastTs, now)

		list = append(list, a)
	}

	// Sort descending by decayed score
	sort.Slice(list, func(i, j int) bool {
		return list[i].DecayedScore > list[j].DecayedScore
	})

	if len(list) > limit {
		list = list[:limit]
	}

	return list, nil
}

// GetTasteDiversityScore calculates the Shannon entropy across artist plays on a 0.0 to 10.0 scale.
func (r *Repository) GetTasteDiversityScore() (float64, error) {
	query := `
		SELECT play_count
		FROM artist_affinity
		WHERE play_count > 0;
	`
	rows, err := r.db.conn.Query(query)
	if err != nil {
		return 5.0, fmt.Errorf("failed querying plays for diversity: %w", err)
	}
	defer rows.Close()

	var counts []float64
	var total float64

	for rows.Next() {
		var c int
		if err := rows.Scan(&c); err == nil && c > 0 {
			counts = append(counts, float64(c))
			total += float64(c)
		}
	}

	if len(counts) <= 1 || total <= 0 {
		return 5.0, nil
	}

	// Shannon entropy: H = - sum(p * ln(p))
	var entropy float64
	for _, c := range counts {
		p := c / total
		if p > 0 {
			entropy -= p * math.Log(p)
		}
	}

	// Maximum possible entropy for N artists: H_max = ln(N)
	maxEntropy := math.Log(float64(len(counts)))
	if maxEntropy <= 0 {
		return 5.0, nil
	}

	// Normalized score from 0.0 to 10.0
	score := (entropy / maxEntropy) * 10.0
	if score > 10.0 {
		score = 10.0
	}
	if score < 0.0 {
		score = 0.0
	}

	return math.Round(score*10.0) / 10.0, nil
}
