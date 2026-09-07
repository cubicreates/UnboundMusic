/*
 * Package: main
 * File: server_router.go
 * Purpose: Centralized route registration, CORS middleware, and HTTP response serialization for the local daemon.
 * Subsystem: Localhost Daemon API
 * Concurrency: Thread-safe HTTP handler multiplexer.
 */

package main

import (
	"encoding/json"
	"net/http"
	"sync/atomic"

	"github.com/cubicreates/unbound-engine/pkg/account"
	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/analytics"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/moods"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// Daemon coordinates HTTP controller endpoints and core engine subsystems.
type Daemon struct {
	repo        *database.Repository
	exploreEng  *ytmusic.ExploreEngine
	moodEng     *moods.Engine
	aiRunner    *ai.Runner
	ytClient    *ytmusic.Client
	radioGen    *ytmusic.RadioGenerator
	syncer      *account.Syncer
	isScanning  int32
}

// NewDaemon creates an instantiated daemon server with all subsystems connected.
func NewDaemon(
	repo *database.Repository,
	exploreEng *ytmusic.ExploreEngine,
	moodEng *moods.Engine,
	aiRunner *ai.Runner,
	ytClient *ytmusic.Client,
) *Daemon {
	if ytClient == nil {
		ytClient = ytmusic.NewClient()
	}
	if exploreEng == nil {
		exploreEng = ytmusic.NewExploreEngine(repo)
	}
	if moodEng == nil {
		moodEng = moods.NewEngine(exploreEng)
	}
	if aiRunner == nil {
		aiRunner = ai.NewRunner()
	}

	markov := analytics.NewMarkovTracker(repo)
	reranker := recommender.NewReRanker()
	radioGen := ytmusic.NewRadioGenerator(ytClient, repo, markov, reranker)
	syncer := account.NewSyncer(repo, ytClient)

	return &Daemon{
		repo:       repo,
		exploreEng: exploreEng,
		moodEng:    moodEng,
		aiRunner:   aiRunner,
		ytClient:   ytClient,
		radioGen:   radioGen,
		syncer:     syncer,
	}
}

// Routes constructs the HTTP ServeMux and attaches CORS and error recovery middleware.
func (d *Daemon) Routes() http.Handler {
	mux := http.NewServeMux()

	// Explore & Mood Endpoints
	mux.HandleFunc("/api/v1/explore/charts", d.HandleGetRegionalCharts)
	mux.HandleFunc("/api/v1/explore/moods", d.HandleGetMoodCapsules)
	mux.HandleFunc("/api/v1/explore/mood/radio", d.HandleGetMoodRadio)
	mux.HandleFunc("/api/v1/explore/moods_genres", d.HandleGetMoodsAndGenres)
	mux.HandleFunc("/api/v1/explore/genre_detail", d.HandleGetGenreDetail)

	// Storage & Scanner Endpoints
	mux.HandleFunc("/api/v1/storage/scan", d.HandleTriggerStorageScan)
	mux.HandleFunc("/api/v1/storage/tracks", d.HandleGetLocalTracks)

	// Search Endpoints (Standard & 4-Stage Intelligent Cascade)
	mux.HandleFunc("/api/v1/search", d.HandleSearch)
	mux.HandleFunc("/api/v1/search/cascade", d.HandleSearchCascade)
	mux.HandleFunc("/api/v1/search/vibe", d.HandleVibeSearch)

	// Phase 1: Magic Serendipity Radio & Taste Telemetry
	mux.HandleFunc("/api/v1/radio/magic", d.HandleMagicRadio)
	mux.HandleFunc("/api/v1/analytics/taste_event", d.HandleRecordTasteEvent)
	mux.HandleFunc("/api/v1/taste/profile", d.HandleGetTasteProfile)

	// Phase 2: YouTube Account Authentication & Library Ingestion
	mux.HandleFunc("/api/v1/account/sync", d.HandleAccountSync)
	mux.HandleFunc("/api/v1/account/status", d.HandleAccountStatus)
	mux.HandleFunc("/api/v1/account/disconnect", d.HandleAccountDisconnect)
	mux.HandleFunc("/api/v1/account/liked", d.HandleGetAccountLiked)
	mux.HandleFunc("/api/v1/track/like", d.HandleToggleTrackLike)

	// Phase 3: Settings Studio, EQ Presets & Storage Cache Purge
	mux.HandleFunc("/api/v1/settings", func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			d.HandleGetSettings(w, r)
		case http.MethodPost:
			d.HandleSetSetting(w, r)
		default:
			d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		}
	})
	mux.HandleFunc("/api/v1/eq/presets", func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			d.HandleGetEqPresets(w, r)
		case http.MethodPost:
			d.HandleSaveEqPreset(w, r)
		default:
			d.writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
		}
	})
	mux.HandleFunc("/api/v1/storage/purge_cache", d.HandlePurgeCache)

	return d.corsMiddleware(mux)
}

// corsMiddleware injects CORS headers and handles preflight OPTIONS requests.
func (d *Daemon) corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, User-Agent")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		next.ServeHTTP(w, r)
	})
}

// writeJSON serializes data as application/json with the provided status code.
func (d *Daemon) writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

// writeError outputs a standardized error JSON payload.
func (d *Daemon) writeError(w http.ResponseWriter, status int, message string) {
	d.writeJSON(w, status, map[string]string{"error": message})
}

// tryLockScan attempts an atomic lock for scanning; returns false if already scanning.
func (d *Daemon) tryLockScan() bool {
	return atomic.CompareAndSwapInt32(&d.isScanning, 0, 1)
}

// unlockScan releases the scanning lock.
func (d *Daemon) unlockScan() {
	atomic.StoreInt32(&d.isScanning, 0)
}
