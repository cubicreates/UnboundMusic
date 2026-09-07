/*
 * Package: canvas
 * File: disk_cache_test.go
 * Purpose: Unit tests for on-disk visual LRU caching, file touch updates, and capacity pruning.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit testing.
 */

package canvas

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

// TestDiskLRUCachePutGetEvict verifies local caching and quota pruning behavior.
func TestDiskLRUCachePutGetEvict(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "unbound_lru_test_*")
	if err != nil {
		t.Fatalf("failed creating temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	// Set small quota of 500 bytes
	cache, err := NewDiskLRUCache(tempDir, 500)
	if err != nil {
		t.Fatalf("failed creating DiskLRUCache: %v", err)
	}

	// 1. Verify missing key returns false
	if _, found := cache.Get("nonexistent_track"); found {
		t.Errorf("expected nonexistent key to return false")
	}

	// 2. Create simulated cached file
	key1 := "track_one:artist_one"
	fileName1 := sanitizeKey(key1) + ".jpg"
	filePath1 := filepath.Join(tempDir, fileName1)

	// Write 300 bytes
	payload1 := make([]byte, 300)
	if err := os.WriteFile(filePath1, payload1, 0644); err != nil {
		t.Fatalf("failed writing simulated asset 1: %v", err)
	}

	// Verify Get finds it
	foundPath, found := cache.Get(key1)
	if !found || foundPath != filePath1 {
		t.Errorf("expected Get to find %s, got %s (found=%v)", filePath1, foundPath, found)
	}

	// 3. Sleep slightly to ensure distinct mtime
	time.Sleep(20 * time.Millisecond)

	// Create second file of 300 bytes -> total 600 bytes > 500 bytes quota
	key2 := "track_two:artist_two"
	fileName2 := sanitizeKey(key2) + ".jpg"
	filePath2 := filepath.Join(tempDir, fileName2)
	payload2 := make([]byte, 300)
	if err := os.WriteFile(filePath2, payload2, 0644); err != nil {
		t.Fatalf("failed writing simulated asset 2: %v", err)
	}

	// Trigger prune
	cache.mu.Lock()
	cache.pruneLocked(0)
	cache.mu.Unlock()

	// Oldest file (filePath1) should have been evicted to make room
	if _, err := os.Stat(filePath1); !os.IsNotExist(err) {
		t.Errorf("expected oldest file %s to be evicted", filePath1)
	}

	// Newer file (filePath2) should still exist
	if _, err := os.Stat(filePath2); err != nil {
		t.Errorf("expected newer file %s to be retained", filePath2)
	}
}
