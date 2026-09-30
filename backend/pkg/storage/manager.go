/*
 * Package: storage
 * File: manager.go
 * Purpose: Scoped Storage partition manager providing strict boundary isolation between
 *          app-private internal cache (ephemeral streams, PCM buffers) and public user exports.
 * Subsystem: Storage Engine & Media Management
 * Concurrency: Thread-safe storage path resolution and file export operations.
 */

package storage

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// PartitionType distinguishes app-private cached artifacts from user-facing public media.
type PartitionType string

const (
	PartitionInternalCache PartitionType = "internal_cache"
	PartitionPublicExports PartitionType = "public_exports"
	PartitionModels        PartitionType = "models"
	PartitionDatabase      PartitionType = "database"
)

// StoragePartition represents an isolated filesystem domain with security and quota metadata.
type StoragePartition struct {
	Type       PartitionType `json:"type"`
	Path       string        `json:"path"`
	IsPrivate  bool          `json:"is_private"`
	QuotaBytes int64         `json:"quota_bytes"` // 0 = unlimited
	UsedBytes  int64         `json:"used_bytes"`
}

// StorageManager orchestrates Scoped Storage partitioning and managed file exports.
type StorageManager struct {
	mu         sync.RWMutex
	tree       *DirectoryTree
	partitions map[PartitionType]*StoragePartition
	cacheQuota int64
}

// DefaultCacheQuotaBytes is the default maximum disk budget for cached audio streams (2 GB).
const DefaultCacheQuotaBytes int64 = 2 * 1024 * 1024 * 1024

// NewStorageManager creates a StorageManager wrapping the provisioned directory tree.
func NewStorageManager(tree *DirectoryTree, cacheQuota ...int64) *StorageManager {
	quota := DefaultCacheQuotaBytes
	if len(cacheQuota) > 0 && cacheQuota[0] > 0 {
		quota = cacheQuota[0]
	}

	sm := &StorageManager{
		tree:       tree,
		partitions: make(map[PartitionType]*StoragePartition),
		cacheQuota: quota,
	}
	sm.initPartitions()
	return sm
}

func (sm *StorageManager) initPartitions() {
	sm.partitions[PartitionInternalCache] = &StoragePartition{
		Type:       PartitionInternalCache,
		Path:       sm.tree.CachePath,
		IsPrivate:  true,
		QuotaBytes: sm.cacheQuota,
	}

	sm.partitions[PartitionPublicExports] = &StoragePartition{
		Type:       PartitionPublicExports,
		Path:       sm.tree.DownloadPath,
		IsPrivate:  false,
		QuotaBytes: 0, // Unconstrained public export storage
	}

	sm.partitions[PartitionModels] = &StoragePartition{
		Type:       PartitionModels,
		Path:       sm.tree.ModelsPath,
		IsPrivate:  true,
		QuotaBytes: 500 * 1024 * 1024, // 500 MB for AI / ONNX models
	}

	sm.partitions[PartitionDatabase] = &StoragePartition{
		Type:       PartitionDatabase,
		Path:       sm.tree.SQLitePath,
		IsPrivate:  true,
		QuotaBytes: 250 * 1024 * 1024,
	}
}

// GetPartitionPath returns the verified absolute directory path for a given partition type.
func (sm *StorageManager) GetPartitionPath(pType PartitionType) (string, error) {
	sm.mu.RLock()
	defer sm.mu.RUnlock()

	p, exists := sm.partitions[pType]
	if !exists {
		return "", fmt.Errorf("unknown partition type: %s", pType)
	}
	return p.Path, nil
}

// ExportTrack safely transfers an audio file from private cache into public export storage.
func (sm *StorageManager) ExportTrack(sourceTempPath, destinationFilename string) (string, error) {
	sm.mu.Lock()
	defer sm.mu.Unlock()

	if _, err := os.Stat(sourceTempPath); os.IsNotExist(err) {
		return "", fmt.Errorf("source file does not exist: %s", sourceTempPath)
	}

	exportDir := sm.tree.DownloadPath
	if err := os.MkdirAll(exportDir, 0755); err != nil {
		return "", fmt.Errorf("failed creating export directory: %w", err)
	}

	destPath := filepath.Join(exportDir, destinationFilename)

	// Attempt fast atomic rename first
	if err := os.Rename(sourceTempPath, destPath); err == nil {
		return destPath, nil
	}

	// Fallback to cross-device file stream copy
	srcFile, err := os.Open(sourceTempPath)
	if err != nil {
		return "", fmt.Errorf("failed opening source file: %w", err)
	}
	defer srcFile.Close()

	destFile, err := os.OpenFile(destPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
	if err != nil {
		return "", fmt.Errorf("failed creating destination export file: %w", err)
	}
	defer destFile.Close()

	if _, err := io.Copy(destFile, srcFile); err != nil {
		return "", fmt.Errorf("failed copying audio data to export destination: %w", err)
	}

	_ = os.Remove(sourceTempPath)
	return destPath, nil
}

// ComputePartitionUsage scans a partition and calculates total disk consumption in bytes.
func (sm *StorageManager) ComputePartitionUsage(pType PartitionType) (int64, error) {
	sm.mu.Lock()
	defer sm.mu.Unlock()

	p, exists := sm.partitions[pType]
	if !exists {
		return 0, fmt.Errorf("unknown partition type: %s", pType)
	}

	var totalBytes int64
	err := filepath.Walk(p.Path, func(_ string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if !info.IsDir() {
			totalBytes += info.Size()
		}
		return nil
	})
	if err != nil {
		return 0, err
	}

	p.UsedBytes = totalBytes
	return totalBytes, nil
}

// SetCacheQuota updates the maximum allowable cache budget.
func (sm *StorageManager) SetCacheQuota(quotaBytes int64) {
	sm.mu.Lock()
	defer sm.mu.Unlock()

	if quotaBytes > 0 {
		sm.cacheQuota = quotaBytes
		if p, ok := sm.partitions[PartitionInternalCache]; ok {
			p.QuotaBytes = quotaBytes
		}
	}
}

// GetCacheQuota returns the current cache quota in bytes.
func (sm *StorageManager) GetCacheQuota() int64 {
	sm.mu.RLock()
	defer sm.mu.RUnlock()
	return sm.cacheQuota
}

// FileEntry represents a file in cache with size and last modified timestamp.
type FileEntry struct {
	Path     string
	Size     int64
	ModTime  time.Time
}
