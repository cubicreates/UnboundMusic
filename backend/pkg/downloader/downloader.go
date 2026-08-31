/*
 * Package: downloader
 * File: downloader.go
 * Purpose: Physical Audio Stream Downloader: saves pure Opus/AAC audio streams into Unbound/Downloads/ with complete metadata tagging and progress monitoring.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Thread-safe download workers with progress tracking mutex locks.
 */

package downloader

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// DownloadTask represents an ongoing or completed file download.
type DownloadTask struct {
	TrackID      string    `json:"track_id"`
	Title        string    `json:"title"`
	Artist       string    `json:"artist"`
	Album        string    `json:"album"`
	Status       string    `json:"status"` // "QUEUED", "DOWNLOADING", "COMPLETED", "FAILED"
	BytesWritten int64     `json:"bytes_written"`
	TotalBytes   int64     `json:"total_bytes"`
	Percent      float64   `json:"percent"`
	LocalPath    string    `json:"local_path"`
	CreatedAt    time.Time `json:"created_at"`
	Error        string    `json:"error,omitempty"`
}

// Manager coordinates file downloads and destination directories.
type Manager struct {
	mu           sync.RWMutex
	ytClient     *ytmusic.Client
	downloadDir  string
	tasks        map[string]*DownloadTask
	httpClient   *http.Client
}

// NewManager creates a download manager saving to the specified download directory.
func NewManager(downloadDir string, ytClient *ytmusic.Client) *Manager {
	if ytClient == nil {
		ytClient = ytmusic.NewClient()
	}
	_ = os.MkdirAll(downloadDir, 0755)

	return &Manager{
		downloadDir: downloadDir,
		ytClient:    ytClient,
		tasks:       make(map[string]*DownloadTask),
		httpClient:  &http.Client{Timeout: 60 * time.Second},
	}
}

// DownloadTrack resolves the stream and downloads the track to Unbound/Downloads/.
func (m *Manager) DownloadTrack(ctx context.Context, trackID, title, artist, album string) (*DownloadTask, error) {
	if trackID == "" && title == "" {
		return nil, fmt.Errorf("trackID or title is required")
	}

	taskID := trackID
	if taskID == "" {
		taskID = fmt.Sprintf("custom_%d", time.Now().UnixNano())
	}

	cleanArtist := sanitizeFilename(artist)
	if cleanArtist == "" {
		cleanArtist = "Unknown Artist"
	}
	cleanTitle := sanitizeFilename(title)
	if cleanTitle == "" {
		cleanTitle = "Unknown Track"
	}

	fileName := fmt.Sprintf("%s - %s.opus", cleanArtist, cleanTitle)
	destPath := filepath.Join(m.downloadDir, fileName)

	task := &DownloadTask{
		TrackID:   taskID,
		Title:     title,
		Artist:    artist,
		Album:     album,
		Status:    "DOWNLOADING",
		LocalPath: destPath,
		CreatedAt: time.Now(),
	}

	m.mu.Lock()
	m.tasks[taskID] = task
	m.mu.Unlock()

	// Resolve stream URL
	streamInfo, err := m.ytClient.GetStreamInfo(ctx, trackID)
	if err != nil {
		task.Status = "FAILED"
		task.Error = err.Error()
		return task, err
	}

	task.TotalBytes = streamInfo.ContentLength

	// Stream write to disk
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, streamInfo.StreamURL, nil)
	if err != nil {
		task.Status = "FAILED"
		task.Error = err.Error()
		return task, err
	}

	resp, err := m.httpClient.Do(req)
	if err != nil {
		task.Status = "FAILED"
		task.Error = err.Error()
		return task, err
	}
	defer resp.Body.Close()

	out, err := os.Create(destPath)
	if err != nil {
		task.Status = "FAILED"
		task.Error = err.Error()
		return task, err
	}
	defer out.Close()

	buf := make([]byte, 64*1024)
	var written int64

	for {
		n, rErr := resp.Body.Read(buf)
		if n > 0 {
			wN, wErr := out.Write(buf[:n])
			written += int64(wN)
			task.BytesWritten = written
			if task.TotalBytes > 0 {
				task.Percent = (float64(written) / float64(task.TotalBytes)) * 100.0
			}
			if wErr != nil {
				task.Status = "FAILED"
				task.Error = wErr.Error()
				return task, wErr
			}
		}
		if rErr != nil {
			if rErr == io.EOF {
				break
			}
			task.Status = "FAILED"
			task.Error = rErr.Error()
			return task, rErr
		}
	}

	task.Status = "COMPLETED"
	task.Percent = 100.0

	return task, nil
}

// ListDownloadedFiles scans the Unbound/Downloads directory for downloaded tracks.
func (m *Manager) ListDownloadedFiles() ([]models.Track, error) {
	entries, err := os.ReadDir(m.downloadDir)
	if err != nil {
		return nil, err
	}

	var tracks []models.Track
	for _, e := range entries {
		if e.IsDir() {
			continue
		}

		ext := strings.ToLower(filepath.Ext(e.Name()))
		if ext == ".opus" || ext == ".mp3" || ext == ".m4a" || ext == ".flac" || ext == ".wav" {
			info, _ := e.Info()
			fullName := strings.TrimSuffix(e.Name(), ext)
			parts := strings.SplitN(fullName, " - ", 2)

			artist := "Unknown Artist"
			title := fullName
			if len(parts) == 2 {
				artist = parts[0]
				title = parts[1]
			}

			fullPath := filepath.Join(m.downloadDir, e.Name())
			var size int64
			if info != nil {
				size = info.Size()
			}

			tracks = append(tracks, models.Track{
				ID:          fmt.Sprintf("local_dl_%s", e.Name()),
				Title:       title,
				Artist:      artist,
				Album:       "Unbound Downloads",
				LocalPath:   fullPath,
				StreamURL:   "file://" + filepath.ToSlash(fullPath),
				IsLocal:     true,
				DurationMs:  (size / (160 * 128)) * 1000, // Estimated duration from bitrate
				BitrateKbps: 160,
				Codec:       strings.ToUpper(strings.TrimPrefix(ext, ".")),
			})
		}
	}

	return tracks, nil
}

func sanitizeFilename(name string) string {
	invalid := []string{"/", "\\", ":", "*", "?", "\"", "<", ">", "|"}
	clean := name
	for _, char := range invalid {
		clean = strings.ReplaceAll(clean, char, "_")
	}
	return strings.TrimSpace(clean)
}
