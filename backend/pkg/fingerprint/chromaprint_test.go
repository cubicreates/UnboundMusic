/*
 * Package: fingerprint
 * File: chromaprint_test.go
 * Purpose: Unit tests for in-process Chromaprint filterbank fingerprinting and AcoustID base64 encoding.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit testing.
 */

package fingerprint

import (
	"context"
	"math"
	"os"
	"path/filepath"
	"testing"
)

func TestInProcessChromaprintPCM(t *testing.T) {
	// Generate 3 seconds of 44100Hz 440Hz test sine wave
	sampleRate := 44100
	durationSec := 3.0
	numSamples := int(durationSec * float64(sampleRate))
	samples := make([]float32, numSamples)

	for i := 0; i < numSamples; i++ {
		t := float64(i) / float64(sampleRate)
		samples[i] = float32(0.8 * math.Sin(2.0*math.Pi*440.0*t))
	}

	// 1. Generate Chromaprint in-process from PCM
	res, err := GenerateChromaprintFromPCM(samples, sampleRate)
	if err != nil {
		t.Fatalf("expected successful in-process chromaprint generation, got %v", err)
	}

	if res.Duration < 2.9 || res.Duration > 3.1 {
		t.Errorf("expected ~3.0s duration, got %f", res.Duration)
	}
	if len(res.Fingerprint) == 0 {
		t.Errorf("expected non-empty base64 fingerprint")
	}

	// 2. Test empty sample error handling
	_, errEmpty := GenerateChromaprintFromPCM(nil, 44100)
	if errEmpty == nil {
		t.Errorf("expected error on empty samples")
	}
}

func TestInProcessFileFallback(t *testing.T) {
	tempDir := t.TempDir()
	rawAudioFile := filepath.Join(tempDir, "mock_audio.raw")

	// Write 50000 16-bit PCM samples (~1.14s at 44.1kHz, enough for multiple 4096 frames)
	buf := make([]byte, 100000)
	for i := 0; i < len(buf); i++ {
		buf[i] = byte(i % 256)
	}
	if err := os.WriteFile(rawAudioFile, buf, 0644); err != nil {
		t.Fatalf("failed to write mock audio file: %v", err)
	}

	// Generate fingerprint directly through in-process file reader
	res, err := GenerateFingerprintFromFileInProcess(rawAudioFile)
	if err != nil {
		t.Fatalf("in-process file fingerprinting failed: %v", err)
	}
	if res == nil || len(res.Fingerprint) == 0 {
		t.Fatalf("expected valid fingerprint result, got %+v", res)
	}

	// Test GenerateFingerprint with invalid fpcalc path (forces fallback)
	resFallback, err := GenerateFingerprint(context.Background(), "invalid_nonexistent_fpcalc", rawAudioFile)
	if err != nil {
		t.Fatalf("expected fallback to succeed, got %v", err)
	}
	if resFallback == nil || len(resFallback.Fingerprint) == 0 {
		t.Fatalf("expected valid fingerprint on fallback, got %+v", resFallback)
	}
}
