/*
 * Package: lyrics
 * File: recognize.go
 * Purpose: Multi-candidate lyrics and singing recognition engine combining Local SQLite Vault,
 *          LRCLIB, Genius multi-section search, YouTube Music catalog hydration, and Edge AI disambiguation.
 * Subsystem: Lyrics & Recognition Engine
 * Concurrency: Thread-safe parallel source querying with bounded timeout.
 */

package lyrics

import (
	"context"
	"fmt"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// LyricsRecognizer orchestrates multi-source lyrics-to-song candidate matching.
type LyricsRecognizer struct {
	repo   *database.Repository
	lrclib *LRCLIBClient
	genius *genius.Client
	yt     *ytmusic.Client
}

// NewLyricsRecognizer constructs a LyricsRecognizer with optional subsystems.
func NewLyricsRecognizer(
	repo *database.Repository,
	lrclib *LRCLIBClient,
	geniusCli *genius.Client,
	ytClient *ytmusic.Client,
) *LyricsRecognizer {
	if lrclib == nil {
		lrclib = NewLRCLIBClient()
	}
	return &LyricsRecognizer{
		repo:   repo,
		lrclib: lrclib,
		genius: geniusCli,
		yt:     ytClient,
	}
}

// Recognize queries all available lyrics and catalog sources concurrently and returns ranked candidate matches.
func (r *LyricsRecognizer) Recognize(ctx context.Context, rawQuery string, mode string, limit int) (*models.RecognitionResponse, error) {
	start := time.Now()
	query := strings.TrimSpace(rawQuery)
	if query == "" {
		return nil, fmt.Errorf("query cannot be empty")
	}
	if mode == "" {
		mode = "lyrics"
	}
	if limit <= 0 {
		limit = 8
	}
	if limit > 12 {
		limit = 12
	}

	boundedCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
	defer cancel()

	var (
		mu         sync.Mutex
		wg         sync.WaitGroup
		vaultHits  []models.RecognitionCandidate
		lrcHits    []LRCLIBCandidate
		geniusHits []genius.SongHit
		ytHits     []models.Track
	)

	// 1. Local SQLite Vault
	if r.repo != nil {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if cands, err := r.repo.SearchLyricsCandidates(boundedCtx, query, limit); err == nil && len(cands) > 0 {
				mu.Lock()
				vaultHits = cands
				mu.Unlock()
			}
		}()
	}

	// 2. LRCLIB Multi-Search
	if r.lrclib != nil {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if cands, err := r.lrclib.SearchMultiple(boundedCtx, query, limit); err == nil && len(cands) > 0 {
				mu.Lock()
				lrcHits = cands
				mu.Unlock()
			}
		}()
	}

	// 3. Genius Multi-Section Search
	if r.genius != nil {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if hits, err := r.genius.SearchSongsMulti(boundedCtx, query, limit); err == nil && len(hits) > 0 {
				mu.Lock()
				geniusHits = hits
				mu.Unlock()
			}
		}()
	}

	// 4. YouTube Music Music-Only Search
	if r.yt != nil {
		wg.Add(1)
		go func() {
			defer wg.Done()
			var combined []models.Track
			seenYT := make(map[string]bool)

			if tracks, err := r.yt.SearchWithCategory(boundedCtx, query, "music"); err == nil {
				for _, t := range tracks {
					if !isValidMusicCandidateTrack(t) {
						continue
					}
					if !seenYT[t.ID] {
						seenYT[t.ID] = true
						combined = append(combined, t)
					}
				}
			}

			if len(strings.Fields(query)) >= 3 && len(combined) < limit {
				if lTracks, err := r.yt.SearchWithCategory(boundedCtx, query+" lyrics", "music"); err == nil {
					for _, t := range lTracks {
						if !isValidMusicCandidateTrack(t) {
							continue
						}
						if !seenYT[t.ID] {
							seenYT[t.ID] = true
							combined = append(combined, t)
						}
					}
				}
			}

			mu.Lock()
			ytHits = combined
			mu.Unlock()
		}()
	}

	wg.Wait()

	merged := MergeRecognitionCandidates(query, vaultHits, lrcHits, geniusHits, ytHits)

	// Hydrate any non-YouTube candidate (e.g. Genius/LRCLIB without 11-char ID) with a playable YouTube track if available
	if r.yt != nil {
		hydrateMissingTrackIDs(boundedCtx, r.yt, merged, 4)
	}

	if len(merged) > limit {
		merged = merged[:limit]
	}

	msg := ""
	if len(merged) == 0 {
		msg = fmt.Sprintf("No matching songs found for %q. Try another lyric line or artist hint.", query)
	} else {
		msg = fmt.Sprintf("Found %d possible matches for %q", len(merged), query)
	}

	return &models.RecognitionResponse{
		Query:      query,
		Mode:       mode,
		Matched:    len(merged) > 0,
		Count:      len(merged),
		Candidates: merged,
		Variants:   merged,
		LatencyMs:  time.Since(start).Milliseconds(),
		Message:    msg,
	}, nil
}

// MergeRecognitionCandidates merges, deduplicates, boosts multi-source consensus, and sorts candidates by confidence.
func MergeRecognitionCandidates(
	query string,
	vaultHits []models.RecognitionCandidate,
	lrcHits []LRCLIBCandidate,
	geniusHits []genius.SongHit,
	ytHits []models.Track,
) []models.RecognitionCandidate {
	type entry struct {
		cand        models.RecognitionCandidate
		sourceCount int
	}

	byKey := make(map[string]*entry)
	var order []string

	upsert := func(c models.RecognitionCandidate) {
		if strings.TrimSpace(c.Title) == "" {
			return
		}
		res := ai.DeduceCanonicalOriginal(c.Title, c.Artist)
		c.IsOriginal = !res.IsRemixOrCover
		key := normalizeCandidateKey(c.Title, c.Artist)

		if existing, ok := byKey[key]; ok {
			existing.sourceCount++
			// Multi-source agreement confidence boost
			boosted := existing.cand.Confidence
			if c.Confidence > boosted {
				boosted = c.Confidence
			}
			boosted += 0.06
			if boosted > 0.99 {
				boosted = 0.99
			}
			existing.cand.Confidence = boosted

			// Prefer playable 11-char YouTube ID & artwork
			if !isPlayableYouTubeID(existing.cand.ID) && isPlayableYouTubeID(c.ID) {
				existing.cand.ID = c.ID
			}
			if existing.cand.CoverURL == "" && c.CoverURL != "" {
				existing.cand.CoverURL = c.CoverURL
				existing.cand.ThumbnailURL = c.CoverURL
			}
			if existing.cand.Album == "" && c.Album != "" {
				existing.cand.Album = c.Album
			}
			if existing.cand.DurationMs == 0 && c.DurationMs > 0 {
				existing.cand.DurationMs = c.DurationMs
			}
			if existing.cand.MatchedLyric == "" && c.MatchedLyric != "" {
				existing.cand.MatchedLyric = c.MatchedLyric
			}
			if existing.cand.MatchedLyric != "" && !strings.Contains(strings.ToLower(existing.cand.RelevanceLabel), "lyric") {
				existing.cand.RelevanceLabel = "Verified Lyric Match"
			}
			return
		}

		byKey[key] = &entry{cand: c, sourceCount: 1}
		order = append(order, key)
	}

	// 1. Local Vault hits
	for _, c := range vaultHits {
		upsert(c)
	}

	// 2. LRCLIB hits
	for _, l := range lrcHits {
		label := "Lyrics Match"
		if l.MatchedLyric != "" {
			label = "Exact Lyric Match"
		}
		upsert(models.RecognitionCandidate{
			ID:             fmt.Sprintf("lrc_%s", strings.ReplaceAll(strings.ToLower(l.TrackName), " ", "_")),
			Title:          l.TrackName,
			Artist:         l.ArtistName,
			Album:          l.AlbumName,
			DurationMs:     l.DurationMs,
			Confidence:     l.Confidence,
			RelevanceLabel: label,
			MatchedLyric:   l.MatchedLyric,
			MatchSource:    "lrclib",
			Explanation:    "Matched in LRCLIB synced lyrics database",
		})
	}

	// 3. Genius hits
	for idx, g := range geniusHits {
		conf := 0.86 - float64(idx)*0.03
		label := "Genius Song Match"
		if g.SectionType == "lyric" || g.MatchedLyric != "" {
			conf = 0.93 - float64(idx)*0.025
			label = "Genius Lyric Match"
		} else if g.SectionType == "top_hit" {
			conf = 0.90
			label = "Genius Top Match"
		}
		if conf < 0.55 {
			conf = 0.55
		}
		upsert(models.RecognitionCandidate{
			ID:             fmt.Sprintf("genius_%d", g.ID),
			Title:          g.Title,
			Artist:         g.Artist,
			CoverURL:       g.ThumbnailURL,
			ThumbnailURL:   g.ThumbnailURL,
			Confidence:     conf,
			RelevanceLabel: label,
			MatchedLyric:   g.MatchedLyric,
			MatchSource:    "genius",
			Explanation:    "Matched via Genius lyric & song index",
		})
	}

	// 4. YouTube Music catalog hits
	lowerQ := strings.ToLower(strings.TrimSpace(query))
	for idx, t := range ytHits {
		conf := 0.80 - float64(idx)*0.035
		label := "Catalog Match"
		if strings.Contains(strings.ToLower(t.Title), lowerQ) {
			conf = 0.85 - float64(idx)*0.025
			label = "Title & Audio Match"
		}
		if conf < 0.50 {
			conf = 0.50
		}
		upsert(models.RecognitionCandidate{
			ID:             t.ID,
			Title:          t.Title,
			Artist:         t.Artist,
			Album:          t.Album,
			DurationMs:     t.DurationMs,
			CoverURL:       t.ThumbnailURL,
			ThumbnailURL:   t.ThumbnailURL,
			Year:           t.Year,
			Confidence:     conf,
			RelevanceLabel: label,
			MatchSource:    "ytmusic",
			Explanation:    "Playable track in YouTube Music catalog",
		})
	}

	merged := make([]models.RecognitionCandidate, 0, len(order))
	for _, k := range order {
		merged = append(merged, byKey[k].cand)
	}

	sort.SliceStable(merged, func(i, j int) bool {
		if merged[i].Confidence != merged[j].Confidence {
			return merged[i].Confidence > merged[j].Confidence
		}
		return isPlayableYouTubeID(merged[i].ID) && !isPlayableYouTubeID(merged[j].ID)
	})

	return merged
}

func hydrateMissingTrackIDs(ctx context.Context, yt *ytmusic.Client, candidates []models.RecognitionCandidate, maxLookups int) {
	lookups := 0
	for i := range candidates {
		if lookups >= maxLookups {
			break
		}
		if isPlayableYouTubeID(candidates[i].ID) {
			continue
		}
		q := strings.TrimSpace(candidates[i].Title + " " + candidates[i].Artist)
		if q == "" {
			continue
		}
		lookups++
		tracks, err := yt.SearchWithCategory(ctx, q, "music")
		if err != nil {
			continue
		}
		for _, t := range tracks {
			if isValidMusicCandidateTrack(t) {
				candidates[i].ID = t.ID
				if candidates[i].CoverURL == "" && t.ThumbnailURL != "" {
					candidates[i].CoverURL = t.ThumbnailURL
					candidates[i].ThumbnailURL = t.ThumbnailURL
				}
				if candidates[i].Album == "" && t.Album != "" {
					candidates[i].Album = t.Album
				}
				if candidates[i].DurationMs == 0 && t.DurationMs > 0 {
					candidates[i].DurationMs = t.DurationMs
				}
				break
			}
		}
	}
}

func isValidMusicCandidateTrack(t models.Track) bool {
	if t.ID == "" {
		return false
	}
	if t.ItemType == "album" || t.ItemType == "artist" || t.ItemType == "playlist" ||
		strings.HasPrefix(t.ID, "UC") || strings.HasPrefix(t.ID, "MPREb_") ||
		strings.HasPrefix(t.ID, "VL") || strings.HasPrefix(t.ID, "PL") {
		return false
	}
	if t.DurationMs > 1200000 { // exclude >20m podcasts/essays
		return false
	}
	return true
}

func isPlayableYouTubeID(id string) bool {
	return len(id) == 11 && !strings.HasPrefix(id, "lrc_") && !strings.HasPrefix(id, "genius_")
}

func normalizeCandidateKey(title, artist string) string {
	t := strings.ToLower(strings.TrimSpace(title))
	a := strings.ToLower(strings.TrimSpace(artist))
	if idx := strings.Index(a, ","); idx > 0 {
		a = strings.TrimSpace(a[:idx])
	}
	return t + "::" + a
}
