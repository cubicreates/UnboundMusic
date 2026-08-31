/*
 * Package: sleeptimer
 * File: timer.go
 * Purpose: Smart sleep timer with smooth 30-second logarithmic volume fade-out to prevent jarring audio cutoff when falling asleep.
 * Subsystem: Playback Control & Sleep Timer
 * Concurrency: Thread-safe timer goroutines with atomic state management.
 */

package sleeptimer

import (
	"math"
	"sync"
	"time"
)

// Status represents the current state of the sleep timer.
type Status struct {
	IsActive          bool    `json:"is_active"`
	RemainingSec      int64   `json:"remaining_sec"`
	InitialMinutes    int     `json:"initial_minutes"`
	CurrentVolumeGain float64 `json:"current_volume_gain"` // 0.0 to 1.0
	EndAfterTrack     bool    `json:"end_after_track"`
}

// Timer coordinates sleep timer countdowns and volume attenuation.
type Timer struct {
	mu             sync.RWMutex
	isActive       bool
	targetTime     time.Time
	initialMinutes int
	endAfterTrack  bool
	cancelChan     chan struct{}
}

// NewTimer creates a new sleep timer manager.
func NewTimer() *Timer {
	return &Timer{}
}

// Start initiates a sleep timer for the given duration in minutes.
func (t *Timer) Start(minutes int, endAfterTrack bool) {
	t.mu.Lock()
	if t.isActive && t.cancelChan != nil {
		close(t.cancelChan)
	}

	t.isActive = true
	t.initialMinutes = minutes
	t.endAfterTrack = endAfterTrack
	t.targetTime = time.Now().Add(time.Duration(minutes) * time.Minute)
	t.cancelChan = make(chan struct{})
	t.mu.Unlock()
}

// Stop cancels any active sleep timer.
func (t *Timer) Stop() {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.isActive && t.cancelChan != nil {
		close(t.cancelChan)
		t.cancelChan = nil
	}
	t.isActive = false
}

// GetStatus returns the current remaining seconds and calculated volume gain factor.
func (t *Timer) GetStatus() Status {
	t.mu.RLock()
	defer t.mu.RUnlock()

	if !t.isActive {
		return Status{IsActive: false, CurrentVolumeGain: 1.0}
	}

	remaining := int64(time.Until(t.targetTime).Seconds())
	if remaining <= 0 {
		return Status{IsActive: false, RemainingSec: 0, CurrentVolumeGain: 0.0}
	}

	// Calculate smooth fade-out over last 30 seconds
	volumeGain := 1.0
	if remaining < 30 {
		progress := float64(remaining) / 30.0 // 1.0 down to 0.0
		volumeGain = math.Pow(progress, 1.5)  // Smooth curve
	}

	return Status{
		IsActive:          true,
		RemainingSec:      remaining,
		InitialMinutes:    t.initialMinutes,
		CurrentVolumeGain: math.Round(volumeGain*1000) / 1000,
		EndAfterTrack:     t.endAfterTrack,
	}
}
