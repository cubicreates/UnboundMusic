/*
 * Package: database
 * File: favorites.go
 * Purpose: Unified SQLite storage for user favorited audio tracks (online and offline).
 * Subsystem: Permanent Storage & User Taste Vault
 * Concurrency: Thread-safe parameterized queries.
 */

package database

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// ToggleFavorite toggles a track's favorite status. Returns true if now favorited, false if removed.
func (r *Repository) ToggleFavorite(ctx context.Context, fav *models.UserFavorite) (bool, error) {
	if fav == nil || fav.TrackID == "" {
		return false, fmt.Errorf("invalid favorite data: track_id required")
	}

	// Check if already favorited
	var count int
	err := r.db.conn.QueryRowContext(ctx, `SELECT COUNT(*) FROM user_favorites WHERE track_id = ?;`, fav.TrackID).Scan(&count)
	if err != nil {
		return false, err
	}

	if count > 0 {
		// Remove favorite
		_, err := r.db.conn.ExecContext(ctx, `DELETE FROM user_favorites WHERE track_id = ?;`, fav.TrackID)
		return false, err
	}

	// Insert favorite
	now := time.Now().Unix()
	if fav.FavoritedAt == 0 {
		fav.FavoritedAt = now
	}

	query := `
	INSERT INTO user_favorites (track_id, title, artist, album, duration_ms, stream_url, cover_url, source, favorited_at)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
	`
	_, err = r.db.conn.ExecContext(ctx, query,
		fav.TrackID, fav.Title, fav.Artist, fav.Album, fav.DurationMs,
		fav.StreamURL, fav.CoverURL, fav.Source, fav.FavoritedAt,
	)
	return true, err
}

// GetFavorites returns all user favorited tracks ordered by most recently favorited.
func (r *Repository) GetFavorites(ctx context.Context) ([]models.UserFavorite, error) {
	query := `
	SELECT track_id, title, artist, album, duration_ms, stream_url, cover_url, source, favorited_at
	FROM user_favorites
	ORDER BY favorited_at DESC;
	`
	rows, err := r.db.conn.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var favs []models.UserFavorite
	for rows.Next() {
		var f models.UserFavorite
		if err := rows.Scan(
			&f.TrackID, &f.Title, &f.Artist, &f.Album, &f.DurationMs,
			&f.StreamURL, &f.CoverURL, &f.Source, &f.FavoritedAt,
		); err != nil {
			return nil, err
		}
		favs = append(favs, f)
	}
	return favs, rows.Err()
}

// IsFavorite checks whether a track ID is favorited.
func (r *Repository) IsFavorite(ctx context.Context, trackID string) (bool, error) {
	var count int
	err := r.db.conn.QueryRowContext(ctx, `SELECT COUNT(*) FROM user_favorites WHERE track_id = ?;`, trackID).Scan(&count)
	if err == sql.ErrNoRows {
		return false, nil
	}
	return count > 0, err
}
