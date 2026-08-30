/*
 * Package: server
 * File: server.go
 * Purpose: Embedded localhost REST / IPC daemon server exposing Unbound Music capabilities, offline recommender, and P2P sync to Kotlin/Compose UI.
 * Subsystem: Localhost Daemon & IPC
 * Concurrency: Standard Go HTTP server managing concurrent client connections via goroutines.
 */

package server

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/aligner"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/fingerprint"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/p2p"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
	"github.com/cubicreates/unbound-engine/pkg/router"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// Config defines network listening options for the local engine server.
type Config struct {
	Port           int    `json:"port"`
	DatabasePath   string `json:"database_path"`
	LibraryRoot    string `json:"library_root"`
	AppStorageRoot string `json:"app_storage_root"`
}

// DefaultConfig provides standard localhost defaults.
func DefaultConfig() Config {
	return Config{
		Port:           45731,
		DatabasePath:   "",
		LibraryRoot:    "",
		AppStorageRoot: "",
	}
}

// Server coordinates HTTP endpoints and engine subsystems.
type Server struct {
	cfg          Config
	httpServer   *http.Server
	db           *database.DB
	repo         *database.Repository
	ytClient     *ytmusic.Client
	geniusClient *genius.Client
	aligner      *aligner.ForcedAligner
	router       *router.Router
	recommender  *recommender.Engine
	discovery    *p2p.Discovery
}

// NewServer initializes all engine subsystems and HTTP routes.
func NewServer(cfg Config) (*Server, error) {
	if cfg.Port <= 0 {
		cfg.Port = 45731
	}

	db, err := database.Open(cfg.DatabasePath)
	if err != nil {
		return nil, fmt.Errorf("failed to open database for server: %w", err)
	}

	repo := database.NewRepository(db)
	ytClient := ytmusic.NewClient()
	geniusClient := genius.NewClient()
	forcdAligner := aligner.NewForcedAligner()
	playbackRouter := router.NewRouter(ytClient, repo)
	recEngine := recommender.NewEngine(repo)
	p2pDiscovery := p2p.NewDiscovery("node_local", "Unbound Desktop", cfg.Port)

	s := &Server{
		cfg:          cfg,
		db:           db,
		repo:         repo,
		ytClient:     ytClient,
		geniusClient: geniusClient,
		aligner:      forcdAligner,
		router:       playbackRouter,
		recommender:  recEngine,
		discovery:    p2pDiscovery,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/status", s.handleStatus)
	mux.HandleFunc("/api/v1/search", s.handleSearch)
	mux.HandleFunc("/api/v1/stream", s.handleStream)
	mux.HandleFunc("/api/v1/lyrics", s.handleLyrics)
	mux.HandleFunc("/api/v1/scan", s.handleScan)
	mux.HandleFunc("/api/v1/recommend", s.handleRecommend)
	mux.HandleFunc("/api/v1/peers", s.handlePeers)

	s.httpServer = &http.Server{
		Addr:         fmt.Sprintf("127.0.0.1:%d", cfg.Port),
		Handler:      corsMiddleware(mux),
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	return s, nil
}

// Start begins listening on the configured localhost address.
func (s *Server) Start() error {
	return s.httpServer.ListenAndServe()
}

// Shutdown cleanly closes active listeners and database connections.
func (s *Server) Shutdown(ctx context.Context) error {
	_ = s.httpServer.Shutdown(ctx)
	if s.db != nil {
		_ = s.db.Close()
	}
	return nil
}

// handleStatus returns engine diagnostic information, memory stats, and storage gatekeeper mode.
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
	}

	writeJSON(w, http.StatusOK, payload)
}

// handleSearch handles catalog search queries.
func (s *Server) handleSearch(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if strings.TrimSpace(query) == "" {
		writeError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}

	tracks, err := s.ytClient.Search(r.Context(), query)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	go func() {
		for _, t := range tracks {
			_ = s.repo.SaveTrack(context.Background(), &t)
		}
	}()

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":  query,
		"count":  len(tracks),
		"tracks": tracks,
	})
}

// handleStream handles zero-data hybrid stream resolution.
func (s *Server) handleStream(w http.ResponseWriter, r *http.Request) {
	trackID := r.URL.Query().Get("id")
	title := r.URL.Query().Get("title")
	artist := r.URL.Query().Get("artist")

	if trackID == "" && title == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' or 'title' is required")
		return
	}

	stream, err := s.router.ResolvePlayback(r.Context(), trackID, title, artist)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, stream)
}

// handleLyrics handles uncensored lyrics resolution with on-device forced alignment and SQLite caching.
func (s *Server) handleLyrics(w http.ResponseWriter, r *http.Request) {
	trackID := r.URL.Query().Get("id")
	title := r.URL.Query().Get("title")
	artist := r.URL.Query().Get("artist")
	durationStr := r.URL.Query().Get("duration")

	var durationMs int64 = 180000
	if durationStr != "" {
		if val, err := strconv.ParseInt(durationStr, 10, 64); err == nil && val > 0 {
			durationMs = val
		}
	}

	if trackID == "" && title == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' or 'title' is required")
		return
	}

	// 1. Check SQLite Cache First
	if trackID != "" {
		cached, err := s.repo.GetLyrics(r.Context(), trackID)
		if err == nil && cached != nil && len(cached.Lines) > 0 {
			writeJSON(w, http.StatusOK, cached)
			return
		}
	}

	// 2. Fetch Genius Verified Uncensored Lyrics
	hit, err := s.geniusClient.SearchSong(r.Context(), title, artist)
	if err == nil && hit != nil {
		plainPayload, err := s.geniusClient.FetchLyrics(r.Context(), hit)
		if err == nil && len(plainPayload.Lines) > 0 {
			aligned, err := s.aligner.AlignLyrics(trackID, hit.Title, hit.Artist, plainPayload.PlainLyrics, durationMs)
			if err == nil {
				_ = s.repo.SaveLyrics(r.Context(), aligned)
				writeJSON(w, http.StatusOK, aligned)
				return
			}
		}
	}

	// 3. Fallback to LRCLIB
	lrclibPayload, err := s.geniusClient.FetchLRCLIBSynced(r.Context(), title, artist, 0)
	if err == nil && len(lrclibPayload.Lines) > 0 {
		lrclibPayload.TrackID = trackID
		_ = s.repo.SaveLyrics(r.Context(), lrclibPayload)
		writeJSON(w, http.StatusOK, lrclibPayload)
		return
	}

	writeError(w, http.StatusNotFound, "lyrics not found")
}

// handleRecommend generates an offline smart radio mix.
func (s *Server) handleRecommend(w http.ResponseWriter, r *http.Request) {
	trackID := r.URL.Query().Get("id")
	countStr := r.URL.Query().Get("limit")

	count := 10
	if countStr != "" {
		if val, err := strconv.Atoi(countStr); err == nil && val > 0 {
			count = val
		}
	}

	mix, err := s.recommender.GenerateRadioMix(r.Context(), trackID, count)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, mix)
}

// handlePeers returns active P2P nodes on local Wi-Fi.
func (s *Server) handlePeers(w http.ResponseWriter, r *http.Request) {
	peers := s.discovery.GetActivePeers()
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"peer_count": len(peers),
		"peers":      peers,
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

// corsMiddleware adds permissive headers for local IPC and web frontend callers.
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
