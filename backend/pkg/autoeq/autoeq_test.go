/*
 * Package: autoeq
 * File: autoeq_test.go
 * Purpose: Unit tests for headphone calibration curve lookups and 10-band equalization curve verification.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package autoeq

import (
	"testing"
)

// TestAutoEqSearchAndPreset validates headphone search and 10-band gain extraction.
func TestAutoEqSearchAndPreset(t *testing.T) {
	engine := NewEngine()

	// Test Search
	results := engine.SearchHeadphones("Sony")
	if len(results) == 0 {
		t.Fatalf("expected to find Sony headphones in database")
	}

	foundXM5 := false
	for _, m := range results {
		if m.ID == "sony_wh1000xm5" {
			foundXM5 = true
			break
		}
	}
	if !foundXM5 {
		t.Errorf("expected WH-1000XM5 in search results")
	}

	// Test Preset Retrieval
	preset, err := engine.GetEQPreset("sony_wh1000xm5")
	if err != nil {
		t.Fatalf("GetEQPreset failed: %v", err)
	}

	if len(preset.Bands) != 10 {
		t.Errorf("expected 10 EQ bands, got %d", len(preset.Bands))
	}

	if preset.PreampGainDB >= 0 {
		t.Errorf("expected negative preamp gain to prevent digital clipping, got %f", preset.PreampGainDB)
	}
}
