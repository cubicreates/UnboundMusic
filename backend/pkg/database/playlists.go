/*
 * Package: database
 * File: playlists.go
 * Purpose: SQLite persistence and CRUD transactions for user custom playlists and playlist tracks.
 * Subsystem: Permanent Storage & Library Management
 * Concurrency: Thread-safe parameterized database queries.
 */

package database

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// CreateCustomPlaylist inserts a new playlist entry and any initial tracks into SQLite.
func (r *Repository) CreateCustomPlaylist(ctx context.Context, p *models.CustomPlaylist) error {
	if p == nil || p.ID == "" || p.Title == "" {
		return fmt.Errorf("invalid playlist data: id and title are required")
	}

	now := time.Now().Unix()
	if p.CreatedAt == 0 {
		p.CreatedAt = now
	}
	if p.UpdatedAt == 0 {
		p.UpdatedAt = now
	}

	tx, err := r.db.conn.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	query := `
	INSERT INTO custom_playlists (id, title, description, cover_url, created_at, updated_at)
	VALUES (?, ?, ?, ?, ?, ?)
	ON CONFLICT(id) DO UPDATE SET
		title = excluded.title,
		description = excluded.description,
		cover_url = excluded.cover_url,
		updated_at = excluded.updated_at;
	`
	_, err = tx.ExecContext(ctx, query, p.ID, p.Title, p.Description, p.CoverURL, p.CreatedAt, p.UpdatedAt)
	if err != nil {
		return err
	}

	// Insert initial tracks if provided
	if len(p.Tracks) > 0 {
		trackQuery := `
		INSERT INTO playlist_tracks (playlist_id, track_id, title, artist, album, duration_ms, stream_url, cover_url, source, position)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
		`
		for i, t := range p.Tracks {
			_, err = tx.ExecContext(ctx, trackQuery,
				p.ID, t.ID, t.Title, t.Artist, t.Album,
				t.DurationMs, t.StreamURL, t.CoverURL, t.Source, i,
			)
			if err != nil {
				return err
			}
		}
	}

	return tx.Commit()
}

// GetCustomPlaylists retrieves all custom playlists along with their ordered tracks.
func (r *Repository) GetCustomPlaylists(ctx context.Context) ([]models.CustomPlaylist, error) {
	query := `
	SELECT id, title, description, cover_url, created_at, updated_at
	FROM custom_playlists
	ORDER BY updated_at DESC;
	`
	rows, err := r.db.conn.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var playlists []models.CustomPlaylist
	for rows.Next() {
		var p models.CustomPlaylist
		if err := rows.Scan(&p.ID, &p.Title, &p.Description, &p.CoverURL, &p.CreatedAt, &p.UpdatedAt); err != nil {
			return nil, err
		}
		playlists = append(playlists, p)
	}

	// Populate tracks for each playlist
	for i := range playlists {
		tracks, err := r.getPlaylistTracks(ctx, playlists[i].ID)
		if err == nil {
			playlists[i].Tracks = tracks
		} else {
			playlists[i].Tracks = []models.PlaylistTrack{}
		}
	}

	return playlists, nil
}

// GetCustomPlaylistByID retrieves a single custom playlist by ID with tracks.
func (r *Repository) GetCustomPlaylistByID(ctx context.Context, id string) (*models.CustomPlaylist, error) {
	query := `
	SELECT id, title, description, cover_url, created_at, updated_at
	FROM custom_playlists
	WHERE id = ?;
	`
	var p models.CustomPlaylist
	err := r.db.conn.QueryRowContext(ctx, query, id).Scan(
		&p.ID, &p.Title, &p.Description, &p.CoverURL, &p.CreatedAt, &p.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	tracks, err := r.getPlaylistTracks(ctx, p.ID)
	if err == nil {
		p.Tracks = tracks
	} else {
		p.Tracks = []models.PlaylistTrack{}
	}

	return &p, nil
}

// UpdateCustomPlaylistDetails updates title, description, and cover image for a playlist.
func (r *Repository) UpdateCustomPlaylistDetails(ctx context.Context, id, title, description, coverURL string) error {
	query := `
	UPDATE custom_playlists
	SET title = ?, description = ?, cover_url = ?, updated_at = ?
	WHERE id = ?;
	`
	_, err := r.db.conn.ExecContext(ctx, query, title, description, coverURL, time.Now().Unix(), id)
	return err
}

// DeleteCustomPlaylist removes a playlist and cascades track deletions.
func (r *Repository) DeleteCustomPlaylist(ctx context.Context, id string) error {
	tx, err := r.db.conn.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	_, _ = tx.ExecContext(ctx, `DELETE FROM playlist_tracks WHERE playlist_id = ?;`, id)
	_, err = tx.ExecContext(ctx, `DELETE FROM custom_playlists WHERE id = ?;`, id)
	if err != nil {
		return err
	}
	return tx.Commit()
}

// AddTrackToCustomPlaylist appends a track to the end of a playlist.
func (r *Repository) AddTrackToCustomPlaylist(ctx context.Context, playlistID string, track *models.PlaylistTrack) error {
	if track == nil || track.ID == "" {
		return fmt.Errorf("track must not be empty")
	}

	tx, err := r.db.conn.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	// Get next position
	var nextPos int
	err = tx.QueryRowContext(ctx, `SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id = ?;`, playlistID).Scan(&nextPos)
	if err != nil {
		nextPos = 0
	}

	query := `
	INSERT INTO playlist_tracks (playlist_id, track_id, title, artist, album, duration_ms, stream_url, cover_url, source, position)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
	`
	_, err = tx.ExecContext(ctx, query,
		playlistID, track.ID, track.Title, track.Artist, track.Album,
		track.DurationMs, track.StreamURL, track.CoverURL, track.Source, nextPos,
	)
	if err != nil {
		return err
	}

	// Update playlist timestamp
	_, _ = tx.ExecContext(ctx, `UPDATE custom_playlists SET updated_at = ? WHERE id = ?;`, time.Now().Unix(), playlistID)

	return tx.Commit()
}

// RemoveTrackFromCustomPlaylist removes the track at the specified 0-based position and compacts remaining positions.
func (r *Repository) RemoveTrackFromCustomPlaylist(ctx context.Context, playlistID string, position int) error {
	tx, err := r.db.conn.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	// Find the ID of the track at position
	var trackRowID int
	err = tx.QueryRowContext(ctx, `SELECT id FROM playlist_tracks WHERE playlist_id = ? AND position = ?;`, playlistID, position).Scan(&trackRowID)
	if err != nil {
		return fmt.Errorf("track not found at position %d", position)
	}

	_, err = tx.ExecContext(ctx, `DELETE FROM playlist_tracks WHERE id = ?;`, trackRowID)
	if err != nil {
		return err
	}

	// Shift subsequent positions down
	_, err = tx.ExecContext(ctx, `UPDATE playlist_tracks SET position = position - 1 WHERE playlist_id = ? AND position > ?;`, playlistID, position)
	if err != nil {
		return err
	}

	_, _ = tx.ExecContext(ctx, `UPDATE custom_playlists SET updated_at = ? WHERE id = ?;`, time.Now().Unix(), playlistID)

	return tx.Commit()
}

// ReorderCustomPlaylistTracks reorders track positions inside a playlist.
func (r *Repository) ReorderCustomPlaylistTracks(ctx context.Context, playlistID string, fromPos, toPos int) error {
	if fromPos == toPos {
		return nil
	}

	tx, err := r.db.conn.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	// Load all track IDs in current order
	rows, err := tx.QueryContext(ctx, `SELECT id FROM playlist_tracks WHERE playlist_id = ? ORDER BY position ASC;`, playlistID)
	if err != nil {
		return err
	}
	defer rows.Close()

	var rowIDs []int
	for rows.Next() {
		var id int
		if err := rows.Scan(&id); err != nil {
			return err
		}
		rowIDs = append(rowIDs, id)
	}

	if fromPos < 0 || fromPos >= len(rowIDs) || toPos < 0 || toPos >= len(rowIDs) {
		return fmt.Errorf("invalid from/to position indices: %d -> %d", fromPos, toPos)
	}

	// Move element in slice
	movedID := rowIDs[fromPos]
	rowIDs = append(rowIDs[:fromPos], rowIDs[fromPos+1:]...)
	newRowIDs := make([]int, 0, len(rowIDs)+1)
	newRowIDs = append(newRowIDs, rowIDs[:toPos]...)
	newRowIDs = append(newRowIDs, movedID)
	newRowIDs = append(newRowIDs, rowIDs[toPos:]...)

	// Update positions in DB
	for pos, rowID := range newRowIDs {
		_, err = tx.ExecContext(ctx, `UPDATE playlist_tracks SET position = ? WHERE id = ?;`, pos, rowID)
		if err != nil {
			return err
		}
	}

	_, _ = tx.ExecContext(ctx, `UPDATE custom_playlists SET updated_at = ? WHERE id = ?;`, time.Now().Unix(), playlistID)

	return tx.Commit()
}

func (r *Repository) getPlaylistTracks(ctx context.Context, playlistID string) ([]models.PlaylistTrack, error) {
	query := `
	SELECT track_id, playlist_id, title, artist, album, duration_ms, stream_url, cover_url, source, position
	FROM playlist_tracks
	WHERE playlist_id = ?
	ORDER BY position ASC;
	`
	rows, err := r.db.conn.QueryContext(ctx, query, playlistID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tracks []models.PlaylistTrack
	for rows.Next() {
		var t models.PlaylistTrack
		if err := rows.Scan(
			&t.ID, &t.PlaylistID, &t.Title, &t.Artist, &t.Album,
			&t.DurationMs, &t.StreamURL, &t.CoverURL, &t.Source, &t.Position,
		); err != nil {
			return nil, err
		}
		tracks = append(tracks, t)
	}
	return tracks, rows.Err()
}
