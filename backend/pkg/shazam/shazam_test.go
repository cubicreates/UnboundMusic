/*
 * Package: shazam
 * File: shazam_test.go
 * Purpose: Unit tests for audio DSP spectrogram peak extraction, landmark frequency pairing, binary signature encoding, and offline fallback matching.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go test harness.
 */

package shazam

import (
	"context"
	"math"
	"testing"
)

// TestExtractConstellationMap validates FFT frame extraction and spectral peak selection.
func TestExtractConstellationMap(t *testing.T) {
	sampleRate := 16000
	numSamples := sampleRate * 2 // 2 seconds
	samples := make([]float32, numSamples)

	// Synthesize 440Hz tone
	for i := 0; i < numSamples; i++ {
		timeSec := float64(i) / float64(sampleRate)
		samples[i] = float32(0.5 * math.Sin(2*math.Pi*440*timeSec))
	}

	cmap, err := ExtractConstellationMap(samples, sampleRate)
	if err != nil {
		t.Fatalf("ExtractConstellationMap failed: %v", err)
	}

	if cmap.SampleRate != 16000 {
		t.Errorf("expected sample rate 16000, got %d", cmap.SampleRate)
	}

	if len(cmap.Peaks) == 0 {
		t.Fatalf("expected extracted peaks for 440Hz tone, got 0")
	}

	// Verify frequency of peak is in reasonable range of 440Hz
	found440 := false
	for _, p := range cmap.Peaks {
		if p.FrequencyHz >= 400 && p.FrequencyHz <= 480 {
			found440 = true
			break
		}
	}
	if !found440 {
		t.Errorf("expected to find spectral peak near 440Hz, got peaks: %v", cmap.Peaks)
	}
}

// TestEncodeConstellationToSignature validates binary signature packing and Base64 URI generation.
func TestEncodeConstellationToSignature(t *testing.T) {
	sampleRate := 16000
	samples := make([]float32, sampleRate*3) // 3 seconds

	// Multi-frequency chord
	for i := 0; i < len(samples); i++ {
		timeSec := float64(i) / float64(sampleRate)
		samples[i] = float32(0.4*math.Sin(2*math.Pi*440*timeSec) + 0.3*math.Sin(2*math.Pi*1200*timeSec))
	}

	cmap, err := ExtractConstellationMap(samples, sampleRate)
	if err != nil {
		t.Fatalf("ExtractConstellationMap failed: %v", err)
	}

	sig, err := EncodeConstellationToSignature(cmap)
	if err != nil {
		t.Fatalf("EncodeConstellationToSignature failed: %v", err)
	}

	if sig.LandmarkCount == 0 {
		t.Errorf("expected non-zero landmark pairs, got 0")
	}

	if len(sig.BinaryData) < 20 {
		t.Errorf("binary signature data too small: %d bytes", len(sig.BinaryData))
	}

	if len(sig.Base64URI) == 0 {
		t.Errorf("expected non-empty Base64URI")
	}
}

// TestOfflineMatchFallback validates graceful offline local vault lookup.
func TestOfflineMatchFallback(t *testing.T) {
	ctx := context.Background()
	res, err := MatchOffline(ctx, nil, "mock_hash_123")
	if err != nil {
		t.Fatalf("MatchOffline failed: %v", err)
	}

	if res.Matched {
		t.Errorf("expected Matched=false for nil repository")
	}
	if res.Source != "LOCAL_OFFLINE_VAULT" {
		t.Errorf("expected Source=LOCAL_OFFLINE_VAULT, got %s", res.Source)
	}
}
