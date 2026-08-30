/*
 * Package: fingerprint
 * File: ingest.go
 * Purpose: Safe storage ingestion rules (non-destructive COPY for WhatsApp/chat media, safe MOVE for loose downloads).
 * Subsystem: Storage & Fingerprint Engine
 * Concurrency: Thread-safe file operations with proper error handling and destination directory creation.
 */

package fingerprint

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// IngestionConfig defines target destination folders and ingestion behavior.
type IngestionConfig struct {
	LibraryRootDir string `json:"library_root_dir"`
	DryRun         bool   `json:"dry_run"`
}

// DefaultIngestionConfig provides standard target path defaults.
func DefaultIngestionConfig(rootDir string) IngestionConfig {
	if rootDir == "" {
		rootDir = filepath.Join(os.TempDir(), "UnboundMusic", "Library")
	}
	return IngestionConfig{
		LibraryRootDir: rootDir,
		DryRun:         false,
	}
}

// IsProtectedChatMedia checks if a source path resides in a messaging app media folder (WhatsApp, Telegram, Signal).
func IsProtectedChatMedia(srcPath string) bool {
	normalized := strings.ToLower(filepath.ToSlash(srcPath))
	protectedMarkers := []string{
		"whatsapp",
		"com.whatsapp",
		"telegram",
		"org.telegram.messenger",
		"signal",
		"org.thoughtcrime.securesms",
		"discord",
		"viber",
		"line",
	}

	for _, marker := range protectedMarkers {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	return false
}

// IngestTrack evaluates a single audio file, applies safety rules (COPY vs MOVE), and organizes into library.
func IngestTrack(meta *AudioMetadata, artist, album, title string, cfg IngestionConfig) (*models.IngestionResult, error) {
	if meta == nil {
		return nil, fmt.Errorf("audio metadata cannot be nil")
	}

	classification := ClassifyAudio(meta)
	if !classification.IsMusic {
		return &models.IngestionResult{
			SourcePath:  meta.FilePath,
			Action:      "IGNORED",
			Fingerprint: meta.AcousticHash,
			DurationMs:  meta.DurationMs,
			IsNoise:     true,
		}, nil
	}

	// Sanitize artist, album, and title for destination filesystem paths
	cleanArtist := sanitizePathPart(artist, "Unknown Artist")
	cleanAlbum := sanitizePathPart(album, "Unknown Album")
	cleanTitle := sanitizePathPart(title, strings.TrimSuffix(filepath.Base(meta.FilePath), filepath.Ext(meta.FilePath)))

	destDir := filepath.Join(cfg.LibraryRootDir, cleanArtist, cleanAlbum)
	destFileName := fmt.Sprintf("%s%s", cleanTitle, meta.Extension)
	destPath := filepath.Join(destDir, destFileName)

	isChatMedia := IsProtectedChatMedia(meta.FilePath)
	action := "MOVED"
	if isChatMedia {
		action = "COPIED"
	}

	if !cfg.DryRun {
		if err := os.MkdirAll(destDir, 0755); err != nil {
			return nil, fmt.Errorf("failed to create destination library directory: %w", err)
		}

		if isChatMedia {
			// Non-destructive COPY for WhatsApp/Telegram media to keep chat app history functional
			if err := copyFile(meta.FilePath, destPath); err != nil {
				return nil, fmt.Errorf("failed to copy protected chat media: %w", err)
			}
		} else {
			// Safe MOVE for loose files (Downloads, temp folders)
			if err := moveFile(meta.FilePath, destPath); err != nil {
				return nil, fmt.Errorf("failed to move loose audio file: %w", err)
			}
		}
	}

	return &models.IngestionResult{
		SourcePath:  meta.FilePath,
		TargetPath:  destPath,
		Action:      action,
		Fingerprint: meta.AcousticHash,
		DurationMs:  meta.DurationMs,
		IsNoise:     false,
	}, nil
}

// copyFile performs atomic file content duplication.
func copyFile(src, dst string) error {
	sourceFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer sourceFile.Close()

	destFile, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer destFile.Close()

	if _, err := io.Copy(destFile, sourceFile); err != nil {
		return err
	}

	return destFile.Sync()
}

// moveFile moves a file, falling back to copy-then-delete across different drive partitions.
func moveFile(src, dst string) error {
	// First attempt atomic rename
	if err := os.Rename(src, dst); err == nil {
		return nil
	}

	// Fallback across different filesystems / drive mount points
	if err := copyFile(src, dst); err != nil {
		return err
	}
	return os.Remove(src)
}

// sanitizePathPart removes illegal filesystem characters like / \ : * ? " < > |
func sanitizePathPart(val, fallback string) string {
	trimmed := strings.TrimSpace(val)
	if trimmed == "" {
		return fallback
	}

	illegalChars := `/\:*?"<>|`
	clean := strings.Map(func(r rune) rune {
		if strings.ContainsRune(illegalChars, r) {
			return '_'
		}
		return r
	}, trimmed)

	return strings.TrimSpace(clean)
}
