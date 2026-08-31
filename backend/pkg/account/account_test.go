/*
 * Package: account
 * File: account_test.go
 * Purpose: Unit tests for SAPISIDHASH generation and account sync state management.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package account

import (
	"context"
	"strings"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// TestSAPISIDHashGeneration validates authorization header formatting.
func TestSAPISIDHashGeneration(t *testing.T) {
	hash := GenerateSAPISIDHash("dummy_sapisid", "https://music.youtube.com")

	if !strings.HasPrefix(hash, "SAPISIDHASH ") {
		t.Errorf("expected SAPISIDHASH prefix, got %s", hash)
	}

	parts := strings.Split(strings.TrimPrefix(hash, "SAPISIDHASH "), "_")
	if len(parts) != 2 {
		t.Errorf("expected timestamp_hash format, got %s", hash)
	}
}

// TestLibrarySync validates liking tracks and local state tracking.
func TestLibrarySync(t *testing.T) {
	syncer := NewSyncer()
	syncer.AddLikedTrack(models.Track{ID: "track_123", Title: "HUMBLE.", Artist: "Kendrick Lamar"})

	lib, err := syncer.SyncLibrary(context.Background())
	if err != nil {
		t.Fatalf("SyncLibrary failed: %v", err)
	}

	if lib.LikedTracksCount != 1 {
		t.Errorf("expected 1 liked track, got %d", lib.LikedTracksCount)
	}
}
