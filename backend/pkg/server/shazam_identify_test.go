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

func TestShazamIdentifySilentPCM(t *testing.T) {
	tempDir := t.TempDir()
	srv, err := NewServer(Config{
		Port:           45793,
		DatabasePath:   filepath.Join(tempDir, "test_shazam_silent.db"),
		LibraryRoot:    tempDir,
		AppStorageRoot: tempDir,
	})
	if err != nil {
		t.Fatalf("failed creating server: %v", err)
	}
	defer srv.Shutdown(context.Background())

	// Synthesize 2 seconds of pure silence (all 0s)
	sampleRate := 16000
	numSamples := sampleRate * 2
	pcmBytes := make([]byte, numSamples*2) // all zeros

	req := httptest.NewRequest(http.MethodPost, "/api/v1/shazam/identify", bytes.NewReader(pcmBytes))
	req.Header.Set("Content-Type", "application/octet-stream")
	w := httptest.NewRecorder()

	srv.handleShazamIdentify(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200 on quiet audio, got %d: %s", resp.StatusCode, w.Body.String())
	}

	var result map[string]any
	if err := json.NewDecoder(w.Body).Decode(&result); err != nil {
		t.Fatalf("failed decoding response JSON: %v", err)
	}

	if matched, ok := result["matched"].(bool); !ok || matched {
		t.Errorf("expected matched=false for silent audio, got %v", result)
	}
}

func TestAcousticDisambiguation(t *testing.T) {
	// 1. Test title cleaning
	testCases := []struct {
		inputTitle  string
		expected    string
		isRemix     bool
	}{
		{"Stay (feat. Justin Bieber) - DJ Snake Remix", "Stay", true},
		{"Unholy (Disclosure Remix)", "Unholy", true},
		{"Titanium (Acoustic Cover)", "Titanium", true},
		{"Shape of You [Club Mix]", "Shape of You", true},
		{"Blinding Lights (Slowed + Reverb)", "Blinding Lights", true},
		{"Ordinary Song", "Ordinary Song", false},
	}

	for _, tc := range testCases {
		clean := cleanRemixTitle(tc.inputTitle)
		if clean != tc.expected {
			t.Errorf("cleanRemixTitle(%q) = %q; expected %q", tc.inputTitle, clean, tc.expected)
		}
		if tc.isRemix && !isRemixIndicator(tc.inputTitle) {
			t.Errorf("isRemixIndicator(%q) = false; expected true", tc.inputTitle)
		}
	}

	// 2. Test artist cleaning
	artistTestCases := []struct {
		inputArtist string
		expected    string
	}{
		{"David Guetta feat. Sia", "David Guetta"},
		{"Marshmello ft. Bastille", "Marshmello"},
		{"Sam Smith & Kim Petras", "Sam Smith"},
		{"Clean Artist", "Clean Artist"},
	}

	for _, tc := range artistTestCases {
		clean := cleanRemixArtist(tc.inputArtist)
		if clean != tc.expected {
			t.Errorf("cleanRemixArtist(%q) = %q; expected %q", tc.inputArtist, clean, tc.expected)
		}
	}

	// 3. Test resolveAcousticVariants structure
	srv := &Server{}
	variants := srv.resolveAcousticVariants(context.Background(), "Despacito (Remix)", "Luis Fonsi feat. Daddy Yankee", "Album", "http://cover.jpg", "track_123")
	if len(variants) == 0 {
		t.Fatalf("expected at least 1 variant (radar match)")
	}
	radarMatch := variants[0]
	if radarMatch["badge"] != "Acoustic Radar Match" {
		t.Errorf("expected badge 'Acoustic Radar Match', got %v", radarMatch["badge"])
	}
	if radarMatch["is_radar_match"] != true {
		t.Errorf("expected is_radar_match=true, got %v", radarMatch["is_radar_match"])
	}
}

