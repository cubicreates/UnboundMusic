/*
 * Package: shazam
 * File: dsp.go
 * Purpose: Audio digital signal processing (DSP), Hann windowing, and 2D spectrogram peak constellation extraction for 16kHz mono audio.
 * Subsystem: Shazam Audio Recognition
 * Concurrency: Pure mathematical functions safe for concurrent execution across worker goroutines.
 */

package shazam

import (
	"fmt"
	"math"
	"math/cmplx"
)

// FrequencyPeak represents a prominent spectral energy peak in time-frequency space.
type FrequencyPeak struct {
	TimeMs      int64   `json:"time_ms"`
	FrequencyHz float64 `json:"frequency_hz"`
	Magnitude   float64 `json:"magnitude"`
	Band        int     `json:"band"`
}

// ConstellationMap holds all extracted spectral peaks across an audio sample.
type ConstellationMap struct {
	SampleRate int             `json:"sample_rate"`
	DurationMs int64           `json:"duration_ms"`
	Peaks      []FrequencyPeak `json:"peaks"`
}

// FrequencyBands defines the 4 landmark frequency evaluation intervals (Hz).
var FrequencyBands = [][2]float64{
	{250, 520},   // Band 0: Bass & Low-Mid fundamentals
	{520, 1450},  // Band 1: Vocal range & Mid harmonics
	{1450, 3500}, // Band 2: Upper-mid timbre
	{3500, 5500}, // Band 3: Treble & Cymbals
}

// ExtractConstellationMap computes FFT frames and picks local energy maxima.
func ExtractConstellationMap(samples []float32, sampleRate int) (*ConstellationMap, error) {
	if len(samples) < 1024 {
		return nil, fmt.Errorf("sample buffer too short for spectral analysis (minimum 1024 samples required)")
	}
	if sampleRate <= 0 {
		sampleRate = 16000
	}

	frameSize := 1024
	hopSize := 512
	numFrames := (len(samples) - frameSize) / hopSize
	if numFrames <= 0 {
		numFrames = 1
	}

	durationMs := int64((float64(len(samples)) / float64(sampleRate)) * 1000)
	var allPeaks []FrequencyPeak

	// Pre-compute Hann window
	window := make([]float64, frameSize)
	for i := 0; i < frameSize; i++ {
		window[i] = 0.5 * (1.0 - math.Cos(2.0*math.Pi*float64(i)/float64(frameSize-1)))
	}

	for frameIdx := 0; frameIdx < numFrames; frameIdx++ {
		start := frameIdx * hopSize
		if start+frameSize > len(samples) {
			break
		}

		timeMs := int64((float64(start) / float64(sampleRate)) * 1000)

		// Apply window
		frame := make([]complex128, frameSize)
		for i := 0; i < frameSize; i++ {
			frame[i] = complex(float64(samples[start+i])*window[i], 0)
		}

		// Compute FFT
		spectrum := computeFFT(frame)
		halfN := frameSize / 2

		// Find peak in each of the 4 frequency bands
		for bandIdx, band := range FrequencyBands {
			minBin := int(band[0] * float64(frameSize) / float64(sampleRate))
			maxBin := int(band[1] * float64(frameSize) / float64(sampleRate))
			if maxBin >= halfN {
				maxBin = halfN - 1
			}

			var maxMag float64
			var bestBin int

			for bin := minBin; bin <= maxBin; bin++ {
				mag := cmplx.Abs(spectrum[bin])
				if mag > maxMag {
					maxMag = mag
					bestBin = bin
				}
			}

			if maxMag > 0.05 {
				freqHz := float64(bestBin) * float64(sampleRate) / float64(frameSize)
				allPeaks = append(allPeaks, FrequencyPeak{
					TimeMs:      timeMs,
					FrequencyHz: freqHz,
					Magnitude:   maxMag,
					Band:        bandIdx,
				})
			}
		}
	}

	return &ConstellationMap{
		SampleRate: sampleRate,
		DurationMs: durationMs,
		Peaks:      allPeaks,
	}, nil
}

// computeFFT calculates radix-2 Cooley-Tukey FFT in pure Go.
func computeFFT(x []complex128) []complex128 {
	n := len(x)
	if n <= 1 {
		return x
	}

	// If not power of 2, fall back to DFT
	if n&(n-1) != 0 {
		return computeDFT(x)
	}

	even := make([]complex128, n/2)
	odd := make([]complex128, n/2)
	for i := 0; i < n/2; i++ {
		even[i] = x[2*i]
		odd[i] = x[2*i+1]
	}

	evenFFT := computeFFT(even)
	oddFFT := computeFFT(odd)

	res := make([]complex128, n)
	for k := 0; k < n/2; k++ {
		t := cmplx.Rect(1, -2*math.Pi*float64(k)/float64(n)) * oddFFT[k]
		res[k] = evenFFT[k] + t
		res[k+n/2] = evenFFT[k] - t
	}

	return res
}

// computeDFT computes basic Discrete Fourier Transform.
func computeDFT(x []complex128) []complex128 {
	n := len(x)
	res := make([]complex128, n)
	for k := 0; k < n; k++ {
		var sum complex128
		for t := 0; t < n; t++ {
			angle := -2 * math.Pi * float64(t*k) / float64(n)
			sum += x[t] * cmplx.Rect(1, angle)
		}
		res[k] = sum
	}
	return res
}
