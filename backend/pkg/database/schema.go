/*
 * Package: database
 * File: schema.go
 * Purpose: Defines SQLite database table schemas and automatic migration DDL scripts for Unbound Music.
 * Subsystem: Permanent Storage & Vector Memory Bank
 * Concurrency: Schema definitions are constant and thread-safe.
 */

package database

// SchemaDDL defines all database tables and indexes required for local music and AI vector operations.
const SchemaDDL = `
CREATE TABLE IF NOT EXISTS tracks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT,
    duration_ms INTEGER NOT NULL,
    stream_url TEXT,
    codec TEXT,
    bitrate_kbps INTEGER,
    is_local INTEGER NOT NULL DEFAULT 0,
    local_path TEXT,
    thumbnail_url TEXT,
    isrc TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);
CREATE INDEX IF NOT EXISTS idx_tracks_is_local ON tracks(is_local);

CREATE TABLE IF NOT EXISTS synced_lyrics (
    track_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    plain_lyrics TEXT NOT NULL,
    lyrics_json TEXT NOT NULL,
    is_word_synced INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fingerprints (
    hash TEXT PRIMARY KEY,
    local_path TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    date_added DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fingerprints_hash ON fingerprints(hash);

CREATE TABLE IF NOT EXISTS taste_vectors (
    track_id TEXT PRIMARY KEY,
    vector_json TEXT NOT NULL,
    genre TEXT,
    tempo_bpm REAL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE
);
`
