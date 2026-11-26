/*
 * Package: podcasts
 * File: podcasts.go
 * Purpose: YouTube Podcasts & RSS browser extracting episode lists, show notes, discovery feeds,
 *          podcast-only search, and exact millisecond position resumption.
 * Subsystem: Podcasts & Spoken Audio
 * Concurrency: Thread-safe in-memory cache and optional SQLite persistence methods.
 */

package podcasts

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

var reApplePodcastArt = regexp.MustCompile(`/\d+x\d+bb`)

// EpisodeChapter represents a timestamped chapter segment within an episode.
type EpisodeChapter struct {
	Title   string `json:"title"`
	StartMs int64  `json:"start_ms"`
}

// PodcastEpisode represents a single podcast audio episode.
type PodcastEpisode struct {
	ID           string           `json:"id"`
	ShowID       string           `json:"show_id"`
	ShowTitle    string           `json:"show_title"`
	Author       string           `json:"author"`
	Title        string           `json:"title"`
	Description  string           `json:"description"`
	Category     string           `json:"category,omitempty"`
	DurationMs   int64            `json:"duration_ms"`
	ThumbnailURL string           `json:"thumbnail_url"`
	ReleaseDate  string           `json:"release_date"`
	ResumePosMs  int64            `json:"resume_pos_ms"`
	Chapters     []EpisodeChapter `json:"chapters"`
}

// PodcastShow contains metadata and episodes for a podcast channel/show.
type PodcastShow struct {
	ID           string           `json:"id"`
	Title        string           `json:"title"`
	Author       string           `json:"author"`
	Description  string           `json:"description"`
	Category     string           `json:"category,omitempty"`
	ThumbnailURL string           `json:"thumbnail_url"`
	EpisodeCount int              `json:"episode_count"`
	Episodes     []PodcastEpisode `json:"episodes"`
}

// PodcastDiscoveryFeed encapsulates curated podcast shows and episodes for the dedicated Podcast screen.
type PodcastDiscoveryFeed struct {
	Category         string           `json:"category"`
	FeaturedShows    []PodcastShow    `json:"featured_shows"`
	RecentEpisodes   []PodcastEpisode `json:"recent_episodes"`
	TopEpisodes      []PodcastEpisode `json:"top_episodes"`
	GlobalEpisodes   []PodcastEpisode `json:"global_episodes"`
	RegionalEpisodes []PodcastEpisode `json:"regional_episodes"`
}

// PodcastLibraryState holds persisted followed shows, saved episodes, and in-progress episodes.
type PodcastLibraryState struct {
	FollowedShows      []PodcastShow    `json:"followed_shows"`
	SavedEpisodes      []PodcastEpisode `json:"saved_episodes"`
	InProgressEpisodes []PodcastEpisode `json:"in_progress_episodes"`
}

// Engine coordinates podcast browsing, discovery, and playback resumption state.
type Engine struct {
	mu       sync.RWMutex
	ytClient *ytmusic.Client
	repo     *database.Repository
	resumes  map[string]int64          // episodeID -> resumePosMs
	followed map[string]PodcastShow    // showID -> PodcastShow
	saved    map[string]PodcastEpisode // episodeID -> PodcastEpisode
	history  []PodcastEpisode          // recent played podcast episodes
}

// NewEngine instantiates a new podcast engine.
func NewEngine(ytClient *ytmusic.Client) *Engine {
	if ytClient == nil {
		ytClient = ytmusic.NewClient()
	}
	return &Engine{
		ytClient: ytClient,
		resumes:  make(map[string]int64),
		followed: make(map[string]PodcastShow),
		saved:    make(map[string]PodcastEpisode),
		history:  make([]PodcastEpisode, 0),
	}
}

// SetRepository attaches an optional SQLite repository for persisting resume positions and feed caches.
func (e *Engine) SetRepository(repo *database.Repository) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.repo = repo
}

// DiscoverPodcasts returns curated podcast shows and episodes, continue-listening history,
// global top podcast episodes, and country-specific regional podcast episodes.
func (e *Engine) DiscoverPodcasts(ctx context.Context, category, country string) (*PodcastDiscoveryFeed, error) {
	cat := strings.TrimSpace(category)
	if cat == "" {
		cat = "All Podcasts"
	}
	gl := strings.ToUpper(strings.TrimSpace(country))
	if gl == "" {
		gl = "US"
	}

	cacheKey := fmt.Sprintf("podcast_discover_v2:%s:%s", strings.ToLower(cat), gl)
	if e.repo != nil {
		if cachedJSON, err := e.repo.GetFeedCache(ctx, cacheKey); err == nil && cachedJSON != "" {
			var cached PodcastDiscoveryFeed
			if err := json.Unmarshal([]byte(cachedJSON), &cached); err == nil &&
				(len(cached.TopEpisodes) > 0 || len(cached.GlobalEpisodes) > 0 || len(cached.FeaturedShows) > 0) {
				e.hydrateResumePositions(&cached)
				return &cached, nil
			}
		}
	}

	query := mapPodcastCategoryQuery(cat)
	var (
		tracks           []models.Track
		ytErr            error
		globalEpisodes   []PodcastEpisode
		regionalEpisodes []PodcastEpisode
		wg               sync.WaitGroup
	)

	wg.Add(3)
	go func() {
		defer wg.Done()
		tracks, ytErr = e.ytClient.SearchWithCategory(ctx, query, "podcast")
	}()
	go func() {
		defer wg.Done()
		globalEpisodes = e.fetchGlobalPodcastEpisodes(ctx)
	}()
	go func() {
		defer wg.Done()
		regionalEpisodes = e.fetchRegionalPodcastEpisodes(ctx, gl)
	}()
	wg.Wait()

	episodes, shows := e.partitionPodcastTracks(tracks, cat)

	if len(globalEpisodes) == 0 && len(episodes) > 0 {
		globalEpisodes = episodes
	}
	if len(regionalEpisodes) == 0 && len(episodes) > 0 {
		regionalEpisodes = episodes
	}

	// Deduplicate global hits out of regional list when user is outside US/GB
	if gl != "US" && gl != "GB" && len(globalEpisodes) > 0 && len(regionalEpisodes) > 0 {
		globalKeys := make(map[string]bool, len(globalEpisodes))
		for _, g := range globalEpisodes {
			globalKeys[strings.ToLower(strings.TrimSpace(g.Title)+"|"+strings.TrimSpace(g.Author))] = true
		}
		distinctReg := make([]PodcastEpisode, 0, len(regionalEpisodes))
		for _, r := range regionalEpisodes {
			k := strings.ToLower(strings.TrimSpace(r.Title) + "|" + strings.TrimSpace(r.Author))
			if !globalKeys[k] {
				distinctReg = append(distinctReg, r)
			}
		}
		if len(distinctReg) >= 4 {
			regionalEpisodes = distinctReg
		}
	}

	if len(episodes) == 0 && len(regionalEpisodes) > 0 {
		episodes = regionalEpisodes
	}
	if len(episodes) == 0 && len(globalEpisodes) > 0 {
		episodes = globalEpisodes
	}
	if len(episodes) == 0 && len(shows) == 0 && ytErr != nil {
		return nil, fmt.Errorf("failed discovering podcasts: %w", ytErr)
	}

	// Prepend user's history/saved episodes in Continue Listening, followed by fresh regional/global episodes
	e.mu.RLock()
	userHistory := make([]PodcastEpisode, len(e.history))
	copy(userHistory, e.history)
	followedShows := make([]PodcastShow, 0, len(e.followed))
	for _, s := range e.followed {
		followedShows = append(followedShows, s)
	}
	e.mu.RUnlock()

	recent := make([]PodcastEpisode, 0, 24)
	seenRecent := make(map[string]bool)
	appendRecent := func(list []PodcastEpisode) {
		for _, ep := range list {
			if ep.ID == "" || strings.TrimSpace(ep.Title) == "" {
				continue
			}
			if !seenRecent[ep.ID] {
				seenRecent[ep.ID] = true
				recent = append(recent, ep)
			}
		}
	}
	appendRecent(userHistory)
	for _, ep := range episodes {
		if e.GetResumePosition(ep.ID) > 0 {
			appendRecent([]PodcastEpisode{ep})
		}
	}
	appendRecent(episodes)
	appendRecent(regionalEpisodes)
	appendRecent(globalEpisodes)

	// Merge user followed shows first, then discovered shows, then show cards built from regional/global episodes
	mergedShows := make([]PodcastShow, 0, 24)
	seenShows := make(map[string]bool)
	appendShow := func(s PodcastShow) {
		k := strings.ToLower(strings.TrimSpace(s.Title))
		if s.ID == "" || k == "" || seenShows[k] {
			return
		}
		seenShows[k] = true
		mergedShows = append(mergedShows, s)
	}
	for _, fs := range followedShows {
		appendShow(fs)
	}
	for _, ds := range shows {
		appendShow(ds)
	}
	for _, ep := range append(append([]PodcastEpisode{}, regionalEpisodes...), globalEpisodes...) {
		if len(mergedShows) >= 24 {
			break
		}
		showTitle := nonEmptyOr(ep.ShowTitle, ep.Title)
		appendShow(PodcastShow{
			ID:           nonEmptyOr(ep.ShowID, showTitle),
			Title:        showTitle,
			Author:       nonEmptyOr(ep.Author, "Podcast Creator"),
			Description:  ep.Description,
			Category:     nonEmptyOr(ep.Category, cat),
			ThumbnailURL: ep.ThumbnailURL,
			EpisodeCount: 1,
			Episodes:     []PodcastEpisode{ep},
		})
	}

	feed := &PodcastDiscoveryFeed{
		Category:         cat,
		FeaturedShows:    mergedShows,
		RecentEpisodes:   recent,
		TopEpisodes:      episodes,
		GlobalEpisodes:   globalEpisodes,
		RegionalEpisodes: regionalEpisodes,
	}
	e.hydrateResumePositions(feed)

	if e.repo != nil && (len(feed.TopEpisodes) > 0 || len(feed.GlobalEpisodes) > 0 || len(feed.FeaturedShows) > 0) {
		if encoded, err := json.Marshal(feed); err == nil {
			_ = e.repo.SetFeedCache(ctx, cacheKey, string(encoded), 1800)
		}
	}

	return feed, nil
}

// SearchPodcasts searches exclusively for podcast episodes and shows.
func (e *Engine) SearchPodcasts(ctx context.Context, query string) ([]PodcastEpisode, []PodcastShow, error) {
	return e.SearchPodcastsWithFilter(ctx, query, "all")
}

// SearchPodcastsWithFilter searches exclusively for podcast episodes and shows,
// optionally narrowing to "episodes" or "shows".
func (e *Engine) SearchPodcastsWithFilter(ctx context.Context, query, filter string) ([]PodcastEpisode, []PodcastShow, error) {
	trimmed := strings.TrimSpace(query)
	if trimmed == "" {
		return nil, nil, fmt.Errorf("podcast search query cannot be empty")
	}

	tracks, err := e.ytClient.SearchWithCategory(ctx, trimmed, "podcast")
	if err != nil {
		return nil, nil, fmt.Errorf("podcast search failed: %w", err)
	}

	episodes, shows := e.partitionPodcastTracks(tracks, "Search")
	switch strings.ToLower(strings.TrimSpace(filter)) {
	case "episodes", "episode":
		return episodes, []PodcastShow{}, nil
	case "shows", "show":
		return []PodcastEpisode{}, shows, nil
	default:
		return episodes, shows, nil
	}
}

// BrowseShow retrieves show details and available episodes from YouTube Music.
func (e *Engine) BrowseShow(ctx context.Context, showID string) (*PodcastShow, error) {
	return e.BrowseShowWithTitle(ctx, showID, "")
}

// BrowseShowWithTitle retrieves show details using a playlist/browse ID or show title fallback.
func (e *Engine) BrowseShowWithTitle(ctx context.Context, showID, showTitle string) (*PodcastShow, error) {
	if showID == "" && showTitle == "" {
		return nil, fmt.Errorf("showID or title cannot be empty")
	}

	// If showID is a YouTube playlist/podcast show browse ID, fetch the playlist directly
	if strings.HasPrefix(showID, "VL") || strings.HasPrefix(showID, "PL") || strings.HasPrefix(showID, "MPSP") {
		if pl, err := e.ytClient.FetchPlaylistOrAlbum(ctx, showID); err == nil && pl != nil && len(pl.Tracks) > 0 {
			episodes := make([]PodcastEpisode, 0, len(pl.Tracks))
			for _, t := range pl.Tracks {
				episodes = append(episodes, PodcastEpisode{
					ID:           t.ID,
					ShowID:       showID,
					ShowTitle:    pl.Title,
					Author:       t.Artist,
					Title:        t.Title,
					Description:  pl.Description,
					DurationMs:   t.DurationMs,
					ThumbnailURL: t.ThumbnailURL,
					ResumePosMs:  e.GetResumePosition(t.ID),
					ReleaseDate:  time.Now().Format("2006-01-02"),
				})
			}
			return &PodcastShow{
				ID:           showID,
				Title:        pl.Title,
				Author:       pl.Subtitle,
				Description:  pl.Description,
				ThumbnailURL: pl.ThumbnailURL,
				EpisodeCount: len(episodes),
				Episodes:     episodes,
			}, nil
		}
	}

	searchTerm := strings.TrimSpace(showTitle)
	if searchTerm == "" {
		searchTerm = showID
	}
	tracks, err := e.ytClient.SearchWithCategory(ctx, searchTerm+" podcast", "podcast")
	if err != nil {
		return nil, fmt.Errorf("failed browsing podcast show: %w", err)
	}

	episodes, _ := e.partitionPodcastTracks(tracks, "Podcast")
	resolvedTitle := showTitle
	resolvedAuthor := "Podcast Creator"
	resolvedThumb := ""
	if len(episodes) > 0 {
		if resolvedTitle == "" {
			resolvedTitle = episodes[0].ShowTitle
		}
		if episodes[0].Author != "" {
			resolvedAuthor = episodes[0].Author
		}
		resolvedThumb = episodes[0].ThumbnailURL
	}
	if resolvedTitle == "" {
		resolvedTitle = "Podcast Show"
	}

	return &PodcastShow{
		ID:           showID,
		Title:        resolvedTitle,
		Author:       resolvedAuthor,
		Description:  fmt.Sprintf("Episodes & discussions from %s", resolvedTitle),
		ThumbnailURL: resolvedThumb,
		EpisodeCount: len(episodes),
		Episodes:     episodes,
	}, nil
}

// SaveResumePosition records the exact playback position for an episode.
func (e *Engine) SaveResumePosition(episodeID string, posMs int64) {
	if episodeID == "" {
		return
	}
	e.mu.Lock()
	e.resumes[episodeID] = posMs
	repo := e.repo
	e.mu.Unlock()

	if repo != nil {
		_ = repo.SetSetting(context.Background(), "podcast_resume:"+episodeID, strconv.FormatInt(posMs, 10))
	}
}

// GetResumePosition retrieves the saved playback position for an episode.
func (e *Engine) GetResumePosition(episodeID string) int64 {
	if episodeID == "" {
		return 0
	}
	e.mu.RLock()
	pos, ok := e.resumes[episodeID]
	repo := e.repo
	e.mu.RUnlock()
	if ok {
		return pos
	}

	if repo != nil {
		if val, err := repo.GetSetting(context.Background(), "podcast_resume:"+episodeID); err == nil && val != "" {
			if parsed, err := strconv.ParseInt(val, 10, 64); err == nil {
				e.mu.Lock()
				e.resumes[episodeID] = parsed
				e.mu.Unlock()
				return parsed
			}
		}
	}
	return 0
}

const (
	settingKeyFollowedShows = "podcast_followed_shows"
	settingKeySavedEpisodes = "podcast_saved_episodes"
	settingKeyPodcastHist   = "podcast_history_episodes"
)

// GetLibraryState returns followed shows, saved episodes, and in-progress episodes from memory and SQLite.
func (e *Engine) GetLibraryState(ctx context.Context) *PodcastLibraryState {
	e.mu.Lock()
	repo := e.repo
	if repo != nil {
		if raw, err := repo.GetSetting(ctx, settingKeyFollowedShows); err == nil && raw != "" {
			var list []PodcastShow
			if json.Unmarshal([]byte(raw), &list) == nil {
				for _, s := range list {
					key := nonEmptyOr(s.ID, s.Title)
					if key != "" {
						e.followed[key] = s
					}
				}
			}
		}
		if raw, err := repo.GetSetting(ctx, settingKeySavedEpisodes); err == nil && raw != "" {
			var list []PodcastEpisode
			if json.Unmarshal([]byte(raw), &list) == nil {
				for _, ep := range list {
					if ep.ID != "" {
						e.saved[ep.ID] = ep
					}
				}
			}
		}
		if raw, err := repo.GetSetting(ctx, settingKeyPodcastHist); err == nil && raw != "" && len(e.history) == 0 {
			var list []PodcastEpisode
			if json.Unmarshal([]byte(raw), &list) == nil {
				e.history = list
			}
		}
	}

	followedList := make([]PodcastShow, 0, len(e.followed))
	for _, s := range e.followed {
		followedList = append(followedList, s)
	}

	savedList := make([]PodcastEpisode, 0, len(e.saved))
	for _, ep := range e.saved {
		savedList = append(savedList, ep)
	}

	historyCopy := make([]PodcastEpisode, len(e.history))
	copy(historyCopy, e.history)
	e.mu.Unlock()

	for i := range savedList {
		if pos := e.GetResumePosition(savedList[i].ID); pos > 0 {
			savedList[i].ResumePosMs = pos
		}
	}

	inProgress := make([]PodcastEpisode, 0, len(historyCopy))
	seenInProg := make(map[string]bool)
	for i := range historyCopy {
		ep := historyCopy[i]
		if pos := e.GetResumePosition(ep.ID); pos > 0 {
			ep.ResumePosMs = pos
		}
		if ep.ResumePosMs > 0 && !seenInProg[ep.ID] {
			seenInProg[ep.ID] = true
			inProgress = append(inProgress, ep)
		}
	}
	if len(inProgress) == 0 && len(historyCopy) > 0 {
		for _, ep := range historyCopy {
			if !seenInProg[ep.ID] {
				seenInProg[ep.ID] = true
				inProgress = append(inProgress, ep)
			}
		}
	}

	return &PodcastLibraryState{
		FollowedShows:      followedList,
		SavedEpisodes:      savedList,
		InProgressEpisodes: inProgress,
	}
}

// ToggleFollowShow toggles follow status for a podcast show and persists it to SQLite.
func (e *Engine) ToggleFollowShow(ctx context.Context, show PodcastShow) (bool, error) {
	key := strings.TrimSpace(nonEmptyOr(show.ID, show.Title))
	if key == "" {
		return false, fmt.Errorf("show id or title is required")
	}
	if show.ID == "" {
		show.ID = key
	}

	e.mu.Lock()
	var nowFollowed bool
	if _, exists := e.followed[key]; exists {
		delete(e.followed, key)
		nowFollowed = false
	} else {
		e.followed[key] = show
		nowFollowed = true
	}
	list := make([]PodcastShow, 0, len(e.followed))
	for _, s := range e.followed {
		list = append(list, s)
	}
	repo := e.repo
	e.mu.Unlock()

	if repo != nil {
		if encoded, err := json.Marshal(list); err == nil {
			_ = repo.SetSetting(ctx, settingKeyFollowedShows, string(encoded))
		}
	}
	return nowFollowed, nil
}

// ToggleSaveEpisode toggles bookmark status for a podcast episode and persists it to SQLite.
func (e *Engine) ToggleSaveEpisode(ctx context.Context, ep PodcastEpisode) (bool, error) {
	id := strings.TrimSpace(ep.ID)
	if id == "" {
		return false, fmt.Errorf("episode id is required")
	}

	e.mu.Lock()
	var nowSaved bool
	if _, exists := e.saved[id]; exists {
		delete(e.saved, id)
		nowSaved = false
	} else {
		e.saved[id] = ep
		nowSaved = true
	}
	list := make([]PodcastEpisode, 0, len(e.saved))
	for _, item := range e.saved {
		list = append(list, item)
	}
	repo := e.repo
	e.mu.Unlock()

	if repo != nil {
		if encoded, err := json.Marshal(list); err == nil {
			_ = repo.SetSetting(ctx, settingKeySavedEpisodes, string(encoded))
		}
	}
	return nowSaved, nil
}

// RecordEpisodeMetadata prepends/updates an episode in the recent podcast history (capped at 50).
func (e *Engine) RecordEpisodeMetadata(ctx context.Context, ep PodcastEpisode) {
	if strings.TrimSpace(ep.ID) == "" || strings.TrimSpace(ep.Title) == "" {
		return
	}
	e.mu.Lock()
	filtered := make([]PodcastEpisode, 0, len(e.history)+1)
	filtered = append(filtered, ep)
	for _, existing := range e.history {
		if existing.ID != ep.ID {
			filtered = append(filtered, existing)
		}
		if len(filtered) >= 50 {
			break
		}
	}
	e.history = filtered
	repo := e.repo
	e.mu.Unlock()

	if repo != nil {
		if encoded, err := json.Marshal(filtered); err == nil {
			_ = repo.SetSetting(ctx, settingKeyPodcastHist, string(encoded))
		}
	}
}

func (e *Engine) hydrateResumePositions(feed *PodcastDiscoveryFeed) {
	if feed == nil {
		return
	}
	for i := range feed.RecentEpisodes {
		feed.RecentEpisodes[i].ResumePosMs = e.GetResumePosition(feed.RecentEpisodes[i].ID)
	}
	for i := range feed.TopEpisodes {
		feed.TopEpisodes[i].ResumePosMs = e.GetResumePosition(feed.TopEpisodes[i].ID)
	}
	for i := range feed.GlobalEpisodes {
		feed.GlobalEpisodes[i].ResumePosMs = e.GetResumePosition(feed.GlobalEpisodes[i].ID)
	}
	for i := range feed.RegionalEpisodes {
		feed.RegionalEpisodes[i].ResumePosMs = e.GetResumePosition(feed.RegionalEpisodes[i].ID)
	}
}

func upscalePodcastArt(raw string) string {
	if raw == "" {
		return ""
	}
	if reApplePodcastArt.MatchString(raw) {
		return reApplePodcastArt.ReplaceAllString(raw, "/544x544bb")
	}
	return ytmusic.UpscaleThumbnail(raw)
}

func (e *Engine) fetchGlobalPodcastEpisodes(ctx context.Context) []PodcastEpisode {
	markets := []string{"us", "gb", "ca", "au"}
	perMarket := make([][]PodcastEpisode, len(markets))
	client := &http.Client{Timeout: 6 * time.Second}

	var wg sync.WaitGroup
	for idx, cc := range markets {
		wg.Add(1)
		go func(i int, market string) {
			defer wg.Done()
			perMarket[i] = fetchApplePodcastEpisodesRSS(ctx, client, market, 15)
		}(idx, cc)
	}
	wg.Wait()

	merged := make([]PodcastEpisode, 0, 40)
	seen := make(map[string]bool)
	for rank := 0; rank < 15; rank++ {
		for m := range markets {
			if rank < len(perMarket[m]) {
				ep := perMarket[m][rank]
				k := strings.ToLower(strings.TrimSpace(ep.Title) + "|" + strings.TrimSpace(ep.Author))
				if !seen[k] {
					seen[k] = true
					merged = append(merged, ep)
				}
			}
		}
	}
	return merged
}

func (e *Engine) fetchRegionalPodcastEpisodes(ctx context.Context, countryUpper string) []PodcastEpisode {
	lowerCC := strings.ToLower(strings.TrimSpace(countryUpper))
	if lowerCC == "" {
		lowerCC = "us"
	}
	client := &http.Client{Timeout: 5 * time.Second}

	var appleEps, itunesShows, ytLocalEps []PodcastEpisode
	var wg sync.WaitGroup
	wg.Add(3)
	go func() {
		defer wg.Done()
		appleEps = fetchApplePodcastEpisodesRSS(ctx, client, lowerCC, 20)
	}()
	go func() {
		defer wg.Done()
		itunesShows = fetchITunesTopPodcastsRSS(ctx, client, lowerCC, 20)
	}()
	go func() {
		defer wg.Done()
		regionQuery := fmt.Sprintf("top podcasts in %s full episode", countryUpper)
		if tracks, err := e.ytClient.SearchWithCategory(ctx, regionQuery, "podcast"); err == nil && len(tracks) > 0 {
			ytLocalEps, _ = e.partitionPodcastTracks(tracks, "Regional")
		}
	}()
	wg.Wait()

	merged := make([]PodcastEpisode, 0, 32)
	seen := make(map[string]bool)
	appendUnique := func(list []PodcastEpisode) {
		for _, ep := range list {
			if strings.TrimSpace(ep.Title) == "" {
				continue
			}
			k := strings.ToLower(strings.TrimSpace(ep.Title) + "|" + strings.TrimSpace(ep.Author))
			if !seen[k] {
				seen[k] = true
				merged = append(merged, ep)
			}
		}
	}
	appendUnique(appleEps)
	appendUnique(itunesShows)
	appendUnique(ytLocalEps)
	return merged
}

func fetchApplePodcastEpisodesRSS(ctx context.Context, client *http.Client, countryLower string, limit int) []PodcastEpisode {
	url := fmt.Sprintf("https://rss.applemarketingtools.com/api/v2/%s/podcasts/top/%d/podcast-episodes.json", countryLower, limit)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "UnboundMusic/2.0")

	resp, err := client.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return nil
	}

	var parsed struct {
		Feed struct {
			Results []struct {
				ID            string `json:"id"`
				Name          string `json:"name"`
				ArtistName    string `json:"artistName"`
				ReleaseDate   string `json:"releaseDate"`
				ArtworkURL100 string `json:"artworkUrl100"`
				Genres        []struct {
					Name string `json:"name"`
				} `json:"genres"`
			} `json:"results"`
		} `json:"feed"`
	}
	if json.Unmarshal(body, &parsed) != nil {
		return nil
	}

	out := make([]PodcastEpisode, 0, len(parsed.Feed.Results))
	for _, r := range parsed.Feed.Results {
		if strings.TrimSpace(r.Name) == "" {
			continue
		}
		cat := "Podcast"
		if len(r.Genres) > 0 && r.Genres[0].Name != "" {
			cat = r.Genres[0].Name
		}
		author := nonEmptyOr(r.ArtistName, "Podcast Creator")
		out = append(out, PodcastEpisode{
			ID:           fmt.Sprintf("apple_pod_%s_%s", countryLower, r.ID),
			ShowID:       author,
			ShowTitle:    author,
			Author:       author,
			Title:        strings.TrimSpace(r.Name),
			Description:  fmt.Sprintf("%s • %s", author, cat),
			Category:     cat,
			ThumbnailURL: upscalePodcastArt(r.ArtworkURL100),
			ReleaseDate:  nonEmptyOr(r.ReleaseDate, time.Now().Format("2006-01-02")),
		})
	}
	return out
}

func fetchITunesTopPodcastsRSS(ctx context.Context, client *http.Client, countryLower string, limit int) []PodcastEpisode {
	url := fmt.Sprintf("https://itunes.apple.com/%s/rss/toppodcasts/limit=%d/json", countryLower, limit)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "UnboundMusic/2.0")

	resp, err := client.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return nil
	}

	var parsed struct {
		Feed struct {
			Entry []struct {
				ID struct {
					Attributes struct {
						ImID string `json:"im:id"`
					} `json:"attributes"`
				} `json:"id"`
				Name struct {
					Label string `json:"label"`
				} `json:"im:name"`
				Artist struct {
					Label string `json:"label"`
				} `json:"im:artist"`
				Image []struct {
					Label string `json:"label"`
				} `json:"im:image"`
				Category struct {
					Attributes struct {
						Label string `json:"label"`
					} `json:"attributes"`
				} `json:"category"`
			} `json:"entry"`
		} `json:"feed"`
	}
	if json.Unmarshal(body, &parsed) != nil {
		return nil
	}

	out := make([]PodcastEpisode, 0, len(parsed.Feed.Entry))
	for _, entry := range parsed.Feed.Entry {
		title := strings.TrimSpace(entry.Name.Label)
		if title == "" {
			continue
		}
		thumb := ""
		if len(entry.Image) > 0 {
			thumb = upscalePodcastArt(entry.Image[len(entry.Image)-1].Label)
		}
		author := nonEmptyOr(entry.Artist.Label, title)
		id := nonEmptyOr(entry.ID.Attributes.ImID, fmt.Sprintf("%d", len(out)+1))
		cat := nonEmptyOr(entry.Category.Attributes.Label, "Podcast")
		out = append(out, PodcastEpisode{
			ID:           fmt.Sprintf("itunes_pod_%s_%s", countryLower, id),
			ShowID:       title,
			ShowTitle:    title,
			Author:       author,
			Title:        title,
			Description:  fmt.Sprintf("%s • %s", author, cat),
			Category:     cat,
			ThumbnailURL: thumb,
			ReleaseDate:  time.Now().Format("2006-01-02"),
		})
	}
	return out
}

func (e *Engine) partitionPodcastTracks(tracks []models.Track, category string) ([]PodcastEpisode, []PodcastShow) {
	var episodes []PodcastEpisode
	showsMap := make(map[string]*PodcastShow)
	var showOrder []string
	seenEpisodeIDs := make(map[string]bool)

	for _, t := range tracks {
		if t.ID == "" {
			continue
		}
		if t.ItemType == "album" || t.ItemType == "artist" {
			continue
		}
		// Exclude short music clips unless explicitly tagged as podcast/episode
		lowerTitle := strings.ToLower(t.Title)
		if t.DurationMs > 0 && t.DurationMs < 120000 &&
			!strings.Contains(lowerTitle, "podcast") &&
			!strings.Contains(lowerTitle, "episode") &&
			!strings.Contains(lowerTitle, "ep.") {
			continue
		}

		isPlaylistShow := t.ItemType == "playlist" || strings.HasPrefix(t.BrowseID, "VL") || strings.HasPrefix(t.BrowseID, "PL") || strings.HasPrefix(t.ID, "MPSP")
		showName := strings.TrimSpace(t.Album)
		if showName == "" {
			showName = strings.TrimSpace(t.Artist)
		}
		if showName == "" {
			showName = t.Title
		}

		if isPlaylistShow {
			showID := t.BrowseID
			if showID == "" {
				showID = t.ID
			}
			if _, exists := showsMap[showID]; !exists {
				showsMap[showID] = &PodcastShow{
					ID:           showID,
					Title:        t.Title,
					Author:       t.Artist,
					Description:  fmt.Sprintf("Podcast series by %s", nonEmptyOr(t.Artist, "Creator")),
					Category:     category,
					ThumbnailURL: t.ThumbnailURL,
					EpisodeCount: 1,
				}
				showOrder = append(showOrder, showID)
			}
			continue
		}

		if seenEpisodeIDs[t.ID] {
			continue
		}
		seenEpisodeIDs[t.ID] = true

		ep := PodcastEpisode{
			ID:           t.ID,
			ShowID:       showName,
			ShowTitle:    showName,
			Author:       nonEmptyOr(t.Artist, "Podcast Host"),
			Title:        t.Title,
			Description:  fmt.Sprintf("%s • %s", nonEmptyOr(t.Artist, "Podcast"), category),
			Category:     category,
			DurationMs:   t.DurationMs,
			ThumbnailURL: t.ThumbnailURL,
			ReleaseDate:  nonEmptyOr(t.Year, time.Now().Format("2006-01-02")),
			ResumePosMs:  e.GetResumePosition(t.ID),
		}
		episodes = append(episodes, ep)

		if _, exists := showsMap[showName]; !exists {
			showsMap[showName] = &PodcastShow{
				ID:           showName,
				Title:        showName,
				Author:       nonEmptyOr(t.Artist, "Podcast Host"),
				Description:  fmt.Sprintf("Episodes & discussions • %s", category),
				Category:     category,
				ThumbnailURL: t.ThumbnailURL,
				EpisodeCount: 1,
				Episodes:     []PodcastEpisode{ep},
			}
			showOrder = append(showOrder, showName)
		} else {
			showsMap[showName].EpisodeCount++
			showsMap[showName].Episodes = append(showsMap[showName].Episodes, ep)
		}
	}

	shows := make([]PodcastShow, 0, len(showOrder))
	for _, key := range showOrder {
		shows = append(shows, *showsMap[key])
		if len(shows) >= 10 {
			break
		}
	}

	return episodes, shows
}

func mapPodcastCategoryQuery(category string) string {
	switch strings.ToLower(strings.TrimSpace(category)) {
	case "technology", "tech":
		return "technology software engineering podcast full episode"
	case "science":
		return "science physics biology cosmos podcast full episode"
	case "true crime":
		return "true crime investigative documentary podcast episode"
	case "comedy":
		return "comedy standup talk show podcast full episode"
	case "business":
		return "business startups economics podcast full episode"
	case "history":
		return "history storytelling podcast full episode"
	case "news":
		return "daily news journalism world affairs podcast full episode"
	case "mindset", "health":
		return "mindset psychology neuroscience podcast full episode"
	default:
		return "top podcasts full episode conversations"
	}
}

func nonEmptyOr(v, fallback string) string {
	if strings.TrimSpace(v) == "" {
		return fallback
	}
	return v
}
