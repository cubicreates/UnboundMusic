/*
 * Package: server
 * File: handlers_storage.go
 * Purpose: Local device filesystem scanning, in-place virtual indexing, audio classification, and track ingestion HTTP handlers.
 * Subsystem: Storage & In-Place Virtual Indexer
 */

package server

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/fingerprint"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/storage"
)

// handleStorageTree inspects the Unbound/ directory structure.
func (s *Server) handleStorageTree(w http.ResponseWriter, r *http.Request) {
	tree, err := s.provisioner.GetTree()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, tree)
}

// handleStorageIndex runs non-destructive in-place virtual indexing on a folder.
func (s *Server) handleStorageIndex(w http.ResponseWriter, r *http.Request) {
	type IndexReq struct {
		DirectoryPath string `json:"directory_path"`
	}
	var req IndexReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.DirectoryPath == "" {
		req.DirectoryPath = s.cfg.LibraryRoot
	}
	if req.DirectoryPath == "" {
		req.DirectoryPath = os.TempDir()
	}

	summary, err := s.indexer.IndexInPlace(r.Context(), req.DirectoryPath)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, summary)
}

// handleStorageConsolidate consolidates loose downloads and copies WhatsApp audio.
func (s *Server) handleStorageConsolidate(w http.ResponseWriter, r *http.Request) {
	type ConsolidateReq struct {
		SourceDir string `json:"source_dir"`
	}
	var req ConsolidateReq
	_ = json.NewDecoder(r.Body).Decode(&req)

	tree, _ := s.provisioner.GetTree()
	targetMusic := os.TempDir()
	if tree != nil {
		targetMusic = tree.MusicPath
	}

	sourceDir := req.SourceDir
	if sourceDir == "" {
		sourceDir = s.cfg.LibraryRoot
	}
	if sourceDir == "" {
		sourceDir = os.TempDir()
	}

	summary, err := s.indexer.ConsolidateLibrary(r.Context(), sourceDir, targetMusic)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, summary)
}

// handleStorageClassify evaluates a filepath against WhatsApp COPY vs Downloads MOVE rules and 30s voice memo filtering.
func (s *Server) handleStorageClassify(w http.ResponseWriter, r *http.Request) {
	type ClassifyReq struct {
		FilePath   string `json:"file_path"`
		DurationMs int64  `json:"duration_ms"`
	}
	var req ClassifyReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.FilePath == "" {
		req.FilePath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-20260901-WA0001.opus"
	}
	if req.DurationMs == 0 {
		req.DurationMs = 15000 // 15 seconds
	}

	meta := &fingerprint.AudioMetadata{
		FilePath:   req.FilePath,
		DurationMs: req.DurationMs,
		Extension:  filepath.Ext(req.FilePath),
	}

	class := fingerprint.ClassifyAudio(meta)
	isChat := fingerprint.IsProtectedChatMedia(req.FilePath)
	action := "MOVED"
	if isChat {
		action = "COPIED (Non-Destructive for WhatsApp/Chat)"
	}
	if !class.IsMusic {
		action = "IGNORED (Dropped: Voice Memo / Sound Effect < 30s)"
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"file_path":         req.FilePath,
		"duration_ms":       req.DurationMs,
		"is_music":          class.IsMusic,
		"rejection_reason":  class.Reason,
		"is_protected_chat": isChat,
		"ingestion_rule":    action,
	})
}

// handleStorageSearch queries indexed local audio tracks using SQLite FTS5 BM25 ranking.
func (s *Server) handleStorageSearch(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		writeError(w, http.StatusBadRequest, "parameter 'q' is required")
		return
	}

	limit := 50
	if lStr := r.URL.Query().Get("limit"); lStr != "" {
		if l, err := strconv.Atoi(lStr); err == nil && l > 0 {
			limit = l
		}
	}

	tracks, err := s.repo.SearchTracksFTS(r.Context(), q, limit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("FTS search failed: %v", err))
		return
	}
	if tracks == nil {
		tracks = []*models.LocalTrack{}
	}
	writeJSON(w, http.StatusOK, tracks)
}

// handleStorageScan initiates a POSIX storage crawl over the provided paths and returns indexed metrics.
func (s *Server) handleStorageScan(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	type ScanReq struct {
		Paths []string `json:"paths"`
	}
	var req ScanReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("invalid json payload: %v", err))
		return
	}

	totalReport := &storage.ScanReport{}
	start := time.Now()

	for _, p := range req.Paths {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}

		lower := strings.ToLower(p)
		sourceFolder := "music"
		if strings.Contains(lower, "whatsapp") {
			sourceFolder = "whatsapp"
		} else if strings.Contains(lower, "telegram") {
			sourceFolder = "telegram"
		} else if strings.Contains(lower, "download") {
			sourceFolder = "downloads"
		}

		rep, err := storage.ScanDirectory(r.Context(), s.repo, p, sourceFolder)
		if err != nil {
			continue
		}
		totalReport.ScannedFiles += rep.ScannedFiles
		totalReport.AudioFilesFound += rep.AudioFilesFound
		totalReport.NewTracksIndexed += rep.NewTracksIndexed
		totalReport.UnchangedTracks += rep.UnchangedTracks
	}

	totalReport.ElapsedMs = time.Since(start).Milliseconds()

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":             "ok",
		"scanned_files":      totalReport.ScannedFiles,
		"audio_files_found":  totalReport.AudioFilesFound,
		"new_tracks_indexed": totalReport.NewTracksIndexed,
		"unchanged_tracks":   totalReport.UnchangedTracks,
		"elapsed_ms":         totalReport.ElapsedMs,
	})
}

// handleStorageTracks returns indexed local tracks filtered by source folder or all if unspecified.
func (s *Server) handleStorageTracks(w http.ResponseWriter, r *http.Request) {
	source := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("source")))
	var tracks []models.LocalTrack
	var err error

	if source == "" || source == "all" {
		tracks, err = s.repo.GetAllLocalTracks(r.Context())
	} else {
		tracks, err = s.repo.GetLocalTracksBySource(r.Context(), source)
	}

	if err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to retrieve tracks: %v", err))
		return
	}
	if tracks == nil {
		tracks = []models.LocalTrack{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
	})
}

// handleStorageIngestBatch ingests an array of MediaStore-indexed audio tracks directly into the SQLite database.
func (s *Server) handleStorageIngestBatch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	type IngestReq struct {
		Tracks []*models.LocalTrack `json:"tracks"`
	}

	var req IngestReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("invalid json payload: %v", err))
		return
	}

	inserted := 0
	for _, tr := range req.Tracks {
		if tr == nil || tr.FilePath == "" {
			continue
		}
		if tr.ID == "" {
			tr.ID = fmt.Sprintf("%x", sha256.Sum256([]byte(tr.FilePath)))[:16]
		}
		if tr.DateIndexed == 0 {
			tr.DateIndexed = time.Now().Unix()
		}
		if tr.MTime == 0 {
			tr.MTime = tr.DateIndexed
		}
		if tr.IdentificationMethod == "" {
			tr.IdentificationMethod = "mediastore_hybrid"
		}
		if tr.Confidence == 0 {
			tr.Confidence = 1.0
		}
		if err := s.repo.UpsertLocalTrack(r.Context(), tr); err == nil {
			inserted++
		}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "success",
		"ingested": inserted,
		"total":    len(req.Tracks),
	})
}

// handleScan initiates local storage scanning.
func (s *Server) handleScan(w http.ResponseWriter, r *http.Request) {
	type ScanRequest struct {
		DirectoryPath string `json:"directory_path"`
	}

	var req ScanRequest
	if r.Body != nil {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.DirectoryPath == "" {
		req.DirectoryPath = s.cfg.LibraryRoot
	}
	if req.DirectoryPath == "" {
		req.DirectoryPath = os.TempDir()
	}

	summary, err := fingerprint.ScanDirectory(context.Background(), req.DirectoryPath, 8)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, summary)
}

// handleFingerprintIdentify identifies an untagged local audio file using AcoustID + Chromaprint.
func (s *Server) handleFingerprintIdentify(w http.ResponseWriter, r *http.Request) {
	type IdentifyReq struct {
		FilePath   string `json:"file_path"`
		FpcalcPath string `json:"fpcalc_path,omitempty"`
	}

	var req IdentifyReq
	if r.Method == http.MethodPost && r.Body != nil {
		_ = json.NewDecoder(r.Body).Decode(&req)
	} else {
		req.FilePath = r.URL.Query().Get("file_path")
		req.FpcalcPath = r.URL.Query().Get("fpcalc_path")
	}

	if strings.TrimSpace(req.FilePath) == "" {
		writeError(w, http.StatusBadRequest, "parameter 'file_path' is required")
		return
	}

	track, err := fingerprint.IngestUntaggedFileWithAI(r.Context(), s.repo, req.FpcalcPath, req.FilePath, s.aiRunner)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, track)
}
