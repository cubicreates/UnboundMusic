/*
 * Package: artist
 * File: artist_test.go
 * Purpose: Unit tests for artist profile caching and discography partitioning.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package artist

import (
	"context"
	"testing"
)

// TestArtistProfileCaching validates discography data retrieval and cache hits.
func TestArtistProfileCaching(t *testing.T) {
	eng := NewEngine(nil)

	prof, err := eng.GetArtistProfile(context.Background(), "Kendrick Lamar")
	if err != nil {
		t.Fatalf("GetArtistProfile failed: %v", err)
	}

	if prof.Name != "Kendrick Lamar" {
		t.Errorf("expected Kendrick Lamar, got %s", prof.Name)
	}

	if len(prof.Albums) == 0 {
		t.Errorf("expected albums in artist discography")
	}

	// Cache test
	cachedProf, err := eng.GetArtistProfile(context.Background(), "Kendrick Lamar")
	if err != nil || cachedProf != prof {
		t.Errorf("expected cache hit on repeated artist profile request")
	}
}
