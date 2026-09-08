/*
 * Package: downloader
 * File: downloader.go
 * Purpose: Physical Audio Stream Downloader: saves pure Opus/AAC audio streams into Unbound/Downloads/ with HTTP Range chunking, pause/resume, atomic part assembly, metadata tagging, and local DB indexing.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Thread-safe download workers with sync.RWMutex and context cancellation.
 */

package downloader

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// Task status constants
const (
	StatusQueued      = "QUEUED"
	StatusDownloading = "DOWNLOADING"
	StatusPaused      = "PAUSED"
	StatusTagging     = "TAGGING"
	StatusCompleted   = "COMPLETED"
	StatusFailed      = "FAILED"
	StatusCancelled   = "CANCELLED"
)

const (
	DefaultChunkSize      = 1024 * 1024 // 1 MB HTTP Range chunk
	MinRequiredFreeBytes  = 20 * 1024 * 1024 // 20 MB safety threshold
	SourceFolderDownloads = "Unbound Downloads"
)

// DownloadTask represents an ongoing, paused, or completed physical file download.
type DownloadTask struct {
	VideoID         string    `json:"video_id"`
	TrackID         string    `json:"track_id"`
	Title           string    `json:"title"`
	Artist          string    `json:"artist"`
	Album           string    `json:"album"`
	ArtworkURL      string    `json:"artwork_url,omitempty"`
	TargetFormat    string    `json:"target_format"` // "opus", "m4a", etc.
	Status          string    `json:"status"`        // Status constants
	DownloadedBytes int64     `json:"downloaded_bytes"`
	BytesWritten    int64     `json:"bytes_written"` // backward compatibility
	TotalBytes      int64     `json:"total_bytes"`
	Progress        float64   `json:"progress"` // 0.0 - 100.0
	Percent         float64   `json:"percent"`  // backward compatibility
	LocalPath       string    `json:"local_path"`
	CreatedAt       time.Time `json:"created_at"`
	UpdatedAt       time.Time `json:"updated_at"`
	Error           string    `json:"error,omitempty"`

	// Internal state coordination
	streamURL   string
	cancelFunc  context.CancelFunc
	pauseSignal chan struct{}
}

// Manager coordinates file downloads, HTTP Range workers, and destination directories.
type Manager struct {
	mu          sync.RWMutex
	ytClient    *ytmusic.Client
	repo        *database.Repository
	downloadDir string
	tasks       map[string]*DownloadTask
	httpClient  *http.Client
	chunkSize   int64
}

// NewManager creates a download manager saving to the specified download directory.
func NewManager(downloadDir string, ytClient *ytmusic.Client, repos ...*database.Repository) *Manager {
	if ytClient == nil {
		ytClient = ytmusic.NewClient()
	}
	var repo *database.Repository
	if len(repos) > 0 {
		repo = repos[0]
	}
	_ = os.MkdirAll(downloadDir, 0755)

	return &Manager{
		downloadDir: downloadDir,
		ytClient:    ytClient,
		repo:        repo,
		tasks:       make(map[string]*DownloadTask),
		httpClient:  &http.Client{Timeout: 60 * time.Second},
		chunkSize:   DefaultChunkSize,
	}
}

// SetRepository configures or updates the SQLite repository for automatic indexing.
func (m *Manager) SetRepository(repo *database.Repository) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.repo = repo
}

// StartDownload initiates or resumes an asynchronous chunked download.
func (m *Manager) StartDownload(ctx context.Context, videoID, title, artist, album, artworkURL string) (*DownloadTask, error) {
	return m.startDownloadInternal(ctx, videoID, title, artist, album, artworkURL, "")
}

// DownloadTrack is a backward-compatible wrapper for StartDownload without artwork URL.
func (m *Manager) DownloadTrack(ctx context.Context, trackID, title, artist, album string) (*DownloadTask, error) {
	return m.StartDownload(ctx, trackID, title, artist, album, "")
}

// StartDownloadWithURL initiates a download using a known direct stream URL (for tests or pre-resolved URLs).
func (m *Manager) StartDownloadWithURL(ctx context.Context, videoID, title, artist, album, artworkURL, directURL string) (*DownloadTask, error) {
	return m.startDownloadInternal(ctx, videoID, title, artist, album, artworkURL, directURL)
}

func (m *Manager) startDownloadInternal(ctx context.Context, videoID, title, artist, album, artworkURL, directURL string) (*DownloadTask, error) {
	if videoID == "" && title == "" {
		return nil, fmt.Errorf("video_id or title is required")
	}

	taskID := videoID
	if taskID == "" {
		taskID = fmt.Sprintf("custom_%d", time.Now().UnixNano())
	}

	m.mu.Lock()
	existing, exists := m.tasks[taskID]
	if exists && (existing.Status == StatusDownloading || existing.Status == StatusTagging) {
		m.mu.Unlock()
		return existing, nil
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

	workerCtx, cancel := context.WithCancel(context.Background())
	task := &DownloadTask{
		VideoID:      taskID,
		TrackID:      taskID,
		Title:        title,
		Artist:       artist,
		Album:        album,
		ArtworkURL:   artworkURL,
		TargetFormat: "opus",
		Status:       StatusQueued,
		LocalPath:    destPath,
		CreatedAt:    time.Now(),
		UpdatedAt:    time.Now(),
		cancelFunc:   cancel,
		pauseSignal:  make(chan struct{}),
		streamURL:    directURL,
	}

	m.tasks[taskID] = task
	m.mu.Unlock()

	// Check disk space before starting
	storageStatus, err := gatekeeper.CheckStorageCapacity(m.downloadDir)
	if err == nil && storageStatus != nil {
		if storageStatus.FreeBytes < MinRequiredFreeBytes {
			task.Status = StatusFailed
			task.Error = fmt.Sprintf("insufficient disk space: %.1f MB available, need at least 20 MB", storageStatus.FreeMB)
			return task, fmt.Errorf("%s", task.Error)
		}
	}

	// Launch chunked download worker goroutine
	go m.runDownloadWorker(workerCtx, task)

	return task, nil
}

// PauseDownload suspends an active download, preserving the .part file on disk.
func (m *Manager) PauseDownload(videoID string) error {
	m.mu.Lock()
	task, exists := m.tasks[videoID]
	if !exists {
		m.mu.Unlock()
		return fmt.Errorf("task not found: %s", videoID)
	}

	if task.Status != StatusDownloading && task.Status != StatusQueued {
		m.mu.Unlock()
		return fmt.Errorf("task is not downloading (status: %s)", task.Status)
	}

	task.Status = StatusPaused
	task.UpdatedAt = time.Now()
	if task.cancelFunc != nil {
		task.cancelFunc()
	}
	m.mu.Unlock()

	return nil
}

// ResumeDownload unpauses a paused download, continuing from the existing .part byte offset.
func (m *Manager) ResumeDownload(ctx context.Context, videoID string) error {
	m.mu.Lock()
	task, exists := m.tasks[videoID]
	if !exists {
		m.mu.Unlock()
		return fmt.Errorf("task not found: %s", videoID)
	}

	if task.Status != StatusPaused && task.Status != StatusFailed {
		m.mu.Unlock()
		return fmt.Errorf("task is not paused (status: %s)", task.Status)
	}

	workerCtx, cancel := context.WithCancel(context.Background())
	task.cancelFunc = cancel
	task.Status = StatusQueued
	task.UpdatedAt = time.Now()
	task.Error = ""
	m.mu.Unlock()

	go m.runDownloadWorker(workerCtx, task)
	return nil
}

// CancelDownload terminates an active download and deletes partial .part artifacts.
func (m *Manager) CancelDownload(videoID string) error {
	m.mu.Lock()
	task, exists := m.tasks[videoID]
	if !exists {
		m.mu.Unlock()
		return fmt.Errorf("task not found: %s", videoID)
	}

	task.Status = StatusCancelled
	task.UpdatedAt = time.Now()
	if task.cancelFunc != nil {
		task.cancelFunc()
	}
	m.mu.Unlock()

	// Clean up .part file
	partPath := task.LocalPath + ".part"
	_ = os.Remove(partPath)

	return nil
}

// GetTask returns the current status and progress of a download task.
func (m *Manager) GetTask(videoID string) *DownloadTask {
	m.mu.RLock()
	defer m.mu.RUnlock()

	task, exists := m.tasks[videoID]
	if !exists {
		return nil
	}

	// Return a copy to avoid concurrent mutations
	copy := *task
	return &copy
}

// ListActiveTasks returns all ongoing, queued, or paused download tasks.
func (m *Manager) ListActiveTasks() []*DownloadTask {
	m.mu.RLock()
	defer m.mu.RUnlock()

	var result []*DownloadTask
	for _, task := range m.tasks {
		copy := *task
		result = append(result, &copy)
	}
	return result
}

// DeleteDownload removes a downloaded track from disk and unregisters it from the SQLite database.
func (m *Manager) DeleteDownload(ctx context.Context, videoID string, deletePhysicalFile bool) error {
	m.mu.Lock()
	task, exists := m.tasks[videoID]
	if exists {
		delete(m.tasks, videoID)
	}
	m.mu.Unlock()

	var filePath string
	if task != nil {
		filePath = task.LocalPath
	}

	if filePath != "" {
		if deletePhysicalFile {
			_ = os.Remove(filePath)
			_ = os.Remove(filePath + ".part")
		}

		if m.repo != nil {
			_ = m.repo.DeleteLocalTrack(ctx, filePath)
		}
	}

	return nil
}

// runDownloadWorker coordinates 1 MB HTTP Range chunking, resume offsets, tagging, and atomic rename.
func (m *Manager) runDownloadWorker(ctx context.Context, task *DownloadTask) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("[PANIC RECOVERED] download worker for %s: %v\nStack trace:\n%s", task.VideoID, r, string(debug.Stack()))
			m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("internal worker panic: %v", r))
		}
	}()

	m.updateTaskStatus(task, StatusDownloading, "")

	// 1. Resolve stream URL if not pre-populated
	if task.streamURL == "" {
		streamInfo, err := m.ytClient.GetStreamInfo(ctx, task.VideoID)
		if err != nil {
			m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("stream resolution failed: %v", err))
			return
		}
		task.streamURL = streamInfo.StreamURL
		task.TotalBytes = streamInfo.ContentLength
	}

	// 2. Discover total file size if not set
	if task.TotalBytes <= 0 {
		headReq, err := http.NewRequestWithContext(ctx, http.MethodHead, task.streamURL, nil)
		if err == nil {
			headResp, err := m.httpClient.Do(headReq)
			if err == nil {
				_ = headResp.Body.Close()
				if headResp.ContentLength > 0 {
					task.TotalBytes = headResp.ContentLength
				}
			}
		}
	}

	// 3. Inspect existing .part file for resume offset
	partPath := task.LocalPath + ".part"
	var currentOffset int64 = 0

	if fi, err := os.Stat(partPath); err == nil {
		currentOffset = fi.Size()
		task.DownloadedBytes = currentOffset
		task.BytesWritten = currentOffset
		if task.TotalBytes > 0 {
			task.Progress = (float64(currentOffset) / float64(task.TotalBytes)) * 100.0
			task.Percent = task.Progress
		}
	}

	// 4. Open .part file in append/read-write mode
	out, err := os.OpenFile(partPath, os.O_CREATE|os.O_RDWR|os.O_APPEND, 0644)
	if err != nil {
		m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("failed to open part file: %v", err))
		return
	}
	defer func() {
		_ = out.Close()
		m.mu.RLock()
		status := task.Status
		m.mu.RUnlock()
		if status == StatusCancelled {
			_ = os.Remove(partPath)
		}
	}()

	chunkSize := m.chunkSize
	if chunkSize <= 0 {
		chunkSize = DefaultChunkSize
	}

	// 5. Chunked Range loop
	for {
		select {
		case <-ctx.Done():
			// Cancelled or paused externally
			m.mu.RLock()
			currentStatus := task.Status
			m.mu.RUnlock()
			if currentStatus != StatusPaused && currentStatus != StatusCancelled {
				m.updateTaskStatus(task, StatusFailed, "download context cancelled")
			}
			return
		default:
		}

		if task.TotalBytes > 0 && currentOffset >= task.TotalBytes {
			break
		}

		rangeEnd := currentOffset + chunkSize - 1
		if task.TotalBytes > 0 && rangeEnd >= task.TotalBytes {
			rangeEnd = task.TotalBytes - 1
		}

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, task.streamURL, nil)
		if err != nil {
			m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("request creation failed: %v", err))
			return
		}

		if rangeEnd >= currentOffset {
			req.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", currentOffset, rangeEnd))
		}

		resp, err := m.httpClient.Do(req)
		if err != nil {
			select {
			case <-ctx.Done():
				return
			default:
			}
			m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("http chunk fetch failed: %v", err))
			return
		}

		// Handle response status (206 Partial Content or 200 OK)
		if resp.StatusCode != http.StatusPartialContent && resp.StatusCode != http.StatusOK {
			_ = resp.Body.Close()
			m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("server returned status %d", resp.StatusCode))
			return
		}

		// Parse content length or content range to set total size if unknown
		if task.TotalBytes <= 0 {
			cr := resp.Header.Get("Content-Range")
			if cr != "" {
				// Format: bytes start-end/total
				slashIdx := strings.LastIndex(cr, "/")
				if slashIdx != -1 && slashIdx < len(cr)-1 {
					totalStr := cr[slashIdx+1:]
					if total, err := strconv.ParseInt(totalStr, 10, 64); err == nil {
						task.TotalBytes = total
					}
				}
			} else if resp.ContentLength > 0 {
				task.TotalBytes = resp.ContentLength
			}
		}

		// If server returned 200 OK (does not support Range), and we had a previous offset, truncate and start from 0
		if resp.StatusCode == http.StatusOK && currentOffset > 0 {
			_ = out.Truncate(0)
			_, _ = out.Seek(0, io.SeekStart)
			currentOffset = 0
			task.DownloadedBytes = 0
			task.BytesWritten = 0
		}

		// Copy chunk into .part file
		buf := make([]byte, 64*1024)
		var chunkWritten int64
		var readErr error

		for {
			n, rErr := resp.Body.Read(buf)
			if n > 0 {
				wN, wErr := out.Write(buf[:n])
				if wErr != nil {
					_ = resp.Body.Close()
					m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("failed writing to disk: %v", wErr))
					return
				}
				chunkWritten += int64(wN)
				currentOffset += int64(wN)

				m.mu.Lock()
				task.DownloadedBytes = currentOffset
				task.BytesWritten = currentOffset
				if task.TotalBytes > 0 {
					task.Progress = (float64(currentOffset) / float64(task.TotalBytes)) * 100.0
					task.Percent = task.Progress
				}
				task.UpdatedAt = time.Now()
				m.mu.Unlock()
			}
			if rErr != nil {
				readErr = rErr
				break
			}
		}
		_ = resp.Body.Close()

		if readErr != nil && readErr != io.EOF {
			m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("chunk read error: %v", readErr))
			return
		}

		// If server sent full body (200 OK) and reached EOF, download is finished
		if resp.StatusCode == http.StatusOK && readErr == io.EOF {
			break
		}

		// If no bytes written in this chunk, break to avoid infinite loop
		if chunkWritten == 0 {
			break
		}
	}

	// 6. Close file handle prior to metadata tagging & atomic promotion
	_ = out.Close()

	// 7. Inject metadata, high-res artwork, and auto-index into SQLite
	m.updateTaskStatus(task, StatusTagging, "")
	if err := m.tagAndRegisterTrack(ctx, task, partPath); err != nil {
		// Log warning but proceed with atomic promotion
		fmt.Printf("[DOWNLOADER] Warning: metadata tagging encountered: %v\n", err)
	}

	// 8. Atomic promotion from .part to final destination
	if err := os.Rename(partPath, task.LocalPath); err != nil {
		m.updateTaskStatus(task, StatusFailed, fmt.Sprintf("atomic file rename failed: %v", err))
		return
	}

	// 9. Mark task completed
	m.mu.Lock()
	task.Status = StatusCompleted
	task.Progress = 100.0
	task.Percent = 100.0
	task.UpdatedAt = time.Now()
	m.mu.Unlock()
}

func (m *Manager) updateTaskStatus(task *DownloadTask, status, errMsg string) {
	m.mu.Lock()
	defer m.mu.Unlock()

	task.Status = status
	task.Error = errMsg
	task.UpdatedAt = time.Now()
}

// tagAndRegisterTrack delegates to tagger for cover art harvesting and SQLite registration.
func (m *Manager) tagAndRegisterTrack(ctx context.Context, task *DownloadTask, targetFile string) error {
	return InjectMetadataAndIndex(ctx, targetFile, task, m.repo, m.httpClient)
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

		// Ignore incomplete .part files
		if strings.HasSuffix(e.Name(), ".part") {
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
				Album:       SourceFolderDownloads,
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
