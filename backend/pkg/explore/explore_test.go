/*
 * Package: explore
 * File: explore_test.go
 * Purpose: Unit tests for Moods & Moments category discovery.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package explore

import (
	"testing"
)

// TestGetMoodCategories validates default curated mood sections.
func TestGetMoodCategories(t *testing.T) {
	eng := NewEngine(nil)
	moods := eng.GetMoodCategories()

	if len(moods) < 8 {
		t.Errorf("expected at least 8 mood categories, got %d", len(moods))
	}

	foundChill := false
	for _, m := range moods {
		if m.ID == "chill" {
			foundChill = true
			break
		}
	}

	if !foundChill {
		t.Errorf("expected 'chill' category in moods list")
	}
}
