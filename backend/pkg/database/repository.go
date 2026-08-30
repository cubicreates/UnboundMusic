/*
 * Package: database
 * File: repository.go
 * Purpose: Data Access Object (DAO) providing CRUD operations for music tracks, offline synced lyrics, acoustic fingerprints, and taste vectors.
 * Subsystem: Permanent Storage & Vector Memory Bank
 * Concurrency: Thread-safe; queries utilize connection pooling and parameterized SQL queries.
 */

package database

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// Repository provides structured database operations for Unbound Music.
type Repository struct {
	db *DB
}

// NewRepository instantiates a new repository layer wrapping the SQLite database handle.
func NewRepository(db *DB) *Repository {
	return &Repository{db: db}
}

// SaveTrack inserts or updates a music track entry in the database.
func (r *Repository) SaveTrack(ctx context.Context, track *models.Track) error {
	if track == nil || track.ID == "" {
		return fmt.Errorf("invalid track data")
	}

	query := `
	INSERT INTO tracks (id, title, artist, album, duration_ms, stream_url, codec, bitrate_kbps, is_local, local_path, thumbnail_url, isrc, updated_at)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
	ON CONFLICT(id) DO UPDATE SET
		title = excluded.title,
		artist = excluded.artist,
		album = excluded.album,
		duration_ms = excluded.duration_ms,
		stream_url = COALESCE(excluded.stream_url, tracks.stream_url),
		codec = COALESCE(excluded.codec, tracks.codec),
		bitrate_kbps = COALESCE(excluded.bitrate_kbps, tracks.bitrate_kbps),
		is_local = excluded.is_local,
		local_path = COALESCE(excluded.local_path, tracks.local_path),
		thumbnail_url = COALESCE(excluded.thumbnail_url, tracks.thumbnail_url),
		isrc = COALESCE(excluded.isrc, tracks.isrc),
		updated_at = CURRENT_TIMESTAMP;
	`

	isLocalInt := 0
	if track.IsLocal {
		isLocalInt = 1
	}

	_, err := r.db.conn.ExecContext(ctx, query,
		track.ID, track.Title, track.Artist, track.Album, track.DurationMs,
		track.StreamURL, track.Codec, track.BitrateKbps, isLocalInt,
		track.LocalPath, track.ThumbnailURL, track.ISRC,
	)
	return err
}

// GetTrack retrieves a track by its unique ID.
func (r *Repository) GetTrack(ctx context.Context, id string) (*models.Track, error) {
	query := `
	SELECT id, title, artist, album, duration_ms, stream_url, codec, bitrate_kbps, is_local, local_path, thumbnail_url, isrc
	FROM tracks WHERE id = ? LIMIT 1;
	`

	row := r.db.conn.QueryRowContext(ctx, query, id)
	var t models.Track
	var streamURL, codec, localPath, thumbnailURL, isrc sql.NullString
	var bitrateKbps sql.NullInt64
	var isLocalInt int

	err := row.Scan(
		&t.ID, &t.Title, &t.Artist, &t.Album, &t.DurationMs,
		&streamURL, &codec, &bitrateKbps, &isLocalInt,
		&localPath, &thumbnailURL, &isrc,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil // Not found
		}
		return nil, err
	}

	t.StreamURL = streamURL.String
	t.Codec = codec.String
	t.LocalPath = localPath.String
	t.ThumbnailURL = thumbnailURL.String
	t.ISRC = isrc.String
	t.BitrateKbps = int(bitrateKbps.Int64)
	t.IsLocal = isLocalInt == 1

	return &t, nil
}

// SaveLyrics writes or updates synchronized lyrics JSON in the permanent vault.
func (r *Repository) SaveLyrics(ctx context.Context, payload *models.LyricsPayload) error {
	if payload == nil || payload.TrackID == "" {
		return fmt.Errorf("invalid lyrics payload")
	}

	linesJSON, err := json.Marshal(payload.Lines)
	if err != nil {
		return fmt.Errorf("failed to marshal lyrics lines to JSON: %w", err)
	}

	isWordSyncedInt := 0
	if payload.IsWordSynced {
		isWordSyncedInt = 1
	}

	query := `
	INSERT INTO synced_lyrics (track_id, title, artist, plain_lyrics, lyrics_json, is_word_synced, source, updated_at)
	VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
	ON CONFLICT(track_id) DO UPDATE SET
		title = excluded.title,
		artist = excluded.artist,
		plain_lyrics = excluded.plain_lyrics,
		lyrics_json = excluded.lyrics_json,
		is_word_synced = excluded.is_word_synced,
		source = excluded.source,
		updated_at = CURRENT_TIMESTAMP;
	`

	_, err = r.db.conn.ExecContext(ctx, query,
		payload.TrackID, payload.Title, payload.Artist, payload.PlainLyrics,
		string(linesJSON), isWordSyncedInt, payload.Source,
	)
	return err
}

// GetLyrics retrieves synchronized lyrics from SQLite cache with zero network overhead.
func (r *Repository) GetLyrics(ctx context.Context, trackID string) (*models.LyricsPayload, error) {
	query := `
	SELECT track_id, title, artist, plain_lyrics, lyrics_json, is_word_synced, source
	FROM synced_lyrics WHERE track_id = ? LIMIT 1;
	`

	row := r.db.conn.QueryRowContext(ctx, query, trackID)
	var p models.LyricsPayload
	var linesJSON string
	var isWordSyncedInt int

	err := row.Scan(&p.TrackID, &p.Title, &p.Artist, &p.PlainLyrics, &linesJSON, &isWordSyncedInt, &p.Source)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil // Not cached
		}
		return nil, err
	}

	p.IsWordSynced = isWordSyncedInt == 1
	if linesJSON != "" {
		_ = json.Unmarshal([]byte(linesJSON), &p.Lines)
	}

	return &p, nil
}

// SaveFingerprint inserts an acoustic hash mapping for 0.2ms local playback interception.
func (r *Repository) SaveFingerprint(ctx context.Context, hash, localPath string, durationMs int64) error {
	query := `
	INSERT INTO fingerprints (hash, local_path, duration_ms, date_added)
	VALUES (?, ?, ?, CURRENT_TIMESTAMP)
	ON CONFLICT(hash) DO UPDATE SET
		local_path = excluded.local_path,
		duration_ms = excluded.duration_ms;
	`
	_, err := r.db.conn.ExecContext(ctx, query, hash, localPath, durationMs)
	return err
}

// GetPathByFingerprint looks up the local disk file path matching an acoustic fingerprint hash.
func (r *Repository) GetPathByFingerprint(ctx context.Context, hash string) (string, error) {
	query := `SELECT local_path FROM fingerprints WHERE hash = ? LIMIT 1;`
	var path string
	err := r.db.conn.QueryRowContext(ctx, query, hash).Scan(&path)
	if err != nil {
		if err == sql.ErrNoRows {
			return "", nil
		}
		return "", err
	}
	return path, nil
}

// SaveTasteVector records a 128-dimensional mathematical taste vector for offline smart mixes.
func (r *Repository) SaveTasteVector(ctx context.Context, trackID string, vector []float32, genre string, tempoBPM float64) error {
	vecJSON, err := json.Marshal(vector)
	if err != nil {
		return err
	}

	query := `
	INSERT INTO taste_vectors (track_id, vector_json, genre, tempo_bpm, updated_at)
	VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
	ON CONFLICT(track_id) DO UPDATE SET
		vector_json = excluded.vector_json,
		genre = excluded.genre,
		tempo_bpm = excluded.tempo_bpm,
		updated_at = CURRENT_TIMESTAMP;
	`

	_, err = r.db.conn.ExecContext(ctx, query, trackID, string(vecJSON), genre, tempoBPM)
	return err
}

// CleanUpExpiredCache removes transient data older than standard retention windows.
func (r *Repository) CleanUpExpiredCache(ctx context.Context, olderThan time.Duration) (int64, error) {
	cutoff := time.Now().Add(-olderThan)
	res, err := r.db.conn.ExecContext(ctx, `DELETE FROM tracks WHERE is_local = 0 AND updated_at < ?`, cutoff)
	if err != nil {
		return 0, err
	}
	return res.RowsAffected()
}
