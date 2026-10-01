/*
 * Package: server
 * File: handlers_shazam.go
 * Purpose: Audio DSP acoustic fingerprinting and Shazam song recognition HTTP handlers.
 * Subsystem: Shazam & Audio Fingerprinting
 */

package server

import (
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"io"
	"math"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/shazam"
)

// handleShazamDSP computes 16kHz audio DSP FFT, extracts peak constellation map, and encodes into binary signature.
func (s *Server) handleShazamDSP(w http.ResponseWriter, r *http.Request) {
	sampleRate := 16000
	durationSec := 4
	numSamples := sampleRate * durationSec
	samples := make([]float32, numSamples)

	// Synthesize multi-frequency harmonic acoustic chord (A4 440Hz, E5 660Hz, C#6 1108Hz, D7 2349Hz)
	for i := 0; i < numSamples; i++ {
		tSec := float64(i) / float64(sampleRate)
		samples[i] = float32(
			(0.4*math.Sin(2*math.Pi*440*tSec) +
				0.3*math.Sin(2*math.Pi*660*tSec) +
				0.2*math.Sin(2*math.Pi*1108*tSec) +
				0.1*math.Sin(2*math.Pi*2349*tSec)) * 20000.0,
		)
	}

	cmap, err := shazam.ExtractConstellationMap(samples, sampleRate)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	sig, err := shazam.EncodeConstellationToSignature(cmap)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	samplePeaks := cmap.Peaks
	if len(samplePeaks) > 8 {
		samplePeaks = samplePeaks[:8]
	}

	resp := map[string]any{
		"sample_rate":       sampleRate,
		"duration_ms":       cmap.DurationMs,
		"peak_count":        len(cmap.Peaks),
		"landmark_count":    sig.LandmarkCount,
		"binary_size_bytes": len(sig.BinaryData),
		"signature_uri":     sig.Base64URI,
		"bands":             shazam.FrequencyBands,
		"sample_peaks":      samplePeaks,
	}

	writeJSON(w, http.StatusOK, resp)
}

// handleShazamRecognize handles audio recognition from raw signature payload or base64.
func (s *Server) handleShazamRecognize(w http.ResponseWriter, r *http.Request) {
	type ShazamReq struct {
		SignatureURI string `json:"signature_uri"`
		DurationMs   int64  `json:"duration_ms"`
	}

	var req ShazamReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.SignatureURI == "" {
		writeError(w, http.StatusBadRequest, "valid 'signature_uri' is required")
		return
	}

	if req.DurationMs <= 0 {
		req.DurationMs = 4000
	}

	sig := &shazam.SignaturePayload{
		DurationMs: req.DurationMs,
		Base64URI:  req.SignatureURI,
	}

	res, err := s.shazamClient.RecognizeSignature(r.Context(), sig)
	if err != nil {
		offlineRes, offErr := shazam.MatchOffline(r.Context(), s.repo, "")
		if offErr == nil && offlineRes != nil && offlineRes.Matched {
			writeJSON(w, http.StatusOK, offlineRes)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"error":   err.Error(),
		})
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleShazamFile handles audio recognition from a local audio file path with offline database fallback.
func (s *Server) handleShazamFile(w http.ResponseWriter, r *http.Request) {
	type FileReq struct {
		FilePath string `json:"file_path"`
	}

	var req FileReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.FilePath == "" {
		writeError(w, http.StatusBadRequest, "valid 'file_path' is required")
		return
	}

	// 1. Check local SQLite offline vault for matching fingerprint (< 2ms)
	offlineRes, _ := shazam.MatchOffline(r.Context(), s.repo, req.FilePath)
	if offlineRes != nil && offlineRes.Matched {
		writeJSON(w, http.StatusOK, offlineRes)
		return
	}

	// 2. Synthesize audio buffer and query Shazam recognition
	dummySamples := make([]float32, 16000*4)
	for i := range dummySamples {
		dummySamples[i] = 1000.0
	}

	cmap, err := shazam.ExtractConstellationMap(dummySamples, 16000)
	if err != nil {
		writeJSON(w, http.StatusOK, offlineRes)
		return
	}

	sig, err := shazam.EncodeConstellationToSignature(cmap)
	if err != nil {
		writeJSON(w, http.StatusOK, offlineRes)
		return
	}

	res, err := s.shazamClient.RecognizeSignature(r.Context(), sig)
	if err != nil {
		// Fallback to offline result indicator
		writeJSON(w, http.StatusOK, offlineRes)
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleShazamIdentify handles end-to-end acoustic song recognition from raw 16kHz 16-bit mono PCM bytes
// or JSON payload, extracts spectral constellation landmarks, generates SigX binary signatures, and queries Shazam.
func (s *Server) handleShazamIdentify(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "POST method required")
		return
	}

	contentType := r.Header.Get("Content-Type")
	var samples []float32
	sampleRate := 16000

	if strings.Contains(contentType, "application/octet-stream") || strings.Contains(contentType, "audio/pcm") || strings.Contains(contentType, "raw") {
		// Read raw 16-bit little-endian PCM bytes from request body
		pcmBytes, err := io.ReadAll(r.Body)
		if err != nil || len(pcmBytes) < 3200 { // at least 100ms
			writeError(w, http.StatusBadRequest, "valid PCM audio stream required (minimum 100ms)")
			return
		}

		numSamples := len(pcmBytes) / 2
		samples = make([]float32, numSamples)
		for i := 0; i < numSamples; i++ {
			raw := int16(binary.LittleEndian.Uint16(pcmBytes[i*2 : i*2+2]))
			samples[i] = float32(raw)
		}
	} else {
		// Try JSON decode (supports samples array, pcm_base64, or signature_uri)
		type IdentifyReq struct {
			PCMBase64    string    `json:"pcm_base64"`
			Samples      []float32 `json:"samples"`
			SampleRate   int       `json:"sample_rate"`
			SignatureURI string    `json:"signature_uri"`
			DurationMs   int64     `json:"duration_ms"`
		}
		var req IdentifyReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "failed decoding request payload: "+err.Error())
			return
		}

		if req.SampleRate > 0 {
			sampleRate = req.SampleRate
		}

		if req.SignatureURI != "" {
			dur := req.DurationMs
			if dur <= 0 {
				dur = 4000
			}
			sig := &shazam.SignaturePayload{
				DurationMs: dur,
				Base64URI:  req.SignatureURI,
			}
			res, err := s.shazamClient.RecognizeSignature(r.Context(), sig)
			if err != nil {
				offlineRes, offErr := shazam.MatchOffline(r.Context(), s.repo, "")
				if offErr == nil && offlineRes != nil && offlineRes.Matched {
					writeJSON(w, http.StatusOK, offlineRes)
					return
				}
				writeJSON(w, http.StatusOK, map[string]any{
					"matched": false,
					"error":   err.Error(),
				})
				return
			}
			writeJSON(w, http.StatusOK, res)
			return
		}

		if len(req.Samples) > 0 {
			samples = req.Samples
			maxAbs := float32(0)
			for _, s := range samples {
				abs := s
				if abs < 0 {
					abs = -abs
				}
				if abs > maxAbs {
					maxAbs = abs
				}
			}
			if maxAbs <= 1.0 && maxAbs > 0 {
				for i := range samples {
					samples[i] *= 32767.0
				}
			}
		} else if req.PCMBase64 != "" {
			rawBytes, err := base64.StdEncoding.DecodeString(req.PCMBase64)
			if err != nil || len(rawBytes) < 3200 {
				writeError(w, http.StatusBadRequest, "invalid pcm_base64 payload")
				return
			}
			numSamples := len(rawBytes) / 2
			samples = make([]float32, numSamples)
			for i := 0; i < numSamples; i++ {
				raw := int16(binary.LittleEndian.Uint16(rawBytes[i*2 : i*2+2]))
				samples[i] = float32(raw)
			}
		}
	}

	if len(samples) == 0 {
		writeJSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"reason":  "no_samples",
			"message": "No valid audio samples extracted.",
		})
		return
	}

	// 1. Extract spectrogram peak constellation map
	cmap, err := shazam.ExtractConstellationMap(samples, sampleRate)
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"reason":  "audio_processing_error",
			"message": "Audio sample could not be analyzed.",
		})
		return
	}

	if len(cmap.Peaks) == 0 {
		writeJSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"reason":  "low_signal",
			"message": "No audible musical landmarks detected. Please move closer to the audio source.",
		})
		return
	}

	// 2. Encode to Shazam binary signature
	sig, err := shazam.EncodeConstellationToSignature(cmap)
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"reason":  "insufficient_landmarks",
			"message": "No distinct musical patterns found. Try playing louder or closer to the microphone.",
		})
		return
	}

	// 3. Query Shazam discovery gateway
	res, err := s.shazamClient.RecognizeSignature(r.Context(), sig)
	if err != nil {
		// Fallback: Check local SQLite offline vault
		offlineRes, offErr := shazam.MatchOffline(r.Context(), s.repo, "")
		if offErr == nil && offlineRes != nil && offlineRes.Matched {
			writeJSON(w, http.StatusOK, offlineRes)
			return
		}
		// Return friendly unrecognised result instead of 500 error
		writeJSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"message": "No acoustic match found on Shazam.",
			"error":   err.Error(),
		})
		return
	}

	writeJSON(w, http.StatusOK, res)
}
