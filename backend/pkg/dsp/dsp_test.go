/*
 * Package: dsp
 * File: dsp_test.go
 * Purpose: Unit tests for ReplayGain volume leveling, DJ crossfade blending curves, and audio silence trimming.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package dsp

import (
	"testing"
)

// TestCalculateReplayGain validates RMS calculations and peak limiting.
func TestCalculateReplayGain(t *testing.T) {
	samples := []float32{0.1, 0.2, -0.2, 0.3, -0.3, 0.1}
	res := CalculateReplayGain(samples, -14.0)

	if res.RecommendedScale <= 0 {
		t.Errorf("expected positive recommended scale multiplier, got %f", res.RecommendedScale)
	}
}

// TestCalculateCrossfadeGains validates constant power and linear curve blends.
func TestCalculateCrossfadeGains(t *testing.T) {
	// Midpoint test (progress = 0.5)
	gainA, gainB := CalculateCrossfadeGains(0.5, CurveConstantPower)
	if gainA < 0.6 || gainB < 0.6 {
		t.Errorf("expected constant power midpoint gain around ~0.707, got gainA=%f gainB=%f", gainA, gainB)
	}

	gainLinearA, gainLinearB := CalculateCrossfadeGains(0.5, CurveLinear)
	if gainLinearA != 0.5 || gainLinearB != 0.5 {
		t.Errorf("expected linear midpoint gain 0.5, got %f, %f", gainLinearA, gainLinearB)
	}
}

// TestDetectSilenceBoundaries validates lead and tail silence detection.
func TestDetectSilenceBoundaries(t *testing.T) {
	sampleRate := 1000
	samples := make([]float32, 3000) // 3 seconds

	// 1 second silence, 1 second active tone, 1 second silence
	for i := 1000; i < 2000; i++ {
		samples[i] = 0.5
	}

	res := DetectSilenceBoundaries(samples, sampleRate, -50.0)

	if res.LeadSilenceMs < 900 || res.LeadSilenceMs > 1100 {
		t.Errorf("expected lead silence near 1000ms, got %d ms", res.LeadSilenceMs)
	}
	if res.TailSilenceMs < 900 || res.TailSilenceMs > 1100 {
		t.Errorf("expected tail silence near 1000ms, got %d ms", res.TailSilenceMs)
	}
}
