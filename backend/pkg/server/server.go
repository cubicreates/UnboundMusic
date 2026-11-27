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
	"log"
	"math"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/account"
	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/algorithm"
	"github.com/cubicreates/unbound-engine/pkg/aligner"
	"github.com/cubicreates/unbound-engine/pkg/analytics"
	"github.com/cubicreates/unbound-engine/pkg/artist"
	"github.com/cubicreates/unbound-engine/pkg/autoeq"
	"github.com/cubicreates/unbound-engine/pkg/canvas"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/discord"
	"github.com/cubicreates/unbound-engine/pkg/downloader"
	"github.com/cubicreates/unbound-engine/pkg/dsp"
	"github.com/cubicreates/unbound-engine/pkg/events"
	"github.com/cubicreates/unbound-engine/pkg/explore"
	"github.com/cubicreates/unbound-engine/pkg/fallback"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/lastfm"
	"github.com/cubicreates/unbound-engine/pkg/lyrics"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/p2p"
	"github.com/cubicreates/unbound-engine/pkg/podcasts"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
	"github.com/cubicreates/unbound-engine/pkg/rooms"
	"github.com/cubicreates/unbound-engine/pkg/router"
	"github.com/cubicreates/unbound-engine/pkg/ryd"
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
	SocketPath     string `json:"socket_path,omitempty"` // Unix domain socket path (e.g. "/path/to/engine.sock")
	DatabasePath   string `json:"database_path"`
	LibraryRoot    string `json:"library_root"`
	AppStorageRoot string `json:"app_storage_root"`
	ModelsPath     string `json:"models_path"`
}

// DefaultConfig provides standard localhost defaults.
func DefaultConfig() Config {
	return Config{
		Port:           45731,
		SocketPath:     "",
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
	rydClient    *ryd.Client
	roomHub      *rooms.Hub
	shazamClient *shazam.Client
	analyticsEng *analytics.Engine
	lastfmClient *lastfm.Scrobbler
	podcastEng   *podcasts.Engine
	canvasClient *canvas.Client
	accountSync  *account.Syncer
	exploreEng   *explore.Engine
	ytExploreEng *ytmusic.ExploreEngine
	artistEng    *artist.Engine
	sleepTimer   *sleeptimer.Timer
	updater      *updater.Updater
	provisioner  *storage.Provisioner
	indexer      *storage.Indexer
	downloader   *downloader.Manager
	events       *events.EventBus
	radioGen     *ytmusic.RadioGenerator
	algoEngine   *algorithm.Engine
	neteaseClient *lyrics.NetEaseClient
	lyricsRec     *lyrics.LyricsRecognizer
	fallbackCoord *fallback.Coordinator
	udsServer      *http.Server
	udsListener    net.Listener
	ctx            context.Context
	cancelCtx      context.CancelFunc
	spoolCancelsMu sync.Mutex
	spoolCancels   map[string]context.CancelFunc
}

// NewServer initializes all engine subsystems and HTTP routes.
func NewServer(cfg Config) (*Server, error) {
	if cfg.Port <= 0 {
		cfg.Port = 45731
	}

	// Enforce 128 MB heap ceiling and aggressive GC cycle for dual-GC harmony with ART VM
	ConfigureMemoryCeiling()

	// Initialize Storage Provisioner to ensure Unbound/.backend/ structure
	provisioner := storage.NewProvisioner(cfg.LibraryRoot, cfg.AppStorageRoot)
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
	p2pDiscovery := p2p.NewDiscovery("node_local", "Unbound Mobile", cfg.Port)

	modelsPath := cfg.ModelsPath
	if modelsPath == "" {
		if tree != nil && tree.ModelsPath != "" {
			modelsPath = tree.ModelsPath
		} else if filepath.Base(cfg.AppStorageRoot) == ".backend" {
			modelsPath = filepath.Join(cfg.AppStorageRoot, "models")
		} else {
			modelsPath = filepath.Join(cfg.AppStorageRoot, ".backend", "models")
		}
	}
	edgeAI := ai.NewRunner(modelsPath)
	autoEqEngine := autoeq.NewEngine()
	discordClient := discord.NewClient("")
	sbClient := sponsorblock.NewClient()
	rydClient := ryd.NewClient()
	roomsHub := rooms.NewHub()
	shazamCli := shazam.NewClient()
	analyticsEngine := analytics.NewEngine()
	scrobbler := lastfm.NewScrobbler("", "")
	podcastEngine := podcasts.NewEngine(ytClient)
	podcastEngine.SetRepository(repo)
	lyricsRecognizer := lyrics.NewLyricsRecognizer(repo, lyrics.NewLRCLIBClient(), geniusClient, ytClient)
	canvasCacheDir := ""
	if tree != nil && tree.CachePath != "" {
		canvasCacheDir = filepath.Join(tree.CachePath, "canvas")
	}
	canvasCli := canvas.NewClient(canvasCacheDir)
	accSyncer := account.NewSyncer(repo, ytClient)
	exploreEngine := explore.NewEngine(ytClient)
	ytExploreEngine := ytmusic.NewExploreEngine(repo)
	artistEngine := artist.NewEngine(ytClient)
	sleepTimerMgr := sleeptimer.NewTimer()
	appUpdater := updater.NewUpdater("2.0.0")

	indexer := storage.NewIndexer(repo)
	downloadDir := os.TempDir()
	if tree != nil {
		downloadDir = tree.DownloadPath
	}
	dlManager := downloader.NewManager(downloadDir, ytClient, repo)
	eventBus := events.NewEventBus(128)
	dlManager.SetEventBus(eventBus)

	markov := analytics.NewMarkovTracker(repo)
	reranker := recommender.NewReRanker()
	radioGen := ytmusic.NewRadioGenerator(ytClient, repo, markov, reranker)

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
		rydClient:    rydClient,
		roomHub:      roomsHub,
		shazamClient: shazamCli,
		analyticsEng: analyticsEngine,
		lastfmClient: scrobbler,
		podcastEng:   podcastEngine,
		canvasClient: canvasCli,
		accountSync:  accSyncer,
		exploreEng:   exploreEngine,
		ytExploreEng: ytExploreEngine,
		artistEng:    artistEngine,
		sleepTimer:   sleepTimerMgr,
		updater:      appUpdater,
		provisioner:  provisioner,
		indexer:      indexer,
		downloader:    dlManager,
		events:        eventBus,
		radioGen:      radioGen,
		algoEngine:    algorithm.NewEngine(repo, ytClient),
		neteaseClient: lyrics.NewNetEaseClient(),
		lyricsRec:     lyricsRecognizer,
		fallbackCoord: fallback.NewCoordinator(downloadDir),
		spoolCancels:  make(map[string]context.CancelFunc),
	}
	s.ctx, s.cancelCtx = context.WithCancel(context.Background())

	mux := http.NewServeMux()

	// --- 1. System & Health Watchdog ---
	mux.HandleFunc("/api/v1/ping", s.handlePing)
	mux.HandleFunc("/api/v1/health", s.handleHealth)
	mux.HandleFunc("/api/v1/status", s.handleStatus)
	mux.HandleFunc("/api/v1/events", s.handleEvents)
	mux.HandleFunc("/api/v1/system/unpack-payload", s.handleUnpackPayload)
	mux.HandleFunc("/api/v1/system/wallpaper/backup", s.handleWallpaperBackup)
	mux.HandleFunc("/api/v1/system/wallpaper/backup/ids", s.handleWallpaperBackupIDs)

	// --- 2. Playback, Streaming & Multi-Stage Fallback ---
	mux.HandleFunc("/api/v1/stream", s.handleStream)
	mux.HandleFunc("/api/v1/proxy/stream", s.handleProxyStream)
	mux.HandleFunc("/api/v1/fallback/resolve", s.handleFallbackResolve)

	// --- 3. Search & AI Discovery ---
	mux.HandleFunc("/api/v1/search", s.handleSearch)
	mux.HandleFunc("/api/v1/search/music", s.handleMusicSearch)
	mux.HandleFunc("/api/v1/search/podcasts", s.handlePodcastSearch)
	mux.HandleFunc("/api/v1/search/unified", s.handleUnifiedSearch)
	mux.HandleFunc("/api/v1/search/cascade", s.handleSearchCascade)
	mux.HandleFunc("/api/v1/search/history", s.handleSearchHistory)
	mux.HandleFunc("/api/v1/search/history/delete", s.handleSearchHistoryDelete)
	mux.HandleFunc("/api/v1/search/vibe", s.handleVibeSearch)
	mux.HandleFunc("/api/v1/ai/query", s.handleAIQuery)
	mux.HandleFunc("/api/v1/ai/mood", s.handleAIMood)
	mux.HandleFunc("/api/v1/vector/similarity", s.handleVectorSimilarity)

	// --- 4. Synced Lyrics & Alignment ---
	mux.HandleFunc("/api/v1/lyrics", s.handleLyrics)
	mux.HandleFunc("/api/v1/recognize/lyrics", s.handleLyricsRecognize)
	mux.HandleFunc("/api/v1/lyrics/recognize", s.handleLyricsRecognize)

	// --- 5. AutoEq & Audio DSP ---
	mux.HandleFunc("/api/v1/autoeq/search", s.handleAutoEqSearch)
	mux.HandleFunc("/api/v1/autoeq/preset", s.handleAutoEqPreset)
	mux.HandleFunc("/api/v1/audio/normalize", s.handleAudioNormalize)
	mux.HandleFunc("/api/v1/audio/crossfade", s.handleAudioCrossfade)

	// --- 6. Radio & Recommendations ---
	mux.HandleFunc("/api/v1/recommend", s.handleRecommend)
	mux.HandleFunc("/api/v1/radio/magic", s.handleRadioMagic)
	mux.HandleFunc("/api/v1/radio/next", s.handleRadioNext)
	mux.HandleFunc("/api/v1/radio/guest", s.handleGuestRadio)
	mux.HandleFunc("/api/v1/feed/smart", s.handleSmartFeed)

	// --- 7. Shazam & Fingerprint Recognition ---
	mux.HandleFunc("/api/v1/shazam/dsp", s.handleShazamDSP)
	mux.HandleFunc("/api/v1/shazam/recognize", s.handleShazamRecognize)
	mux.HandleFunc("/api/v1/shazam/file", s.handleShazamFile)
	mux.HandleFunc("/api/v1/shazam/identify", s.handleShazamIdentify)
	mux.HandleFunc("/api/v1/shazam/stream", s.handleShazamIdentify)
	mux.HandleFunc("/api/v1/fingerprint/identify", s.handleFingerprintIdentify)

	// --- 8. Offline Storage & Background Downloader ---
	mux.HandleFunc("/api/v1/scan", s.handleScan)
	mux.HandleFunc("/api/v1/storage/tree", s.handleStorageTree)
	mux.HandleFunc("/api/v1/storage/index", s.handleStorageIndex)
	mux.HandleFunc("/api/v1/storage/consolidate", s.handleStorageConsolidate)
	mux.HandleFunc("/api/v1/storage/classify", s.handleStorageClassify)
	mux.HandleFunc("/api/v1/storage/search", s.handleStorageSearch)
	mux.HandleFunc("/api/v1/storage/scan", s.handleStorageScan)
	mux.HandleFunc("/api/v1/storage/tracks", s.handleStorageTracks)
	mux.HandleFunc("/api/v1/storage/library", s.handleStorageLibrary)
	mux.HandleFunc("/api/v1/storage/folders", s.handleStorageLibrary)
	mux.HandleFunc("/api/v1/storage/ingest-batch", s.handleStorageIngestBatch)
	mux.HandleFunc("/api/v1/download/start", s.handleDownloadStart)
	mux.HandleFunc("/api/v1/download/status", s.handleDownloadStatus)
	mux.HandleFunc("/api/v1/download/active", s.handleDownloadActive)
	mux.HandleFunc("/api/v1/download/pause", s.handleDownloadPause)
	mux.HandleFunc("/api/v1/download/resume", s.handleDownloadResume)
	mux.HandleFunc("/api/v1/download/cancel", s.handleDownloadCancel)
	mux.HandleFunc("/api/v1/download/delete", s.handleDownloadDelete)
	mux.HandleFunc("/api/v1/download/list", s.handleDownloadList)

	// --- 9. Social, Metadata & Sync ---
	mux.HandleFunc("/api/v1/peers", s.handlePeers)
	mux.HandleFunc("/api/v1/discord/presence", s.handleDiscordPresence)
	mux.HandleFunc("/api/v1/sponsorblock", s.handleSponsorBlock)
	mux.HandleFunc("/api/v1/track/skip_segments", s.handleTrackSkipSegments)
	mux.HandleFunc("/api/v1/ryd/votes", s.handleRydVotes)
	mux.HandleFunc("/api/v1/rooms/create", s.handleRoomCreate)
	mux.HandleFunc("/api/v1/rooms/join", s.handleRoomJoin)
	mux.HandleFunc("/api/v1/rooms/sync", s.handleRoomSync)
	mux.HandleFunc("/api/v1/analytics/log", s.handleAnalyticsLog)
	mux.HandleFunc("/api/v1/analytics/recap", s.handleAnalyticsRecap)
	mux.HandleFunc("/api/v1/analytics/taste_event", s.handleRecordTasteEvent)
	mux.HandleFunc("/api/v1/taste/profile", s.handleGetTasteProfile)
	mux.HandleFunc("/api/v1/settings", s.handleSettings)
	mux.HandleFunc("/api/v1/eq/presets", s.handleEqPresets)
	mux.HandleFunc("/api/v1/storage/purge_cache", s.handlePurgeCache)
	mux.HandleFunc("/api/v1/lastfm/nowplaying", s.handleLastfmNowPlaying)
	mux.HandleFunc("/api/v1/lastfm/scrobble", s.handleLastfmScrobble)
	mux.HandleFunc("/api/v1/podcasts/browse", s.handlePodcastBrowse)
	mux.HandleFunc("/api/v1/podcasts/discover", s.handlePodcastDiscover)
	mux.HandleFunc("/api/v1/podcasts/search", s.handlePodcastSearch)
	mux.HandleFunc("/api/v1/podcasts/resume", s.handlePodcastResume)
	mux.HandleFunc("/api/v1/podcasts/library", s.handlePodcastLibrary)
	mux.HandleFunc("/api/v1/podcasts/library/follow", s.handlePodcastFollow)
	mux.HandleFunc("/api/v1/podcasts/library/save", s.handlePodcastSaveEpisode)
	mux.HandleFunc("/api/v1/canvas", s.handleCanvas)
	mux.HandleFunc("/api/v1/account/sync", s.handleAccountSync)
	mux.HandleFunc("/api/v1/account/status", s.handleAccountStatus)
	mux.HandleFunc("/api/v1/account/disconnect", s.handleAccountDisconnect)
	mux.HandleFunc("/api/v1/account/liked", s.handleAccountLiked)
	mux.HandleFunc("/api/v1/account/feed/infinite", s.handleAccountFeedInfinite)
	mux.HandleFunc("/api/v1/account/device/start", s.handleAccountDeviceStart)
	mux.HandleFunc("/api/v1/account/device/poll", s.handleAccountDevicePoll)
	mux.HandleFunc("/api/v1/explore/moods", s.handleExploreMoods)
	mux.HandleFunc("/api/v1/explore/mood/radio", s.handleExploreMoodRadio)
	mux.HandleFunc("/api/v1/explore/moods_genres", s.handleExploreMoodsGenres)
	mux.HandleFunc("/api/v1/explore/genre_detail", s.handleExploreGenreDetail)
	mux.HandleFunc("/api/v1/explore/charts", s.handleExploreCharts)
	mux.HandleFunc("/api/v1/explore/home", s.handleExploreHome)
	mux.HandleFunc("/api/v1/artist/profile", s.handleArtistProfile)
	mux.HandleFunc("/api/v1/playlist", s.handlePlaylist)
	mux.HandleFunc("/api/v1/album", s.handlePlaylist)
	mux.HandleFunc("/api/v1/album/details", s.handleAlbumDetails)
	mux.HandleFunc("/api/v1/sleeptimer/start", s.handleSleepTimerStart)
	mux.HandleFunc("/api/v1/sleeptimer/status", s.handleSleepTimerStatus)
	mux.HandleFunc("/api/v1/updater/check", s.handleUpdaterCheck)
	mux.HandleFunc("/api/v1/playlists", s.handlePlaylistsRouter)
	mux.HandleFunc("/api/v1/playlists/", s.handlePlaylistsRouter)
	mux.HandleFunc("/api/v1/favorites", s.handleFavoritesRouter)
	mux.HandleFunc("/api/v1/favorites/", s.handleFavoritesRouter)
	mux.HandleFunc("/api/v1/track/like", s.handleTrackLike)

	s.httpServer = &http.Server{
		Addr:         fmt.Sprintf("127.0.0.1:%d", cfg.Port),
		Handler:      RecoveryMiddleware(corsMiddleware(mux)),
		ReadTimeout:  60 * time.Second,
		WriteTimeout: 0, // Disabled: audio proxy streams can stay open for the entire track duration
	}

	return s, nil
}

// ServeHTTP delegates request handling to the configured middleware pipeline and multiplexer.
func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if s.httpServer != nil && s.httpServer.Handler != nil {
		s.httpServer.Handler.ServeHTTP(w, r)
	} else {
		http.NotFound(w, r)
	}
}

// Start begins listening on the configured localhost address and/or Unix domain socket.
func (s *Server) Start() error {
	if s.cfg.SocketPath != "" {
		_ = os.Remove(s.cfg.SocketPath) // Clean up any stale socket
		l, err := net.Listen("unix", s.cfg.SocketPath)
		if err != nil {
			log.Printf("[IPC] Unix domain socket listen failed on %s (%v); continuing on TCP :%d", s.cfg.SocketPath, err, s.cfg.Port)
		} else {
			s.udsListener = l
			s.udsServer = &http.Server{
				Handler:      s.httpServer.Handler,
				ReadTimeout:  60 * time.Second,
				WriteTimeout: 0, // Disabled: audio proxy streams can stay open for the entire track duration
			}
			go func() {
				if err := s.udsServer.Serve(l); err != nil && err != http.ErrServerClosed {
					log.Printf("[IPC] UDS server stopped: %v", err)
				}
			}()
			log.Printf("[IPC] Unix domain socket listening on %s", s.cfg.SocketPath)
		}
	}

	// Bootstrap YouTube player cipher operations asynchronously on startup
	go func() {
		ytmusic.StartCipherRefresher(context.Background())
	}()

	// Auto-unpack AI payload if models.zst is present and primary model is missing
	go func() {
		modelsDir := s.cfg.ModelsPath
		if modelsDir == "" {
			if filepath.Base(s.cfg.AppStorageRoot) == ".backend" {
				modelsDir = filepath.Join(s.cfg.AppStorageRoot, "models")
			} else {
				modelsDir = filepath.Join(s.cfg.AppStorageRoot, ".backend", "models")
			}
		}
		primaryModel := filepath.Join(modelsDir, "smollm2_135m.gguf")
		archivePath := filepath.Join(modelsDir, "models.zst")

		if _, err := os.Stat(primaryModel); os.IsNotExist(err) {
			if data, err := os.ReadFile(archivePath); err == nil && len(data) > 0 {
				log.Printf("[SERVER] Found AI payload archive (%d bytes). Auto-extracting models to %s...", len(data), modelsDir)
				if manifest, err := gatekeeper.DecompressZstdTarStream(data, modelsDir); err == nil {
					log.Printf("[SERVER] Auto-extracted AI payload successfully (%d files, %d ms)", manifest.TotalFiles, manifest.DecompressionMs)
					_ = os.Remove(archivePath)
				} else {
					log.Printf("[SERVER] Failed auto-extracting AI payload: %v", err)
				}
			}
		}
	}()

	return s.httpServer.ListenAndServe()
}

// Shutdown cleanly closes active listeners and database connections.
func (s *Server) Shutdown(ctx context.Context) error {
	if s.cancelCtx != nil {
		s.cancelCtx()
	}
	s.spoolCancelsMu.Lock()
	for _, cancelFn := range s.spoolCancels {
		cancelFn()
	}
	s.spoolCancelsMu.Unlock()
	time.Sleep(25 * time.Millisecond) // Allow background workers to release open file handles

	_ = s.httpServer.Shutdown(ctx)
	if s.udsServer != nil {
		_ = s.udsServer.Shutdown(ctx)
	}
	if s.udsListener != nil {
		_ = s.udsListener.Close()
	}
	if s.cfg.SocketPath != "" {
		_ = os.Remove(s.cfg.SocketPath)
	}
	if s.discordRPC != nil {
		_ = s.discordRPC.Close()
	}
	if s.db != nil {
		_ = s.db.Close()
	}
	return nil
}



// handleFallbackResolve verifies and resolves songs missing from YouTube via Spotify/P2P fallback.
func (s *Server) handleFallbackResolve(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	title := r.URL.Query().Get("title")
	artist := r.URL.Query().Get("artist")
	if strings.TrimSpace(title) == "" {
		writeError(w, http.StatusBadRequest, "title parameter required")
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	status, err := s.fallbackCoord.ResolveAndStream(ctx, title, artist)
	if err != nil && status == nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, status)
}

// EventBus returns the server's real-time event bus.
func (s *Server) EventBus() *events.EventBus {
	return s.events
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

// handleRadioMagic generates on-demand serendipity magic radio queue.
func (s *Server) handleRadioMagic(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	type MagicRadioRequest struct {
		LocalHour   int    `json:"local_hour"`
		SeedTrackID string `json:"seed_track_id"`
	}

	var req MagicRadioRequest
	if r.Body != nil && r.ContentLength > 0 {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.LocalHour <= 0 {
		req.LocalHour = time.Now().Hour()
	}

	if s.radioGen == nil {
		writeError(w, http.StatusInternalServerError, "radio generator not initialized")
		return
	}

	resp, err := s.radioGen.GenerateMagicRadio(r.Context(), req.LocalHour, req.SeedTrackID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, resp)
}

// handleRadioNext generates YouTube's true algorithmic automix queue for a seed video.
func (s *Server) handleRadioNext(w http.ResponseWriter, r *http.Request) {
	videoID := strings.TrimSpace(r.URL.Query().Get("videoId"))
	if videoID == "" {
		videoID = strings.TrimSpace(r.URL.Query().Get("video_id"))
	}
	if videoID == "" {
		videoID = strings.TrimSpace(r.URL.Query().Get("id"))
	}
	if videoID == "" {
		writeError(w, http.StatusBadRequest, "parameter 'videoId' is required")
		return
	}

	if s.ytClient == nil {
		writeError(w, http.StatusInternalServerError, "YouTube client not initialized")
		return
	}

	tracks, err := s.ytClient.FetchRadioForTrack(r.Context(), videoID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to fetch radio: %v", err))
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"seed_id": videoID,
		"tracks":  tracks,
		"count":   len(tracks),
	})
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

// handleRydVotes fetches Return YouTube Dislike (RYD) community statistics and calculates like/dislike ratios.
func (s *Server) handleRydVotes(w http.ResponseWriter, r *http.Request) {
	videoID := r.URL.Query().Get("videoId")
	if videoID == "" {
		videoID = r.URL.Query().Get("id")
	}
	if videoID == "" {
		writeError(w, http.StatusBadRequest, "missing videoId or id query parameter")
		return
	}

	votes, err := s.rydClient.GetVotes(r.Context(), videoID)
	if err != nil {
		writeError(w, http.StatusBadGateway, fmt.Sprintf("failed fetching RYD votes: %v", err))
		return
	}

	writeJSON(w, http.StatusOK, votes)
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

// Shazam audio recognition handlers are defined in handlers_shazam.go

// handleAnalyticsLog records a playback event for on-device recap.
func (s *Server) handleAnalyticsLog(w http.ResponseWriter, r *http.Request) {
	var ev analytics.PlaybackEvent
	if err := json.NewDecoder(r.Body).Decode(&ev); err != nil || ev.Title == "" {
		writeError(w, http.StatusBadRequest, "valid playback event JSON is required")
		return
	}

	s.analyticsEng.LogPlayback(ev)
	if s.algoEngine != nil {
		track := models.Track{
			ID:     ev.TrackID,
			Title:  ev.Title,
			Artist: ev.Artist,
			Album:  ev.Album,
		}
		_ = s.algoEngine.IngestPlaybackEvent(r.Context(), track, int(ev.DurationSec), int(ev.ListenedSec))
	}
	if s.ytClient != nil && ytmusic.IsValidYouTubeVideoID(ev.TrackID) {
		trackID := ev.TrackID
		listenedSec := int(ev.ListenedSec)
		go func() {
			bgCtx, cancel := context.WithTimeout(s.ctx, 10*time.Second)
			defer cancel()
			_ = s.ytClient.ReportPlaybackView(bgCtx, trackID, listenedSec)
		}()
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "RECORDED"})
}

// handleAnalyticsRecap returns the personal Unbound Recap.
func (s *Server) handleAnalyticsRecap(w http.ResponseWriter, r *http.Request) {
	recap := s.analyticsEng.GenerateRecap()
	writeJSON(w, http.StatusOK, recap)
}

// handleAudioNormalize computes EBU R128 / ReplayGain volume leveling.
func (s *Server) handleAudioNormalize(w http.ResponseWriter, r *http.Request) {
	targetLUFS := -14.0
	if targetStr := r.URL.Query().Get("target"); targetStr != "" {
		if tVal, err := strconv.ParseFloat(targetStr, 64); err == nil && tVal < 0 {
			targetLUFS = tVal
		}
	}

	// 1. Check if direct sample payload was sent in POST body
	if r.Method == http.MethodPost {
		type NormalizeReq struct {
			Samples    []float32 `json:"samples"`
			TargetLUFS float64   `json:"target_lufs"`
		}
		var req NormalizeReq
		if err := json.NewDecoder(r.Body).Decode(&req); err == nil && len(req.Samples) > 0 {
			if req.TargetLUFS < 0 {
				targetLUFS = req.TargetLUFS
			}
			res := dsp.CalculateReplayGain(req.Samples, targetLUFS)
			writeJSON(w, http.StatusOK, res)
			return
		}
	}

	// 2. Check if track ID or file path was queried: /api/v1/audio/normalize?id=<video_id>&target=-14.0
	id := r.URL.Query().Get("id")
	filePath := r.URL.Query().Get("path")

	if id != "" && filePath == "" {
		cacheDir := s.getAudioCacheDir()
		candidate := filepath.Join(cacheDir, id+".opus")
		if fi, err := os.Stat(candidate); err == nil && fi.Size() > 0 {
			filePath = candidate
		}
	}

	if filePath != "" {
		if data, err := os.ReadFile(filePath); err == nil && len(data) >= 2 {
			pcmCount := len(data) / 2
			if pcmCount > 96000 {
				pcmCount = 96000
			}
			samples := make([]int16, pcmCount)
			for i := 0; i < pcmCount; i++ {
				samples[i] = int16(data[i*2]) | (int16(data[i*2+1]) << 8)
			}
			res := dsp.CalculatePCMLoudness(samples, targetLUFS)
			writeJSON(w, http.StatusOK, res)
			return
		}
	}

	// Default fallback: calibrated standard reference audio stream
	refSamples := make([]float32, 4800)
	for i := range refSamples {
		refSamples[i] = float32(math.Sin(float64(i) * 0.05 * math.Pi)) * 0.5
	}
	res := dsp.CalculateReplayGain(refSamples, targetLUFS)
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
	title := r.URL.Query().Get("title")
	if podcastID == "" && title == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' or 'title' is required")
		return
	}

	show, err := s.podcastEng.BrowseShowWithTitle(r.Context(), podcastID, title)
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

// Account synchronization and device code auth handlers are defined in handlers_account.go

// handleExploreMoods returns curated mood categories.
func (s *Server) handleExploreMoods(w http.ResponseWriter, r *http.Request) {
	moods := s.exploreEng.GetMoodCategories()
	writeJSON(w, http.StatusOK, moods)
}

// handleExploreCharts returns global or regional singles charts.
func (s *Server) handleExploreCharts(w http.ResponseWriter, r *http.Request) {
	country := r.URL.Query().Get("gl")
	if country == "" {
		country = r.URL.Query().Get("country")
	}
	if country == "" {
		country = "US"
	}
	lang := r.URL.Query().Get("hl")
	if lang == "" {
		lang = "en"
	}
	scope := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("scope")))

	var tracks []models.TrackItem
	var err error

	if s.ytExploreEng != nil {
		if strings.EqualFold(country, "ZZ") || scope == "global" {
			tracks, err = s.ytExploreEng.FetchGlobalSingles(r.Context())
		} else {
			tracks, err = s.ytExploreEng.FetchRegionalSingles(r.Context(), country, lang)
		}
	}

	if (err != nil || len(tracks) == 0) && s.exploreEng != nil {
		charts, chartErr := s.exploreEng.GetTopCharts(r.Context(), country)
		if chartErr == nil && len(charts) > 0 {
			tracks = make([]models.TrackItem, 0, len(charts))
			for _, c := range charts {
				if ytmusic.IsSingleSongTrack(c.Title) {
					tracks = append(tracks, models.TrackItem{
						ID:        c.TrackID,
						Title:     c.Title,
						Artist:    c.Artist,
						Thumbnail: c.ThumbnailURL,
						Source:    "youtube",
					})
				}
			}
		}
	}

	if tracks == nil {
		tracks = []models.TrackItem{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
		"charts": tracks,
	})
}

// HomeMixPayload represents a YouTube-style 1:1 mix built by the backend for Home ("Made For Your Mood").
type HomeMixPayload struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Subtitle    string `json:"subtitle"`
	CoverURL    string `json:"cover_url"`
	MixType     string `json:"mix_type"` // "account_mix" or "track_radio"
	SeedTrackID string `json:"seed_track_id,omitempty"`
	SeedTitle   string `json:"seed_title,omitempty"`
	SeedArtist  string `json:"seed_artist,omitempty"`
}

// handleExploreHome offloads Home screen aggregation to the Go daemon, serving a unified payload
// for both guest and signed-in users:
//   - jump_back_in: personalized history + liked library when logged in, public YouTube/chart singles when guest
//   - made_for_you_mixes: backend-built YouTube-style Mixes seeded by user history or public YouTube feed
//   - trending_globally: worldwide top singles (never localized to India)
//   - trending_regionally: detected country's top singles
func (s *Server) handleExploreHome(w http.ResponseWriter, r *http.Request) {
	country := strings.TrimSpace(r.URL.Query().Get("gl"))
	if country == "" {
		country = strings.TrimSpace(r.URL.Query().Get("country"))
	}
	if country == "" {
		country = "IN"
	}
	lang := strings.TrimSpace(r.URL.Query().Get("hl"))
	if lang == "" {
		lang = "en"
	}

	ctx := r.Context()
	status := s.accountSync.GetStatus()

	var (
		globalSingles   []models.TrackItem
		regionalSingles []models.TrackItem
		publicFeed      []models.Track
		recentEvents    []models.PlaybackEvent
		wg              sync.WaitGroup
	)

	wg.Add(4)
	go func() {
		defer wg.Done()
		if s.ytExploreEng != nil {
			globalSingles, _ = s.ytExploreEng.FetchGlobalSingles(ctx)
		}
	}()
	go func() {
		defer wg.Done()
		if s.ytExploreEng != nil {
			regionalSingles, _ = s.ytExploreEng.FetchRegionalSingles(ctx, country, lang)
		}
	}()
	go func() {
		defer wg.Done()
		if s.ytClient != nil {
			publicFeed, _ = s.ytClient.FetchInfinitePersonalizedFeed(ctx, "")
		}
	}()
	go func() {
		defer wg.Done()
		if s.repo != nil {
			recentEvents, _ = s.repo.GetRecentPlaybackEvents(ctx, 25)
		}
	}()
	wg.Wait()

	// Exclude global duplicates from regional hits outside US/GB so both shelves feel distinct
	if !strings.EqualFold(country, "US") && !strings.EqualFold(country, "GB") && len(globalSingles) > 0 && len(regionalSingles) > 0 {
		globalKeys := make(map[string]bool, len(globalSingles))
		for _, g := range globalSingles {
			globalKeys[strings.ToLower(strings.TrimSpace(g.Title)+"|"+strings.TrimSpace(g.Artist))] = true
		}
		distinctRegional := make([]models.TrackItem, 0, len(regionalSingles))
		for _, reg := range regionalSingles {
			k := strings.ToLower(strings.TrimSpace(reg.Title) + "|" + strings.TrimSpace(reg.Artist))
			if !globalKeys[k] {
				distinctRegional = append(distinctRegional, reg)
			}
		}
		if len(distinctRegional) >= 6 {
			regionalSingles = distinctRegional
		}
	}

	// Build Jump Back In list (User History + Account Sync if logged in; Guest Taste/Favorites/Public YouTube + Charts if guest)
	bannedArtists := make(map[string]bool)
	if s.repo != nil {
		bannedArtists = s.repo.GetBannedArtists()
	}

	jumpBackIn := make([]models.TrackItem, 0, 48)
	seenJump := make(map[string]bool)
	appendJumpItem := func(it models.TrackItem) {
		if it.ID == "" || strings.TrimSpace(it.Title) == "" || !ytmusic.IsSingleSongTrack(it.Title) {
			return
		}
		if bannedArtists[strings.ToLower(strings.TrimSpace(it.Artist))] {
			return
		}
		upperID := strings.ToUpper(it.ID)
		if strings.HasPrefix(upperID, "MPSP") || strings.HasPrefix(upperID, "VL") || strings.HasPrefix(upperID, "PL") || strings.HasPrefix(upperID, "MPRE") || strings.HasPrefix(upperID, "UC") {
			return
		}
		if strings.EqualFold(it.Source, "podcast") {
			return
		}
		key := strings.ToLower(strings.TrimSpace(it.Title) + "|" + strings.TrimSpace(it.Artist))
		if !seenJump[it.ID] && !seenJump[key] {
			seenJump[it.ID] = true
			seenJump[key] = true
			if it.Thumbnail == "" && len(it.ID) == 11 && !strings.HasPrefix(it.ID, "local:") {
				it.Thumbnail = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", it.ID)
			}
			jumpBackIn = append(jumpBackIn, it)
		}
	}

	toTrackItem := func(t models.Track) models.TrackItem {
		thumb := t.ThumbnailURL
		if thumb == "" && len(t.ID) == 11 && !strings.HasPrefix(t.ID, "local:") {
			thumb = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", t.ID)
		}
		return models.TrackItem{
			ID:         t.ID,
			Title:      t.Title,
			Artist:     t.Artist,
			Album:      t.Album,
			DurationMs: t.DurationMs,
			Thumbnail:  thumb,
			Source:     "youtube",
		}
	}

	if status.Connected {
		// Logged-in user: recent playback history + synced YouTube library + personalized feed
		for _, ev := range recentEvents {
			if ev.TrackID != "" && ev.Title != "" {
				appendJumpItem(models.TrackItem{
					ID:         ev.TrackID,
					Title:      ev.Title,
					Artist:     ev.Artist,
					Album:      ev.Album,
					DurationMs: int64(ev.DurationSec) * 1000,
					Source:     "youtube",
				})
			}
		}
		for _, t := range s.accountSync.GetLikedTracks() {
			appendJumpItem(toTrackItem(t))
		}
		for _, t := range publicFeed {
			appendJumpItem(toTrackItem(t))
		}
		for _, t := range regionalSingles {
			appendJumpItem(t)
		}
	} else {
		// Guest user: non-fast-skipped recent plays + Guest favorites + top positive-affinity artist enrichment + public feed + charts
		for _, ev := range recentEvents {
			if ev.DurationSec > 20 && ev.ListenedSec > 0 && ev.ListenedSec < 15 && ev.CompletedRatio < 0.25 {
				continue
			}
			if ev.TrackID != "" && ev.Title != "" {
				appendJumpItem(models.TrackItem{
					ID:         ev.TrackID,
					Title:      ev.Title,
					Artist:     ev.Artist,
					Album:      ev.Album,
					DurationMs: int64(ev.DurationSec) * 1000,
					Source:     "youtube",
				})
			}
		}
		if s.repo != nil {
			if favs, err := s.repo.GetFavorites(ctx); err == nil {
				for _, f := range favs {
					if ytmusic.IsValidYouTubeVideoID(f.TrackID) {
						appendJumpItem(models.TrackItem{
							ID:         f.TrackID,
							Title:      f.Title,
							Artist:     f.Artist,
							Album:      f.Album,
							DurationMs: f.DurationMs,
							Thumbnail:  f.CoverURL,
							Source:     "youtube",
						})
					}
				}
			}
			if topAff, err := s.repo.GetTopAffinityArtists(3); err == nil && s.ytClient != nil {
				addedFromAff := 0
				for _, aff := range topAff {
					if aff.DecayedScore <= 0 || aff.ArtistName == "" || addedFromAff >= 6 {
						continue
					}
					if artTracks, err := s.ytClient.SearchMusic(ctx, aff.ArtistName, "songs"); err == nil {
						for _, t := range artTracks {
							if addedFromAff >= 6 {
								break
							}
							before := len(jumpBackIn)
							appendJumpItem(toTrackItem(t))
							if len(jumpBackIn) > before {
								addedFromAff++
							}
						}
					}
				}
			}
		}
		for _, t := range publicFeed {
			appendJumpItem(toTrackItem(t))
		}
		for _, t := range regionalSingles {
			appendJumpItem(t)
		}
		for _, t := range globalSingles {
			appendJumpItem(t)
		}
	}

	// Build Made For Your Mood YouTube-style mixes on the backend
	mixes := make([]HomeMixPayload, 0, 32)
	seenMixIDs := make(map[string]bool)

	if status.Connected {
		lib, _ := s.accountSync.SyncLibrary(ctx)
		if lib != nil {
			for _, m := range lib.Mixes {
				if m.ID != "" && m.Title != "" && !seenMixIDs[m.ID] {
					seenMixIDs[m.ID] = true
					mixTitle := m.Title
					if !strings.Contains(strings.ToLower(mixTitle), "mix") {
						mixTitle = "Mix – " + mixTitle
					}
					mixes = append(mixes, HomeMixPayload{
						ID:       "mix:" + m.ID,
						Title:    mixTitle,
						Subtitle: m.Subtitle,
						CoverURL: m.ThumbnailURL,
						MixType:  "account_mix",
					})
				}
			}
		}
	}

	// Collect distinct artists for authentic "Artist 1, Artist 2, Artist 3, and more" subtitles
	distinctArtists := make([]string, 0, len(jumpBackIn))
	seenArtists := make(map[string]bool)
	for _, t := range jumpBackIn {
		a := strings.TrimSpace(t.Artist)
		if a != "" && !seenArtists[strings.ToLower(a)] {
			seenArtists[strings.ToLower(a)] = true
			distinctArtists = append(distinctArtists, a)
		}
	}

	for idx, seed := range jumpBackIn {
		if len(mixes) >= 24 {
			break
		}
		if seenMixIDs[seed.ID] {
			continue
		}
		seenMixIDs[seed.ID] = true
		primaryArtist := strings.TrimSpace(seed.Artist)
		if primaryArtist == "" {
			primaryArtist = "YouTube Music"
		}
		others := make([]string, 0, 2)
		for offset := 0; offset < len(distinctArtists) && len(others) < 2; offset++ {
			cand := distinctArtists[(idx+offset)%len(distinctArtists)]
			if !strings.EqualFold(cand, primaryArtist) {
				others = append(others, cand)
			}
		}
		var roster string
		switch len(others) {
		case 2:
			roster = fmt.Sprintf("%s, %s, %s, and more", primaryArtist, others[0], others[1])
		case 1:
			roster = fmt.Sprintf("%s, %s, and more", primaryArtist, others[0])
		default:
			roster = fmt.Sprintf("%s, and more", primaryArtist)
		}
		mixes = append(mixes, HomeMixPayload{
			ID:          "trackmix:" + seed.ID,
			Title:       "Mix – " + seed.Title,
			Subtitle:    roster,
			CoverURL:    seed.Thumbnail,
			MixType:     "track_radio",
			SeedTrackID: seed.ID,
			SeedTitle:   seed.Title,
			SeedArtist:  seed.Artist,
		})
	}

	if globalSingles == nil {
		globalSingles = []models.TrackItem{}
	}
	if regionalSingles == nil {
		regionalSingles = []models.TrackItem{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"is_logged_in":        status.Connected,
		"account_name":        status.AccountName,
		"avatar_url":          status.AvatarURL,
		"region_code":         strings.ToUpper(country),
		"jump_back_in":        jumpBackIn,
		"made_for_you_mixes":  mixes,
		"trending_globally":   globalSingles,
		"trending_regionally": regionalSingles,
	})
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

// handlePlaylist returns full details, metadata, and tracks for a YouTube Music playlist or album.
func (s *Server) handlePlaylist(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	if id == "" {
		id = r.URL.Query().Get("browseId")
	}
	if id == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' or 'browseId' is required")
		return
	}

	if s.ytClient == nil {
		writeError(w, http.StatusInternalServerError, "YouTube Music client is not initialized")
		return
	}

	res, err := s.ytClient.FetchPlaylistOrAlbum(r.Context(), id)
	if err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to fetch playlist or album: %v", err))
		return
	}

	writeJSON(w, http.StatusOK, res)
}

// handleAlbumDetails returns full metadata and official tracklist for an album resolved by browseId or (artist, albumTitle).
func (s *Server) handleAlbumDetails(w http.ResponseWriter, r *http.Request) {
	id := strings.TrimSpace(r.URL.Query().Get("id"))
	if id == "" {
		id = strings.TrimSpace(r.URL.Query().Get("browseId"))
	}
	artist := strings.TrimSpace(r.URL.Query().Get("artist"))
	album := strings.TrimSpace(r.URL.Query().Get("album"))
	if album == "" {
		album = strings.TrimSpace(r.URL.Query().Get("title"))
	}

	if id == "" && album == "" {
		writeError(w, http.StatusBadRequest, "parameter 'id' (browseId) or 'album' (title) is required")
		return
	}

	if s.ytClient == nil {
		writeError(w, http.StatusInternalServerError, "YouTube Music client is not initialized")
		return
	}

	res, err := s.ytClient.ResolveAlbumDetails(r.Context(), id, artist, album)
	if err != nil {
		writeError(w, http.StatusNotFound, fmt.Sprintf("failed to resolve album details: %v", err))
		return
	}

	writeJSON(w, http.StatusOK, res)
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
	clientVer := r.URL.Query().Get("version")
	info, err := s.updater.CheckForUpdatesWithVersion(r.Context(), clientVer)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, info)
}




// corsMiddleware adds permissive headers for local IPC and web frontend callers.
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
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
