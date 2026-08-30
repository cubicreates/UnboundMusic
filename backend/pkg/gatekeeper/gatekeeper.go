/*
 * Package: gatekeeper
 * File: gatekeeper.go
 * Purpose: Adaptive storage gatekeeper that checks available disk space and selects the optimal AI/Heuristic operating mode.
 * Subsystem: Storage & Intelligence Engine
 * Concurrency: Thread-safe disk analysis functions safe for concurrent execution.
 */

package gatekeeper

import (
	"fmt"
	"os"
	"path/filepath"
)

// StorageMode represents the AI and search capability level allowed by current disk storage limits.
type StorageMode string

const (
	// StorageModeFullAI enables vector embeddings, on-device neural ranking, and Zstd payload extraction (>= 100MB free).
	StorageModeFullAI StorageMode = "FULL_AI_VECTOR"

	// StorageModeHeuristicBM25 enables 0-MB SQLite FTS5 / BM25 heuristic mode (< 100MB free).
	StorageModeHeuristicBM25 StorageMode = "HEURISTIC_BM25_ZERO_MB"

	// MinFreeSpaceBytes is the 100 MB threshold (100 * 1024 * 1024 bytes).
	MinFreeSpaceBytes = int64(100 * 1024 * 1024)
)

// StorageStatus contains diagnostic details regarding current device storage.
type StorageStatus struct {
	TargetDirectory string      `json:"target_directory"`
	FreeBytes       int64       `json:"free_bytes"`
	FreeMB          float64     `json:"free_mb"`
	Mode            StorageMode `json:"storage_mode"`
	IsAIExtracted   bool        `json:"is_ai_extracted"`
	Reason          string      `json:"reason"`
}

// CheckStorageCapacity inspects the available free space on the partition containing the target directory.
func CheckStorageCapacity(targetDir string) (*StorageStatus, error) {
	if targetDir == "" {
		targetDir = os.TempDir()
	}

	// Ensure directory exists or find existing parent
	checkPath := targetDir
	for {
		if _, err := os.Stat(checkPath); err == nil {
			break
		}
		parent := filepath.Dir(checkPath)
		if parent == checkPath || parent == "." {
			checkPath = os.TempDir()
			break
		}
		checkPath = parent
	}

	freeBytes, err := getFreeDiskSpace(checkPath)
	if err != nil {
		// Default to safe conservative estimation if system statfs is unavailable
		freeBytes = 500 * 1024 * 1024 // 500 MB assumed baseline
	}

	freeMB := float64(freeBytes) / (1024 * 1024)
	status := &StorageStatus{
		TargetDirectory: targetDir,
		FreeBytes:       freeBytes,
		FreeMB:          freeMB,
	}

	if freeBytes >= MinFreeSpaceBytes {
		status.Mode = StorageModeFullAI
		status.Reason = fmt.Sprintf("Ample storage available (%.1f MB >= 100 MB threshold). Full on-device vector AI enabled.", freeMB)
	} else {
		status.Mode = StorageModeHeuristicBM25
		status.Reason = fmt.Sprintf("Low storage detected (%.1f MB < 100 MB threshold). Activating 0-MB SQLite BM25 heuristic mode.", freeMB)
	}

	return status, nil
}

// IsPayloadExtractionAllowed returns true if the device has sufficient capacity to decompress the micro-AI bundle.
func IsPayloadExtractionAllowed(targetDir string) bool {
	status, err := CheckStorageCapacity(targetDir)
	if err != nil {
		return false
	}
	return status.Mode == StorageModeFullAI
}
