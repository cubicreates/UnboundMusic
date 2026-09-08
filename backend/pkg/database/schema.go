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
    local_path TEXT NOT NULL DEFAULT '',
    file_path TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    artist TEXT NOT NULL DEFAULT '',
    album TEXT NOT NULL DEFAULT '',
    duration_ms INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT 'acoustid',
    updated_at INTEGER NOT NULL DEFAULT 0,
    date_added DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fingerprints_hash ON fingerprints(hash);
CREATE INDEX IF NOT EXISTS idx_fingerprints_path ON fingerprints(file_path);

CREATE TABLE IF NOT EXISTS local_tracks (
    id TEXT PRIMARY KEY,
    file_path TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL DEFAULT '',
    duration_ms INTEGER NOT NULL DEFAULT 0,
    format TEXT NOT NULL,
    file_size INTEGER NOT NULL DEFAULT 0,
    source_folder TEXT NOT NULL,
    date_indexed INTEGER NOT NULL,
    mtime INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_local_tracks_source ON local_tracks(source_folder);
CREATE INDEX IF NOT EXISTS idx_local_tracks_mtime ON local_tracks(mtime);

CREATE VIRTUAL TABLE IF NOT EXISTS local_tracks_fts USING fts5(
    track_id UNINDEXED,
    title,
    artist,
    album,
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TRIGGER IF NOT EXISTS trg_local_tracks_ai AFTER INSERT ON local_tracks BEGIN
    INSERT INTO local_tracks_fts(track_id, title, artist, album)
    VALUES (new.id, new.title, new.artist, new.album);
END;

CREATE TRIGGER IF NOT EXISTS trg_local_tracks_ad AFTER DELETE ON local_tracks BEGIN
    DELETE FROM local_tracks_fts WHERE track_id = old.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_local_tracks_au AFTER UPDATE ON local_tracks BEGIN
    DELETE FROM local_tracks_fts WHERE track_id = old.id;
    INSERT INTO local_tracks_fts(track_id, title, artist, album)
    VALUES (new.id, new.title, new.artist, new.album);
END;

CREATE TABLE IF NOT EXISTS playback_events (
    event_id TEXT PRIMARY KEY,
    track_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL DEFAULT '',
    genre TEXT NOT NULL DEFAULT '',
    duration_sec INTEGER NOT NULL DEFAULT 0,
    listened_sec INTEGER NOT NULL DEFAULT 0,
    completed_ratio REAL NOT NULL DEFAULT 0.0,
    timestamp INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_playback_events_timestamp ON playback_events(timestamp DESC);

CREATE TABLE IF NOT EXISTS feed_cache (
    cache_key TEXT PRIMARY KEY,
    data_json TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feed_cache_expires ON feed_cache(expires_at);

CREATE TABLE IF NOT EXISTS taste_vectors (
    track_id TEXT PRIMARY KEY,
    vector_json TEXT NOT NULL,
    genre TEXT,
    tempo_bpm REAL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS taste_events (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    track_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist_id TEXT NOT NULL,
    artist_name TEXT NOT NULL,
    genre TEXT,
    duration_ms INTEGER NOT NULL,
    listened_ms INTEGER NOT NULL,
    completion_ratio REAL NOT NULL,
    score_delta REAL NOT NULL,
    event_type TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_taste_events_artist ON taste_events(artist_id);
CREATE INDEX IF NOT EXISTS idx_taste_events_track ON taste_events(track_id);
CREATE INDEX IF NOT EXISTS idx_taste_events_time ON taste_events(timestamp DESC);

CREATE TABLE IF NOT EXISTS artist_affinity (
    artist_id TEXT PRIMARY KEY,
    artist_name TEXT NOT NULL,
    affinity_score REAL DEFAULT 0.0,
    play_count INTEGER DEFAULT 0,
    skip_count INTEGER DEFAULT 0,
    fast_skip_streak INTEGER DEFAULT 0,
    is_banned INTEGER DEFAULT 0,
    ban_until_timestamp INTEGER DEFAULT 0,
    last_listened_timestamp INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_affinity_score ON artist_affinity(affinity_score DESC);
CREATE INDEX IF NOT EXISTS idx_affinity_banned ON artist_affinity(is_banned);

CREATE TABLE IF NOT EXISTS track_transitions (
    source_track_id TEXT NOT NULL,
    target_track_id TEXT NOT NULL,
    transition_count INTEGER DEFAULT 1,
    last_transition_timestamp INTEGER NOT NULL,
    PRIMARY KEY (source_track_id, target_track_id)
);
CREATE INDEX IF NOT EXISTS idx_transitions_source ON track_transitions(source_track_id, transition_count DESC);

CREATE TABLE IF NOT EXISTS synced_tracks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL DEFAULT '',
    duration_ms INTEGER NOT NULL DEFAULT 0,
    thumbnail_url TEXT NOT NULL DEFAULT '',
    synced_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_synced_tracks_artist ON synced_tracks(artist);

CREATE TABLE IF NOT EXISTS user_credentials (
    key TEXT PRIMARY KEY,
    val TEXT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS user_eq_presets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    band_levels_json TEXT NOT NULL,
    bass_boost INTEGER DEFAULT 0,
    virtualizer INTEGER DEFAULT 0
);
`

