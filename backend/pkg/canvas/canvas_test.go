/*
 * Package: canvas
 * File: canvas_test.go
 * Purpose: Unit tests for Spotify Canvas resolution and in-memory caching.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package canvas

import (
	"context"
	"testing"
)

// TestCanvasClientCaching validates caching behavior for canvas metadata.
func TestCanvasClientCaching(t *testing.T) {
	client := NewClient()

	res, err := client.GetCanvas(context.Background(), "Blinding Lights", "The Weeknd")
	if err != nil {
		t.Fatalf("GetCanvas failed: %v", err)
	}

	if res.Title != "Blinding Lights" {
		t.Errorf("expected title Blinding Lights, got %s", res.Title)
	}

	// Second query should hit cache
	cachedRes, err := client.GetCanvas(context.Background(), "Blinding Lights", "The Weeknd")
	if err != nil {
		t.Fatalf("cached GetCanvas failed: %v", err)
	}

	if cachedRes != res {
		t.Errorf("expected pointer match for cached canvas result")
	}
}
