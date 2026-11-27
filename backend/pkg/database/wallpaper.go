/*
 * Package: database
 * File: wallpaper.go
 * Purpose: Persists and retrieves the user's original lock-screen wallpaper in SQLite
 *          so dynamic album art never overwrites or wipes a custom wallpaper on exit.
 * Subsystem: Permanent Storage & System State
 */

package database

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// WallpaperBackupMeta describes the stored original lock-screen wallpaper without holding the image blob.
type WallpaperBackupMeta struct {
	HasBackup         bool   `json:"has_backup"`
	MimeType          string `json:"mime_type"`
	SourceFlag        string `json:"source_flag"`
	LockWallpaperID   int64  `json:"lock_wallpaper_id"`
	SystemWallpaperID int64  `json:"system_wallpaper_id"`
	SHA256Hex         string `json:"sha256_hex"`
	ByteSize          int64  `json:"byte_size"`
	UpdatedAt         int64  `json:"updated_at"`
}

// SaveWallpaperBackup upserts the singleton original lock-screen wallpaper row (id = 1).
func (r *Repository) SaveWallpaperBackup(
	ctx context.Context,
	imageBytes []byte,
	mimeType string,
	sourceFlag string,
	lockID int64,
	systemID int64,
) (*WallpaperBackupMeta, error) {
	if len(imageBytes) == 0 {
		return nil, fmt.Errorf("cannot save empty wallpaper backup")
	}

	resolvedMime := strings.TrimSpace(mimeType)
	if resolvedMime == "" || resolvedMime == "application/octet-stream" {
		resolvedMime = http.DetectContentType(imageBytes)
	}
	resolvedSource := strings.TrimSpace(sourceFlag)
	if resolvedSource == "" {
		resolvedSource = "lock"
	}

	sum := sha256.Sum256(imageBytes)
	shaHex := hex.EncodeToString(sum[:])
	byteSize := int64(len(imageBytes))
	now := time.Now().UnixMilli()

	query := `
	INSERT INTO lockscreen_wallpaper_backup (
		id, image_blob, mime_type, source_flag, lock_wallpaper_id, system_wallpaper_id, sha256_hex, byte_size, updated_at
	)
	VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(id) DO UPDATE SET
		image_blob = excluded.image_blob,
		mime_type = excluded.mime_type,
		source_flag = excluded.source_flag,
		lock_wallpaper_id = excluded.lock_wallpaper_id,
		system_wallpaper_id = excluded.system_wallpaper_id,
		sha256_hex = excluded.sha256_hex,
		byte_size = excluded.byte_size,
		updated_at = excluded.updated_at;
	`

	if _, err := r.db.conn.ExecContext(ctx, query,
		imageBytes, resolvedMime, resolvedSource, lockID, systemID, shaHex, byteSize, now,
	); err != nil {
		return nil, err
	}

	return &WallpaperBackupMeta{
		HasBackup:         true,
		MimeType:          resolvedMime,
		SourceFlag:        resolvedSource,
		LockWallpaperID:   lockID,
		SystemWallpaperID: systemID,
		SHA256Hex:         shaHex,
		ByteSize:          byteSize,
		UpdatedAt:         now,
	}, nil
}

// GetWallpaperBackupMeta returns lightweight metadata for the backed-up original wallpaper without loading image_blob.
func (r *Repository) GetWallpaperBackupMeta(ctx context.Context) (*WallpaperBackupMeta, error) {
	query := `
	SELECT mime_type, source_flag, lock_wallpaper_id, system_wallpaper_id, sha256_hex, byte_size, updated_at
	FROM lockscreen_wallpaper_backup
	WHERE id = 1
	LIMIT 1;
	`

	var meta WallpaperBackupMeta
	err := r.db.conn.QueryRowContext(ctx, query).Scan(
		&meta.MimeType,
		&meta.SourceFlag,
		&meta.LockWallpaperID,
		&meta.SystemWallpaperID,
		&meta.SHA256Hex,
		&meta.ByteSize,
		&meta.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return &WallpaperBackupMeta{
				HasBackup:         false,
				LockWallpaperID:   -1,
				SystemWallpaperID: -1,
			}, nil
		}
		return nil, err
	}

	meta.HasBackup = meta.ByteSize > 0
	return &meta, nil
}

// GetWallpaperBackup returns both the raw original wallpaper bytes and its metadata.
func (r *Repository) GetWallpaperBackup(ctx context.Context) ([]byte, *WallpaperBackupMeta, error) {
	query := `
	SELECT image_blob, mime_type, source_flag, lock_wallpaper_id, system_wallpaper_id, sha256_hex, byte_size, updated_at
	FROM lockscreen_wallpaper_backup
	WHERE id = 1
	LIMIT 1;
	`

	var blob []byte
	var meta WallpaperBackupMeta
	err := r.db.conn.QueryRowContext(ctx, query).Scan(
		&blob,
		&meta.MimeType,
		&meta.SourceFlag,
		&meta.LockWallpaperID,
		&meta.SystemWallpaperID,
		&meta.SHA256Hex,
		&meta.ByteSize,
		&meta.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, &WallpaperBackupMeta{
				HasBackup:         false,
				LockWallpaperID:   -1,
				SystemWallpaperID: -1,
			}, nil
		}
		return nil, nil, err
	}

	if len(blob) == 0 {
		return nil, &WallpaperBackupMeta{
			HasBackup:         false,
			LockWallpaperID:   -1,
			SystemWallpaperID: -1,
		}, nil
	}

	meta.HasBackup = true
	if meta.ByteSize == 0 {
		meta.ByteSize = int64(len(blob))
	}
	return blob, &meta, nil
}

// UpdateWallpaperBackupIDs updates the tracked Android WallpaperManager IDs without rewriting image_blob.
func (r *Repository) UpdateWallpaperBackupIDs(ctx context.Context, lockID, systemID int64) error {
	now := time.Now().UnixMilli()
	_, err := r.db.conn.ExecContext(ctx,
		`UPDATE lockscreen_wallpaper_backup SET lock_wallpaper_id = ?, system_wallpaper_id = ?, updated_at = ? WHERE id = 1;`,
		lockID, systemID, now,
	)
	return err
}
