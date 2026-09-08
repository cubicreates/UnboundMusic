/*
 * Package: server
 * File: memory.go
 * Purpose: Memory management and low-memory trimming for dual-GC harmony with Android ART runtime.
 * Subsystem: System Diagnostics & Memory Management
 * Concurrency: Thread-safe runtime memory tuning.
 */

package server

import (
	"runtime"
	"runtime/debug"
)

const (
	// DefaultMemoryLimitBytes defines the 128 MiB soft heap ceiling for mobile environments.
	DefaultMemoryLimitBytes = 128 * 1024 * 1024
	// DefaultGCPercent defines an aggressive collection cycle (50%) to prevent heap spikes.
	DefaultGCPercent = 50
)

// ConfigureMemoryCeiling establishes a bounded heap ceiling and collection target.
func ConfigureMemoryCeiling() {
	debug.SetMemoryLimit(DefaultMemoryLimitBytes)
	debug.SetGCPercent(DefaultGCPercent)
}

// TrimEngineMemory immediately executes garbage collection and returns idle memory to the OS kernel.
func TrimEngineMemory() {
	runtime.GC()
	debug.FreeOSMemory()
}
