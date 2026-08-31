/*
 * Package: podcasts
 * File: podcasts_test.go
 * Purpose: Unit tests for podcast playback resumption timestamps and show browsing.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package podcasts

import (
	"testing"
)

// TestPodcastResumePositions validates recording and retrieving exact second episode timestamps.
func TestPodcastResumePositions(t *testing.T) {
	eng := NewEngine(nil)

	episodeID := "episode_huberman_123"
	eng.SaveResumePosition(episodeID, 345000) // 5 minutes 45 seconds

	pos := eng.GetResumePosition(episodeID)
	if pos != 345000 {
		t.Errorf("expected 345000ms resume position, got %d", pos)
	}

	// Non-existent episode should return 0
	unknownPos := eng.GetResumePosition("non_existent")
	if unknownPos != 0 {
		t.Errorf("expected 0ms for unknown episode, got %d", unknownPos)
	}
}
