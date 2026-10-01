/*
 * Package: shazam
 * File: shazam_test.go
 * Purpose: Comprehensive unit tests for reverse-engineered Shazam DSP, 2048-point FFT, SigX binary encode/decode roundtrip, and recognition.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go test harness.
 */

package shazam

import (
	"context"
	"encoding/binary"
	"math"
	"os"
	"strings"
	"testing"
)

// TestHanningMatrix2048 verifies the 2048-element Hann window values.
func TestHanningMatrix2048(t *testing.T) {
	if len(hanningMatrix2048) != 2048 {
		t.Fatalf("expected 2048 window elements, got %d", len(hanningMatrix2048))
	}

	// First element should be > 0 (endpoints removed)
	if hanningMatrix2048[0] <= 0 {
		t.Errorf("expected positive first window element, got %f", hanningMatrix2048[0])
	}

	// Peak near center should be close to 1.0
	center := hanningMatrix2048[1024]
	if math.Abs(center-1.0) > 0.01 {
		t.Errorf("expected window center near 1.0, got %f", center)
	}
}

// TestRadix2FFT2048 validates the FFT on a pure 440 Hz sinusoidal input.
func TestRadix2FFT2048(t *testing.T) {
	var input [2048]complex128
	const sampleRate = 16000.0
	const freq = 440.0

	for i := 0; i < 2048; i++ {
		tSec := float64(i) / sampleRate
		val := math.Sin(2.0 * math.Pi * freq * tSec)
		input[i] = complex(val, 0)
	}

	spectrum := computeRadix2FFT2048(input)

	// Frequency bin corresponding to 440 Hz:
	// bin = 440 * 2048 / 16000 = 56.32 -> bin 56
	expectedBin := int(math.Round(freq * 2048.0 / sampleRate))

	maxMag := 0.0
	maxBin := 0
	for b := 0; b < 1025; b++ {
		re := real(spectrum[b])
		im := imag(spectrum[b])
		mag := math.Sqrt(re*re + im*im)
		if mag > maxMag {
			maxMag = mag
			maxBin = b
		}
	}

	if math.Abs(float64(maxBin-expectedBin)) > 1 {
		t.Errorf("expected peak near bin %d (440Hz), got peak at bin %d", expectedBin, maxBin)
	}
}

// TestExtractConstellationMap validates spectral peak extraction from multi-frequency harmonic audio.
func TestExtractConstellationMap(t *testing.T) {
	sampleRate := 16000
	numSamples := sampleRate * 3 // 3 seconds
	samples := make([]float32, numSamples)

	// Synthesize multi-frequency harmonic chord with raw int16 amplitude scale (20000)
	for i := 0; i < numSamples; i++ {
		tSec := float64(i) / float64(sampleRate)
		samples[i] = float32(
			(0.5*math.Sin(2*math.Pi*440*tSec) +
				0.3*math.Sin(2*math.Pi*880*tSec) +
				0.2*math.Sin(2*math.Pi*1760*tSec)) * 20000.0,
		)
	}

	cmap, err := ExtractConstellationMap(samples, sampleRate)
	if err != nil {
		t.Fatalf("ExtractConstellationMap failed: %v", err)
	}

	t.Logf("Extracted %d total peaks from harmonic chord", len(cmap.Peaks))
	for _, p := range cmap.Peaks {
		t.Logf("  band=%d fft=%d mag=%d bin=%d freq=%.1fHz", p.Band, p.FFTNumber, p.Magnitude, p.CorrectedBin, p.FrequencyHz)
	}

	if cmap.SampleRate != 16000 {
		t.Errorf("expected sample rate 16000, got %d", cmap.SampleRate)
	}

	if len(cmap.Peaks) == 0 {
		t.Fatalf("expected extracted peaks for harmonic chord, got 0")
	}

	// Verify peaks fall in proper Shazam bands
	bandsFound := make(map[int]bool)
	for _, p := range cmap.Peaks {
		bandsFound[p.Band] = true
	}

	if !bandsFound[Band250_520] || !bandsFound[Band520_1450] || !bandsFound[Band1450_3500] {
		t.Errorf("expected peaks in bands 0, 1, 2, got bands: %v", bandsFound)
	}
}

// TestSigXBinaryEncodeDecodeRoundtrip proves exact roundtrip encoding and decoding of Shazam SigX binary format.
func TestSigXBinaryEncodeDecodeRoundtrip(t *testing.T) {
	original := &DecodedSignature{
		SampleRateHz:  16000,
		NumberSamples: 16000 * 3,
		Bands: map[int][]FrequencyPeak{
			Band250_520: {
				{FFTNumber: 10, Magnitude: 8000, CorrectedBin: 3600, SampleRateHz: 16000},
				{FFTNumber: 25, Magnitude: 8500, CorrectedBin: 3750, SampleRateHz: 16000},
				{FFTNumber: 300, Magnitude: 9000, CorrectedBin: 3800, SampleRateHz: 16000}, // delta > 255
			},
			Band520_1450: {
				{FFTNumber: 15, Magnitude: 7500, CorrectedBin: 7200, SampleRateHz: 16000},
				{FFTNumber: 40, Magnitude: 7800, CorrectedBin: 7300, SampleRateHz: 16000},
			},
		},
	}

	bin, err := original.EncodeToBinary()
	if err != nil {
		t.Fatalf("EncodeToBinary failed: %v", err)
	}

	if len(bin) < 56 {
		t.Fatalf("encoded binary too small: %d bytes", len(bin))
	}

	decoded, err := DecodeFromBinary(bin)
	if err != nil {
		t.Fatalf("DecodeFromBinary failed: %v", err)
	}

	if decoded.SampleRateHz != original.SampleRateHz {
		t.Errorf("expected sample rate %d, got %d", original.SampleRateHz, decoded.SampleRateHz)
	}

	if decoded.NumberSamples != original.NumberSamples {
		t.Errorf("expected %d samples, got %d", original.NumberSamples, decoded.NumberSamples)
	}

	for band, expectedPeaks := range original.Bands {
		actualPeaks := decoded.Bands[band]
		if len(actualPeaks) != len(expectedPeaks) {
			t.Fatalf("band %d: expected %d peaks, got %d", band, len(expectedPeaks), len(actualPeaks))
		}
		for i := range expectedPeaks {
			if actualPeaks[i].FFTNumber != expectedPeaks[i].FFTNumber {
				t.Errorf("band %d peak %d: expected FFTNumber %d, got %d", band, i, expectedPeaks[i].FFTNumber, actualPeaks[i].FFTNumber)
			}
			if actualPeaks[i].Magnitude != expectedPeaks[i].Magnitude {
				t.Errorf("band %d peak %d: expected Magnitude %d, got %d", band, i, expectedPeaks[i].Magnitude, actualPeaks[i].Magnitude)
			}
			if actualPeaks[i].CorrectedBin != expectedPeaks[i].CorrectedBin {
				t.Errorf("band %d peak %d: expected CorrectedBin %d, got %d", band, i, expectedPeaks[i].CorrectedBin, actualPeaks[i].CorrectedBin)
			}
		}
	}
}

// TestEncodeConstellationToSignature verifies that a ConstellationMap creates a valid SigX URI.
func TestEncodeConstellationToSignature(t *testing.T) {
	cmap := &ConstellationMap{
		SampleRate: 16000,
		DurationMs: 3000,
		Peaks: []FrequencyPeak{
			{FFTNumber: 5, Magnitude: 8200, CorrectedBin: 3500, Band: Band250_520},
			{FFTNumber: 12, Magnitude: 8400, CorrectedBin: 7000, Band: Band520_1450},
			{FFTNumber: 20, Magnitude: 8100, CorrectedBin: 15000, Band: Band1450_3500},
		},
	}

	payload, err := EncodeConstellationToSignature(cmap)
	if err != nil {
		t.Fatalf("EncodeConstellationToSignature failed: %v", err)
	}

	if !strings.HasPrefix(payload.Base64URI, DataURIPrefix) {
		t.Errorf("expected URI to start with '%s', got: %s", DataURIPrefix, payload.Base64URI)
	}

	if payload.LandmarkCount != 3 {
		t.Errorf("expected 3 landmarks, got %d", payload.LandmarkCount)
	}
}

// TestOfflineMatchFallback validates local vault lookup fallback.
func TestOfflineMatchFallback(t *testing.T) {
	ctx := context.Background()
	res, err := MatchOffline(ctx, nil, "non_existent_file")
	if err != nil {
		t.Fatalf("MatchOffline failed: %v", err)
	}
	if res.Matched {
		t.Errorf("expected Matched=false for nil repo")
	}
}

// TestRecognizeGloriaSnippet verifies end-to-end recognition of real audio snippet from Gloria.ogg.
func TestRecognizeGloriaSnippet(t *testing.T) {
	pcmPath := `C:\Users\Tida\.gemini\antigravity-ide\brain\035cf7b8-1267-4f84-99d8-833ff9ca488f\scratch\gloria_snippet.pcm`
	pcmBytes, err := os.ReadFile(pcmPath)
	if err != nil {
		t.Skipf("Skipping gloria snippet test: %v", err)
	}

	numSamples := len(pcmBytes) / 2
	samples := make([]float32, numSamples)
	for i := 0; i < numSamples; i++ {
		raw := int16(binary.LittleEndian.Uint16(pcmBytes[i*2 : i*2+2]))
		samples[i] = float32(raw)
	}

	cmap, err := ExtractConstellationMap(samples, 16000)
	if err != nil {
		t.Fatalf("ExtractConstellationMap failed: %v", err)
	}

	t.Logf("Go extracted %d peaks from Gloria snippet", len(cmap.Peaks))
	sig, err := EncodeConstellationToSignature(cmap)
	if err != nil {
		t.Fatalf("EncodeConstellationToSignature failed: %v", err)
	}

	t.Logf("Go signature URI (len=%d): %s...", len(sig.Base64URI), sig.Base64URI[:50])

	client := NewClient()
	res, err := client.RecognizeSignature(context.Background(), sig)
	if err != nil {
		t.Fatalf("RecognizeSignature failed: %v", err)
	}

	t.Logf("Recognition result: matched=%v, title=%s, artist=%s", res.Matched, res.Title, res.Artist)
	if !res.Matched {
		t.Errorf("expected match for Gloria snippet, got matched=false")
	}
}

