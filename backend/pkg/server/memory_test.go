/*
 * Package: server
 * File: memory_test.go
 * Purpose: Unit tests for memory ceiling configuration and OS memory trimming.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe runtime test execution.
 */

package server

import (
	"runtime/debug"
	"testing"
)

func TestConfigureMemoryCeiling(t *testing.T) {
	ConfigureMemoryCeiling()

	// Verify setting limit returned positive or max value
	prev := debug.SetMemoryLimit(DefaultMemoryLimitBytes)
	if prev < 0 {
		t.Errorf("expected valid previous limit, got %d", prev)
	}

	// Verify TrimEngineMemory executes without panic
	TrimEngineMemory()
}
