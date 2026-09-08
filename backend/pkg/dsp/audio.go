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
	OriginalRMSDBFS  float64 `json:"original_rms_dbfs"`
	MeasuredLUFS     float64 `json:"measured_lufs"`
	TargetLUFS       float64 `json:"target_lufs"`
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

// KWeightingFilter implements the 2-stage biquad filtering defined in ITU-R BS.1770-4.
// Stage 1: High-shelf filter modeling the acoustic head effect (+4 dB boost above ~1.5 kHz).
// Stage 2: High-pass RLB weighting filter (-3 dB cutoff at ~38 Hz).
type KWeightingFilter struct {
	s1_x1, s1_x2, s1_y1, s1_y2 float64
	s2_x1, s2_x2, s2_y1, s2_y2 float64
}

// Standard BS.1770 filter coefficients for 48kHz audio.
const (
	// Stage 1 High-shelf
	kStage1_b0 = 1.53512485958697
	kStage1_b1 = -2.69169618940638
	kStage1_b2 = 1.19839281085285
	kStage1_a1 = -1.69065929318241
	kStage1_a2 = 0.73248077421585

	// Stage 2 High-pass (RLB)
	kStage2_b0 = 1.0
	kStage2_b1 = -2.0
	kStage2_b2 = 1.0
	kStage2_a1 = -1.99004745483398
	kStage2_a2 = 0.99007225035621
)

func (f *KWeightingFilter) ProcessSample(x float64) float64 {
	// Stage 1: High-shelf filter
	y1 := kStage1_b0*x + kStage1_b1*f.s1_x1 + kStage1_b2*f.s1_x2 - kStage1_a1*f.s1_y1 - kStage1_a2*f.s1_y2
	f.s1_x2 = f.s1_x1
	f.s1_x1 = x
	f.s1_y2 = f.s1_y1
	f.s1_y1 = y1

	// Stage 2: High-pass (RLB) filter
	y2 := kStage2_b0*y1 + kStage2_b1*f.s2_x1 + kStage2_b2*f.s2_x2 - kStage2_a1*f.s2_y1 - kStage2_a2*f.s2_y2
	f.s2_x2 = f.s2_x1
	f.s2_x1 = y1
	f.s2_y2 = f.s2_y1
	f.s2_y1 = y2

	return y2
}

// CalculateReplayGain computes loudness adjustment to match standard target (-14 LUFS / -18 LUFS)
// using ITU-R BS.1770-4 K-weighting pre-filtering.
func CalculateReplayGain(samples []float32, targetLUFS float64) NormalizationResult {
	if len(samples) == 0 {
		return NormalizationResult{TargetLUFS: targetLUFS, RecommendedScale: 1.0}
	}
	if targetLUFS == 0 {
		targetLUFS = -14.0 // Standard streaming target (Spotify / YouTube Music)
	}

	var sumRawSquares float64
	var sumKSquares float64
	var peak float64

	filter := &KWeightingFilter{}

	for _, s := range samples {
		val := float64(s)
		absVal := math.Abs(val)
		if absVal > peak {
			peak = absVal
		}
		sumRawSquares += val * val

		kSample := filter.ProcessSample(val)
		sumKSquares += kSample * kSample
	}

	n := float64(len(samples))
	rms := math.Sqrt(sumRawSquares / n)
	rmsDBFS := -100.0
	if rms > 0.00001 {
		rmsDBFS = 20.0 * math.Log10(rms)
	}

	// ITU-R BS.1770-4 LUFS formula: -0.691 + 10 * log10(mean_square_k)
	meanKSquare := sumKSquares / n
	measuredLUFS := -100.0
	if meanKSquare > 0.0000000001 {
		measuredLUFS = -0.691 + 10.0*math.Log10(meanKSquare)
	} else if rmsDBFS > -100 {
		measuredLUFS = rmsDBFS
	}

	gainDB := targetLUFS - measuredLUFS

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
		MeasuredLUFS:     math.Round(measuredLUFS*10) / 10,
		TargetLUFS:       targetLUFS,
		GainAdjustmentDB: math.Round(gainDB*10) / 10,
		RecommendedScale: math.Round(scale*1000) / 1000,
		PreventClipping:  preventClipping,
	}
}

// CalculatePCMLoudness computes EBU R128 loudness and gain adjustment directly from 16-bit signed PCM samples.
func CalculatePCMLoudness(samples []int16, targetLUFS float64) NormalizationResult {
	if len(samples) == 0 {
		return NormalizationResult{TargetLUFS: targetLUFS, RecommendedScale: 1.0}
	}
	floatSamples := make([]float32, len(samples))
	for i, s := range samples {
		floatSamples[i] = float32(s) / 32768.0
	}
	return CalculateReplayGain(floatSamples, targetLUFS)
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
