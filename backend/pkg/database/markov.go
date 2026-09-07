/*
 * Package: database
 * File: markov.go
 * Purpose: SQLite persistence for track-to-track Markov transition edges and progression counts.
 * Subsystem: On-Device Taste Engine
 * Concurrency: Thread-safe database operations utilizing SQLite upserts.
 */

package database

import (
	"database/sql"
	"fmt"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// RecordTrackTransition upserts a directed edge between source and target audio tracks.
func (r *Repository) RecordTrackTransition(sourceTrackID, targetTrackID string, timestamp int64) error {
	if sourceTrackID == "" || targetTrackID == "" {
		return fmt.Errorf("source and target track IDs must not be empty")
	}
	if sourceTrackID == targetTrackID {
		return nil // Avoid trivial self-loops unless explicitly requested
	}
	if timestamp <= 0 {
		timestamp = time.Now().Unix()
	}

	query := `
		INSERT INTO track_transitions (
			source_track_id, target_track_id, transition_count, last_transition_timestamp
		) VALUES (?, ?, 1, ?)
		ON CONFLICT(source_track_id, target_track_id)
		DO UPDATE SET
			transition_count = transition_count + 1,
			last_transition_timestamp = excluded.last_transition_timestamp;
	`
	_, err := r.db.conn.Exec(query, sourceTrackID, targetTrackID, timestamp)
	if err != nil {
		return fmt.Errorf("failed recording track transition: %w", err)
	}

	return nil
}

// GetTrackTransitions returns outgoing transitions from a source track ordered by frequency.
func (r *Repository) GetTrackTransitions(sourceTrackID string, limit int) ([]models.TrackTransition, error) {
	if sourceTrackID == "" {
		return nil, fmt.Errorf("source track ID must not be empty")
	}
	if limit <= 0 {
		limit = 20
	}

	query := `
		SELECT source_track_id, target_track_id, transition_count, last_transition_timestamp
		FROM track_transitions
		WHERE source_track_id = ?
		ORDER BY transition_count DESC
		LIMIT ?;
	`
	rows, err := r.db.conn.Query(query, sourceTrackID, limit)
	if err != nil {
		return nil, fmt.Errorf("failed querying track transitions: %w", err)
	}
	defer rows.Close()

	var totalTransitions int
	var list []models.TrackTransition

	for rows.Next() {
		var t models.TrackTransition
		if err := rows.Scan(&t.SourceTrackID, &t.TargetTrackID, &t.Count, &t.LastSeenAt); err != nil {
			return nil, fmt.Errorf("failed scanning track transition row: %w", err)
		}
		totalTransitions += t.Count
		list = append(list, t)
	}

	// Compute transition probability distribution P(target | source)
	if totalTransitions > 0 {
		for i := range list {
			list[i].Probability = float64(list[i].Count) / float64(totalTransitions)
		}
	}

	return list, nil
}

// GetTrackTransitionCount returns the observed count of transitions between two specific tracks.
func (r *Repository) GetTrackTransitionCount(sourceTrackID, targetTrackID string) (int, error) {
	query := `
		SELECT transition_count
		FROM track_transitions
		WHERE source_track_id = ? AND target_track_id = ?;
	`
	var count int
	err := r.db.conn.QueryRow(query, sourceTrackID, targetTrackID).Scan(&count)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	if err != nil {
		return 0, fmt.Errorf("failed querying track transition count: %w", err)
	}
	return count, nil
}
