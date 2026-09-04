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

// UpsertLocalTrack inserts or updates an indexed local audio track.
func (r *Repository) UpsertLocalTrack(ctx context.Context, track *models.LocalTrack) error {
	if track == nil || track.FilePath == "" {
		return fmt.Errorf("invalid local track data: file_path required")
	}

	query := `
	INSERT INTO local_tracks (id, file_path, title, artist, album, duration_ms, format, file_size, source_folder, date_indexed, mtime)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(file_path) DO UPDATE SET
		title = excluded.title,
		artist = excluded.artist,
		album = excluded.album,
		duration_ms = excluded.duration_ms,
		format = excluded.format,
		file_size = excluded.file_size,
		source_folder = excluded.source_folder,
		date_indexed = excluded.date_indexed,
		mtime = excluded.mtime;
	`
	_, err := r.db.conn.ExecContext(ctx, query,
		track.ID, track.FilePath, track.Title, track.Artist, track.Album,
		track.DurationMs, track.Format, track.FileSize, track.SourceFolder,
		track.DateIndexed, track.MTime,
	)
	return err
}

// GetLocalTracksBySource returns all indexed local tracks for a given source folder ("whatsapp", "telegram", "downloads").
func (r *Repository) GetLocalTracksBySource(ctx context.Context, sourceFolder string) ([]models.LocalTrack, error) {
	query := `
	SELECT id, file_path, title, artist, album, duration_ms, format, file_size, source_folder, date_indexed, mtime
	FROM local_tracks
	WHERE source_folder = ?
	ORDER BY title ASC;
	`
	rows, err := r.db.conn.QueryContext(ctx, query, sourceFolder)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tracks []models.LocalTrack
	for rows.Next() {
		var t models.LocalTrack
		if err := rows.Scan(
			&t.ID, &t.FilePath, &t.Title, &t.Artist, &t.Album,
			&t.DurationMs, &t.Format, &t.FileSize, &t.SourceFolder,
			&t.DateIndexed, &t.MTime,
		); err != nil {
			return nil, err
		}
		tracks = append(tracks, t)
	}
	return tracks, rows.Err()
}

// GetAllLocalTracks returns all indexed physical tracks on the device.
func (r *Repository) GetAllLocalTracks(ctx context.Context) ([]models.LocalTrack, error) {
	query := `
	SELECT id, file_path, title, artist, album, duration_ms, format, file_size, source_folder, date_indexed, mtime
	FROM local_tracks
	ORDER BY title ASC;
	`
	rows, err := r.db.conn.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tracks []models.LocalTrack
	for rows.Next() {
		var t models.LocalTrack
		if err := rows.Scan(
			&t.ID, &t.FilePath, &t.Title, &t.Artist, &t.Album,
			&t.DurationMs, &t.Format, &t.FileSize, &t.SourceFolder,
			&t.DateIndexed, &t.MTime,
		); err != nil {
			return nil, err
		}
		tracks = append(tracks, t)
	}
	return tracks, rows.Err()
}

// GetLocalTrackByPath finds a local track by its absolute file path.
func (r *Repository) GetLocalTrackByPath(ctx context.Context, filePath string) (*models.LocalTrack, error) {
	query := `
	SELECT id, file_path, title, artist, album, duration_ms, format, file_size, source_folder, date_indexed, mtime
	FROM local_tracks
	WHERE file_path = ?
	LIMIT 1;
	`
	row := r.db.conn.QueryRowContext(ctx, query, filePath)
	var t models.LocalTrack
	err := row.Scan(
		&t.ID, &t.FilePath, &t.Title, &t.Artist, &t.Album,
		&t.DurationMs, &t.Format, &t.FileSize, &t.SourceFolder,
		&t.DateIndexed, &t.MTime,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	return &t, nil
}

// UpsertFingerprint stores an acoustic waveform hash with full MusicBrainz metadata.
func (r *Repository) UpsertFingerprint(ctx context.Context, fp *models.FingerprintRecord) error {
	if fp == nil || fp.Hash == "" {
		return fmt.Errorf("invalid fingerprint data: hash required")
	}

	query := `
	INSERT INTO fingerprints (hash, local_path, file_path, title, artist, album, duration_ms, source, updated_at)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(hash) DO UPDATE SET
		local_path = excluded.local_path,
		file_path = excluded.file_path,
		title = excluded.title,
		artist = excluded.artist,
		album = excluded.album,
		duration_ms = excluded.duration_ms,
		source = excluded.source,
		updated_at = excluded.updated_at;
	`
	_, err := r.db.conn.ExecContext(ctx, query,
		fp.Hash, fp.FilePath, fp.FilePath, fp.Title, fp.Artist, fp.Album,
		fp.DurationMs, fp.Source, fp.UpdatedAt,
	)
	return err
}

// GetFingerprintByHash retrieves acoustic metadata for a given Chromaprint hash.
func (r *Repository) GetFingerprintByHash(ctx context.Context, hash string) (*models.FingerprintRecord, error) {
	query := `
	SELECT hash, file_path, title, artist, album, duration_ms, source, updated_at
	FROM fingerprints
	WHERE hash = ?
	LIMIT 1;
	`
	row := r.db.conn.QueryRowContext(ctx, query, hash)
	var fp models.FingerprintRecord
	var filePath, title, artist, album, source sql.NullString
	var durationMs, updatedAt sql.NullInt64

	err := row.Scan(
		&fp.Hash, &filePath, &title, &artist, &album,
		&durationMs, &source, &updatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}

	fp.FilePath = filePath.String
	fp.Title = title.String
	fp.Artist = artist.String
	fp.Album = album.String
	fp.DurationMs = durationMs.Int64
	fp.Source = source.String
	fp.UpdatedAt = updatedAt.Int64

	return &fp, nil
}

// RecordPlaybackEvent writes a user listening session event for offline taste profiling.
func (r *Repository) RecordPlaybackEvent(ctx context.Context, event *models.PlaybackEvent) error {
	if event == nil || event.EventID == "" {
		return fmt.Errorf("invalid playback event data")
	}

	query := `
	INSERT INTO playback_events (event_id, track_id, title, artist, album, genre, duration_sec, listened_sec, completed_ratio, timestamp)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
	`
	_, err := r.db.conn.ExecContext(ctx, query,
		event.EventID, event.TrackID, event.Title, event.Artist, event.Album,
		event.Genre, event.DurationSec, event.ListenedSec, event.CompletedRatio,
		event.Timestamp,
	)
	return err
}

// GetRecentPlaybackEvents retrieves the most recent playback telemetry events.
func (r *Repository) GetRecentPlaybackEvents(ctx context.Context, limit int) ([]models.PlaybackEvent, error) {
	if limit <= 0 {
		limit = 50
	}

	query := `
	SELECT event_id, track_id, title, artist, album, genre, duration_sec, listened_sec, completed_ratio, timestamp
	FROM playback_events
	ORDER BY timestamp DESC
	LIMIT ?;
	`
	rows, err := r.db.conn.QueryContext(ctx, query, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var events []models.PlaybackEvent
	for rows.Next() {
		var e models.PlaybackEvent
		if err := rows.Scan(
			&e.EventID, &e.TrackID, &e.Title, &e.Artist, &e.Album,
			&e.Genre, &e.DurationSec, &e.ListenedSec, &e.CompletedRatio,
			&e.Timestamp,
		); err != nil {
			return nil, err
		}
		events = append(events, e)
	}
	return events, rows.Err()
}

// SetFeedCache stores public InnerTube JSON responses with a TTL.
func (r *Repository) SetFeedCache(ctx context.Context, key string, dataJSON string, ttlSeconds int64) error {
	expiresAt := time.Now().Unix() + ttlSeconds
	query := `
	INSERT INTO feed_cache (cache_key, data_json, expires_at)
	VALUES (?, ?, ?)
	ON CONFLICT(cache_key) DO UPDATE SET
		data_json = excluded.data_json,
		expires_at = excluded.expires_at;
	`
	_, err := r.db.conn.ExecContext(ctx, query, key, dataJSON, expiresAt)
	return err
}

// GetFeedCache retrieves cached explore feeds if not yet expired.
func (r *Repository) GetFeedCache(ctx context.Context, key string) (string, error) {
	now := time.Now().Unix()
	query := `
	SELECT data_json
	FROM feed_cache
	WHERE cache_key = ? AND expires_at > ?
	LIMIT 1;
	`
	var dataJSON string
	err := r.db.conn.QueryRowContext(ctx, query, key, now).Scan(&dataJSON)
	if err != nil {
		if err == sql.ErrNoRows {
			return "", nil // Expired or not found
		}
		return "", err
	}
	return dataJSON, nil
}
