/*
 * Package: downloader
 * File: worker_test.go
 * Purpose: Comprehensive unit tests for HTTP Range chunked download worker, resume, pause, cancel, and atomic file assembly.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Standard Go testing framework.
 */

package downloader

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"
)

// setupMockAudioServer creates a test HTTP server supporting HTTP Range requests with optional per-request delay.
func setupMockAudioServer(data []byte, delay time.Duration) *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if delay > 0 {
			time.Sleep(delay)
		}
		w.Header().Set("Accept-Ranges", "bytes")
		w.Header().Set("Content-Type", "audio/opus")

		rangeHeader := r.Header.Get("Range")
		if rangeHeader == "" {
			w.Header().Set("Content-Length", strconv.Itoa(len(data)))
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(data)
			return
		}

		// Parse Range: bytes=start-end
		if !strings.HasPrefix(rangeHeader, "bytes=") {
			http.Error(w, "invalid range header", http.StatusRequestedRangeNotSatisfiable)
			return
		}

		parts := strings.Split(strings.TrimPrefix(rangeHeader, "bytes="), "-")
		start, err := strconv.ParseInt(parts[0], 10, 64)
		if err != nil || start >= int64(len(data)) {
			http.Error(w, "out of range", http.StatusRequestedRangeNotSatisfiable)
			return
		}

		end := int64(len(data) - 1)
		if len(parts) > 1 && parts[1] != "" {
			parsedEnd, err := strconv.ParseInt(parts[1], 10, 64)
			if err == nil && parsedEnd < end {
				end = parsedEnd
			}
		}

		contentLength := end - start + 1
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, len(data)))
		w.Header().Set("Content-Length", strconv.FormatInt(contentLength, 10))
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write(data[start : end+1])
	}))
}

func TestChunkedWorker_FullDownload(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), fmt.Sprintf("unbound_test_chunk_%d", time.Now().UnixNano()))
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	// Create 2.5 MB of dummy audio data (spans multiple 1 MB chunks)
	totalSize := int(2.5 * 1024 * 1024)
	mockData := make([]byte, totalSize)
	for i := 0; i < totalSize; i++ {
		mockData[i] = byte(i % 256)
	}

	server := setupMockAudioServer(mockData, 0)
	defer server.Close()

	mgr := NewManager(tempDir, nil)
	mgr.chunkSize = 1024 * 1024 // 1 MB chunk size for testing

	task, err := mgr.StartDownloadWithURL(context.Background(), "test_video_1", "Test Track", "Test Artist", "Test Album", "", server.URL)
	if err != nil {
		t.Fatalf("StartDownloadWithURL failed: %v", err)
	}

	// Wait for completion
	deadline := time.Now().Add(5 * time.Second)
	for {
		status := mgr.GetTask(task.VideoID)
		if status != nil && (status.Status == StatusCompleted || status.Status == StatusFailed) {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for download to complete")
		}
		time.Sleep(50 * time.Millisecond)
	}

	finalTask := mgr.GetTask(task.VideoID)
	if finalTask.Status != StatusCompleted {
		t.Fatalf("expected task status COMPLETED, got %s (err: %s)", finalTask.Status, finalTask.Error)
	}

	// Verify final file exists on disk and .part is removed
	if _, err := os.Stat(finalTask.LocalPath); err != nil {
		t.Fatalf("expected final file at %s, got error: %v", finalTask.LocalPath, err)
	}
	partPath := finalTask.LocalPath + ".part"
	if _, err := os.Stat(partPath); !os.IsNotExist(err) {
		t.Fatalf("expected .part file to be removed after completion, but it exists: %s", partPath)
	}

	// Verify file size matches original
	fi, err := os.Stat(finalTask.LocalPath)
	if err != nil {
		t.Fatalf("stat failed: %v", err)
	}
	if fi.Size() != int64(totalSize) {
		t.Fatalf("expected file size %d, got %d", totalSize, fi.Size())
	}
}

func TestChunkedWorker_PauseAndResume(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), fmt.Sprintf("unbound_test_pause_%d", time.Now().UnixNano()))
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	totalSize := 3 * 1024 * 1024 // 3 MB
	mockData := make([]byte, totalSize)
	for i := 0; i < totalSize; i++ {
		mockData[i] = byte(i % 256)
	}

	server := setupMockAudioServer(mockData, 40*time.Millisecond)
	defer server.Close()

	mgr := NewManager(tempDir, nil)
	mgr.chunkSize = 512 * 1024 // 512 KB chunks

	task, err := mgr.StartDownloadWithURL(context.Background(), "test_video_pause", "Pause Song", "Pause Artist", "Pause Album", "", server.URL)
	if err != nil {
		t.Fatalf("StartDownloadWithURL failed: %v", err)
	}

	// Wait until at least 512 KB is downloaded, then pause
	deadline := time.Now().Add(3 * time.Second)
	for {
		current := mgr.GetTask(task.VideoID)
		if current != nil && current.DownloadedBytes > 0 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for first bytes")
		}
		time.Sleep(20 * time.Millisecond)
	}

	err = mgr.PauseDownload(task.VideoID)
	if err != nil {
		t.Fatalf("PauseDownload failed: %v", err)
	}

	// Confirm task is paused
	time.Sleep(100 * time.Millisecond)
	pausedTask := mgr.GetTask(task.VideoID)
	if pausedTask.Status != StatusPaused {
		t.Fatalf("expected status PAUSED, got %s", pausedTask.Status)
	}

	partPath := pausedTask.LocalPath + ".part"
	fi, err := os.Stat(partPath)
	if err != nil {
		t.Fatalf("expected .part file to exist while paused: %v", err)
	}
	bytesWhilePaused := fi.Size()

	// Resume download
	err = mgr.ResumeDownload(context.Background(), task.VideoID)
	if err != nil {
		t.Fatalf("ResumeDownload failed: %v", err)
	}

	// Wait for completion
	deadline = time.Now().Add(5 * time.Second)
	for {
		current := mgr.GetTask(task.VideoID)
		if current != nil && (current.Status == StatusCompleted || current.Status == StatusFailed) {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for resumed download to complete")
		}
		time.Sleep(50 * time.Millisecond)
	}

	finalTask := mgr.GetTask(task.VideoID)
	if finalTask.Status != StatusCompleted {
		t.Fatalf("expected COMPLETED after resume, got %s: %s", finalTask.Status, finalTask.Error)
	}

	finalFi, err := os.Stat(finalTask.LocalPath)
	if err != nil {
		t.Fatalf("stat failed: %v", err)
	}
	if finalFi.Size() != int64(totalSize) {
		t.Fatalf("expected resumed total size %d, got %d (paused at %d)", totalSize, finalFi.Size(), bytesWhilePaused)
	}
}

func TestChunkedWorker_Cancel(t *testing.T) {
	tempDir := filepath.Join(os.TempDir(), fmt.Sprintf("unbound_test_cancel_%d", time.Now().UnixNano()))
	_ = os.MkdirAll(tempDir, 0755)
	defer os.RemoveAll(tempDir)

	totalSize := 4 * 1024 * 1024
	mockData := make([]byte, totalSize)

	server := setupMockAudioServer(mockData, 40*time.Millisecond)
	defer server.Close()

	mgr := NewManager(tempDir, nil)
	mgr.chunkSize = 256 * 1024

	task, err := mgr.StartDownloadWithURL(context.Background(), "test_video_cancel", "Cancel Song", "Cancel Artist", "Cancel Album", "", server.URL)
	if err != nil {
		t.Fatalf("StartDownloadWithURL failed: %v", err)
	}

	time.Sleep(50 * time.Millisecond)
	err = mgr.CancelDownload(task.VideoID)
	if err != nil {
		t.Fatalf("CancelDownload failed: %v", err)
	}

	cancelledTask := mgr.GetTask(task.VideoID)
	if cancelledTask.Status != StatusCancelled {
		t.Fatalf("expected status CANCELLED, got %s", cancelledTask.Status)
	}

	// Verify .part file is purged
	partPath := cancelledTask.LocalPath + ".part"
	removed := false
	for i := 0; i < 20; i++ {
		if _, err := os.Stat(partPath); os.IsNotExist(err) {
			removed = true
			break
		}
		time.Sleep(50 * time.Millisecond)
	}
	if !removed {
		t.Fatalf("expected .part file to be removed after cancel: %s", partPath)
	}
}
