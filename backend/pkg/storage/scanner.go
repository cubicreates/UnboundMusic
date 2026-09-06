/*
 * Package: storage
 * File: scanner.go
 * Purpose: High-performance POSIX storage crawler that discovers audio in chat folders (WhatsApp, Telegram) and public Downloads, ignoring .nomedia flags.
 * Subsystem: Storage & Indexing Engine
 * Concurrency: Thread-safe directory scanner with incremental mtime caching.
 */

package storage

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// ScanReport summarizes the metrics of a POSIX filesystem crawl.
type ScanReport struct {
	ScannedFiles     int   `json:"scanned_files"`
	AudioFilesFound  int   `json:"audio_files_found"`
	NewTracksIndexed int   `json:"new_tracks_indexed"`
	UnchangedTracks  int   `json:"unchanged_tracks"`
	ElapsedMs        int64 `json:"elapsed_ms"`
}

// ScanDirectory walks a target physical path, bypassing .nomedia flags, probing the first 32 magic bytes, and indexing files in SQLite.
func ScanDirectory(ctx context.Context, repo *database.Repository, targetPath, sourceFolder string) (*ScanReport, error) {
	start := time.Now()

	report := &ScanReport{}

	if sourceFolder == "" {
		sourceFolder = "music"
	}

	// Ensure target directory exists
	if _, err := os.Stat(targetPath); os.IsNotExist(err) {
		return report, nil
	}

	err := filepath.WalkDir(targetPath, func(path string, d fs.DirEntry, walkErr error) error {
		// Context cancellation check
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		// Handle permission errors gracefully: skip inaccessible directory without aborting entire scan
		if walkErr != nil {
			if os.IsPermission(walkErr) {
				if d != nil && d.IsDir() {
					return filepath.SkipDir
				}
				return nil
			}
			return nil
		}

		if d == nil {
			return nil
		}

		// Do not stop or skip on .nomedia; continue walking subdirectories
		if d.IsDir() {
			return nil
		}

		name := d.Name()
		if strings.EqualFold(name, ".nomedia") {
			return nil
		}

		report.ScannedFiles++

		info, err := d.Info()
		if err != nil || info.Size() < 32 {
			return nil
		}

		fileMTime := info.ModTime().Unix()
		fileSize := info.Size()

		// 1. Incremental MTime check: if size and modification time match SQLite record, skip disk byte probe
		if repo != nil {
			existing, err := repo.GetLocalTrackByPath(ctx, path)
			if err == nil && existing != nil {
				if existing.FileSize == fileSize && existing.MTime == fileMTime {
					report.UnchangedTracks++
					report.AudioFilesFound++
					return nil
				}
			}
		}

		// 2. Open file and read first 32 magic bytes
		f, err := os.Open(path)
		if err != nil {
			return nil
		}

		header := make([]byte, 32)
		n, err := io.ReadFull(f, header)
		f.Close()
		if err != nil && err != io.ErrUnexpectedEOF && err != io.EOF {
			return nil
		}

		// 3. Identify audio format via magic bytes
		format := DetectMagicBytes(header[:n])
		if format == "" {
			return nil
		}

		report.AudioFilesFound++

		// 4. Construct local track entity and index into SQLite
		trackID := fmt.Sprintf("%x", sha256.Sum256([]byte(path)))[:16]
		baseName := strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))

		localTrack := &models.LocalTrack{
			ID:           trackID,
			FilePath:     path,
			Title:        baseName,
			Artist:       "Unknown Artist",
			Album:        sourceFolder,
			DurationMs:   0,
			Format:       format,
			FileSize:     fileSize,
			SourceFolder: sourceFolder,
			DateIndexed:  time.Now().Unix(),
			MTime:        fileMTime,
		}

		if repo != nil {
			if err := repo.UpsertLocalTrack(ctx, localTrack); err == nil {
				report.NewTracksIndexed++
			}
		} else {
			report.NewTracksIndexed++
		}

		return nil
	})

	report.ElapsedMs = time.Since(start).Milliseconds()
	return report, err
}
