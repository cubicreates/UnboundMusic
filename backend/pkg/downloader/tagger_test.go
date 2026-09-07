/*
 * Package: downloader
 * File: tagger_test.go
 * Purpose: Unit tests for 1080x1080 master artwork upscaling, Vorbis comment generation, and SQLite local_tracks indexing.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Standard Go testing framework.
 */

package downloader

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

func TestUpscaleThumbnailMasterArt(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name:     "Google User Content with w60-h60",
			input:    "https://lh3.googleusercontent.com/abc123xyz=w60-h60-l90-rj",
			expected: "https://lh3.googleusercontent.com/abc123xyz=w1080-h1080-l90-rj",
		},
		{
			name:     "Google User Content with s544 square",
			input:    "https://lh3.googleusercontent.com/abc123xyz=s544-c-k-c0x00ffffff-no-rj",
			expected: "https://lh3.googleusercontent.com/abc123xyz=w1080-h1080-l90-rj",
		},
		{
			name:     "Google User Content with no size params",
			input:    "https://lh3.googleusercontent.com/abc123xyz",
			expected: "https://lh3.googleusercontent.com/abc123xyz=w1080-h1080-l90-rj",
		},
		{
			name:     "YouTube img hqdefault to maxresdefault",
			input:    "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
			expected: "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
		},
		{
			name:     "YouTube img sddefault to maxresdefault",
			input:    "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
			expected: "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
		},
		{
			name:     "Generic artwork URL without known patterns",
			input:    "https://example.com/cover.jpg",
			expected: "https://example.com/cover.jpg",
		},
		{
			name:     "Empty URL",
			input:    "",
			expected: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual := UpscaleThumbnailMasterArt(tt.input)
			if actual != tt.expected {
				t.Errorf("UpscaleThumbnailMasterArt(%q)\ngot:  %q\nwant: %q", tt.input, actual, tt.expected)
			}
		})
	}
}

func TestInjectMetadataAndIndex(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), "unbound_tagger_test")
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	// Mock artwork server
	mockImgBytes := []byte("fake-high-res-jpeg-bytes-1080p")
	imgServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/jpeg")
		_, _ = w.Write(mockImgBytes)
	}))
	defer imgServer.Close()

	// Initialize SQLite database
	dbPath := filepath.Join(tempDir, "test_tagger.db")
	db, err := database.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open test db: %v", err)
	}
	defer db.Close()
	repo := database.NewRepository(db)

	// Create dummy audio file
	dummyAudioPath := filepath.Join(tempDir, "Artist - Song.opus")
	dummyAudioContent := make([]byte, 1024*50)
	if err := os.WriteFile(dummyAudioPath, dummyAudioContent, 0644); err != nil {
		t.Fatalf("failed to write dummy audio: %v", err)
	}

	task := &DownloadTask{
		VideoID:      "test_vid_123",
		TrackID:      "test_vid_123",
		Title:        "Master Song",
		Artist:       "Master Artist",
		Album:        "Master Album",
		ArtworkURL:   imgServer.URL + "/thumb=w60-h60",
		TargetFormat: "opus",
		LocalPath:    dummyAudioPath,
		CreatedAt:    time.Now(),
	}

	client := &http.Client{Timeout: 5 * time.Second}
	err = InjectMetadataAndIndex(context.Background(), dummyAudioPath, task, repo, client)
	if err != nil {
		t.Fatalf("InjectMetadataAndIndex failed: %v", err)
	}

	// Verify cover art companion file was created
	coverPath := dummyAudioPath + ".cover.jpg"
	savedCoverBytes, err := os.ReadFile(coverPath)
	if err != nil {
		t.Fatalf("expected companion cover art at %s: %v", coverPath, err)
	}
	if string(savedCoverBytes) != string(mockImgBytes) {
		t.Fatalf("cover bytes do not match expected")
	}

	// Verify track was automatically indexed into local_tracks in SQLite
	tracks, err := repo.GetLocalTracksBySource(context.Background(), SourceFolderDownloads)
	if err != nil {
		t.Fatalf("GetLocalTracksBySource failed: %v", err)
	}

	if len(tracks) != 1 {
		t.Fatalf("expected 1 indexed track in SQLite, got %d", len(tracks))
	}

	indexed := tracks[0]
	if indexed.Title != "Master Song" {
		t.Errorf("expected Title 'Master Song', got %q", indexed.Title)
	}
	if indexed.Artist != "Master Artist" {
		t.Errorf("expected Artist 'Master Artist', got %q", indexed.Artist)
	}
	if indexed.Album != "Master Album" {
		t.Errorf("expected Album 'Master Album', got %q", indexed.Album)
	}
	if indexed.SourceFolder != SourceFolderDownloads {
		t.Errorf("expected SourceFolder '%s', got %q", SourceFolderDownloads, indexed.SourceFolder)
	}
	if indexed.FilePath != dummyAudioPath {
		t.Errorf("expected FilePath %s, got %s", dummyAudioPath, indexed.FilePath)
	}
}
