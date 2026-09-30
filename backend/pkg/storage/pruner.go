/*
 * Package: storage
 * File: pruner.go
 * Purpose: Automatic stale audio stream and range cache pruning engine to enforce LRU disk quotas
 *          and prevent storage bloat in mobile and desktop environments.
 * Subsystem: Storage Engine / Cache Lifecycle
 * Concurrency: Thread-safe cache scanning and atomic file removal.
 */

package storage

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// PrunePolicy specifies criteria for evicting cached files.
type PrunePolicy struct {
	MaxAge        time.Duration `json:"max_age"`        // Max age threshold (e.g. 7 days).
	TargetQuota   int64         `json:"target_quota"`   // Max bytes to keep (e.g. 2 GB).
	WatermarkFrac float64       `json:"watermark_frac"` // Prune down to this fraction of quota (e.g. 0.80).
}

// DefaultPrunePolicy provides sensible defaults: 7-day TTL and 2 GB quota pruned to 80% watermark.
var DefaultPrunePolicy = PrunePolicy{
	MaxAge:        7 * 24 * time.Hour,
	TargetQuota:   DefaultCacheQuotaBytes,
	WatermarkFrac: 0.80,
}

// PruneResult details the outcome of a cache cleanup execution.
type PruneResult struct {
	FilesDeleted   int   `json:"files_deleted"`
	BytesFreed     int64 `json:"bytes_freed"`
	RemainingBytes int64 `json:"remaining_bytes"`
}

// CachePruner coordinates scheduled and reactive cache eviction.
type CachePruner struct {
	mu sync.Mutex
	sm *StorageManager
}

// NewCachePruner initializes a cache pruner bound to a StorageManager.
func NewCachePruner(sm *StorageManager) *CachePruner {
	return &CachePruner{sm: sm}
}

// Prune executes cache cleanup based on the provided policy.
func (cp *CachePruner) Prune(policy PrunePolicy) (*PruneResult, error) {
	cp.mu.Lock()
	defer cp.mu.Unlock()

	cacheDir, err := cp.sm.GetPartitionPath(PartitionInternalCache)
	if err != nil {
		return nil, fmt.Errorf("failed resolving cache partition: %w", err)
	}

	if _, err := os.Stat(cacheDir); os.IsNotExist(err) {
		return &PruneResult{}, nil
	}

	var entries []FileEntry
	var totalBytes int64

	now := time.Now()
	res := &PruneResult{}

	// 1. Walk directory and collect file metadata
	err = filepath.Walk(cacheDir, func(path string, info os.FileInfo, walkErr error) error {
		if walkErr != nil || info.IsDir() {
			return nil
		}

		size := info.Size()
		modTime := info.ModTime()

		// Immediate age-based eviction if MaxAge is configured
		if policy.MaxAge > 0 && now.Sub(modTime) > policy.MaxAge {
			if removeErr := os.Remove(path); removeErr == nil {
				res.FilesDeleted++
				res.BytesFreed += size
				return nil
			}
		}

		entries = append(entries, FileEntry{
			Path:    path,
			Size:    size,
			ModTime: modTime,
		})
		totalBytes += size
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("failed walking cache directory: %w", err)
	}

	// 2. Quota-based eviction (LRU: oldest modified first)
	quota := policy.TargetQuota
	if quota <= 0 {
		quota = cp.sm.GetCacheQuota()
	}

	targetBytes := quota
	if policy.WatermarkFrac > 0 && policy.WatermarkFrac < 1.0 {
		targetBytes = int64(float64(quota) * policy.WatermarkFrac)
	}

	if totalBytes > quota {
		// Sort by ModTime ascending (oldest first)
		sort.Slice(entries, func(i, j int) bool {
			return entries[i].ModTime.Before(entries[j].ModTime)
		})

		for _, file := range entries {
			if totalBytes <= targetBytes {
				break
			}
			if removeErr := os.Remove(file.Path); removeErr == nil {
				res.FilesDeleted++
				res.BytesFreed += file.Size
				totalBytes -= file.Size
			}
		}
	}

	res.RemainingBytes = totalBytes
	return res, nil
}
