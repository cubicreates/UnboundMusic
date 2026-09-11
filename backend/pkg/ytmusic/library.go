/*
 * Package: ytmusic
 * File: library.go
 * Purpose: Extraction and management of authenticated YouTube Music user libraries: Liked Music (FLLM),
 *          custom user playlists, and like/unlike track mutations.
 * Subsystem: YouTube Integration Engine
 * Concurrency: Thread-safe client methods using context.Context.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

// PlaylistSummary represents a user's remote YouTube Music playlist.
type PlaylistSummary struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	TrackCount   int    `json:"track_count"`
	ThumbnailURL string `json:"thumbnail_url"`
}

var thumbRegex = regexp.MustCompile(`=w\d+-h\d+`)

// parseDurationMs converts a time string like "3:45" or "1:02:15" to milliseconds.
func parseDurationMs(durStr string) int64 {
	parts := strings.Split(durStr, ":")
	if len(parts) == 2 {
		m, _ := strconv.ParseInt(parts[0], 10, 64)
		s, _ := strconv.ParseInt(parts[1], 10, 64)
		return (m*60 + s) * 1000
	} else if len(parts) == 3 {
		h, _ := strconv.ParseInt(parts[0], 10, 64)
		m, _ := strconv.ParseInt(parts[1], 10, 64)
		s, _ := strconv.ParseInt(parts[2], 10, 64)
		return (h*3600 + m*60 + s) * 1000
	}
	return 0
}

// parseMusicResponsiveItem extracts a normalized Track model from a musicResponsiveListItemRenderer dictionary.
func parseMusicResponsiveItem(item map[string]interface{}) (models.Track, bool) {
	var track models.Track

	// Extract Title & VideoID from flexColumns[0]
	flexCols, ok := item["flexColumns"].([]interface{})
	if !ok || len(flexCols) == 0 {
		return track, false
	}

	col0, _ := flexCols[0].(map[string]interface{})
	flex0, _ := col0["musicResponsiveListItemFlexColumnRenderer"].(map[string]interface{})
	text0, _ := flex0["text"].(map[string]interface{})
	runs0, _ := text0["runs"].([]interface{})
	if len(runs0) == 0 {
		return track, false
	}

	firstRun0, _ := runs0[0].(map[string]interface{})
	track.Title, _ = firstRun0["text"].(string)

	navEndpoint, _ := firstRun0["navigationEndpoint"].(map[string]interface{})
	watchEndpoint, _ := navEndpoint["watchEndpoint"].(map[string]interface{})
	track.ID, _ = watchEndpoint["videoId"].(string)

	// If no watchEndpoint directly on run, check item navigationEndpoint
	if track.ID == "" {
		itemNav, _ := item["navigationEndpoint"].(map[string]interface{})
		itemWatch, _ := itemNav["watchEndpoint"].(map[string]interface{})
		track.ID, _ = itemWatch["videoId"].(string)
	}

	// Extract Artist and Album from flexColumns[1]
	if len(flexCols) > 1 {
		col1, _ := flexCols[1].(map[string]interface{})
		flex1, _ := col1["musicResponsiveListItemFlexColumnRenderer"].(map[string]interface{})
		text1, _ := flex1["text"].(map[string]interface{})
		runs1, _ := text1["runs"].([]interface{})

		var artistParts []string
		var albumPart string
		isAlbum := false

		for _, r := range runs1 {
			rm, _ := r.(map[string]interface{})
			txt, _ := rm["text"].(string)
			if txt == " • " {
				isAlbum = true
				continue
			}
			if !isAlbum {
				artistParts = append(artistParts, txt)
			} else if albumPart == "" {
				albumPart = txt
			}
		}
		track.Artist = strings.Join(artistParts, ", ")
		track.Album = albumPart
	}

	// Extract Duration from fixedColumns[0] if available
	if fixedCols, ok := item["fixedColumns"].([]interface{}); ok && len(fixedCols) > 0 {
		col0, _ := fixedCols[0].(map[string]interface{})
		fixed0, _ := col0["musicResponsiveListItemFixedColumnRenderer"].(map[string]interface{})
		textF, _ := fixed0["text"].(map[string]interface{})
		runsF, _ := textF["runs"].([]interface{})
		if len(runsF) > 0 {
			durRun, _ := runsF[0].(map[string]interface{})
			durText, _ := durRun["text"].(string)
			track.DurationMs = parseDurationMs(durText)
		}
	}

	// Extract Thumbnail and scale to high resolution =w800-h800
	if thumbObj, ok := item["thumbnail"].(map[string]interface{}); ok {
		if renderer, ok := thumbObj["musicThumbnailRenderer"].(map[string]interface{}); ok {
			if tObj, ok := renderer["thumbnail"].(map[string]interface{}); ok {
				if thumbs, ok := tObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok {
						track.ThumbnailURL = thumbRegex.ReplaceAllString(rawURL, "=w800-h800")
					}
				}
			}
		}
	}

	if track.Title == "" || track.ID == "" {
		return track, false
	}

	return track, true
}

// parseGenericVideoRenderer extracts a normalized Track model from standard YouTube video renderers.
func parseGenericVideoRenderer(item map[string]interface{}) (models.Track, bool) {
	var track models.Track
	vid, _ := item["videoId"].(string)
	if vid == "" {
		if nav, ok := item["navigationEndpoint"].(map[string]interface{}); ok {
			if w, ok := nav["watchEndpoint"].(map[string]interface{}); ok {
				vid, _ = w["videoId"].(string)
			}
		}
	}
	if vid == "" {
		if onSel, ok := item["onSelect"].(map[string]interface{}); ok {
			if w, ok := onSel["watchEndpoint"].(map[string]interface{}); ok {
				vid, _ = w["videoId"].(string)
			}
		}
	}
	if vid == "" {
		if cid, ok := item["contentId"].(string); ok && len(cid) == 11 {
			vid = cid
		}
	}
	if vid == "" {
		return track, false
	}
	track.ID = vid

	// Title
	if titleObj, ok := item["title"].(map[string]interface{}); ok {
		if s, ok := titleObj["simpleText"].(string); ok && s != "" {
			track.Title = s
		} else if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]interface{}); ok {
				track.Title, _ = r0["text"].(string)
			}
		}
	}

	// Artist / Channel
	byline := item["shortBylineText"]
	if byline == nil {
		byline = item["ownerText"]
	}
	if byline == nil {
		byline = item["longBylineText"]
	}
	if bylineObj, ok := byline.(map[string]interface{}); ok {
		if runs, ok := bylineObj["runs"].([]interface{}); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]interface{}); ok {
				track.Artist, _ = r0["text"].(string)
			}
		} else if s, ok := bylineObj["simpleText"].(string); ok {
			track.Artist = s
		}
	}
	if track.Artist == "" {
		track.Artist = "YouTube Artist"
	}
	track.Artist = strings.TrimSuffix(track.Artist, " - Topic")
	track.Artist = strings.TrimSuffix(track.Artist, "VEVO")

	// Thumbnail
	if thumbObj, ok := item["thumbnail"].(map[string]interface{}); ok {
		if thumbs, ok := thumbObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
			lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
			if rawURL, ok := lastThumb["url"].(string); ok {
				track.ThumbnailURL = thumbRegex.ReplaceAllString(rawURL, "=w800-h800")
			}
		}
	}
	if track.ThumbnailURL == "" {
		track.ThumbnailURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", vid)
	}

	if track.Title == "" {
		return track, false
	}
	return track, true
}

// parseTileRenderer extracts a Track model from a YouTube on TV tileRenderer dictionary.
func parseTileRenderer(item map[string]interface{}) (models.Track, bool) {
	var track models.Track
	vid, _ := item["contentId"].(string)
	if vid == "" {
		if onSel, ok := item["onSelect"].(map[string]interface{}); ok {
			if w, ok := onSel["watchEndpoint"].(map[string]interface{}); ok {
				vid, _ = w["videoId"].(string)
			}
		}
	}
	if vid == "" || len(vid) != 11 {
		return track, false
	}
	track.ID = vid

	// Metadata
	if meta, ok := item["metadata"].(map[string]interface{}); ok {
		if tileMeta, ok := meta["tileMetadataRenderer"].(map[string]interface{}); ok {
			if titleObj, ok := tileMeta["title"].(map[string]interface{}); ok {
				if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						track.Title, _ = r0["text"].(string)
					}
				} else if s, ok := titleObj["simpleText"].(string); ok {
					track.Title = s
				}
			}
			if lines, ok := tileMeta["lines"].([]interface{}); ok {
				for _, line := range lines {
					if lObj, ok := line.(map[string]interface{}); ok {
						if lr, ok := lObj["lineRenderer"].(map[string]interface{}); ok {
							if items, ok := lr["items"].([]interface{}); ok {
								for _, itm := range items {
									if itmObj, ok := itm.(map[string]interface{}); ok {
										if lir, ok := itmObj["lineItemRenderer"].(map[string]interface{}); ok {
											if tObj, ok := lir["text"].(map[string]interface{}); ok {
												if runs, ok := tObj["runs"].([]interface{}); ok && len(runs) > 0 {
													if r0, ok := runs[0].(map[string]interface{}); ok {
														txt, _ := r0["text"].(string)
														if txt != "" && !strings.Contains(txt, "views") && !strings.Contains(txt, "ago") {
															track.Artist = txt
															break
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
					if track.Artist != "" {
						break
					}
				}
			}
		}
	}

	// Thumbnail
	if header, ok := item["header"].(map[string]interface{}); ok {
		if tileHeader, ok := header["tileHeaderRenderer"].(map[string]interface{}); ok {
			if thumbObj, ok := tileHeader["thumbnail"].(map[string]interface{}); ok {
				if thumbs, ok := thumbObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok {
						track.ThumbnailURL = thumbRegex.ReplaceAllString(rawURL, "=w800-h800")
					}
				}
			}
		}
	}
	if track.ThumbnailURL == "" {
		track.ThumbnailURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", vid)
	}

	if track.Artist == "" {
		track.Artist = "YouTube Artist"
	}
	track.Artist = strings.TrimSuffix(track.Artist, " - Topic")
	track.Artist = strings.TrimSuffix(track.Artist, "VEVO")

	if track.Title == "" {
		return track, false
	}
	return track, true
}

// parseMusicTwoRowItemRenderer extracts a playable track from a YouTube Music two-row item renderer.
func parseMusicTwoRowItemRenderer(item map[string]interface{}) (models.Track, bool) {
	var track models.Track
	var vid string

	if nav, ok := item["navigationEndpoint"].(map[string]interface{}); ok {
		if watch, ok := nav["watchEndpoint"].(map[string]interface{}); ok {
			vid, _ = watch["videoId"].(string)
		}
	}
	if vid == "" {
		return track, false
	}
	track.ID = vid

	if titleObj, ok := item["title"].(map[string]interface{}); ok {
		if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]interface{}); ok {
				track.Title, _ = r0["text"].(string)
			}
		} else if s, ok := titleObj["simpleText"].(string); ok {
			track.Title = s
		}
	}
	if subObj, ok := item["subtitle"].(map[string]interface{}); ok {
		if runs, ok := subObj["runs"].([]interface{}); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]interface{}); ok {
				track.Artist, _ = r0["text"].(string)
			}
		}
	}
	if track.Artist == "" {
		track.Artist = "YouTube Artist"
	}
	track.Artist = strings.TrimSuffix(track.Artist, " - Topic")
	track.Artist = strings.TrimSuffix(track.Artist, "VEVO")

	if thumbObj, ok := item["thumbnailRenderer"].(map[string]interface{}); ok {
		if renderer, ok := thumbObj["musicThumbnailRenderer"].(map[string]interface{}); ok {
			if tObj, ok := renderer["thumbnail"].(map[string]interface{}); ok {
				if thumbs, ok := tObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok {
						track.ThumbnailURL = thumbRegex.ReplaceAllString(rawURL, "=w800-h800")
					}
				}
			}
		}
	}
	if track.ThumbnailURL == "" {
		track.ThumbnailURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", vid)
	}

	if track.Title == "" {
		return track, false
	}
	return track, true
}

// recursiveExtractTracks traverses an arbitrary InnerTube JSON tree searching for music and video renderer objects.
func recursiveExtractTracks(data interface{}, out *[]models.Track) {
	switch v := data.(type) {
	case map[string]interface{}:
		if item, ok := v["musicResponsiveListItemRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseMusicResponsiveItem(item); ok {
				*out = append(*out, tr)
			}
		}
		if item, ok := v["videoRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseGenericVideoRenderer(item); ok {
				*out = append(*out, tr)
			}
		}
		if item, ok := v["compactVideoRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseGenericVideoRenderer(item); ok {
				*out = append(*out, tr)
			}
		}
		if item, ok := v["playlistVideoRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseGenericVideoRenderer(item); ok {
				*out = append(*out, tr)
			}
		}
		if item, ok := v["gridVideoRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseGenericVideoRenderer(item); ok {
				*out = append(*out, tr)
			}
		}
		if item, ok := v["tileRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseTileRenderer(item); ok {
				*out = append(*out, tr)
			}
		}
		if item, ok := v["musicTwoRowItemRenderer"].(map[string]interface{}); ok {
			if tr, ok := parseMusicTwoRowItemRenderer(item); ok {
				*out = append(*out, tr)
			}
		}
		for _, child := range v {
			recursiveExtractTracks(child, out)
		}
	case []interface{}:
		for _, child := range v {
			recursiveExtractTracks(child, out)
		}
	}
}

// FetchLikedMusic queries InnerTube for the user's music taste from Watch History, Liked Videos, and Recommendations.
func (c *Client) FetchLikedMusic(ctx context.Context) ([]models.Track, error) {
	return c.FetchUserTasteAndHistory(ctx)
}

// FetchUserTasteAndHistory extracts music tracks directly from the user's authentic YouTube activity.
func (c *Client) FetchUserTasteAndHistory(ctx context.Context) ([]models.Track, error) {
	var allTracks []models.Track

	// 1. YouTube Watch History (FEhistory) - the primary source of user's active listening
	for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigTVHTML5Simply, ConfigWeb} {
		body := map[string]interface{}{
			"context":  c.buildContext(cfg),
			"browseId": "FEhistory",
		}
		if respBytes, err := c.post(ctx, "browse", body, cfg); err == nil {
			var root map[string]interface{}
			if err := json.Unmarshal(respBytes, &root); err == nil {
				recursiveExtractTracks(root, &allTracks)
				if len(allTracks) >= 20 {
					break
				}
			}
		}
	}

	// 2. YouTube Liked Videos (VLLL) & Library playlists (VLWM, VLWL, FElibrary)
	for _, browseID := range []string{"VLLL", "VLWM", "VLWL", "FElibrary"} {
		for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb} {
			body := map[string]interface{}{
				"context":  c.buildContext(cfg),
				"browseId": browseID,
			}
			if respBytes, err := c.post(ctx, "browse", body, cfg); err == nil {
				var root map[string]interface{}
				if err := json.Unmarshal(respBytes, &root); err == nil {
					recursiveExtractTracks(root, &allTracks)
					if len(allTracks) >= 30 {
						break
					}
				}
			}
		}
		if len(allTracks) >= 30 {
			break
		}
	}

	// 3. YouTube Music Liked Music across ANDROID_MUSIC and WEB_REMIX
	for _, cfg := range []ClientConfig{ConfigAndroidMusic, ConfigWebRemix} {
		for _, browseID := range []string{"FLLM", "LM", "VLLM"} {
			body := map[string]interface{}{
				"context":  c.buildContext(cfg),
				"browseId": browseID,
			}
			if respBytes, err := c.post(ctx, "browse", body, cfg); err == nil {
				var root map[string]interface{}
				if err := json.Unmarshal(respBytes, &root); err == nil {
					recursiveExtractTracks(root, &allTracks)
					if len(allTracks) >= 40 {
						break
					}
				}
			}
		}
		if len(allTracks) >= 40 {
			break
		}
	}

	// 4. Personalized YouTube Home Recommendations (FEwhat_to_watch)
	if len(allTracks) < 10 {
		for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb} {
			body := map[string]interface{}{
				"context":  c.buildContext(cfg),
				"browseId": "FEwhat_to_watch",
			}
			if respBytes, err := c.post(ctx, "browse", body, cfg); err == nil {
				var root map[string]interface{}
				if err := json.Unmarshal(respBytes, &root); err == nil {
					recursiveExtractTracks(root, &allTracks)
				}
			}
		}
	}

	// Filter all items so strictly music tracks/videos are preserved
	filtered := gatekeeper.FilterMusicTracks(allTracks)
	// If strict music filter yields 0 tracks, use relaxed filter to retain user's content
	if len(filtered) == 0 && len(allTracks) > 0 {
		filtered = gatekeeper.FilterRelaxedTracks(allTracks)
	}

	// Deduplicate by track ID
	seen := make(map[string]bool)
	unique := make([]models.Track, 0, len(filtered))
	for _, t := range filtered {
		if !seen[t.ID] {
			seen[t.ID] = true
			unique = append(unique, t)
		}
	}

	return unique, nil
}

// FetchUserPlaylists retrieves the user's custom and liked playlists.
func (c *Client) FetchUserPlaylists(ctx context.Context) ([]PlaylistSummary, error) {
	body := map[string]interface{}{
		"context":  c.buildContext(ConfigWebRemix),
		"browseId": "FEmusic_liked_playlists",
	}

	respBytes, err := c.post(ctx, "browse", body, ConfigWebRemix)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch user playlists: %w", err)
	}

	var root map[string]interface{}
	if err := json.Unmarshal(respBytes, &root); err != nil {
		return nil, fmt.Errorf("failed to decode playlists response: %w", err)
	}

	var playlists []PlaylistSummary
	// Search for playlist renderers
	var findPlaylists func(val interface{})
	findPlaylists = func(val interface{}) {
		switch v := val.(type) {
		case map[string]interface{}:
			if item, ok := v["musicTwoRowItemRenderer"].(map[string]interface{}); ok {
				var p PlaylistSummary
				if titleObj, ok := item["title"].(map[string]interface{}); ok {
					if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 {
						r, _ := runs[0].(map[string]interface{})
						p.Title, _ = r["text"].(string)
					}
				}
				if nav, ok := item["navigationEndpoint"].(map[string]interface{}); ok {
					if bEndpoint, ok := nav["browseEndpoint"].(map[string]interface{}); ok {
						p.ID, _ = bEndpoint["browseId"].(string)
					}
				}
				if p.ID != "" && p.Title != "" {
					playlists = append(playlists, p)
				}
			}
			for _, child := range v {
				findPlaylists(child)
			}
		case []interface{}:
			for _, child := range v {
				findPlaylists(child)
			}
		}
	}

	findPlaylists(root)
	return playlists, nil
}

// LikeTrack sends a like mutation to YouTube Music for the specified video ID.
func (c *Client) LikeTrack(ctx context.Context, videoID string) error {
	body := map[string]interface{}{
		"context": c.buildContext(ConfigWebRemix),
		"target": map[string]interface{}{
			"videoId": videoID,
		},
	}

	_, err := c.post(ctx, "like/like", body, ConfigWebRemix)
	return err
}

// UnlikeTrack sends a remove-like mutation to YouTube Music for the specified video ID.
func (c *Client) UnlikeTrack(ctx context.Context, videoID string) error {
	body := map[string]interface{}{
		"context": c.buildContext(ConfigWebRemix),
		"target": map[string]interface{}{
			"videoId": videoID,
		},
	}

	_, err := c.post(ctx, "like/removelike", body, ConfigWebRemix)
	return err
}
