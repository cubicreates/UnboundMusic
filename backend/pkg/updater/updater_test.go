/*
 * Package: updater
 * File: updater_test.go
 * Purpose: Unit tests for GitHub release updater and version string parsing.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package updater

import (
	"context"
	"testing"
)

// TestUpdaterInitialization validates initial version and repo configurations.
func TestUpdaterInitialization(t *testing.T) {
	up := NewUpdater("1.0.0")

	if up.currentVersion != "1.0.0" {
		t.Errorf("expected version 1.0.0, got %s", up.currentVersion)
	}

	info, err := up.CheckForUpdates(context.Background())
	if err != nil {
		t.Fatalf("CheckForUpdates failed: %v", err)
	}

	if info.CurrentVersion != "1.0.0" {
		t.Errorf("expected current version 1.0.0, got %s", info.CurrentVersion)
	}
}
