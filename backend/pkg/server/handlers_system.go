package server

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/events"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
)

var serverStartTime = time.Now()

// handlePing returns immediate 200 OK pong response for low-latency watchdog heartbeats.
func (s *Server) handlePing(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"pong":      true,
		"timestamp": time.Now().UnixMilli(),
	})
}

// handleHealth returns a lightweight health probe response for watchdogs.
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "HEALTHY",
		"uptime":  time.Since(serverStartTime).String(),
		"version": "1.0.0-FOSS",
	})
}

// handleStatus returns comprehensive system telemetry including memory usage and storage capacity.
func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	storageStatus, _ := gatekeeper.CheckStorageCapacity(s.cfg.AppStorageRoot)

	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	payload := map[string]interface{}{
		"status":           "ONLINE",
		"engine_version":   "1.0.0-FOSS",
		"storage":          storageStatus,
		"goroutines":       runtime.NumGoroutine(),
		"allocated_ram_mb": float64(m.Alloc) / (1024 * 1024),
		"uptime_seconds":   time.Since(serverStartTime).Seconds(),
	}

	writeJSON(w, http.StatusOK, payload)
}

// handleEvents streams real-time Server-Sent Events (SSE) to connected clients.
func (s *Server) handleEvents(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		writeError(w, http.StatusInternalServerError, "streaming unsupported by client connection")
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if s.events == nil {
		s.events = events.NewEventBus(128)
	}

	ch := s.events.Subscribe()
	defer s.events.Unsubscribe(ch)

	// Send initial connection handshake
	initEvt := events.Event{
		Type:      "connected",
		Payload:   map[string]string{"status": "READY"},
		Timestamp: time.Now(),
	}
	_, _ = w.Write(initEvt.SSEMessage())
	flusher.Flush()

	ctx := r.Context()
	for {
		select {
		case <-ctx.Done():
			return
		case evt, open := <-ch:
			if !open {
				return
			}
			_, err := w.Write(evt.SSEMessage())
			if err != nil {
				return
			}
			flusher.Flush()
		}
	}
}

// handleUnpackPayload unpacks .tar.zst payload archives into the destination directory.
func (s *Server) handleUnpackPayload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req struct {
		ArchivePath string `json:"archive_path"`
		DestDir     string `json:"dest_dir"`
	}

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid JSON body")
		return
	}

	if req.ArchivePath == "" || req.DestDir == "" {
		writeError(w, http.StatusBadRequest, "archive_path and dest_dir are required")
		return
	}

	data, err := os.ReadFile(req.ArchivePath)
	if err != nil {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("failed to read archive: %v", err))
		return
	}

	manifest, err := gatekeeper.DecompressZstdTarStream(data, req.DestDir)
	if err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("decompression failed: %v", err))
		return
	}

	// Clean up archive file to reclaim storage space
	_ = os.Remove(req.ArchivePath)

	writeJSON(w, http.StatusOK, manifest)
}

const maxWallpaperBackupBytes = 32 * 1024 * 1024 // 32 MiB hard cap

// handleWallpaperBackup stores (POST) or retrieves (GET) the user's original lock-screen wallpaper in SQLite.
func (s *Server) handleWallpaperBackup(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		metaOnly := strings.EqualFold(r.URL.Query().Get("meta"), "1") ||
			strings.EqualFold(r.URL.Query().Get("meta"), "true")
		if metaOnly {
			meta, err := s.repo.GetWallpaperBackupMeta(r.Context())
			if err != nil {
				writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to query wallpaper backup meta: %v", err))
				return
			}
			writeJSON(w, http.StatusOK, meta)
			return
		}

		blob, meta, err := s.repo.GetWallpaperBackup(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to read wallpaper backup: %v", err))
			return
		}
		if meta == nil || !meta.HasBackup || len(blob) == 0 {
			writeError(w, http.StatusNotFound, "no original lockscreen wallpaper backup stored")
			return
		}

		mimeType := meta.MimeType
		if mimeType == "" {
			mimeType = "application/octet-stream"
		}
		w.Header().Set("Content-Type", mimeType)
		w.Header().Set("Content-Length", strconv.Itoa(len(blob)))
		w.Header().Set("X-Wallpaper-Source", meta.SourceFlag)
		w.Header().Set("X-Lock-Wallpaper-Id", strconv.FormatInt(meta.LockWallpaperID, 10))
		w.Header().Set("X-System-Wallpaper-Id", strconv.FormatInt(meta.SystemWallpaperID, 10))
		w.Header().Set("X-Wallpaper-Sha256", meta.SHA256Hex)
		w.Header().Set("Cache-Control", "no-store")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(blob)

	case http.MethodPost:
		data, err := io.ReadAll(io.LimitReader(r.Body, maxWallpaperBackupBytes+1))
		if err != nil {
			writeError(w, http.StatusBadRequest, fmt.Sprintf("failed to read wallpaper bytes: %v", err))
			return
		}
		if len(data) == 0 {
			writeError(w, http.StatusBadRequest, "wallpaper payload cannot be empty")
			return
		}
		if len(data) > maxWallpaperBackupBytes {
			writeError(w, http.StatusRequestEntityTooLarge, "wallpaper payload exceeds 32 MiB limit")
			return
		}

		sourceFlag := strings.TrimSpace(r.URL.Query().Get("source_flag"))
		if sourceFlag == "" {
			sourceFlag = strings.TrimSpace(r.Header.Get("X-Wallpaper-Source"))
		}
		if sourceFlag == "" {
			sourceFlag = "lock"
		}

		lockID := parseWallpaperIDParam(r.URL.Query().Get("lock_id"), r.Header.Get("X-Lock-Wallpaper-Id"))
		systemID := parseWallpaperIDParam(r.URL.Query().Get("system_id"), r.Header.Get("X-System-Wallpaper-Id"))
		mimeType := strings.TrimSpace(r.Header.Get("Content-Type"))

		meta, err := s.repo.SaveWallpaperBackup(r.Context(), data, mimeType, sourceFlag, lockID, systemID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to save wallpaper backup: %v", err))
			return
		}
		writeJSON(w, http.StatusOK, meta)

	default:
		writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
	}
}

// handleWallpaperBackupIDs updates the tracked Android WallpaperManager IDs without rewriting image_blob.
func (s *Server) handleWallpaperBackupIDs(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		return
	}

	var req struct {
		LockWallpaperID   int64 `json:"lock_wallpaper_id"`
		SystemWallpaperID int64 `json:"system_wallpaper_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid JSON body")
		return
	}

	if err := s.repo.UpdateWallpaperBackupIDs(r.Context(), req.LockWallpaperID, req.SystemWallpaperID); err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to update wallpaper IDs: %v", err))
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"status": "updated"})
}

func parseWallpaperIDParam(queryVal, headerVal string) int64 {
	raw := strings.TrimSpace(queryVal)
	if raw == "" {
		raw = strings.TrimSpace(headerVal)
	}
	if raw == "" {
		return -1
	}
	parsed, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return -1
	}
	return parsed
}
