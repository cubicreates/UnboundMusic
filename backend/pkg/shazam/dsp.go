/*
 * Package: shazam
 * File: dsp.go
 * Purpose: Native Go audio DSP pipeline implementing Shazam's reverse-engineered FFT, peak spreading, and spectral recognition.
 * Subsystem: Shazam Audio Recognition
 * Concurrency: Thread-safe signal processing with zero shared mutable state between requests.
 */

package shazam

import (
	"fmt"
	"math"
	"math/cmplx"
)

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

// Precomputed 2048-point Hanning window without zero endpoints: np.hanning(2050)[1:-1]
var hanningMatrix2048 [2048]float64

func init() {
	for i := 0; i < 2048; i++ {
		// w[i] = 0.5 * (1.0 - cos(2*pi*(i+1) / 2049))
		hanningMatrix2048[i] = 0.5 * (1.0 - math.Cos(2.0*math.Pi*float64(i+1)/2049.0))
	}
}

// SignatureGenerator encapsulates the streaming acoustic fingerprinting pipeline.
type SignatureGenerator struct {
	SampleRate     int
	RingSamples    [2048]float64
	RingSamplePos  int
	RingNumWritten int

	FFTOutputs    [256][1025]float64
	FFTPos        int
	FFTNumWritten int

	SpreadOutputs    [256][1025]float64
	SpreadPos        int
	SpreadNumWritten int

	Threshold float64
	Peaks     []FrequencyPeak
}

// NewSignatureGenerator creates a new generator instance.
func NewSignatureGenerator(sampleRate int, threshold float64) *SignatureGenerator {
	if sampleRate <= 0 {
		sampleRate = 16000
	}
	if threshold <= 0 {
		threshold = 1.0 / 64.0 // Standard Shazam threshold
	}
	return &SignatureGenerator{
		SampleRate: sampleRate,
		Threshold:  threshold,
		Peaks:      make([]FrequencyPeak, 0, 128),
	}
}

// FeedSamples processes audio samples in 128-sample hops.
func (sg *SignatureGenerator) FeedSamples(samples []float32) {
	for i := 0; i < len(samples); i += 128 {
		end := i + 128
		if end > len(samples) {
			break
		}
		sg.processBatch128(samples[i:end])
	}
}

// processBatch128 handles a single 128-sample batch.
func (sg *SignatureGenerator) processBatch128(batch []float32) {
	// 1. Insert 128 samples into circular ring buffer
	for j := 0; j < 128; j++ {
		sg.RingSamples[sg.RingSamplePos] = float64(batch[j])
		sg.RingSamplePos = (sg.RingSamplePos + 1) % 2048
		sg.RingNumWritten++
	}

	// 2. Extract ordered 2048-sample window (oldest to newest)
	var windowed [2048]complex128
	idx := sg.RingSamplePos
	for k := 0; k < 2048; k++ {
		val := sg.RingSamples[idx] * hanningMatrix2048[k]
		windowed[k] = complex(val, 0)
		idx = (idx + 1) % 2048
	}

	// 3. Compute 2048-point radix-2 FFT
	fftComplex := computeRadix2FFT2048(windowed)

	// 4. Calculate power spectrum for 1025 positive frequency bins
	// fft_results = (real^2 + imag^2) / (1 << 17)
	// fft_results = max(fft_results, 1e-10)
	var power [1025]float64
	for b := 0; b < 1025; b++ {
		re := real(fftComplex[b])
		im := imag(fftComplex[b])
		p := (re*re + im*im) / 131072.0
		if p < 1e-10 {
			p = 1e-10
		}
		power[b] = p
	}

	// 5. Store into FFTOutputs ring buffer
	sg.FFTOutputs[sg.FFTPos] = power
	sg.FFTPos = (sg.FFTPos + 1) % 256
	sg.FFTNumWritten++

	// 6. Peak spreading across frequency and time
	sg.doPeakSpreading()

	// 7. Peak recognition at offset -46
	if sg.SpreadNumWritten >= 46 {
		sg.doPeakRecognition()
	}
}

// doPeakSpreading performs frequency spreading and time-domain smoothing.
func (sg *SignatureGenerator) doPeakSpreading() {
	lastFFTPos := (sg.FFTPos - 1 + 256) % 256
	originLastFFT := sg.FFTOutputs[lastFFTPos]

	var spreadLastFFT [1025]float64
	copy(spreadLastFFT[:], originLastFFT[:])

	// Frequency-domain spreading (across 3 consecutive bins)
	for pos := 0; pos < 1023; pos++ {
		m := spreadLastFFT[pos]
		if spreadLastFFT[pos+1] > m {
			m = spreadLastFFT[pos+1]
		}
		if spreadLastFFT[pos+2] > m {
			m = spreadLastFFT[pos+2]
		}
		spreadLastFFT[pos] = m
	}

	// Time-domain spreading across former FFT frames at offsets [-1, -3, -6]
	for pos := 0; pos < 1025; pos++ {
		maxVal := spreadLastFFT[pos]

		for _, formerOffset := range []int{-1, -3, -6} {
			formerIdx := (sg.SpreadPos + formerOffset + 256) % 256
			if sg.SpreadOutputs[formerIdx][pos] > maxVal {
				maxVal = sg.SpreadOutputs[formerIdx][pos]
			}
			sg.SpreadOutputs[formerIdx][pos] = maxVal
		}
	}

	// Save spread output locally in ring buffer
	sg.SpreadOutputs[sg.SpreadPos] = spreadLastFFT
	sg.SpreadPos = (sg.SpreadPos + 1) % 256
	sg.SpreadNumWritten++
}

// Neighbor evaluation offsets
var freqOffsets = []int{-10, -7, -4, -3, 1, 2, 5, 8}
var timeOffsets = []int{-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249}

// doPeakRecognition checks for local time-frequency energy maxima and applies parabolic interpolation.
func (sg *SignatureGenerator) doPeakRecognition() {
	pos46 := (sg.FFTPos - 46 + 256) % 256
	fftMinus46 := sg.FFTOutputs[pos46]

	pos49 := (sg.SpreadPos - 49 + 256) % 256
	fftMinus49 := sg.SpreadOutputs[pos49]

	for bin := 10; bin <= 1014; bin++ {
		val := fftMinus46[bin]

		// 1. Threshold and predecessor check
		if val < sg.Threshold || val < fftMinus49[bin-1] {
			continue
		}

		// 2. Frequency-domain local maximum in fft_minus_49
		maxNeighbor := 0.0
		for _, o := range freqOffsets {
			nb := bin + o
			if nb >= 0 && nb < 1025 {
				if fftMinus49[nb] > maxNeighbor {
					maxNeighbor = fftMinus49[nb]
				}
			}
		}

		if val <= maxNeighbor {
			continue
		}

		// 3. Time-domain local maximum across adjacent frames
		maxOther := maxNeighbor
		for _, o := range timeOffsets {
			fIdx := (sg.SpreadPos + o) % 256
			if fIdx < 0 {
				fIdx += 256
			}
			nv := sg.SpreadOutputs[fIdx][bin-1]
			if nv > maxOther {
				maxOther = nv
			}
		}

		if val <= maxOther {
			continue
		}

		// 4. Parabolic sub-bin interpolation
		fftNumber := sg.SpreadNumWritten - 46

		vCur := math.Max(1.0/64.0, val)
		vPrev := math.Max(1.0/64.0, fftMinus46[bin-1])
		vNext := math.Max(1.0/64.0, fftMinus46[bin+1])

		peakMag := math.Log(vCur)*1477.3 + 6144.0
		peakMagBefore := math.Log(vPrev)*1477.3 + 6144.0
		peakMagAfter := math.Log(vNext)*1477.3 + 6144.0

		peakVar1 := peakMag*2.0 - peakMagBefore - peakMagAfter
		peakVar2 := 0.0
		if peakVar1 > 0 {
			peakVar2 = (peakMagAfter - peakMagBefore) * 32.0 / peakVar1
		}

		correctedBin := float64(bin)*64.0 + peakVar2
		freqHz := correctedBin * (float64(sg.SampleRate) / 2.0 / 1024.0 / 64.0)

		// 5. Band determination
		var band int
		if freqHz > 250 && freqHz < 520 {
			band = Band250_520
		} else if freqHz >= 520 && freqHz < 1450 {
			band = Band520_1450
		} else if freqHz >= 1450 && freqHz < 3500 {
			band = Band1450_3500
		} else if freqHz >= 3500 && freqHz <= 5500 {
			band = Band3500_5500
		} else {
			continue
		}

		timeMs := int64((fftNumber * 128 * 1000) / sg.SampleRate)

		sg.Peaks = append(sg.Peaks, FrequencyPeak{
			FFTNumber:    fftNumber,
			Magnitude:    int(peakMag),
			CorrectedBin: int(correctedBin),
			SampleRateHz: sg.SampleRate,
			FrequencyHz:  freqHz,
			TimeMs:       timeMs,
			Band:         band,
		})
	}
}

// ExtractConstellationMap runs the native Shazam engine over input PCM samples.
func ExtractConstellationMap(samples []float32, sampleRate int) (*ConstellationMap, error) {
	if len(samples) < 1024 {
		return nil, fmt.Errorf("sample buffer too short for spectral analysis (minimum 1024 samples required)")
	}
	if sampleRate <= 0 {
		sampleRate = 16000
	}

	// Auto-scale normalized float samples in [-1.0, 1.0] to full 16-bit PCM integer range [-32768, 32767]
	maxVal := float32(0)
	for _, s := range samples {
		abs := s
		if abs < 0 {
			abs = -abs
		}
		if abs > maxVal {
			maxVal = abs
		}
	}
	if maxVal <= 1.0 && maxVal > 0 {
		scaled := make([]float32, len(samples))
		for i, s := range samples {
			scaled[i] = s * 32767.0
		}
		samples = scaled
		maxVal *= 32767.0
	}

	// AGC (Automatic Gain Control) for ambient microphone recordings:
	// If audio is quiet (peak < 16,000), boost toward Shazam's optimal operating level (~20,000).
	// Because Shazam's sub-bin frequency calculation is mathematically invariant to scalar gain,
	// this lifts all 4 frequency bands cleanly above the 1/64 threshold.
	if maxVal > 0 && maxVal < 16000 {
		gain := float32(20000.0) / maxVal
		if gain > 25.0 {
			gain = 25.0 // cap gain at ~28dB to avoid lifting white noise
		}
		scaled := make([]float32, len(samples))
		for i, s := range samples {
			scaled[i] = s * gain
		}
		samples = scaled
	}

	durationMs := int64((float64(len(samples)) / float64(sampleRate)) * 1000)

	// Primary pass: standard Shazam threshold (1/64)
	gen := NewSignatureGenerator(sampleRate, 1.0/64.0)
	gen.FeedSamples(samples)

	// Fallback pass: if quiet room audio produced very few peaks (< 5), re-run with 1/256 threshold
	if len(gen.Peaks) < 5 {
		genFallback := NewSignatureGenerator(sampleRate, 1.0/256.0)
		genFallback.FeedSamples(samples)
		if len(genFallback.Peaks) > len(gen.Peaks) {
			gen = genFallback
		}
	}

	return &ConstellationMap{
		SampleRate: sampleRate,
		DurationMs: durationMs,
		Peaks:      gen.Peaks,
	}, nil
}

// computeRadix2FFT2048 calculates in-place Cooley-Tukey radix-2 FFT for N=2048.
func computeRadix2FFT2048(x [2048]complex128) [2048]complex128 {
	const n = 2048

	// Bit reversal permutation
	var a [n]complex128
	for i := 0; i < n; i++ {
		// Reverse 11 bits (2^11 = 2048)
		rev := 0
		temp := i
		for b := 0; b < 11; b++ {
			rev = (rev << 1) | (temp & 1)
			temp >>= 1
		}
		a[rev] = x[i]
	}

	// Cooley-Tukey butterflies
	for s := 1; s <= 11; s++ {
		m := 1 << s
		m2 := m >> 1
		wM := cmplx.Rect(1.0, -2.0*math.Pi/float64(m))

		for k := 0; k < n; k += m {
			w := complex(1.0, 0.0)
			for j := 0; j < m2; j++ {
				t := w * a[k+j+m2]
				u := a[k+j]
				a[k+j] = u + t
				a[k+j+m2] = u - t
				w *= wM
			}
		}
	}

	return a
}
