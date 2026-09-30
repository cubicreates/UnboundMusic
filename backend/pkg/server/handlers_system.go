package server

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"runtime"
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
