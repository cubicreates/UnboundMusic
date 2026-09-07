/*
 * Package: ytmusic
 * File: radio.go
 * Purpose: Magic "Now Playing" Serendipity Radio generator combining InnerTube candidates with on-device re-ranking.
 * Subsystem: On-Device Taste Engine & Serendipity Sequencing
 * Concurrency: Thread-safe radio generation utilizing background worker contexts.
 */

package ytmusic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/analytics"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
)

// MagicRadioResponse encapsulates the generated serendipity radio mix.
type MagicRadioResponse struct {
	SeedTrack   models.TrackItem   `json:"seed_track"`
	Queue       []models.TrackItem `json:"queue"`
	Source      string             `json:"source"` // "youtube_hybrid" or "offline_storage"
	GeneratedMs int64              `json:"generated_ms"`
}

// RadioGenerator orchestrates candidate retrieval, local affinity re-ranking, and offline fallback.
type RadioGenerator struct {
	client     *Client
	repo       *database.Repository
	markov     *analytics.MarkovTracker
	reranker   *recommender.ReRanker
	httpClient *http.Client
}

// NewRadioGenerator creates a new serendipity radio generator.
func NewRadioGenerator(client *Client, repo *database.Repository, markov *analytics.MarkovTracker, reranker *recommender.ReRanker) *RadioGenerator {
	if reranker == nil {
		reranker = recommender.NewReRanker()
	}
	if markov == nil && repo != nil {
		markov = analytics.NewMarkovTracker(repo)
	}

	return &RadioGenerator{
		client:   client,
		repo:     repo,
		markov:   markov,
		reranker: reranker,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// GenerateMagicRadio generates a personalized 25-track serendipity queue in < 300ms.
func (rg *RadioGenerator) GenerateMagicRadio(ctx context.Context, localHour int, seedTrackID string) (*MagicRadioResponse, error) {
	start := time.Now()

	// 1. Resolve Seed Track
	seed, err := rg.resolveSeedTrack(ctx, localHour, seedTrackID)
	if err != nil || seed.ID == "" {
		// Fallback to offline local storage if seed could not be fetched
		return rg.fallbackToOfflineStorage(start)
	}

	// 2. Fetch Candidates from InnerTube /youtubei/v1/next
	candidates, err := rg.fetchNextCandidates(ctx, seed.ID)
	if err != nil || len(candidates) == 0 {
		// Try search fallback if /next failed
		searchTracks, searchErr := rg.client.Search(ctx, seed.Artist)
		if searchErr == nil && len(searchTracks) > 0 {
			for _, st := range searchTracks {
				candidates = append(candidates, models.TrackItem{
					ID:         st.ID,
					Title:      st.Title,
					Artist:     st.Artist,
					Album:      st.Album,
					DurationMs: st.DurationMs,
					Thumbnail:  st.ThumbnailURL,
					Source:     "youtube",
				})
			}
		} else {
			return rg.fallbackToOfflineStorage(start)
		}
	}

	// 3. Query Local Affinity Map
	affinityMap := make(map[string]models.ArtistAffinity)
	if rg.repo != nil {
		affList, _ := rg.repo.GetTopAffinityArtists(100)
		for _, a := range affList {
			affinityMap[a.ArtistName] = a
			affinityMap[a.ArtistID] = a
		}
	}

	// 4. Query Markov Transitional Probabilities
	var candidateIDs []string
	for _, c := range candidates {
		candidateIDs = append(candidateIDs, c.ID)
	}
	var markovScores map[string]float64
	if rg.markov != nil {
		markovScores = rg.markov.ScoreCandidates(seed.ID, candidateIDs)
	}

	// 5. Tier 2 Local Re-Ranking (Affinity + Markov + Novelty Injection)
	queue := rg.reranker.ReRankCandidates(seed, candidates, affinityMap, markovScores, 25)

	return &MagicRadioResponse{
		SeedTrack:   seed,
		Queue:       queue,
		Source:      "youtube_hybrid",
		GeneratedMs: time.Since(start).Milliseconds(),
	}, nil
}

// resolveSeedTrack selects an optimal seed based on explicit ID, highest affinity artist, or charts.
func (rg *RadioGenerator) resolveSeedTrack(ctx context.Context, localHour int, seedTrackID string) (models.TrackItem, error) {
	if seedTrackID != "" {
		return models.TrackItem{
			ID:     seedTrackID,
			Title:  "Selected Track",
			Artist: "Various Artists",
			Source: "youtube",
		}, nil
	}

	// 1. Check highest affinity artist in SQLite
	if rg.repo != nil {
		top, err := rg.repo.GetTopAffinityArtists(1)
		if err == nil && len(top) > 0 && top[0].ArtistName != "" {
			searchTracks, err := rg.client.Search(ctx, top[0].ArtistName)
			if err == nil && len(searchTracks) > 0 {
				st := searchTracks[0]
				return models.TrackItem{
					ID:         st.ID,
					Title:      st.Title,
					Artist:     st.Artist,
					Album:      st.Album,
					DurationMs: st.DurationMs,
					Thumbnail:  st.ThumbnailURL,
					Source:     "youtube",
				}, nil
			}
		}
	}

	// 2. Cold Start Fallback: Regional Billboard Top 1
	explore := NewExploreEngine(rg.repo)
	charts, err := explore.FetchRegionalCharts(ctx, "US", "en")
	if err == nil && len(charts) > 0 {
		return charts[0], nil
	}

	return models.TrackItem{}, fmt.Errorf("unable to resolve seed track")
}

// fetchNextCandidates queries InnerTube /youtubei/v1/next with the seed video ID.
func (rg *RadioGenerator) fetchNextCandidates(ctx context.Context, videoID string) ([]models.TrackItem, error) {
	body := map[string]any{
		"context": map[string]any{
			"client": map[string]any{
				"clientName":    "WEB_REMIX",
				"clientVersion": "1.20240101.01.00",
				"hl":            "en",
				"gl":            "US",
			},
		},
		"videoId": videoID,
	}

	jsonBytes, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://music.youtube.com/youtubei/v1/next", bytes.NewReader(jsonBytes))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
	req.Header.Set("Referer", "https://music.youtube.com/")
	req.Header.Set("x-origin", "https://music.youtube.com")

	resp, err := rg.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("InnerTube /next returned HTTP %d", resp.StatusCode)
	}

	respData, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	return ParseNextTracks(respData)
}

// ParseNextTracks extracts candidate tracks from InnerTube /next response JSON.
func ParseNextTracks(jsonData []byte) ([]models.TrackItem, error) {
	var root map[string]any
	if err := json.Unmarshal(jsonData, &root); err != nil {
		return nil, err
	}

	var tracks []models.TrackItem
	seenIDs := make(map[string]bool)

	// Recursive traversal to find playlistPanelVideoRenderer or musicResponsiveListItemRenderer
	var extract func(val any)
	extract = func(val any) {
		switch node := val.(type) {
		case map[string]any:
			// 1. playlistPanelVideoRenderer
			if panel, ok := node["playlistPanelVideoRenderer"].(map[string]any); ok {
				if vid, ok := panel["videoId"].(string); ok && !seenIDs[vid] {
					seenIDs[vid] = true
					item := models.TrackItem{
						ID:     vid,
						Source: "youtube",
					}

					// Title
					if titleObj, ok := panel["title"].(map[string]any); ok {
						if runs, ok := titleObj["runs"].([]any); ok && len(runs) > 0 {
							if r0, ok := runs[0].(map[string]any); ok {
								item.Title, _ = r0["text"].(string)
							}
						}
					}

					// Artist
					if subObj, ok := panel["shortBylineText"].(map[string]any); ok {
						if runs, ok := subObj["runs"].([]any); ok && len(runs) > 0 {
							if r0, ok := runs[0].(map[string]any); ok {
								item.Artist, _ = r0["text"].(string)
							}
						}
					}

					// Thumbnail
					if thumbObj, ok := panel["thumbnail"].(map[string]any); ok {
						if thumbs, ok := thumbObj["thumbnails"].([]any); ok && len(thumbs) > 0 {
							if last, ok := thumbs[len(thumbs)-1].(map[string]any); ok {
								if u, ok := last["url"].(string); ok {
									item.Thumbnail = UpscaleThumbnail(u)
								}
							}
						}
					}

					if item.Title != "" {
						tracks = append(tracks, item)
					}
				}
			}

			// Continue traversal
			for _, v := range node {
				extract(v)
			}

		case []any:
			for _, elem := range node {
				extract(elem)
			}
		}
	}

	extract(root)

	// Fallback to ParseBrowseTracks if playlistPanelVideoRenderer was not found
	if len(tracks) == 0 {
		return ParseBrowseTracks(jsonData)
	}

	return tracks, nil
}

// fallbackToOfflineStorage produces a smart shuffle from local indexed tracks when network is unavailable.
func (rg *RadioGenerator) fallbackToOfflineStorage(start time.Time) (*MagicRadioResponse, error) {
	if rg.repo == nil {
		return nil, fmt.Errorf("offline storage unavailable: database repository is nil")
	}

	locals, err := rg.repo.GetAllLocalTracks(context.Background())
	if err != nil || len(locals) == 0 {
		return nil, fmt.Errorf("no offline tracks found in local storage")
	}

	// Shuffle local tracks
	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	rng.Shuffle(len(locals), func(i, j int) {
		locals[i], locals[j] = locals[j], locals[i]
	})

	var queue []models.TrackItem
	for i, lt := range locals {
		if i >= 25 {
			break
		}
		queue = append(queue, models.TrackItem{
			ID:         lt.ID,
			Title:      lt.Title,
			Artist:     lt.Artist,
			Album:      lt.Album,
			DurationMs: lt.DurationMs,
			FilePath:   "file://" + lt.FilePath,
			Source:     "local",
		})
	}

	seed := queue[0]

	return &MagicRadioResponse{
		SeedTrack:   seed,
		Queue:       queue,
		Source:      "offline_storage",
		GeneratedMs: time.Since(start).Milliseconds(),
	}, nil
}
