/*
 * Package: storage
 * File: indexer.go
 * Purpose: In-Place Virtual Audio Indexer & Consolidator: indexes scattered audio files non-destructively in SQLite for 0-data playback by default, with an opt-in consolidation routine.
 * Subsystem: Storage & Indexing Engine
 * Concurrency: Thread-safe repository updates and concurrent disk scanning.
 */

package storage

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/fingerprint"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// IndexSummary summarizes the results of an indexing operation.
type IndexSummary struct {
	Mode             string        `json:"mode"` // "VIRTUAL_IN_PLACE" or "CONSOLIDATED"
	TotalScanned     int           `json:"total_scanned"`
	IndexedTracks    int           `json:"indexed_tracks"`
	VoiceNotesSkipped int          `json:"voice_notes_skipped"`
	ElapsedMs        int64         `json:"elapsed_ms"`
	Timestamp        time.Time     `json:"timestamp"`
	IndexedPaths     []string      `json:"indexed_paths"`
}

// Indexer coordinates audio discovery and persistence.
type Indexer struct {
	repo *database.Repository
}

// NewIndexer creates a new in-place audio indexer.
func NewIndexer(repo *database.Repository) *Indexer {
	return &Indexer{
		repo: repo,
	}
}

// IndexInPlace scans directories and registers acoustic fingerprints in SQLite without moving files.
func (idx *Indexer) IndexInPlace(ctx context.Context, targetDir string) (*IndexSummary, error) {
	start := time.Now()

	summary, err := fingerprint.ScanDirectory(ctx, targetDir, 8)
	if err != nil {
		return nil, fmt.Errorf("scan failed: %w", err)
	}

	indexedPaths := make([]string, 0, len(summary.AudioTracks))
	indexedCount := 0
	voiceCount := 0

	for _, track := range summary.AudioTracks {
		class := fingerprint.ClassifyAudio(track)
		if !class.IsMusic {
			voiceCount++
			continue
		}

		if idx.repo != nil {
			_ = idx.repo.SaveFingerprint(ctx, track.AcousticHash, track.FilePath, track.DurationMs)

			baseName := strings.TrimSuffix(filepath.Base(track.FilePath), filepath.Ext(track.FilePath))
			dbTrack := &models.Track{
				ID:          track.AcousticHash,
				Title:       baseName,
				Artist:      "Local Artist",
				Album:       "Device Storage",
				DurationMs:  track.DurationMs,
				LocalPath:   track.FilePath,
				IsLocal:     true,
				StreamURL:   "file://" + filepath.ToSlash(track.FilePath),
				BitrateKbps: 320,
				Codec:       strings.ToUpper(strings.TrimPrefix(filepath.Ext(track.FilePath), ".")),
			}
			_ = idx.repo.SaveTrack(ctx, dbTrack)
		}

		indexedPaths = append(indexedPaths, track.FilePath)
		indexedCount++
	}

	return &IndexSummary{
		Mode:              "VIRTUAL_IN_PLACE",
		TotalScanned:      summary.TotalFilesScanned,
		IndexedTracks:     indexedCount,
		VoiceNotesSkipped: voiceCount,
		ElapsedMs:         time.Since(start).Milliseconds(),
		Timestamp:         time.Now(),
		IndexedPaths:      indexedPaths,
	}, nil
}

// ConsolidateLibrary moves downloads and copies WhatsApp audio into the target Unbound/Music folder.
func (idx *Indexer) ConsolidateLibrary(ctx context.Context, sourceDir, targetMusicDir string) (*IndexSummary, error) {
	start := time.Now()

	summary, err := fingerprint.ScanDirectory(ctx, sourceDir, 8)
	if err != nil {
		return nil, fmt.Errorf("consolidation scan failed: %w", err)
	}

	indexedPaths := make([]string, 0, len(summary.AudioTracks))
	indexedCount := 0
	voiceCount := 0

	for _, track := range summary.AudioTracks {
		class := fingerprint.ClassifyAudio(track)
		if !class.IsMusic {
			voiceCount++
			continue
		}

		isChat := fingerprint.IsProtectedChatMedia(track.FilePath)
		destFile := filepath.Join(targetMusicDir, filepath.Base(track.FilePath))

		if isChat {
			// COPY WhatsApp / Telegram media
			_ = copyFileContents(track.FilePath, destFile)
		} else {
			// MOVE regular downloads
			_ = os.Rename(track.FilePath, destFile)
		}

		if idx.repo != nil {
			_ = idx.repo.SaveFingerprint(ctx, track.AcousticHash, destFile, track.DurationMs)
		}

		indexedPaths = append(indexedPaths, destFile)
		indexedCount++
	}

	return &IndexSummary{
		Mode:              "CONSOLIDATED",
		TotalScanned:      summary.TotalFilesScanned,
		IndexedTracks:     indexedCount,
		VoiceNotesSkipped: voiceCount,
		ElapsedMs:         time.Since(start).Milliseconds(),
		Timestamp:         time.Now(),
		IndexedPaths:      indexedPaths,
	}, nil
}

func copyFileContents(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()

	buf := make([]byte, 32*1024)
	for {
		n, err := in.Read(buf)
		if n > 0 {
			if _, wErr := out.Write(buf[:n]); wErr != nil {
				return wErr
			}
		}
		if err != nil {
			break
		}
	}
	return nil
}
