/*
 * Package: gatekeeper
 * File: gatekeeper_test.go
 * Purpose: Unit tests for adaptive storage gatekeeper thresholds and mode selection.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package gatekeeper

import (
	"os"
	"testing"
)

// TestCheckStorageCapacity validates that storage threshold inspection runs without panic.
func TestCheckStorageCapacity(t *testing.T) {
	tempDir := t.TempDir()
	status, err := CheckStorageCapacity(tempDir)
	if err != nil {
		t.Fatalf("CheckStorageCapacity failed: %v", err)
	}

	if status.FreeBytes <= 0 {
		t.Errorf("expected positive free bytes, got %d", status.FreeBytes)
	}

	if status.Mode != StorageModeFullAI && status.Mode != StorageModeHeuristicBM25 {
		t.Errorf("invalid storage mode: %s", status.Mode)
	}
}

// TestIsPayloadExtractionAllowed tests boolean extraction check.
func TestIsPayloadExtractionAllowed(t *testing.T) {
	allowed := IsPayloadExtractionAllowed(os.TempDir())
	// Boolean result must evaluate without error
	_ = allowed
}
