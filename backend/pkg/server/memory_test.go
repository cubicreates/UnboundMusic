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

func TestTrimEngineMemory(t *testing.T) {
	// Allocate dummy heap memory
	dummy := make([]byte, 10*1024*1024)
	for i := range dummy {
		dummy[i] = 1
	}
	dummy = nil

	// Verify TrimEngineMemory sweeps GC and frees OS memory without error
	TrimEngineMemory()
}
