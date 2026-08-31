/*
 * Package: sleeptimer
 * File: sleeptimer_test.go
 * Purpose: Unit tests for sleep timer countdown, volume attenuation, and cancel mechanics.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package sleeptimer

import (
	"testing"
)

// TestSleepTimerLifecycle validates start, status, and stop operations.
func TestSleepTimerLifecycle(t *testing.T) {
	timer := NewTimer()

	// Initial state
	status := timer.GetStatus()
	if status.IsActive || status.CurrentVolumeGain != 1.0 {
		t.Errorf("expected inactive sleep timer with 1.0 volume gain")
	}

	// Start 30-min timer
	timer.Start(30, false)
	status = timer.GetStatus()
	if !status.IsActive || status.RemainingSec <= 0 {
		t.Errorf("expected active sleep timer with positive remaining seconds")
	}

	// Stop timer
	timer.Stop()
	status = timer.GetStatus()
	if status.IsActive {
		t.Errorf("expected inactive timer after stop")
	}
}
