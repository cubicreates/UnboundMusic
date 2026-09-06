/*
 * Package: fingerprint
 * File: pipeline.go
 * Purpose: End-to-end acoustic identification pipeline for unindexed or unlabeled chat audio files.
 * Subsystem: Acoustic Fingerprinting Engine
 * Concurrency: Thread-safe; operates against thread-safe repository interfaces.
 */

package fingerprint

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// GenericFilenamePattern matches standard automated voice note and chat audio filenames.
var GenericFilenamePattern = regexp.MustCompile(`(?i)^(AUD-\d+|voice[_-]|PTT-\d+|audio[_-]|\d{8}[_-]\d+)`)

// FingerprintRepo defines the storage persistence contract needed by the fingerprinting pipeline.
type FingerprintRepo interface {
	GetFingerprintByPath(ctx context.Context, filePath string) (*models.FingerprintRecord, error)
	GetFingerprintByHash(ctx context.Context, hash string) (*models.FingerprintRecord, error)
	UpsertFingerprint(ctx context.Context, fp *models.FingerprintRecord) error
	UpsertLocalTrack(ctx context.Context, track *models.LocalTrack) error
	GetLocalTrackByPath(ctx context.Context, filePath string) (*models.LocalTrack, error)
}

// IsGenericTitle checks if an audio title or filename resembles an unedited recording or voice note.
func IsGenericTitle(name string) bool {
	clean := strings.TrimSpace(name)
	if clean == "" {
		return true
	}
	base := filepath.Base(clean)
	ext := filepath.Ext(base)
	withoutExt := strings.TrimSuffix(base, ext)

	return GenericFilenamePattern.MatchString(withoutExt) || strings.HasPrefix(strings.ToLower(withoutExt), "recording")
}

// IngestUntaggedFile processes an unlabeled audio file through the Chromaprint + AcoustID pipeline.
// 1. Checks SQLite fingerprints cache by path.
// 2. Runs fpcalc to compute waveform fingerprint.
// 3. Checks SQLite fingerprints cache by hash.
// 4. Queries AcoustID web service with 3 QPS rate limit.
// 5. If score >= 0.70, resolves Title, Artist, Album and persists to SQLite.
// 6. If score < 0.70, stores fallback ("Audio Recording - <Date>", "Local Device") to prevent repeated requests.
func IngestUntaggedFile(ctx context.Context, repo FingerprintRepo, fpcalcPath, filePath string) (*models.LocalTrack, error) {
	if repo == nil {
		return nil, errors.New("repository instance must not be nil")
	}
	if strings.TrimSpace(filePath) == "" {
		return nil, errors.New("audio file path must not be empty")
	}

	cleanPath := filepath.Clean(filePath)

	// Obtain or initialize local track record
	track, err := repo.GetLocalTrackByPath(ctx, cleanPath)
	if err != nil {
		return nil, fmt.Errorf("failed to query local track from database: %w", err)
	}
	if track == nil {
		fileInfo, statErr := os.Stat(cleanPath)
		fileSize := int64(0)
		mtime := time.Now().Unix()
		if statErr == nil {
			fileSize = fileInfo.Size()
			mtime = fileInfo.ModTime().Unix()
		}

		baseName := filepath.Base(cleanPath)
		track = &models.LocalTrack{
			ID:           cleanPath,
			FilePath:     cleanPath,
			Title:        baseName,
			Artist:       "Unknown Artist",
			Album:        "",
			DurationMs:   0,
			Format:       strings.TrimPrefix(strings.ToLower(filepath.Ext(cleanPath)), "."),
			FileSize:     fileSize,
			SourceFolder: "whatsapp",
			DateIndexed:  time.Now().Unix(),
			MTime:        mtime,
		}
	}

	// Step 1: Check cache by local file path
	cachedByPath, err := repo.GetFingerprintByPath(ctx, cleanPath)
	if err == nil && cachedByPath != nil && cachedByPath.Title != "" {
		track.Title = cachedByPath.Title
		track.Artist = cachedByPath.Artist
		track.Album = cachedByPath.Album
		if cachedByPath.DurationMs > 0 {
			track.DurationMs = cachedByPath.DurationMs
		}
		_ = repo.UpsertLocalTrack(ctx, track)
		return track, nil
	}

	// Step 2: Run fpcalc -json <filePath>
	fpResult, err := GenerateFingerprint(ctx, fpcalcPath, cleanPath)
	if err != nil {
		return nil, fmt.Errorf("fingerprint generation failed: %w", err)
	}

	durationMs := int64(fpResult.Duration * 1000)
	if durationMs > 0 {
		track.DurationMs = durationMs
	}

	// Step 3: Check cache by acoustic hash
	cachedByHash, err := repo.GetFingerprintByHash(ctx, fpResult.Fingerprint)
	if err == nil && cachedByHash != nil && cachedByHash.Title != "" {
		track.Title = cachedByHash.Title
		track.Artist = cachedByHash.Artist
		track.Album = cachedByHash.Album
		if cachedByHash.DurationMs > 0 {
			track.DurationMs = cachedByHash.DurationMs
		}
		_ = repo.UpsertLocalTrack(ctx, track)
		return track, nil
	}

	// Step 4: Query AcoustID API
	meta, err := LookupAcoustID(ctx, fpResult.Duration, fpResult.Fingerprint)
	nowSec := time.Now().Unix()
	todayStr := time.Now().Format("2006-01-02")

	// Step 5: Score >= 0.70 -> Match found
	if err == nil && meta != nil && meta.Score >= MinConfidenceScore && meta.Title != "" {
		track.Title = meta.Title
		if meta.Artist != "" {
			track.Artist = meta.Artist
		}
		track.Album = meta.Album

		fpRecord := &models.FingerprintRecord{
			Hash:       fpResult.Fingerprint,
			FilePath:   cleanPath,
			Title:      meta.Title,
			Artist:     meta.Artist,
			Album:      meta.Album,
			DurationMs: durationMs,
			Source:     "acoustid",
			UpdatedAt:  nowSec,
		}

		_ = repo.UpsertFingerprint(ctx, fpRecord)
		_ = repo.UpsertLocalTrack(ctx, track)
		return track, nil
	}

	// Step 6: Low confidence (< 0.70), empty results, or offline -> Save fallback
	fallbackTitle := fmt.Sprintf("Audio Recording - %s", todayStr)
	fallbackArtist := "Local Device"

	track.Title = fallbackTitle
	track.Artist = fallbackArtist

	fpRecord := &models.FingerprintRecord{
		Hash:       fpResult.Fingerprint,
		FilePath:   cleanPath,
		Title:      fallbackTitle,
		Artist:     fallbackArtist,
		Album:      "",
		DurationMs: durationMs,
		Source:     "local_unrecognized",
		UpdatedAt:  nowSec,
	}

	_ = repo.UpsertFingerprint(ctx, fpRecord)
	_ = repo.UpsertLocalTrack(ctx, track)

	return track, nil
}
