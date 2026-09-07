/*
 * Package: main
 * File: handlers_downloader.go
 * Purpose: REST API controllers for physical audio stream downloading, pause/resume, status monitoring, and storage management.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Thread-safe HTTP handlers delegating to pkg/downloader.Manager.
 */

package main

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/downloader"
)

// StartDownloadPayload represents the payload to initiate a track download.
type StartDownloadPayload struct {
	VideoID    string `json:"video_id"`
	Title      string `json:"title"`
	Artist     string `json:"artist"`
	Album      string `json:"album"`
	ArtworkURL string `json:"artwork_url"`
}

// DownloadActionPayload represents a request to pause, resume, or cancel a download.
type DownloadActionPayload struct {
	VideoID string `json:"video_id"`
}

// DeleteDownloadPayload represents a request to purge a downloaded track.
type DeleteDownloadPayload struct {
	VideoID    string `json:"video_id"`
	DeleteFile bool   `json:"delete_file"`
}

// HandleStartDownload triggers an asynchronous chunked background download.
func (d *Daemon) HandleStartDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeError(w, http.StatusServiceUnavailable, "Download manager not initialized")
		return
	}

	var req StartDownloadPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "Invalid JSON request payload")
		return
	}

	if strings.TrimSpace(req.VideoID) == "" && strings.TrimSpace(req.Title) == "" {
		d.writeError(w, http.StatusBadRequest, "video_id or title is required")
		return
	}

	task, err := d.downloadMgr.StartDownload(r.Context(), req.VideoID, req.Title, req.Artist, req.Album, req.ArtworkURL)
	if err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, task)
}

// HandleGetDownloadStatus queries the progress of a specific download task.
func (d *Daemon) HandleGetDownloadStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeError(w, http.StatusServiceUnavailable, "Download manager not initialized")
		return
	}

	videoID := r.URL.Query().Get("video_id")
	if strings.TrimSpace(videoID) == "" {
		d.writeError(w, http.StatusBadRequest, "video_id parameter is required")
		return
	}

	task := d.downloadMgr.GetTask(videoID)
	if task == nil {
		d.writeError(w, http.StatusNotFound, "download task not found")
		return
	}

	d.writeJSON(w, http.StatusOK, task)
}

// HandleGetActiveDownloads returns all active, queued, and completed downloads.
func (d *Daemon) HandleGetActiveDownloads(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeJSON(w, http.StatusOK, []*downloader.DownloadTask{})
		return
	}

	tasks := d.downloadMgr.ListActiveTasks()
	if tasks == nil {
		tasks = []*downloader.DownloadTask{}
	}

	d.writeJSON(w, http.StatusOK, tasks)
}

// HandlePauseDownload suspends an active download task.
func (d *Daemon) HandlePauseDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeError(w, http.StatusServiceUnavailable, "Download manager not initialized")
		return
	}

	var req DownloadActionPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "Invalid JSON request payload")
		return
	}

	if strings.TrimSpace(req.VideoID) == "" {
		d.writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if err := d.downloadMgr.PauseDownload(req.VideoID); err != nil {
		d.writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"success":  true,
		"video_id": req.VideoID,
		"message":  "download paused",
	})
}

// HandleResumeDownload resumes a paused download task.
func (d *Daemon) HandleResumeDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeError(w, http.StatusServiceUnavailable, "Download manager not initialized")
		return
	}

	var req DownloadActionPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "Invalid JSON request payload")
		return
	}

	if strings.TrimSpace(req.VideoID) == "" {
		d.writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if err := d.downloadMgr.ResumeDownload(r.Context(), req.VideoID); err != nil {
		d.writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"success":  true,
		"video_id": req.VideoID,
		"message":  "download resumed",
	})
}

// HandleCancelDownload cancels a download and removes partial artifacts.
func (d *Daemon) HandleCancelDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeError(w, http.StatusServiceUnavailable, "Download manager not initialized")
		return
	}

	var req DownloadActionPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "Invalid JSON request payload")
		return
	}

	if strings.TrimSpace(req.VideoID) == "" {
		d.writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if err := d.downloadMgr.CancelDownload(req.VideoID); err != nil {
		d.writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"success":  true,
		"video_id": req.VideoID,
		"message":  "download cancelled",
	})
}

// HandleDeleteDownload purges a downloaded track from disk and SQLite local_tracks.
func (d *Daemon) HandleDeleteDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	if d.downloadMgr == nil {
		d.writeError(w, http.StatusServiceUnavailable, "Download manager not initialized")
		return
	}

	var req DeleteDownloadPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		d.writeError(w, http.StatusBadRequest, "Invalid JSON request payload")
		return
	}

	if strings.TrimSpace(req.VideoID) == "" {
		d.writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}

	if err := d.downloadMgr.DeleteDownload(r.Context(), req.VideoID, req.DeleteFile); err != nil {
		d.writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	d.writeJSON(w, http.StatusOK, map[string]interface{}{
		"success":  true,
		"video_id": req.VideoID,
		"message":  "download deleted",
	})
}
