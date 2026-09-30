/*
 * Package: server
 * File: handlers_downloader.go
 * Purpose: Track stream downloading, progress tracking, pausing, resuming, and disk management HTTP handlers.
 * Subsystem: Downloader & Storage
 */

package server

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/downloader"
)

// handleDownloadStart downloads a track directly to Unbound/Downloads/.
func (s *Server) handleDownloadStart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}
	if s.downloader == nil {
		writeError(w, http.StatusServiceUnavailable, "downloader not initialized")
		return
	}

	type DownloadReq struct {
		VideoID    string `json:"video_id"`
		TrackID    string `json:"track_id"`
		Title      string `json:"title"`
		Artist     string `json:"artist"`
		Album      string `json:"album"`
		ArtworkURL string `json:"artwork_url"`
		StreamURL  string `json:"stream_url"`
	}
	var req DownloadReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	vid := req.VideoID
	if vid == "" {
		vid = req.TrackID
	}
	if vid == "" && req.Title == "" {
		writeError(w, http.StatusBadRequest, "video_id or title is required")
		return
	}

	var task *downloader.DownloadTask
	var err error
	if req.StreamURL != "" && strings.HasPrefix(req.StreamURL, "http") && !strings.Contains(req.StreamURL, "127.0.0.1") {
		task, err = s.downloader.StartDownloadWithURL(r.Context(), vid, req.Title, req.Artist, req.Album, req.ArtworkURL, req.StreamURL)
	} else {
		task, err = s.downloader.StartDownload(r.Context(), vid, req.Title, req.Artist, req.Album, req.ArtworkURL)
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, task)
}

// handleDownloadStatus returns the progress and status of a download task.
func (s *Server) handleDownloadStatus(w http.ResponseWriter, r *http.Request) {
	if s.downloader == nil {
		writeError(w, http.StatusServiceUnavailable, "downloader not initialized")
		return
	}
	videoID := r.URL.Query().Get("video_id")
	if videoID == "" {
		videoID = r.URL.Query().Get("track_id")
	}
	if strings.TrimSpace(videoID) == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}
	task := s.downloader.GetTask(videoID)
	if task == nil {
		writeError(w, http.StatusNotFound, "download task not found")
		return
	}
	writeJSON(w, http.StatusOK, task)
}

// handleDownloadActive returns all active, queued, paused, or completed tasks.
func (s *Server) handleDownloadActive(w http.ResponseWriter, r *http.Request) {
	if s.downloader == nil {
		writeJSON(w, http.StatusOK, []*downloader.DownloadTask{})
		return
	}
	tasks := s.downloader.ListActiveTasks()
	if tasks == nil {
		tasks = []*downloader.DownloadTask{}
	}
	writeJSON(w, http.StatusOK, tasks)
}

// handleDownloadPause pauses an active download.
func (s *Server) handleDownloadPause(w http.ResponseWriter, r *http.Request) {
	if s.downloader == nil {
		writeError(w, http.StatusServiceUnavailable, "downloader not initialized")
		return
	}
	var req struct {
		VideoID string `json:"video_id"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if strings.TrimSpace(req.VideoID) == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}
	if err := s.downloader.PauseDownload(req.VideoID); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "PAUSED", "video_id": req.VideoID})
}

// handleDownloadResume resumes a paused download.
func (s *Server) handleDownloadResume(w http.ResponseWriter, r *http.Request) {
	if s.downloader == nil {
		writeError(w, http.StatusServiceUnavailable, "downloader not initialized")
		return
	}
	var req struct {
		VideoID string `json:"video_id"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if strings.TrimSpace(req.VideoID) == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}
	if err := s.downloader.ResumeDownload(r.Context(), req.VideoID); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "RESUMED", "video_id": req.VideoID})
}

// handleDownloadCancel cancels an ongoing download and cleans up.
func (s *Server) handleDownloadCancel(w http.ResponseWriter, r *http.Request) {
	if s.downloader == nil {
		writeError(w, http.StatusServiceUnavailable, "downloader not initialized")
		return
	}
	var req struct {
		VideoID string `json:"video_id"`
		TrackID string `json:"track_id"`
		Title   string `json:"title"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	id := req.VideoID
	if id == "" {
		id = req.TrackID
	}
	if id == "" {
		id = req.Title
	}
	if id == "" {
		id = r.URL.Query().Get("video_id")
	}
	if id == "" {
		id = r.URL.Query().Get("id")
	}

	if strings.TrimSpace(id) != "" {
		_ = s.downloader.CancelDownload(id)
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "CANCELLED", "video_id": id})
}

// handleDownloadDelete removes a downloaded track from disk and database.
func (s *Server) handleDownloadDelete(w http.ResponseWriter, r *http.Request) {
	if s.downloader == nil {
		writeError(w, http.StatusServiceUnavailable, "downloader not initialized")
		return
	}
	var req struct {
		VideoID    string `json:"video_id"`
		DeleteFile bool   `json:"delete_file"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if strings.TrimSpace(req.VideoID) == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}
	if err := s.downloader.DeleteDownload(r.Context(), req.VideoID, req.DeleteFile); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "DELETED", "video_id": req.VideoID})
}

// handleDownloadList lists all physical tracks inside Unbound/Downloads/.
func (s *Server) handleDownloadList(w http.ResponseWriter, r *http.Request) {
	tracks, err := s.downloader.ListDownloadedFiles()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
		"count":  len(tracks),
	})
}
