/*
 * Package: server
 * File: shazam_identify_test.go
 * Purpose: Unit tests for end-to-end Shazam identification endpoint handling raw PCM audio buffers and JSON sample payloads.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe endpoint testing with mock and synthetic audio streams.
 */

package server

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"math"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

func TestShazamIdentifyRawPCM(t *testing.T) {
	tempDir := t.TempDir()
	srv, err := NewServer(Config{
		Port:           45791,
		DatabasePath:   filepath.Join(tempDir, "test_shazam.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	})
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Synthesize 2 seconds of 16kHz 16-bit Mono PCM audio (440Hz + 880Hz harmonics)
	sampleRate := 16000
	numSamples := sampleRate * 2
	pcmBuf := new(bytes.Buffer)

	for i := 0; i < numSamples; i++ {
		tSec := float64(i) / float64(sampleRate)
		val := 0.5*math.Sin(2*math.Pi*440*tSec) + 0.3*math.Sin(2*math.Pi*880*tSec)
		sampleInt16 := int16(val * 32767.0)
		_ = binary.Write(pcmBuf, binary.LittleEndian, sampleInt16)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/v1/shazam/identify", pcmBuf)
	req.Header.Set("Content-Type", "application/octet-stream")
	w := httptest.NewRecorder()

	srv.handleShazamIdentify(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200, got %d: %s", resp.StatusCode, w.Body.String())
	}

	var result map[string]any
	if err := json.NewDecoder(w.Body).Decode(&result); err != nil {
		t.Fatalf("failed decoding response JSON: %v", err)
	}

	// Result must either be matched (true/false) or contain recognized metadata
	if _, ok := result["matched"]; !ok {
		t.Errorf("expected 'matched' field in response, got %v", result)
	}
}

func TestShazamIdentifyJSONSamples(t *testing.T) {
	tempDir := t.TempDir()
	srv, err := NewServer(Config{
		Port:           45792,
		DatabasePath:   filepath.Join(tempDir, "test_shazam_json.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	})
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Synthesize samples as float array
	sampleRate := 16000
	samples := make([]float32, sampleRate*2)
	for i := range samples {
		tSec := float64(i) / float64(sampleRate)
		samples[i] = float32(0.4 * math.Sin(2*math.Pi*440*tSec))
	}

	payload := map[string]any{
		"samples":     samples,
		"sample_rate": sampleRate,
	}
	jsonBytes, _ := json.Marshal(payload)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/shazam/identify", bytes.NewReader(jsonBytes))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	srv.handleShazamIdentify(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200, got %d: %s", resp.StatusCode, w.Body.String())
	}
}
