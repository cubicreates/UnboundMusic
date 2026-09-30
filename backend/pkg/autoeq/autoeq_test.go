/*
 * Package: autoeq
 * File: autoeq_test.go
 * Purpose: Unit tests for headphone calibration curve lookups, 10-band equalization,
 *          logarithmic curve interpolation, and target frequency resampling.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package autoeq

import (
	"math"
	"testing"
)

// TestAutoEqSearchAndPreset validates headphone search and 10-band gain extraction.
func TestAutoEqSearchAndPreset(t *testing.T) {
	engine := NewEngine()

	// Test Search
	results := engine.SearchHeadphones("Sony")
	if len(results) == 0 {
		t.Fatalf("expected to find Sony headphones in database")
	}

	foundXM5 := false
	for _, m := range results {
		if m.ID == "sony_wh1000xm5" {
			foundXM5 = true
			break
		}
	}
	if !foundXM5 {
		t.Errorf("expected WH-1000XM5 in search results")
	}

	// Test Preset Retrieval
	preset, err := engine.GetEQPreset("sony_wh1000xm5")
	if err != nil {
		t.Fatalf("GetEQPreset failed: %v", err)
	}

	if len(preset.Bands) != 10 {
		t.Errorf("expected 10 EQ bands, got %d", len(preset.Bands))
	}

	if preset.PreampGainDB >= 0 {
		t.Errorf("expected negative preamp gain to prevent digital clipping, got %f", preset.PreampGainDB)
	}

	// Test Nonexistent Preset
	_, errNotFound := engine.GetEQPreset("nonexistent_model_xyz")
	if errNotFound == nil {
		t.Errorf("expected error for non-existent model ID")
	}
}

// TestInterpolateGain validates continuous logarithmic frequency response interpolation.
func TestInterpolateGain(t *testing.T) {
	engine := NewEngine()
	preset, err := engine.GetEQPreset("sony_wh1000xm5")
	if err != nil {
		t.Fatalf("failed to retrieve WH-1000XM5 preset: %v", err)
	}

	// Exact match at 1000 Hz
	gain1000 := preset.InterpolateGain(1000.0)
	if math.Abs(gain1000-1.8) > 0.001 {
		t.Errorf("expected 1.8 dB at 1000Hz, got %f", gain1000)
	}

	// Low-frequency clamping (< 31 Hz)
	gain10 := preset.InterpolateGain(10.0)
	if math.Abs(gain10-(-1.2)) > 0.001 {
		t.Errorf("expected clamp to 31Hz gain (-1.2 dB) at 10Hz, got %f", gain10)
	}

	// High-frequency clamping (> 16000 Hz)
	gain20k := preset.InterpolateGain(20000.0)
	if math.Abs(gain20k-(-0.5)) > 0.001 {
		t.Errorf("expected clamp to 16kHz gain (-0.5 dB) at 20kHz, got %f", gain20k)
	}

	// Intermediate frequency between 1000 Hz (1.8 dB) and 2000 Hz (3.2 dB)
	// At geometric mean sqrt(1000*2000) ~ 1414.2 Hz, interpolated gain should be midway: (1.8 + 3.2)/2 = 2.5 dB
	geomMean := math.Sqrt(1000.0 * 2000.0)
	gainMid := preset.InterpolateGain(geomMean)
	if math.Abs(gainMid-2.5) > 0.05 {
		t.Errorf("expected ~2.5 dB at geometric mean %f Hz, got %f", geomMean, gainMid)
	}
}

// TestResampleCurve validates resampling preset curves to arbitrary hardware band configurations.
func TestResampleCurve(t *testing.T) {
	engine := NewEngine()
	preset, err := engine.GetEQPreset("sennheiser_hd650")
	if err != nil {
		t.Fatalf("failed to retrieve HD 650 preset: %v", err)
	}

	// Resample to 5-band consumer EQ (60Hz, 250Hz, 1kHz, 4kHz, 14kHz)
	targetFreqs := []int{60, 250, 1000, 4000, 14000}
	resampled := preset.ResampleCurve(targetFreqs)

	if len(resampled) != len(targetFreqs) {
		t.Fatalf("expected %d bands, got %d", len(targetFreqs), len(resampled))
	}

	for i, band := range resampled {
		if band.FrequencyHz != targetFreqs[i] {
			t.Errorf("band %d: expected frequency %d, got %d", i, targetFreqs[i], band.FrequencyHz)
		}
		if band.QFactor <= 0 {
			t.Errorf("band %d: expected positive Q factor, got %f", i, band.QFactor)
		}
	}
}

// TestExpandedHeadphoneProfiles verifies all added reference models exist.
func TestExpandedHeadphoneProfiles(t *testing.T) {
	engine := NewEngine()
	expectedIDs := []string{
		"sony_wh1000xm5",
		"apple_airpods_pro_2",
		"sennheiser_hd650",
		"audio_technica_ath_m50x",
		"moondrop_blessing2",
		"beyerdynamic_dt770pro_80",
	}

	for _, id := range expectedIDs {
		preset, err := engine.GetEQPreset(id)
		if err != nil {
			t.Errorf("missing expected headphone preset: %s", id)
			continue
		}
		if len(preset.Bands) != 10 {
			t.Errorf("headphone %s: expected 10 bands, got %d", id, len(preset.Bands))
		}
	}
}
