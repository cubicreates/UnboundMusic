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

// MixItem represents a curated YouTube song mix or artist station.
type MixItem struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	Subtitle     string `json:"subtitle"`
	ThumbnailURL string `json:"thumbnail_url"`
}

var thumbRegex = regexp.MustCompile(`=w\d+-h\d+`)

// formatMusicFirstThumbnail formats a thumbnail URL prioritizing YouTube Music square artwork, with YouTube fallback.
func formatMusicFirstThumbnail(rawURL string, vid string) string {
	if rawURL != "" {
		if strings.Contains(rawURL, "googleusercontent.com") || strings.Contains(rawURL, "ggpht.com") {
			return thumbRegex.ReplaceAllString(rawURL, "=w800-h800")
		}
		return rawURL
	}
	if vid != "" {
		return fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", vid)
	}
	return ""
}

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
	if len(runs0) > 0 {
		firstRun0, _ := runs0[0].(map[string]interface{})
		track.Title, _ = firstRun0["text"].(string)

		navEndpoint, _ := firstRun0["navigationEndpoint"].(map[string]interface{})
		watchEndpoint, _ := navEndpoint["watchEndpoint"].(map[string]interface{})
		track.ID, _ = watchEndpoint["videoId"].(string)
	} else if s, ok := text0["simpleText"].(string); ok && s != "" {
		track.Title = s
	}

	// 1. If no watchEndpoint directly on run 0, check other runs in flexColumns[0]
	if track.ID == "" && len(runs0) > 1 {
		for _, r := range runs0[1:] {
			if rMap, ok := r.(map[string]interface{}); ok {
				if nav, ok := rMap["navigationEndpoint"].(map[string]interface{}); ok {
					if w, ok := nav["watchEndpoint"].(map[string]interface{}); ok {
						if vid, ok := w["videoId"].(string); ok && vid != "" {
							track.ID = vid
							break
						}
					}
				}
			}
		}
	}

	// 2. Check playlistItemData (standard on YouTube Music FLLM / Liked Music)
	if track.ID == "" {
		if pid, ok := item["playlistItemData"].(map[string]interface{}); ok {
			if vid, ok := pid["videoId"].(string); ok && vid != "" {
				track.ID = vid
			}
		}
	}

	// 3. Check overlay play button watchEndpoint
	if track.ID == "" {
		if overlay, ok := item["overlay"].(map[string]interface{}); ok {
			if thumbOverlay, ok := overlay["musicItemThumbnailOverlayRenderer"].(map[string]interface{}); ok {
				if content, ok := thumbOverlay["content"].(map[string]interface{}); ok {
					if playBtn, ok := content["musicPlayButtonRenderer"].(map[string]interface{}); ok {
						if playNav, ok := playBtn["playNavigationEndpoint"].(map[string]interface{}); ok {
							if watch, ok := playNav["watchEndpoint"].(map[string]interface{}); ok {
								if vid, ok := watch["videoId"].(string); ok && vid != "" {
									track.ID = vid
								}
							}
						}
					}
				}
			}
		}
	}

	// 4. Check item navigationEndpoint
	if track.ID == "" {
		if itemNav, ok := item["navigationEndpoint"].(map[string]interface{}); ok {
			if itemWatch, ok := itemNav["watchEndpoint"].(map[string]interface{}); ok {
				track.ID, _ = itemWatch["videoId"].(string)
			}
		}
	}

	// 5. Check top-level videoId
	if track.ID == "" {
		if vid, ok := item["videoId"].(string); ok && vid != "" {
			track.ID = vid
		}
	}

	// 6. Check onSelect watchEndpoint
	if track.ID == "" {
		if onSel, ok := item["onSelect"].(map[string]interface{}); ok {
			if w, ok := onSel["watchEndpoint"].(map[string]interface{}); ok {
				track.ID, _ = w["videoId"].(string)
			}
		}
	}

	// 7. Check doubleTapEndpoint
	if track.ID == "" {
		if dt, ok := item["doubleTapEndpoint"].(map[string]interface{}); ok {
			if w, ok := dt["watchEndpoint"].(map[string]interface{}); ok {
				track.ID, _ = w["videoId"].(string)
			}
		}
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
	if track.Artist == "" {
		track.Artist = "YouTube Artist"
	}
	track.Artist = strings.TrimSuffix(track.Artist, " - Topic")
	track.Artist = strings.TrimSuffix(track.Artist, "VEVO")

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
	if track.ThumbnailURL == "" && track.ID != "" {
		track.ThumbnailURL = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", track.ID)
	}

	if track.Title == "" || track.ID == "" {
		return track, false
	}

	return track, true
}

// parseGenericVideoRenderer extracts a normalized Track model from standard YouTube video renderers.
func parseGenericVideoRenderer(item map[string]interface{}) (models.Track, bool) {
	var track models.Track

	// 1. Shorts / Reel Endpoint Detection -> Immediate Rejection
	if nav, ok := item["navigationEndpoint"].(map[string]interface{}); ok {
		if _, ok := nav["reelWatchEndpoint"]; ok {
			return track, false
		}
		if cmdMeta, ok := nav["commandMetadata"].(map[string]interface{}); ok {
			if webMeta, ok := cmdMeta["webCommandMetadata"].(map[string]interface{}); ok {
				if urlStr, _ := webMeta["url"].(string); strings.Contains(urlStr, "/shorts/") {
					return track, false
				}
			}
		}
	}

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

	// 2. Duration extraction from lengthText
	if lenObj, ok := item["lengthText"].(map[string]interface{}); ok {
		if s, ok := lenObj["simpleText"].(string); ok && s != "" {
			track.DurationMs = parseDurationMs(s)
		} else if runs, ok := lenObj["runs"].([]interface{}); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]interface{}); ok {
				if s, ok := r0["text"].(string); ok {
					track.DurationMs = parseDurationMs(s)
				}
			}
		}
	}

	// 3. Shorts Badge & Overlay Detection
	if overlays, ok := item["thumbnailOverlays"].([]interface{}); ok {
		for _, ov := range overlays {
			if ovMap, ok := ov.(map[string]interface{}); ok {
				if timeStatus, ok := ovMap["thumbnailOverlayTimeStatusRenderer"].(map[string]interface{}); ok {
					if style, _ := timeStatus["style"].(string); strings.EqualFold(style, "SHORTS") {
						return track, false
					}
					if iconObj, ok := timeStatus["icon"].(map[string]interface{}); ok {
						if iconType, _ := iconObj["iconType"].(string); strings.Contains(strings.ToUpper(iconType), "SHORTS") {
							return track, false
						}
					}
					if textObj, ok := timeStatus["text"].(map[string]interface{}); ok {
						durStr := ""
						if s, ok := textObj["simpleText"].(string); ok {
							durStr = s
						} else if runs, ok := textObj["runs"].([]interface{}); ok && len(runs) > 0 {
							if r0, ok := runs[0].(map[string]interface{}); ok {
								durStr, _ = r0["text"].(string)
							}
						}
						if strings.EqualFold(durStr, "SHORTS") {
							return track, false
						}
						if durStr != "" && track.DurationMs == 0 {
							track.DurationMs = parseDurationMs(durStr)
						}
					}
				}
			}
		}
	}

	// Strict shorts threshold: any video < 75 seconds is discarded
	if track.DurationMs > 0 && track.DurationMs < 75000 {
		return track, false
	}

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

	// Strict music validation: non-music vlogs, gaming, couple channels, or #shorts are barred
	if !gatekeeper.IsMusicTrack(track) {
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

	// Strict music validation
	if !gatekeeper.IsMusicTrack(track) {
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
		if pid, ok := item["playlistItemData"].(map[string]interface{}); ok {
			vid, _ = pid["videoId"].(string)
		}
	}
	if vid == "" {
		if overlay, ok := item["thumbnailOverlay"].(map[string]interface{}); ok {
			if thumbOverlay, ok := overlay["musicItemThumbnailOverlayRenderer"].(map[string]interface{}); ok {
				if content, ok := thumbOverlay["content"].(map[string]interface{}); ok {
					if playBtn, ok := content["musicPlayButtonRenderer"].(map[string]interface{}); ok {
						if playNav, ok := playBtn["playNavigationEndpoint"].(map[string]interface{}); ok {
							if watch, ok := playNav["watchEndpoint"].(map[string]interface{}); ok {
								vid, _ = watch["videoId"].(string)
							}
						}
					}
				}
			}
		}
	}
	if vid == "" {
		if onTap, ok := item["onTap"].(map[string]interface{}); ok {
			if watch, ok := onTap["watchEndpoint"].(map[string]interface{}); ok {
				vid, _ = watch["videoId"].(string)
			}
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
		// Strict skip: immediately reject all Shorts and Reels renderers
		if _, ok := v["reelItemRenderer"]; ok {
			return
		}
		if _, ok := v["shortsLockupViewModel"]; ok {
			return
		}
		if _, ok := v["reelShelfRenderer"]; ok {
			return
		}
		if _, ok := v["shortsVideoRenderer"]; ok {
			return
		}

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
// Prioritizes YouTube Music (FEmusic_home, FLLM, LM, FEmusic_history) to guarantee pure music content without shorts.
func (c *Client) FetchUserTasteAndHistory(ctx context.Context) ([]models.Track, error) {
	var allTracks []models.Track

	// 1. PRIMARY SOURCE: YouTube Music (Pure music only, zero shorts)
	// Query FEmusic_home, FLLM (Liked Music), LM, and FEmusic_history
	for _, cfg := range []ClientConfig{ConfigWebRemix, ConfigAndroidMusic} {
		for _, browseID := range []string{"FLLM", "LM", "FEmusic_home", "FEmusic_history"} {
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

	// 2. SECONDARY SOURCE: YouTube Liked Videos (VLLL) & Playlists
	// Strictly filtered: only genuine music tracks pass parseGenericVideoRenderer and FilterMusicTracks
	if len(allTracks) < 30 {
		for _, browseID := range []string{"VLLL", "VLWM", "FElibrary"} {
			for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb} {
				body := map[string]interface{}{
					"context":  c.buildContext(cfg),
					"browseId": browseID,
				}
				if respBytes, err := c.post(ctx, "browse", body, cfg); err == nil {
					var root map[string]interface{}
					if err := json.Unmarshal(respBytes, &root); err == nil {
						recursiveExtractTracks(root, &allTracks)
						if len(allTracks) >= 35 {
							break
						}
					}
				}
			}
			if len(allTracks) >= 35 {
				break
			}
		}
	}

	// Filter all items so strictly pure music tracks/videos are preserved
	filtered := gatekeeper.FilterMusicTracks(allTracks)

	// Deduplicate by track ID
	seen := make(map[string]bool)
	unique := make([]models.Track, 0, len(filtered))
	for _, t := range filtered {
		if !seen[t.ID] {
			seen[t.ID] = true
			unique = append(unique, t)
		}
	}

	// 3. ENRICH WITH INFINITE MUSIC: If we have music seeds, fetch related songs via YouTube Music /next
	if len(unique) > 0 && len(unique) < 50 {
		seedTrack := unique[0]
		if nextTracks, err := c.FetchRadioForTrack(ctx, seedTrack.ID); err == nil && len(nextTracks) > 0 {
			for _, nt := range nextTracks {
				if !seen[nt.ID] {
					seen[nt.ID] = true
					unique = append(unique, nt)
				}
			}
		}
	}

	return unique, nil
}

// FetchRadioForTrack queries YouTube Music /next to generate pure music songs related to a track.
func (c *Client) FetchRadioForTrack(ctx context.Context, videoID string) ([]models.Track, error) {
	body := map[string]interface{}{
		"context": c.buildContext(ConfigWebRemix),
		"videoId": videoID,
	}

	respBytes, err := c.post(ctx, "next", body, ConfigWebRemix)
	if err != nil {
		return nil, err
	}

	items, err := ParseNextTracks(respBytes)
	if err != nil {
		return nil, err
	}

	var tracks []models.Track
	for _, item := range items {
		t := models.Track{
			ID:           item.ID,
			Title:        item.Title,
			Artist:       item.Artist,
			Album:        item.Album,
			DurationMs:   item.DurationMs,
			ThumbnailURL: item.Thumbnail,
		}
		if t.ThumbnailURL == "" {
			t.ThumbnailURL = formatMusicFirstThumbnail(item.Thumbnail, t.ID)
		}
		if gatekeeper.IsMusicTrack(t) {
			tracks = append(tracks, t)
		}
	}

	return tracks, nil
}

// GenerateUserMixes builds rich YouTube song and artist mixes derived strictly from authentic music artists.
func (c *Client) GenerateUserMixes(tracks []models.Track) []MixItem {
	var mixes []MixItem
	seenTitles := make(map[string]bool)

	// 1. My Supermix (Personalized Endless Mix)
	if len(tracks) > 0 {
		firstTrack := tracks[0]
		mixes = append(mixes, MixItem{
			ID:           "RDTMAK5uy_kset8DisdE7LSD4TNjEVsnKrtGctD5JU8",
			Title:        "My Supermix",
			Subtitle:     "Endless personalized music blend",
			ThumbnailURL: firstTrack.ThumbnailURL,
		})
		seenTitles["My Supermix"] = true
	}

	// 2. Discover / Replay Mix
	if len(tracks) > 3 {
		t := tracks[len(tracks)/2]
		mixes = append(mixes, MixItem{
			ID:           "RDAMVM" + t.ID,
			Title:        "Replay & Discover Mix",
			Subtitle:     "Fresh picks & songs you love",
			ThumbnailURL: t.ThumbnailURL,
		})
		seenTitles["Replay & Discover Mix"] = true
	}

	// 3. Artist Radios from verified music artists in the user's tracks
	artistCounts := make(map[string]int)
	artistThumb := make(map[string]string)
	artistFirstID := make(map[string]string)

	for _, t := range tracks {
		if gatekeeper.IsAuthenticMusicArtist(t.Artist) {
			artistCounts[t.Artist]++
			if artistThumb[t.Artist] == "" {
				artistThumb[t.Artist] = t.ThumbnailURL
				artistFirstID[t.Artist] = t.ID
			}
		}
	}

	for artist, count := range artistCounts {
		if count >= 1 && len(mixes) < 10 {
			title := artist + " Mix"
			if !seenTitles[title] {
				seenTitles[title] = true
				firstID := artistFirstID[artist]
				mixes = append(mixes, MixItem{
					ID:           "RDAMVM" + firstID,
					Title:        title,
					Subtitle:     "Songs by " + artist + " & similar artists",
					ThumbnailURL: artistThumb[artist],
				})
			}
		}
	}

	return mixes
}

// FetchInfinitePersonalizedFeed retrieves the next wave of personalized music recommendations.
func (c *Client) FetchInfinitePersonalizedFeed(ctx context.Context, seedVideoID string) ([]models.Track, error) {
	if seedVideoID != "" {
		return c.FetchRadioForTrack(ctx, seedVideoID)
	}

	// Fetch from FEmusic_home
	body := map[string]interface{}{
		"context":  c.buildContext(ConfigWebRemix),
		"browseId": "FEmusic_home",
	}
	var tracks []models.Track
	if respBytes, err := c.post(ctx, "browse", body, ConfigWebRemix); err == nil {
		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err == nil {
			recursiveExtractTracks(root, &tracks)
		}
	}
	return gatekeeper.FilterMusicTracks(tracks), nil
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
