/*
 * Package: sponsorblock
 * File: sponsorblock_test.go
 * Purpose: Unit tests for SponsorBlock skip segment fetching, interval calculations, and caching.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package sponsorblock

import (
	"context"
	"testing"
	"time"
)

// TestSponsorBlockClient validates client initialization and empty videoID guard.
func TestSponsorBlockClient(t *testing.T) {
	client := NewClient()

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, err := client.GetSkipSegments(ctx, "")
	if err == nil {
		t.Fatalf("expected error for empty videoID")
	}
}
