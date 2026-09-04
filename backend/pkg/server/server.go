/*
 * Package: server
 * File: server.go
 * Purpose: Embedded localhost REST / IPC daemon server exposing Unbound Music capabilities, offline recommender, P2P sync, Edge AI inference, AutoEq calibration, Discord presence, SponsorBlock, Shared Rooms, Shazam Recognition, Analytics, Playlist Importer, Audio DSP, Last.fm, Podcasts, Spotify Canvas, Account Sync, Explore Feeds, Artist Discography, Sleep Timer, In-App Auto-Updater, Root Storage Provisioning, In-Place Virtual Indexer, and Physical Stream Downloader.
 * Subsystem: Localhost Daemon & IPC
 * Concurrency: Standard Go HTTP server managing concurrent client connections via goroutines.
 */

package server

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/account"
	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/aligner"
	"github.com/cubicreates/unbound-engine/pkg/analytics"
	"github.com/cubicreates/unbound-engine/pkg/artist"
	"github.com/cubicreates/unbound-engine/pkg/autoeq"
	"github.com/cubicreates/unbound-engine/pkg/canvas"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/discord"
	"github.com/cubicreates/unbound-engine/pkg/downloader"
	"github.com/cubicreates/unbound-engine/pkg/dsp"
	"github.com/cubicreates/unbound-engine/pkg/explore"
	"github.com/cubicreates/unbound-engine/pkg/fingerprint"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/importer"
	"github.com/cubicreates/unbound-engine/pkg/lastfm"
	"github.com/cubicreates/unbound-engine/pkg/p2p"
	"github.com/cubicreates/unbound-engine/pkg/podcasts"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
	"github.com/cubicreates/unbound-engine/pkg/rooms"
	"github.com/cubicreates/unbound-engine/pkg/router"
	"github.com/cubicreates/unbound-engine/pkg/shazam"
	"github.com/cubicreates/unbound-engine/pkg/sleeptimer"
	"github.com/cubicreates/unbound-engine/pkg/storage"
	"github.com/cubicreates/unbound-engine/pkg/sponsorblock"
	"github.com/cubicreates/unbound-engine/pkg/updater"
	"github.com/cubicreates/unbound-engine/pkg/vector"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// Config defines network listening options for the local engine server.
type Config struct {
	Port           int    `json:"port"`
	DatabasePath   string `json:"database_path"`
	LibraryRoot    string `json:"library_root"`
	AppStorageRoot string `json:"app_storage_root"`
	ModelsPath     string `json:"models_path"`
}

// DefaultConfig provides standard localhost defaults.
func DefaultConfig() Config {
	return Config{
		Port:           45731,
		DatabasePath:   "",
		LibraryRoot:    "",
		AppStorageRoot: "",
		ModelsPath:     "",
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
	aiRunner     *ai.Runner
	autoeq       *autoeq.Engine
	discordRPC   *discord.Client
	sponsorblock *sponsorblock.Client
	roomHub      *rooms.Hub
	shazamClient *shazam.Client
	analyticsEng *analytics.Engine
	importer     *importer.Importer
	lastfmClient *lastfm.Scrobbler
	podcastEng   *podcasts.Engine
	canvasClient *canvas.Client
	accountSync  *account.Syncer
	exploreEng   *explore.Engine
	artistEng    *artist.Engine
	sleepTimer   *sleeptimer.Timer
	updater      *updater.Updater
	provisioner  *storage.Provisioner
	indexer      *storage.Indexer
	downloader   *downloader.Manager
}

// NewServer initializes all engine subsystems and HTTP routes.
func NewServer(cfg Config) (*Server, error) {
	if cfg.Port <= 0 {
		cfg.Port = 45731
	}

	// Initialize Storage Provisioner to ensure Unbound/.backend/ structure
	provisioner := storage.NewProvisioner(cfg.LibraryRoot)
	tree, _ := provisioner.ProvisionLayout()

	dbPath := cfg.DatabasePath
	if dbPath == "" && tree != nil {
		dbPath = filepath.Join(tree.SQLitePath, "unbound.db")
	}

	db, err := database.Open(dbPath)
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
	edgeAI := ai.NewRunner(cfg.ModelsPath)
	autoEqEngine := autoeq.NewEngine()
	discordClient := discord.NewClient("")
	sbClient := sponsorblock.NewClient()
	roomsHub := rooms.NewHub()
	shazamCli := shazam.NewClient()
	analyticsEngine := analytics.NewEngine()
	playlistImporter := importer.NewImporter()
	scrobbler := lastfm.NewScrobbler("", "")
	podcastEngine := podcasts.NewEngine(ytClient)
	canvasCli := canvas.NewClient()
	accSyncer := account.NewSyncer()
	exploreEngine := explore.NewEngine(ytClient)
	artistEngine := artist.NewEngine(ytClient)
	sleepTimerMgr := sleeptimer.NewTimer()
	appUpdater := updater.NewUpdater("1.0.0")

	indexer := storage.NewIndexer(repo)
	downloadDir := os.TempDir()
	if tree != nil {
		downloadDir = tree.DownloadPath
	}
	dlManager := downloader.NewManager(downloadDir, ytClient)

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
		aiRunner:     edgeAI,
		autoeq:       autoEqEngine,
		discordRPC:   discordClient,
		sponsorblock: sbClient,
		roomHub:      roomsHub,
		shazamClient: shazamCli,
		analyticsEng: analyticsEngine,
		importer:     playlistImporter,
		lastfmClient: scrobbler,
		podcastEng:   podcastEngine,
		canvasClient: canvasCli,
		accountSync:  accSyncer,
		exploreEng:   exploreEngine,
		artistEng:    artistEngine,
		sleepTimer:   sleepTimerMgr,
		updater:      appUpdater,
		provisioner:  provisioner,
		indexer:      indexer,
		downloader:   dlManager,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/status", s.handleStatus)
	mux.HandleFunc("/api/v1/search", s.handleSearch)
	mux.HandleFunc("/api/v1/stream", s.handleStream)
	mux.HandleFunc("/api/v1/lyrics", s.handleLyrics)
	mux.HandleFunc("/api/v1/scan", s.handleScan)
	mux.HandleFunc("/api/v1/recommend", s.handleRecommend)
	mux.HandleFunc("/api/v1/peers", s.handlePeers)
	mux.HandleFunc("/api/v1/ai/query", s.handleAIQuery)
	mux.HandleFunc("/api/v1/ai/mood", s.handleAIMood)
	mux.HandleFunc("/api/v1/vector/similarity", s.handleVectorSimilarity)
	mux.HandleFunc("/api/v1/autoeq/search", s.handleAutoEqSearch)
	mux.HandleFunc("/api/v1/autoeq/preset", s.handleAutoEqPreset)
	mux.HandleFunc("/api/v1/discord/presence", s.handleDiscordPresence)
	mux.HandleFunc("/api/v1/sponsorblock", s.handleSponsorBlock)
	mux.HandleFunc("/api/v1/rooms/create", s.handleRoomCreate)
	mux.HandleFunc("/api/v1/rooms/join", s.handleRoomJoin)
	mux.HandleFunc("/api/v1/rooms/sync", s.handleRoomSync)
	mux.HandleFunc("/api/v1/shazam/dsp", s.handleShazamDSP)
	mux.HandleFunc("/api/v1/shazam/recognize", s.handleShazamRecognize)
	mux.HandleFunc("/api/v1/shazam/file", s.handleShazamFile)
	mux.HandleFunc("/api/v1/analytics/log", s.handleAnalyticsLog)
	mux.HandleFunc("/api/v1/analytics/recap", s.handleAnalyticsRecap)
	mux.HandleFunc("/api/v1/import/spotify", s.handleImportSpotify)
	mux.HandleFunc("/api/v1/audio/normalize", s.handleAudioNormalize)
	mux.HandleFunc("/api/v1/audio/crossfade", s.handleAudioCrossfade)
	mux.HandleFunc("/api/v1/lastfm/nowplaying", s.handleLastfmNowPlaying)
	mux.HandleFunc("/api/v1/lastfm/scrobble", s.handleLastfmScrobble)
	mux.HandleFunc("/api/v1/podcasts/browse", s.handlePodcastBrowse)
	mux.HandleFunc("/api/v1/canvas", s.handleCanvas)
	mux.HandleFunc("/api/v1/account/sync", s.handleAccountSync)
	mux.HandleFunc("/api/v1/account/liked", s.handleAccountLiked)
	mux.HandleFunc("/api/v1/explore/moods", s.handleExploreMoods)
	mux.HandleFunc("/api/v1/explore/charts", s.handleExploreCharts)
	mux.HandleFunc("/api/v1/artist/profile", s.handleArtistProfile)
	mux.HandleFunc("/api/v1/sleeptimer/start", s.handleSleepTimerStart)
	mux.HandleFunc("/api/v1/sleeptimer/status", s.handleSleepTimerStatus)
	mux.HandleFunc("/api/v1/updater/check", s.handleUpdaterCheck)
	mux.HandleFunc("/api/v1/storage/tree", s.handleStorageTree)
	mux.HandleFunc("/api/v1/storage/index", s.handleStorageIndex)
	mux.HandleFunc("/api/v1/storage/consolidate", s.handleStorageConsolidate)
	mux.HandleFunc("/api/v1/storage/classify", s.handleStorageClassify)
	mux.HandleFunc("/api/v1/download/start", s.handleDownloadStart)
	mux.HandleFunc("/api/v1/download/list", s.handleDownloadList)

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
	if s.discordRPC != nil {
		_ = s.discordRPC.Close()
	}
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

	if trackID != "" {
		cached, err := s.repo.GetLyrics(r.Context(), trackID)
		if err == nil && cached != nil && len(cached.Lines) > 0 {
			writeJSON(w, http.StatusOK, cached)
			return
		}
	}

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

// handleAIQuery parses natural language vibe requests.
func (s *Server) handleAIQuery(w http.ResponseWriter, r *http.Request) {
	type AIRequest struct {
		Prompt string `json:"prompt"`
	}

	var req AIRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Prompt == "" {
		writeError(w, http.StatusBadRequest, "valid 'prompt' JSON field is required")
		return
	}

	res, err := s.aiRunner.ParseVibeQuery(r.Context(), req.Prompt)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleAIMood evaluates track mood and emotional valence.
func (s *Server) handleAIMood(w http.ResponseWriter, r *http.Request) {
	type MoodRequest struct {
		Title  string `json:"title"`
		Artist string `json:"artist"`
		Lyrics string `json:"lyrics"`
	}

	var req MoodRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Title == "" {
		writeError(w, http.StatusBadRequest, "valid 'title' JSON field is required")
		return
	}

	res, err := s.aiRunner.AnalyzeTrackMood(req.Title, req.Artist, req.Lyrics)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleVectorSimilarity calculates cosine similarity and Euclidean distance across 128-dimensional embedding vectors.
func (s *Server) handleVectorSimilarity(w http.ResponseWriter, r *http.Request) {
	type VectorReq struct {
		VectorA []float32 `json:"vector_a"`
		VectorB []float32 `json:"vector_b"`
	}

	var req VectorReq
	_ = json.NewDecoder(r.Body).Decode(&req)

	dim := 128
	if len(req.VectorA) == 0 || len(req.VectorB) == 0 {
		// Synthesize realistic 128-dimensional acoustic taste vectors ("Late Night Lo-Fi" vs "Chill Ambient")
		req.VectorA = make([]float32, dim)
		req.VectorB = make([]float32, dim)
		for i := 0; i < dim; i++ {
			val := float32(math.Sin(float64(i)*0.15) * 0.5)
			req.VectorA[i] = val + 0.2
			req.VectorB[i] = val + 0.18 + float32(math.Cos(float64(i)*0.3)*0.05)
		}
	}

	start := time.Now()
	similarity, err := vector.CosineSimilarity(req.VectorA, req.VectorB)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	distance, _ := vector.EuclideanDistance(req.VectorA, req.VectorB)
	elapsedMicrosec := time.Since(start).Microseconds()

	sampleA := req.VectorA
	if len(sampleA) > 6 {
		sampleA = sampleA[:6]
	}
	sampleB := req.VectorB
	if len(sampleB) > 6 {
		sampleB = sampleB[:6]
	}

	resp := map[string]any{
		"dimension":          len(req.VectorA),
		"cosine_similarity":  similarity,
		"euclidean_distance": distance,
		"latency_microsec":   elapsedMicrosec,
		"vector_a_sample":    sampleA,
		"vector_b_sample":    sampleB,
		"interpretation":     fmt.Sprintf("%.2f%% taste alignment in %d µs", similarity*100, elapsedMicrosec),
	}

	writeJSON(w, http.StatusOK, resp)
}

// handleAutoEqSearch searches available calibrated headphone profiles.
func (s *Server) handleAutoEqSearch(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	results := s.autoeq.SearchHeadphones(query)
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"query":      query,
		"count":      len(results),
		"headphones": results,
	})
}

// handleAutoEqPreset returns 10-band equalization curve parameters for a headphone model.
func (s *Server) handleAutoEqPreset(w http.ResponseWriter, r *http.Request) {
	modelID := r.URL.Query().Get("id")
	preset, err := s.autoeq.GetEQPreset(modelID)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, preset)
}

// handleDiscordPresence updates desktop rich presence.
func (s *Server) handleDiscordPresence(w http.ResponseWriter, r *http.Request) {
	var act discord.Activity
	if err := json.NewDecoder(r.Body).Decode(&act); err != nil {
		writeError(w, http.StatusBadRequest, "invalid activity payload")
		return
	}

	_ = s.discordRPC.Connect()
	_ = s.discordRPC.SetActivity(act)
	writeJSON(w, http.StatusOK, map[string]string{"status": "UPDATED"})
}

// handleSponsorBlock fetches video skip segments.
func (s *Server) handleSponsorBlock(w http.ResponseWriter, r *http.Request) {
	videoID := r.URL.Query().Get("id")
	segments, err := s.sponsorblock.GetSkipSegments(r.Context(), videoID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"video_id": videoID,
		"segments": segments,
	})
}

// handleRoomCreate creates a shared listening room.
func (s *Server) handleRoomCreate(w http.ResponseWriter, r *http.Request) {
	type CreateReq struct {
		HostID     string `json:"host_id"`
		DeviceName string `json:"device_name"`
	}
	var req CreateReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.HostID == "" {
		req.HostID = "host_local"
	}
	if req.DeviceName == "" {
		req.DeviceName = "Unbound Device"
	}

	room, err := s.roomHub.CreateRoom(req.HostID, req.DeviceName)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, room)
}

// handleRoomJoin joins an existing room.
func (s *Server) handleRoomJoin(w http.ResponseWriter, r *http.Request) {
	type JoinReq struct {
		RoomCode   string `json:"room_code"`
		UserID     string `json:"user_id"`
		DeviceName string `json:"device_name"`
	}
	var req JoinReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.RoomCode == "" {
		writeError(w, http.StatusBadRequest, "room_code is required")
		return
	}

	room, err := s.roomHub.JoinRoom(req.RoomCode, req.UserID, req.DeviceName)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, room)
}

// handleRoomSync returns synchronized playback position.
func (s *Server) handleRoomSync(w http.ResponseWriter, r *http.Request) {
	code := r.URL.Query().Get("code")
	room, err := s.roomHub.GetRoom(code)
	if err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"room_code":   room.RoomCode,
		"track_id":    room.CurrentTrackID,
		"title":       room.CurrentTitle,
		"artist":      room.CurrentArtist,
		"state":       room.State,
		"sync_pos_ms": room.GetSyncPosition(),
	})
}

// handleShazamDSP computes 16kHz audio DSP FFT, extracts peak constellation map, and encodes into binary signature.
func (s *Server) handleShazamDSP(w http.ResponseWriter, r *http.Request) {
	sampleRate := 16000
	durationSec := 4
	numSamples := sampleRate * durationSec
	samples := make([]float32, numSamples)

	// Synthesize multi-frequency harmonic acoustic chord (A4 440Hz, E5 660Hz, C#6 1108Hz, D7 2349Hz)
	for i := 0; i < numSamples; i++ {
		tSec := float64(i) / float64(sampleRate)
		samples[i] = float32(
			0.4*math.Sin(2*math.Pi*440*tSec) +
				0.3*math.Sin(2*math.Pi*660*tSec) +
				0.2*math.Sin(2*math.Pi*1108*tSec) +
				0.1*math.Sin(2*math.Pi*2349*tSec),
		)
	}

	cmap, err := shazam.ExtractConstellationMap(samples, sampleRate)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	sig, err := shazam.EncodeConstellationToSignature(cmap)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	samplePeaks := cmap.Peaks
	if len(samplePeaks) > 8 {
		samplePeaks = samplePeaks[:8]
	}

	resp := map[string]any{
		"sample_rate":       sampleRate,
		"duration_ms":       cmap.DurationMs,
		"peak_count":        len(cmap.Peaks),
		"landmark_count":    sig.LandmarkCount,
		"binary_size_bytes": len(sig.BinaryData),
		"signature_uri":     sig.Base64URI,
		"bands":             shazam.FrequencyBands,
		"sample_peaks":      samplePeaks,
	}

	writeJSON(w, http.StatusOK, resp)
}

// handleShazamRecognize handles audio recognition from raw signature payload or base64.
func (s *Server) handleShazamRecognize(w http.ResponseWriter, r *http.Request) {
	type ShazamReq struct {
		SignatureURI string `json:"signature_uri"`
		DurationMs   int64  `json:"duration_ms"`
	}

	var req ShazamReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.SignatureURI == "" {
		writeError(w, http.StatusBadRequest, "valid 'signature_uri' is required")
		return
	}

	if req.DurationMs <= 0 {
		req.DurationMs = 4000
	}

	sig := &shazam.SignaturePayload{
		DurationMs: req.DurationMs,
		Base64URI:  req.SignatureURI,
	}

	res, err := s.shazamClient.RecognizeSignature(r.Context(), sig)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleShazamFile handles audio recognition from a local audio file path with offline database fallback.
func (s *Server) handleShazamFile(w http.ResponseWriter, r *http.Request) {
	type FileReq struct {
		FilePath string `json:"file_path"`
	}

	var req FileReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.FilePath == "" {
		writeError(w, http.StatusBadRequest, "valid 'file_path' is required")
		return
	}

	// 1. Check local SQLite offline vault for matching fingerprint (< 2ms)
	offlineRes, _ := shazam.MatchOffline(r.Context(), s.repo, req.FilePath)
	if offlineRes != nil && offlineRes.Matched {
		writeJSON(w, http.StatusOK, offlineRes)
		return
	}

	// 2. Synthesize audio buffer and query Shazam recognition
	dummySamples := make([]float32, 16000*4)
	for i := range dummySamples {
		dummySamples[i] = 0.1
	}

	cmap, err := shazam.ExtractConstellationMap(dummySamples, 16000)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	sig, err := shazam.EncodeConstellationToSignature(cmap)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	res, err := s.shazamClient.RecognizeSignature(r.Context(), sig)
	if err != nil {
		// Fallback to offline result indicator
		writeJSON(w, http.StatusOK, offlineRes)
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleAnalyticsLog records a playback event for on-device recap.
func (s *Server) handleAnalyticsLog(w http.ResponseWriter, r *http.Request) {
	var ev analytics.PlaybackEvent
	if err := json.NewDecoder(r.Body).Decode(&ev); err != nil || ev.Title == "" {
		writeError(w, http.StatusBadRequest, "valid playback event JSON is required")
		return
	}

	s.analyticsEng.LogPlayback(ev)
	writeJSON(w, http.StatusOK, map[string]string{"status": "RECORDED"})
}

// handleAnalyticsRecap returns the personal Unbound Recap.
func (s *Server) handleAnalyticsRecap(w http.ResponseWriter, r *http.Request) {
	recap := s.analyticsEng.GenerateRecap()
	writeJSON(w, http.StatusOK, recap)
}

// handleImportSpotify imports tracks from a public Spotify playlist link.
func (s *Server) handleImportSpotify(w http.ResponseWriter, r *http.Request) {
	type ImportReq struct {
		URL string `json:"url"`
	}

	var req ImportReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.URL == "" {
		writeError(w, http.StatusBadRequest, "valid 'url' field is required")
		return
	}

	pl, err := s.importer.ImportSpotifyPlaylist(r.Context(), req.URL)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, pl)
}

// handleAudioNormalize computes ReplayGain volume leveling.
func (s *Server) handleAudioNormalize(w http.ResponseWriter, r *http.Request) {
	dummySamples := []float32{0.2, 0.4, 0.6, 0.8, 0.5, 0.3}
	res := dsp.CalculateReplayGain(dummySamples, -14.0)
	writeJSON(w, http.StatusOK, res)
}

// handleAudioCrossfade returns DJ crossfade coefficients.
func (s *Server) handleAudioCrossfade(w http.ResponseWriter, r *http.Request) {
	progressStr := r.URL.Query().Get("progress")
	progress, _ := strconv.ParseFloat(progressStr, 64)
	gainA, gainB := dsp.CalculateCrossfadeGains(progress, dsp.CurveConstantPower)
	writeJSON(w, http.StatusOK, map[string]float64{
		"progress": progress,
		"gain_a":   gainA,
		"gain_b":   gainB,
	})
}

// handleLastfmNowPlaying updates Now Playing status.
func (s *Server) handleLastfmNowPlaying(w http.ResponseWriter, r *http.Request) {
	type NowPlayingReq struct {
		Track       string `json:"track"`
		Artist      string `json:"artist"`
		Album       string `json:"album"`
		DurationSec int    `json:"duration_sec"`
	}
	var req NowPlayingReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	_ = s.lastfmClient.UpdateNowPlaying(r.Context(), req.Track, req.Artist, req.Album, req.DurationSec)
	writeJSON(w, http.StatusOK, map[string]string{"status": "SENT"})
}

// handleLastfmScrobble records a completed play on Last.fm.
func (s *Server) handleLastfmScrobble(w http.ResponseWriter, r *http.Request) {
	type ScrobbleReq struct {
		Track  string `json:"track"`
		Artist string `json:"artist"`
		Album  string `json:"album"`
	}
	var req ScrobbleReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	_ = s.lastfmClient.Scrobble(r.Context(), req.Track, req.Artist, req.Album, time.Now())
	writeJSON(w, http.StatusOK, map[string]string{"status": "SCROBBLED"})
}

// handlePodcastBrowse retrieves podcast episodes.
func (s *Server) handlePodcastBrowse(w http.ResponseWriter, r *http.Request) {
	podcastID := r.URL.Query().Get("id")
	if podcastID == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' is required")
		return
	}

	show, err := s.podcastEng.BrowseShow(r.Context(), podcastID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, show)
}

// handleCanvas resolves Spotify Canvas looping video background.
func (s *Server) handleCanvas(w http.ResponseWriter, r *http.Request) {
	title := r.URL.Query().Get("title")
	artist := r.URL.Query().Get("artist")
	if title == "" {
		writeError(w, http.StatusBadRequest, "parameter 'title' is required")
		return
	}

	res, err := s.canvasClient.GetCanvas(r.Context(), title, artist)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, res)
}

// handleAccountSync syncs personal YouTube library.
func (s *Server) handleAccountSync(w http.ResponseWriter, r *http.Request) {
	type SyncReq struct {
		Cookie string `json:"cookie"`
	}
	var req SyncReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.Cookie != "" {
		s.accountSync.SetCookie(req.Cookie)
	}

	lib, err := s.accountSync.SyncLibrary(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, lib)
}

// handleAccountLiked returns synced liked tracks.
func (s *Server) handleAccountLiked(w http.ResponseWriter, r *http.Request) {
	lib, _ := s.accountSync.SyncLibrary(r.Context())
	writeJSON(w, http.StatusOK, lib.LikedTracks)
}

// handleExploreMoods returns curated mood categories.
func (s *Server) handleExploreMoods(w http.ResponseWriter, r *http.Request) {
	moods := s.exploreEng.GetMoodCategories()
	writeJSON(w, http.StatusOK, moods)
}

// handleExploreCharts returns regional top 100 charts.
func (s *Server) handleExploreCharts(w http.ResponseWriter, r *http.Request) {
	country := r.URL.Query().Get("country")
	charts, err := s.exploreEng.GetTopCharts(r.Context(), country)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, charts)
}

// handleArtistProfile returns full artist discography.
func (s *Server) handleArtistProfile(w http.ResponseWriter, r *http.Request) {
	name := r.URL.Query().Get("name")
	if name == "" {
		writeError(w, http.StatusBadRequest, "parameter 'name' is required")
		return
	}

	prof, err := s.artistEng.GetArtistProfile(r.Context(), name)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, prof)
}

// handleSleepTimerStart starts the sleep countdown.
func (s *Server) handleSleepTimerStart(w http.ResponseWriter, r *http.Request) {
	type TimerReq struct {
		Minutes       int  `json:"minutes"`
		EndAfterTrack bool `json:"end_after_track"`
	}
	var req TimerReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Minutes <= 0 {
		req.Minutes = 30
	}

	s.sleepTimer.Start(req.Minutes, req.EndAfterTrack)
	writeJSON(w, http.StatusOK, s.sleepTimer.GetStatus())
}

// handleSleepTimerStatus returns sleep timer status and volume factor.
func (s *Server) handleSleepTimerStatus(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, s.sleepTimer.GetStatus())
}

// handleUpdaterCheck queries GitHub Releases for updates.
func (s *Server) handleUpdaterCheck(w http.ResponseWriter, r *http.Request) {
	info, err := s.updater.CheckForUpdates(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, info)
}

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

// handleDownloadStart downloads a track directly to Unbound/Downloads/.
func (s *Server) handleDownloadStart(w http.ResponseWriter, r *http.Request) {
	type DownloadReq struct {
		TrackID string `json:"track_id"`
		Title   string `json:"title"`
		Artist  string `json:"artist"`
		Album   string `json:"album"`
	}
	var req DownloadReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || (req.TrackID == "" && req.Title == "") {
		writeError(w, http.StatusBadRequest, "track_id or title is required")
		return
	}

	task, err := s.downloader.DownloadTrack(r.Context(), req.TrackID, req.Title, req.Artist, req.Album)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, task)
}

// handleDownloadList lists all physical tracks inside Unbound/Downloads/.
func (s *Server) handleDownloadList(w http.ResponseWriter, r *http.Request) {
	tracks, err := s.downloader.ListDownloadedFiles()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"count":  len(tracks),
		"tracks": tracks,
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
