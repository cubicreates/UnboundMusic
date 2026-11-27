/*
 * Package: server
 * File: handlers_guest_algo.go
 * Purpose: Guest-mode algorithmic radio, taste telemetry, taste profile, explore moods/genres,
 *          SponsorBlock skip segments, and account-independent settings/EQ/cache handlers.
 * Subsystem: Guest Algorithm & Independent Utilities
 */

package server

import (
	"context"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/analytics"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

type tasteEventReq struct {
	TrackID     string `json:"track_id"`
	NextTrackID string `json:"next_track_id,omitempty"`
	PrevTrackID string `json:"prev_track_id,omitempty"`
	Title       string `json:"title"`
	ArtistID    string `json:"artist_id,omitempty"`
	ArtistName  string `json:"artist_name"`
	Genre       string `json:"genre,omitempty"`
	DurationMs  int64  `json:"duration_ms"`
	ListenedMs  int64  `json:"listened_ms"`
	EventType   string `json:"event_type,omitempty"`
}

// handleRecordTasteEvent ingests playback/skip/favorite taste events and Markov transitions.
// Route: POST /api/v1/analytics/taste_event
func (s *Server) handleRecordTasteEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req tasteEventReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json payload: "+err.Error())
		return
	}

	trackID := strings.TrimSpace(req.TrackID)
	artistName := strings.TrimSpace(req.ArtistName)
	if trackID == "" {
		trackID = strings.TrimSpace(req.Title)
	}
	if trackID == "" || artistName == "" {
		writeError(w, http.StatusBadRequest, "track_id and artist_name are required")
		return
	}

	delta, eventType := database.CalculateScoreDelta(req.DurationMs, req.ListenedMs, models.TasteEventType(req.EventType))
	now := time.Now().Unix()

	event := models.TasteEvent{
		TrackID:    trackID,
		Title:      req.Title,
		ArtistID:   req.ArtistID,
		ArtistName: artistName,
		Genre:      req.Genre,
		DurationMs: req.DurationMs,
		ListenedMs: req.ListenedMs,
		ScoreDelta: delta,
		EventType:  eventType,
		Timestamp:  now,
	}

	if s.repo != nil {
		if err := s.repo.RecordTasteEvent(event); err != nil {
			writeError(w, http.StatusInternalServerError, "failed recording taste event: "+err.Error())
			return
		}
		if eventType != models.TasteEventFastSkip {
			if nextID := strings.TrimSpace(req.NextTrackID); nextID != "" && nextID != trackID {
				_ = s.repo.RecordTrackTransition(trackID, nextID, now)
			} else if prevID := strings.TrimSpace(req.PrevTrackID); prevID != "" && prevID != trackID {
				_ = s.repo.RecordTrackTransition(prevID, trackID, now)
			}
		}
	}

	if s.algoEngine != nil {
		s.algoEngine.InvalidateCache()
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"status":      "recorded",
		"score_delta": delta,
		"event_type":  string(eventType),
	})
}

// handleGetTasteProfile returns top non-banned artists and Shannon diversity score.
// Route: GET /api/v1/taste/profile
func (s *Server) handleGetTasteProfile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	limit := 10
	if lStr := r.URL.Query().Get("limit"); lStr != "" {
		if parsed, err := strconv.Atoi(lStr); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	if s.repo == nil {
		writeJSON(w, http.StatusOK, map[string]any{
			"top_artists":           []models.ArtistAffinity{},
			"taste_diversity_score": 5.0,
		})
		return
	}

	topArtists, err := s.repo.GetTopAffinityArtists(limit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed querying top artists: "+err.Error())
		return
	}
	if topArtists == nil {
		topArtists = []models.ArtistAffinity{}
	}
	diversityScore, _ := s.repo.GetTasteDiversityScore()

	writeJSON(w, http.StatusOK, map[string]any{
		"top_artists":           topArtists,
		"taste_diversity_score": diversityScore,
	})
}

// handleGuestRadio builds a hierarchical, taste-weighted, ban-filtered Guest radio queue in one backend call.
// Route: GET /api/v1/radio/guest?video_id=...&title=...&artist=...&album=...&limit=45
func (s *Server) handleGuestRadio(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	q := r.URL.Query()
	videoID := strings.TrimSpace(q.Get("video_id"))
	if videoID == "" {
		videoID = strings.TrimSpace(q.Get("id"))
	}
	title := strings.TrimSpace(q.Get("title"))
	artist := strings.TrimSpace(q.Get("artist"))
	album := strings.TrimSpace(q.Get("album"))
	limit := 45
	if lStr := q.Get("limit"); lStr != "" {
		if parsed, err := strconv.Atoi(lStr); err == nil && parsed > 0 && parsed <= 80 {
			limit = parsed
		}
	}

	if videoID == "" && title == "" && artist == "" {
		writeError(w, http.StatusBadRequest, "video_id, title, or artist is required")
		return
	}

	ctx := r.Context()
	banned := make(map[string]bool)
	affinityMap := make(map[string]models.ArtistAffinity)
	if s.repo != nil {
		banned = s.repo.GetBannedArtists()
		if affList, err := s.repo.GetTopAffinityArtists(50); err == nil {
			for _, a := range affList {
				affinityMap[a.ArtistName] = a
				affinityMap[a.ArtistID] = a
			}
		}
	}

	var (
		artistTracks []models.Track
		albumTracks  []models.Track
		nextTracks   []models.Track
		magicTracks  []models.TrackItem
		wg           sync.WaitGroup
	)

	if s.ytClient != nil && artist != "" && !strings.EqualFold(artist, "Unknown Artist") {
		wg.Add(1)
		go func() {
			defer wg.Done()
			artistTracks, _ = s.ytClient.SearchMusic(ctx, artist, "songs")
		}()
	}
	if s.ytClient != nil && album != "" && !strings.EqualFold(album, "Single") && !strings.EqualFold(album, title) {
		wg.Add(1)
		go func() {
			defer wg.Done()
			albumTracks, _ = s.ytClient.SearchMusic(ctx, album+" songs", "songs")
		}()
	}
	if s.ytClient != nil && ytmusic.IsValidYouTubeVideoID(videoID) {
		wg.Add(1)
		go func() {
			defer wg.Done()
			nextTracks, _ = s.ytClient.FetchRadioForTrack(ctx, videoID)
		}()
	}
	if s.radioGen != nil {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if resp, err := s.radioGen.GenerateMagicRadio(ctx, time.Now().Hour(), videoID); err == nil && resp != nil {
				magicTracks = resp.Queue
			}
		}()
	}
	wg.Wait()

	seenIDs := make(map[string]bool)
	seenTitles := make(map[string]bool)
	if videoID != "" {
		seenIDs[videoID] = true
	}
	if title != "" {
		seenTitles[strings.ToLower(title)] = true
	}

	isPlayableCandidate := func(id, tTitle, tArtist, itemType string) bool {
		if id == "" || strings.TrimSpace(tTitle) == "" {
			return false
		}
		if strings.EqualFold(itemType, "artist") || strings.EqualFold(itemType, "album") ||
			strings.EqualFold(itemType, "playlist") || strings.EqualFold(itemType, "podcast") {
			return false
		}
		upper := strings.ToUpper(id)
		if strings.HasPrefix(upper, "UC") || strings.HasPrefix(upper, "MPRE") ||
			strings.HasPrefix(upper, "VL") || strings.HasPrefix(upper, "PL") ||
			strings.HasPrefix(upper, "MPSP") {
			return false
		}
		if !ytmusic.IsValidYouTubeVideoID(id) && !strings.HasPrefix(id, "local:") {
			return false
		}
		if banned[strings.ToLower(strings.TrimSpace(tArtist))] {
			return false
		}
		if seenIDs[id] || seenTitles[strings.ToLower(strings.TrimSpace(tTitle))] {
			return false
		}
		return true
	}

	toItem := func(t models.Track) models.TrackItem {
		return models.TrackItem{
			ID:         t.ID,
			Title:      t.Title,
			Artist:     t.Artist,
			Album:      t.Album,
			DurationMs: t.DurationMs,
			Thumbnail:  t.ThumbnailURL,
			Source:     "youtube",
		}
	}

	var priorityQueue []models.TrackItem
	var pool []models.TrackItem

	// Tier 1: Up to 2 same-artist tracks
	artCount := 0
	for _, t := range artistTracks {
		if artCount >= 2 {
			break
		}
		if isPlayableCandidate(t.ID, t.Title, t.Artist, t.ItemType) {
			seenIDs[t.ID] = true
			seenTitles[strings.ToLower(strings.TrimSpace(t.Title))] = true
			priorityQueue = append(priorityQueue, toItem(t))
			artCount++
		}
	}

	// Tier 2: Up to 4 same-album / soundtrack tracks
	albCount := 0
	for _, t := range albumTracks {
		if albCount >= 4 {
			break
		}
		if isPlayableCandidate(t.ID, t.Title, t.Artist, t.ItemType) {
			seenIDs[t.ID] = true
			seenTitles[strings.ToLower(strings.TrimSpace(t.Title))] = true
			priorityQueue = append(priorityQueue, toItem(t))
			albCount++
		}
	}

	// Tier 3 & 4: Innertube /next + Magic Radio candidates for Markov + Affinity re-ranking
	for _, t := range nextTracks {
		if isPlayableCandidate(t.ID, t.Title, t.Artist, t.ItemType) {
			seenIDs[t.ID] = true
			seenTitles[strings.ToLower(strings.TrimSpace(t.Title))] = true
			pool = append(pool, toItem(t))
		}
	}
	for _, it := range magicTracks {
		if isPlayableCandidate(it.ID, it.Title, it.Artist, "") {
			seenIDs[it.ID] = true
			seenTitles[strings.ToLower(strings.TrimSpace(it.Title))] = true
			pool = append(pool, it)
		}
	}

	// Re-rank pool using Markov transitions & Artist Affinity
	var markovScores map[string]float64
	if s.repo != nil && videoID != "" && len(pool) > 0 {
		ids := make([]string, 0, len(pool))
		for _, p := range pool {
			ids = append(ids, p.ID)
		}
		markovScores = analytics.NewMarkovTracker(s.repo).ScoreCandidates(videoID, ids)
	}
	seedItem := models.TrackItem{
		ID:     videoID,
		Title:  title,
		Artist: artist,
		Album:  album,
		Source: "youtube",
	}
	rerankedPool := recommender.NewReRanker().ReRankCandidates(seedItem, pool, affinityMap, markovScores, limit)

	finalQueue := make([]models.TrackItem, 0, limit+1)
	if title != "" || videoID != "" {
		finalQueue = append(finalQueue, seedItem)
	}
	for _, it := range priorityQueue {
		if len(finalQueue) >= limit {
			break
		}
		finalQueue = append(finalQueue, it)
	}
	for _, it := range rerankedPool {
		if len(finalQueue) >= limit {
			break
		}
		finalQueue = append(finalQueue, it)
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"seed_id": videoID,
		"tracks":  finalQueue,
		"count":   len(finalQueue),
	})
}

// handleExploreMoodsGenres returns Moods & Genres discovery boards.
// Route: GET /api/v1/explore/moods_genres
func (s *Server) handleExploreMoodsGenres(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	gl := strings.TrimSpace(r.URL.Query().Get("gl"))
	if gl == "" {
		gl = "US"
	}
	hl := strings.TrimSpace(r.URL.Query().Get("hl"))
	if hl == "" {
		hl = "en"
	}

	sections, err := s.ytExploreEng.FetchMoodsAndGenres(r.Context(), gl, hl)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to retrieve moods and genres: "+err.Error())
		return
	}
	if sections == nil {
		sections = []ytmusic.GenreSection{}
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"sections": sections,
	})
}

// handleExploreGenreDetail returns curated playlist shelves for a genre token.
// Route: GET /api/v1/explore/genre_detail
func (s *Server) handleExploreGenreDetail(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	params := r.URL.Query().Get("params")
	name := r.URL.Query().Get("name")
	if name == "" {
		name = "Genre"
	}
	gl := strings.TrimSpace(r.URL.Query().Get("gl"))
	if gl == "" {
		gl = "US"
	}
	hl := strings.TrimSpace(r.URL.Query().Get("hl"))
	if hl == "" {
		hl = "en"
	}

	shelves, err := s.ytExploreEng.FetchGenrePlaylists(r.Context(), params, name, gl, hl)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to retrieve genre playlists: "+err.Error())
		return
	}
	if shelves == nil {
		shelves = []ytmusic.PlaylistShelf{}
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"shelves": shelves,
	})
}

// handleExploreMoodRadio resolves tracks for a mood capsule browseId.
// Route: GET /api/v1/explore/mood/radio?browse_id=...
func (s *Server) handleExploreMoodRadio(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	browseID := strings.TrimSpace(r.URL.Query().Get("browse_id"))
	if browseID == "" {
		browseID = strings.TrimSpace(r.URL.Query().Get("id"))
	}
	if browseID == "" {
		writeError(w, http.StatusBadRequest, "browse_id is required")
		return
	}

	var tracks []models.TrackItem
	if s.ytClient != nil {
		if pl, err := s.ytClient.FetchPlaylistOrAlbum(r.Context(), browseID); err == nil && pl != nil && len(pl.Tracks) > 0 {
			for _, t := range pl.Tracks {
				tracks = append(tracks, models.TrackItem{
					ID:         t.ID,
					Title:      t.Title,
					Artist:     t.Artist,
					Album:      t.Album,
					DurationMs: t.DurationMs,
					Thumbnail:  t.ThumbnailURL,
					Source:     "youtube",
				})
			}
		}
		if len(tracks) == 0 {
			if searched, err := s.ytClient.SearchMusic(r.Context(), browseID+" mix", "songs"); err == nil {
				for _, t := range searched {
					tracks = append(tracks, models.TrackItem{
						ID:         t.ID,
						Title:      t.Title,
						Artist:     t.Artist,
						Album:      t.Album,
						DurationMs: t.DurationMs,
						Thumbnail:  t.ThumbnailURL,
						Source:     "youtube",
					})
				}
			}
		}
	}
	if tracks == nil {
		tracks = []models.TrackItem{}
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"tracks": tracks,
	})
}

// handleTrackSkipSegments serves SponsorBlock music_offtopic skip segments.
// Route: GET /api/v1/track/skip_segments?video_id=...
func (s *Server) handleTrackSkipSegments(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	videoID := strings.TrimSpace(r.URL.Query().Get("video_id"))
	if videoID == "" {
		videoID = strings.TrimSpace(r.URL.Query().Get("id"))
	}
	if videoID == "" {
		writeError(w, http.StatusBadRequest, "video_id is required")
		return
	}
	if s.sponsorblock == nil {
		writeJSON(w, http.StatusOK, []any{})
		return
	}
	segments, err := s.sponsorblock.GetMusicSkipSegments(r.Context(), videoID)
	if err != nil {
		writeJSON(w, http.StatusOK, []any{})
		return
	}
	writeJSON(w, http.StatusOK, segments)
}

// handleSettings gets or sets account-independent app settings.
// Route: GET|POST /api/v1/settings
func (s *Server) handleSettings(w http.ResponseWriter, r *http.Request) {
	if s.repo == nil {
		writeError(w, http.StatusServiceUnavailable, "repository not initialized")
		return
	}
	switch r.Method {
	case http.MethodGet:
		settings, err := s.repo.GetAllSettings(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"settings": settings})
	case http.MethodPost:
		var req struct {
			Key   string `json:"key"`
			Value string `json:"value"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Key == "" {
			writeError(w, http.StatusBadRequest, "key is required")
			return
		}
		if err := s.repo.SetSetting(r.Context(), req.Key, req.Value); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "key": req.Key, "value": req.Value})
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// handleEqPresets gets or saves account-independent custom EQ presets.
// Route: GET|POST /api/v1/eq/presets
func (s *Server) handleEqPresets(w http.ResponseWriter, r *http.Request) {
	if s.repo == nil {
		writeError(w, http.StatusServiceUnavailable, "repository not initialized")
		return
	}
	switch r.Method {
	case http.MethodGet:
		presets, err := s.repo.GetUserEqPresets(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		if presets == nil {
			presets = []database.UserEqPreset{}
		}
		writeJSON(w, http.StatusOK, map[string]any{"presets": presets})
	case http.MethodPost:
		var p database.UserEqPreset
		if err := json.NewDecoder(r.Body).Decode(&p); err != nil || p.Name == "" {
			writeError(w, http.StatusBadRequest, "preset name is required")
			return
		}
		if err := s.repo.SaveUserEqPreset(r.Context(), p); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "name": p.Name})
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// handlePurgeCache purges temporary streaming/artwork cache directories.
// Route: POST /api/v1/storage/purge_cache
func (s *Server) handlePurgeCache(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	var freedBytes int64
	cacheDirs := []string{
		s.getAudioCacheDir(),
		filepath.Join(os.TempDir(), "unbound_cache"),
		filepath.Join(os.TempDir(), "unbound_stream"),
	}
	for _, dir := range cacheDirs {
		if dir == "" {
			continue
		}
		_ = filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
			if err == nil && !info.IsDir() {
				freedBytes += info.Size()
				_ = os.Remove(path)
			}
			return nil
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status":      "ok",
		"freed_bytes": freedBytes,
		"freed_mb":    float64(freedBytes) / (1024 * 1024),
	})
}

func (s *Server) recordPlaybackSearchSafe(domain, query string) {
	if s.repo == nil || strings.TrimSpace(query) == "" {
		return
	}
	SafeGo("search_history_recorder", func() {
		_ = s.repo.RecordSearchQuery(context.Background(), domain, query)
	})
}
