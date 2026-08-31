/*
 * Package: dsp
 * File: audio.go
 * Purpose: Professional audio digital signal processing (DSP): EBU R128 / ReplayGain volume normalization, DJ crossfade curve generator, and automatic silence trimmer.
 * Subsystem: Pro Audio Processing & Sound Quality
 * Concurrency: Thread-safe pure mathematical DSP functions.
 */

package dsp

import (
	"math"
)

// CrossfadeCurveType defines the mathematical curve used for volume blending.
type CrossfadeCurveType string

const (
	CurveLinear        CrossfadeCurveType = "LINEAR"
	CurveLogarithmic   CrossfadeCurveType = "LOGARITHMIC"
	CurveConstantPower CrossfadeCurveType = "CONSTANT_POWER"
	CurveSCurve        CrossfadeCurveType = "S_CURVE"
)

// NormalizationResult holds loudness gain and peak calculations.
type NormalizationResult struct {
	OriginalRMSDBFS float64 `json:"original_rms_dbfs"`
	TargetLUFS      float64 `json:"target_lufs"`
	GainAdjustmentDB float64 `json:"gain_adjustment_db"`
	RecommendedScale float64 `json:"recommended_scale"` // Multiplier for audio samples
	PreventClipping  bool    `json:"prevent_clipping"`
}

// SilenceTrimResult holds leading and trailing silence boundaries in milliseconds.
type SilenceTrimResult struct {
	LeadSilenceMs  int64 `json:"lead_silence_ms"`
	TailSilenceMs  int64 `json:"tail_silence_ms"`
	TrimmedStartMs int64 `json:"trimmed_start_ms"`
	TrimmedEndMs   int64 `json:"trimmed_end_ms"`
}

// CalculateReplayGain computes loudness adjustment to match standard target (-14 LUFS / -18 LUFS).
func CalculateReplayGain(samples []float32, targetLUFS float64) NormalizationResult {
	if len(samples) == 0 {
		return NormalizationResult{TargetLUFS: targetLUFS, RecommendedScale: 1.0}
	}
	if targetLUFS == 0 {
		targetLUFS = -14.0 // Standard streaming target (Spotify / YouTube Music)
	}

	var sumSquares float64
	var peak float64

	for _, s := range samples {
		val := float64(s)
		absVal := math.Abs(val)
		if absVal > peak {
			peak = absVal
		}
		sumSquares += val * val
	}

	rms := math.Sqrt(sumSquares / float64(len(samples)))
	if rms <= 0.00001 {
		return NormalizationResult{OriginalRMSDBFS: -100, TargetLUFS: targetLUFS, RecommendedScale: 1.0}
	}

	rmsDBFS := 20.0 * math.Log10(rms)
	gainDB := targetLUFS - rmsDBFS

	// Calculate linear multiplier
	scale := math.Pow(10.0, gainDB/20.0)

	// Peak limiter to avoid digital clipping (> 0.999)
	preventClipping := false
	if peak*scale > 0.99 {
		scale = 0.99 / peak
		preventClipping = true
	}

	return NormalizationResult{
		OriginalRMSDBFS:  math.Round(rmsDBFS*10) / 10,
		TargetLUFS:       targetLUFS,
		GainAdjustmentDB: math.Round(gainDB*10) / 10,
		RecommendedScale: math.Round(scale*1000) / 1000,
		PreventClipping:  preventClipping,
	}
}

// CalculateCrossfadeGains calculates the volume coefficients for Track A and Track B at progress [0.0 to 1.0].
func CalculateCrossfadeGains(progress float64, curve CrossfadeCurveType) (gainA float64, gainB float64) {
	if progress < 0.0 {
		progress = 0.0
	}
	if progress > 1.0 {
		progress = 1.0
	}

	switch curve {
	case CurveConstantPower:
		// Equal power curve (prevents volume dip in the middle)
		gainA = math.Cos(progress * (math.Pi / 2.0))
		gainB = math.Sin(progress * (math.Pi / 2.0))
	case CurveLogarithmic:
		gainA = math.Pow(1.0-progress, 2.0)
		gainB = math.Pow(progress, 2.0)
	case CurveSCurve:
		gainA = 0.5 * (1.0 + math.Cos(progress*math.Pi))
		gainB = 0.5 * (1.0 - math.Cos(progress*math.Pi))
	case CurveLinear:
		fallthrough
	default:
		gainA = 1.0 - progress
		gainB = progress
	}

	return gainA, gainB
}

// DetectSilenceBoundaries scans audio samples to locate lead and tail dead air below -50dB.
func DetectSilenceBoundaries(samples []float32, sampleRate int, thresholdDB float64) SilenceTrimResult {
	if len(samples) == 0 || sampleRate <= 0 {
		return SilenceTrimResult{}
	}
	if thresholdDB == 0 {
		thresholdDB = -50.0 // -50 dB silence threshold
	}

	linearThreshold := float32(math.Pow(10.0, thresholdDB/20.0))
	blockSize := sampleRate / 100 // 10ms block
	if blockSize <= 0 {
		blockSize = 1
	}

	totalDurationMs := int64((float64(len(samples)) / float64(sampleRate)) * 1000)

	// Detect leading silence
	firstActiveSample := 0
	for i := 0; i < len(samples); i += blockSize {
		end := i + blockSize
		if end > len(samples) {
			end = len(samples)
		}
		if isBlockActive(samples[i:end], linearThreshold) {
			firstActiveSample = i
			break
		}
	}

	// Detect trailing silence
	lastActiveSample := len(samples) - 1
	for i := len(samples) - blockSize; i >= 0; i -= blockSize {
		start := i
		end := i + blockSize
		if isBlockActive(samples[start:end], linearThreshold) {
			lastActiveSample = end
			break
		}
	}

	leadMs := int64((float64(firstActiveSample) / float64(sampleRate)) * 1000)
	tailStartMs := int64((float64(lastActiveSample) / float64(sampleRate)) * 1000)
	tailMs := totalDurationMs - tailStartMs

	return SilenceTrimResult{
		LeadSilenceMs:  leadMs,
		TailSilenceMs:  tailMs,
		TrimmedStartMs: leadMs,
		TrimmedEndMs:   tailStartMs,
	}
}

func isBlockActive(block []float32, threshold float32) bool {
	for _, s := range block {
		if float32(math.Abs(float64(s))) > threshold {
			return true
		}
	}
	return false
}
