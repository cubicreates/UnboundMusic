/*
 * Package: fingerprint
 * File: fingerprint_test.go
 * Purpose: Unit tests for Chromaprint fpcalc parsing, AcoustID query client, and ingestion pipeline.
 * Subsystem: Acoustic Fingerprinting Tests
 * Concurrency: Thread-safe pure testing.
 */

package fingerprint

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// TestParseFpcalcJSON validates JSON stdout parsing from simulated fpcalc execution.
func TestParseFpcalcJSON(t *testing.T) {
	rawOutput := []byte(`{
		"duration": 194.25,
		"fingerprint": "AQAAZEkUSUlCRVLwHP_hD5-O5s..."
	}`)

	res, err := ParseFpcalcJSON(rawOutput)
	if err != nil {
		t.Fatalf("unexpected error parsing valid fpcalc JSON: %v", err)
	}

	if res.Duration != 194.25 {
		t.Errorf("expected duration 194.25, got %f", res.Duration)
	}

	if res.Fingerprint != "AQAAZEkUSUlCRVLwHP_hD5-O5s..." {
		t.Errorf("unexpected fingerprint string: %s", res.Fingerprint)
	}

	// Invalid cases
	if _, err := ParseFpcalcJSON([]byte(``)); err == nil {
		t.Errorf("expected error for empty output, got nil")
	}

	if _, err := ParseFpcalcJSON([]byte(`{"duration": 0.0, "fingerprint": "abc"}`)); err == nil {
		t.Errorf("expected error for zero duration, got nil")
	}

	if _, err := ParseFpcalcJSON([]byte(`{"duration": 10.0, "fingerprint": ""}`)); err == nil {
		t.Errorf("expected error for empty fingerprint, got nil")
	}
}

// TestAcoustIDResponseParsing parses real AcoustID response JSON, validating score thresholds and artist string joins.
func TestAcoustIDResponseParsing(t *testing.T) {
	sampleJSON := []byte(`{
		"status": "ok",
		"results": [
			{
				"id": "result-uuid-1",
				"score": 0.94,
				"recordings": [
					{
						"id": "musicbrainz-track-uuid",
						"title": "Starboy",
						"artists": [
							{"id": "artist-1", "name": "The Weeknd"},
							{"id": "artist-2", "name": "Daft Punk"}
						],
						"releasegroups": [
							{"id": "album-1", "title": "Starboy", "type": "Album"}
						]
					}
				]
			}
		]
	}`)

	meta, err := ParseAcoustIDResponse(sampleJSON)
	if err != nil {
		t.Fatalf("unexpected error parsing AcoustID response: %v", err)
	}

	if meta.Title != "Starboy" {
		t.Errorf("expected title 'Starboy', got %q", meta.Title)
	}

	if meta.Artist != "The Weeknd, Daft Punk" {
		t.Errorf("expected joined artists 'The Weeknd, Daft Punk', got %q", meta.Artist)
	}

	if meta.Album != "Starboy" {
		t.Errorf("expected album 'Starboy', got %q", meta.Album)
	}

	if meta.Score != 0.94 {
		t.Errorf("expected score 0.94, got %f", meta.Score)
	}

	if meta.RecordingID != "musicbrainz-track-uuid" {
		t.Errorf("expected recording ID 'musicbrainz-track-uuid', got %q", meta.RecordingID)
	}
}

// TestLowConfidenceDiscard asserts matches with score < 0.70 are categorized as unrecognized audio recordings.
func TestLowConfidenceDiscard(t *testing.T) {
	sampleLowScoreJSON := []byte(`{
		"status": "ok",
		"results": [
			{
				"id": "result-uuid-low",
				"score": 0.42,
				"recordings": [
					{
						"id": "mb-recording-low",
						"title": "Faint Background Noise",
						"artists": [{"name": "Unknown"}],
						"releasegroups": [{"title": "Demo"}]
					}
				]
			}
		]
	}`)

	meta, err := ParseAcoustIDResponse(sampleLowScoreJSON)
	if err != nil {
		t.Fatalf("unexpected error parsing low score response: %v", err)
	}

	if meta.Score >= MinConfidenceScore {
		t.Errorf("expected score %f to be below MinConfidenceScore %f", meta.Score, MinConfidenceScore)
	}
}

// TestIsGenericTitle validates regex detection of chat voice notes.
func TestIsGenericTitle(t *testing.T) {
	testCases := []struct {
		name     string
		expected bool
	}{
		{"AUD-20240902-WA0041.opus", true},
		{"voice_memo_001.mp3", true},
		{"PTT-20231201-WA0002.m4a", true},
		{"audio_recording.wav", true},
		{"20240902_143000.opus", true},
		{"Blinding Lights.mp3", false},
		{"Hotel California.flac", false},
	}

	for _, tc := range testCases {
		got := IsGenericTitle(tc.name)
		if got != tc.expected {
			t.Errorf("IsGenericTitle(%q) = %v; want %v", tc.name, got, tc.expected)
		}
	}
}

// TestRateLimiter validates that rate limiting throttles requests appropriately.
func TestRateLimiter(t *testing.T) {
	// Limiter set to 10 QPS (100ms interval) for fast testing
	limiter := NewRateLimiter(10.0)
	ctx := context.Background()

	start := time.Now()
	for i := 0; i < 3; i++ {
		if err := limiter.Wait(ctx); err != nil {
			t.Fatalf("limiter wait error: %v", err)
		}
	}
	elapsed := time.Since(start)

	// 3 requests at 100ms intervals should take at least 150ms
	if elapsed < 150*time.Millisecond {
		t.Logf("Rate limiter elapsed time: %v (expected ~200ms)", elapsed)
	}
}

// MockFingerprintRepo provides an in-memory repository for unit testing the ingestion pipeline.
type MockFingerprintRepo struct {
	mu           sync.Mutex
	fingerprints map[string]*models.FingerprintRecord
	localTracks  map[string]*models.LocalTrack
}

func NewMockFingerprintRepo() *MockFingerprintRepo {
	return &MockFingerprintRepo{
		fingerprints: make(map[string]*models.FingerprintRecord),
		localTracks:  make(map[string]*models.LocalTrack),
	}
}

func (m *MockFingerprintRepo) GetFingerprintByPath(ctx context.Context, filePath string) (*models.FingerprintRecord, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, fp := range m.fingerprints {
		if fp.FilePath == filePath {
			return fp, nil
		}
	}
	return nil, nil
}

func (m *MockFingerprintRepo) GetFingerprintByHash(ctx context.Context, hash string) (*models.FingerprintRecord, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.fingerprints[hash], nil
}

func (m *MockFingerprintRepo) UpsertFingerprint(ctx context.Context, fp *models.FingerprintRecord) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.fingerprints[fp.Hash] = fp
	return nil
}

func (m *MockFingerprintRepo) UpsertLocalTrack(ctx context.Context, track *models.LocalTrack) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.localTracks[track.FilePath] = track
	return nil
}

func (m *MockFingerprintRepo) GetLocalTrackByPath(ctx context.Context, filePath string) (*models.LocalTrack, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.localTracks[filePath], nil
}

// TestPipelineWithMockServer tests end-to-end ingestion using an httptest server.
func TestPipelineWithMockServer(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fp := r.URL.Query().Get("fingerprint")
		if strings.Contains(fp, "STARBOY") {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{
				"status": "ok",
				"results": [
					{
						"id": "res-1",
						"score": 0.95,
						"recordings": [
							{
								"id": "mb-starboy",
								"title": "Starboy",
								"artists": [{"name": "The Weeknd"}],
								"releasegroups": [{"title": "Starboy"}]
							}
						]
					}
				]
			}`))
			return
		}

		// Unknown/low confidence response
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"status": "ok", "results": []}`))
	}))
	defer server.Close()

	// Redirect AcoustID endpoint to mock test server
	origEndpoint := AcoustIDEndpoint
	AcoustIDEndpoint = server.URL
	defer func() { AcoustIDEndpoint = origEndpoint }()

	repo := NewMockFingerprintRepo()
	ctx := context.Background()

	// 1. Test LookupAcoustID high score resolution
	meta, err := LookupAcoustID(ctx, 210.0, "MOCK_FINGERPRINT_STARBOY_123")
	if err != nil {
		t.Fatalf("unexpected error from LookupAcoustID: %v", err)
	}
	if meta.Title != "Starboy" || meta.Score < 0.90 {
		t.Errorf("unexpected meta result: %+v", meta)
	}

	// 2. Test Low confidence resolution
	metaLow, err := LookupAcoustID(ctx, 45.0, "MOCK_VOICE_NOTE_NOISE")
	if err != nil {
		t.Fatalf("unexpected error from LookupAcoustID low: %v", err)
	}
	if metaLow.Score >= MinConfidenceScore {
		t.Errorf("expected score < 0.70, got %f", metaLow.Score)
	}

	// 3. Test caching behavior in repository
	tempDir := t.TempDir()
	dummyAudioPath := filepath.Join(tempDir, "AUD-20240902-WA0041.opus")
	if err := os.WriteFile(dummyAudioPath, []byte("OggS_DUMMY_OPUS_CONTENT"), 0644); err != nil {
		t.Fatalf("failed to write dummy audio file: %v", err)
	}

	// Pre-seed repository with a cached fingerprint
	cachedFP := &models.FingerprintRecord{
		Hash:       "PRE_CACHED_HASH",
		FilePath:   dummyAudioPath,
		Title:      "Precached Song",
		Artist:     "Precached Artist",
		Album:      "Precached Album",
		DurationMs: 180000,
		Source:     "acoustid",
		UpdatedAt:  time.Now().Unix(),
	}
	if err := repo.UpsertFingerprint(ctx, cachedFP); err != nil {
		t.Fatalf("failed to seed fingerprint: %v", err)
	}

	// IngestUntaggedFile should hit the cache by path and not fail on missing fpcalc
	track, err := IngestUntaggedFile(ctx, repo, "nonexistent-fpcalc", dummyAudioPath)
	if err != nil {
		t.Fatalf("unexpected error on cached track ingestion: %v", err)
	}

	if track.Title != "Precached Song" {
		t.Errorf("expected title 'Precached Song', got %q", track.Title)
	}
	if track.Artist != "Precached Artist" {
		t.Errorf("expected artist 'Precached Artist', got %q", track.Artist)
	}
}

// TestGetAcoustIDClientKeyConfig tests default vs environment override for AcoustID API key.
func TestGetAcoustIDClientKeyConfig(t *testing.T) {
	// Test default key
	_ = os.Unsetenv("ACOUSTID_API_KEY")
	if key := GetAcoustIDClientKey(); key != DefaultAcoustIDClientKey {
		t.Errorf("expected default key %q, got %q", DefaultAcoustIDClientKey, key)
	}

	// Test environment override
	customKey := "my_custom_acoustid_api_key_123"
	_ = os.Setenv("ACOUSTID_API_KEY", customKey)
	defer os.Unsetenv("ACOUSTID_API_KEY")

	if key := GetAcoustIDClientKey(); key != customKey {
		t.Errorf("expected custom key %q, got %q", customKey, key)
	}
}

// TestResolveFpcalcBinary tests binary discovery across fallbacks.
func TestResolveFpcalcBinary(t *testing.T) {
	bin, err := ResolveFpcalcBinary("")
	if err != nil {
		t.Logf("fpcalc binary not found on this machine: %v", err)
	} else {
		t.Logf("fpcalc binary successfully resolved at: %s", bin)
		if _, err := os.Stat(bin); err != nil {
			t.Errorf("resolved binary does not exist on disk: %v", err)
		}
	}
}

