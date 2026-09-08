/*
 * Package: lyrics
 * File: netease.go
 * Purpose: NetEase Cloud Music open JSON API client for synchronized timestamped LRC harvesting.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Thread-safe HTTP client with connection pooling and timeouts.
 */

package lyrics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// NetEaseFetcher defines the contract for NetEase synced lyric harvesting.
type NetEaseFetcher interface {
	Fetch(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error)
}

// NetEaseClient queries NetEase Cloud Music's open JSON endpoints to extract synchronized LRC lines.
type NetEaseClient struct {
	httpClient *http.Client
	baseURL    string
}

// NewNetEaseClient instantiates a new NetEase Cloud Music lyric client.
func NewNetEaseClient() *NetEaseClient {
	return &NetEaseClient{
		httpClient: &http.Client{Timeout: 8 * time.Second},
		baseURL:    "https://music.163.com/api",
	}
}

type netEaseSearchResp struct {
	Result struct {
		Songs []struct {
			ID       int64  `json:"id"`
			Name     string `json:"name"`
			Duration int64  `json:"duration"`
			Artists  []struct {
				Name string `json:"name"`
			} `json:"artists"`
		} `json:"songs"`
	} `json:"result"`
}

type netEaseLyricResp struct {
	Lrc struct {
		Lyric string `json:"lyric"`
	} `json:"lrc"`
	Nolyric     bool `json:"nolyric"`
	Uncollected bool `json:"uncollected"`
}

// Fetch searches for a matching track on NetEase and extracts its synchronized LRC lyrics.
func (c *NetEaseClient) Fetch(ctx context.Context, title, artist string, durationSec int) (*models.LyricsPayload, error) {
	title = strings.TrimSpace(title)
	artist = strings.TrimSpace(artist)
	if title == "" {
		return nil, fmt.Errorf("title cannot be empty")
	}

	searchQuery := title
	if artist != "" {
		searchQuery = fmt.Sprintf("%s %s", title, artist)
	}

	// 1. Search for song ID
	searchURL := fmt.Sprintf("%s/search/get/web?s=%s&type=1&limit=5", c.baseURL, url.QueryEscape(searchQuery))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed creating search request: %w", err)
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
	req.Header.Set("Referer", "https://music.163.com")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("search request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("search returned status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed reading search response: %w", err)
	}

	var searchData netEaseSearchResp
	if err := json.Unmarshal(body, &searchData); err != nil {
		return nil, fmt.Errorf("failed parsing search JSON: %w", err)
	}

	if len(searchData.Result.Songs) == 0 {
		return nil, fmt.Errorf("no songs found on NetEase for %q", searchQuery)
	}

	song := searchData.Result.Songs[0]
	songID := song.ID

	// 2. Fetch lyrics for song ID
	lyricURL := fmt.Sprintf("%s/song/lyric?os=pc&id=%d&lv=-1&kv=-1&tv=-1", c.baseURL, songID)
	lreq, err := http.NewRequestWithContext(ctx, http.MethodGet, lyricURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed creating lyric request: %w", err)
	}
	lreq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
	lreq.Header.Set("Referer", "https://music.163.com")

	lresp, err := c.httpClient.Do(lreq)
	if err != nil {
		return nil, fmt.Errorf("lyric request failed: %w", err)
	}
	defer lresp.Body.Close()

	if lresp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("lyric returned status %d", lresp.StatusCode)
	}

	lbody, err := io.ReadAll(lresp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed reading lyric response: %w", err)
	}

	var lyricData netEaseLyricResp
	if err := json.Unmarshal(lbody, &lyricData); err != nil {
		return nil, fmt.Errorf("failed parsing lyric JSON: %w", err)
	}

	if lyricData.Nolyric || strings.Contains(lyricData.Lrc.Lyric, "纯音乐") {
		return &models.LyricsPayload{
			Title:        song.Name,
			Artist:       artist,
			PlainLyrics:  "[Instrumental Track - Pure Audio]",
			Instrumental: true,
			Source:       "NetEase Cloud Music (Instrumental)",
		}, nil
	}

	lrcText := strings.TrimSpace(lyricData.Lrc.Lyric)
	if lrcText == "" {
		return nil, fmt.Errorf("empty lyrics returned from NetEase for song %d", songID)
	}

	lines := ParseLRCLyrics(lrcText)
	if len(lines) == 0 {
		return nil, fmt.Errorf("failed parsing any timestamped lines from NetEase LRC")
	}

	return &models.LyricsPayload{
		Title:        song.Name,
		Artist:       artist,
		PlainLyrics:  lrcText,
		Lines:        lines,
		IsWordSynced: true,
		Source:       "NetEase Cloud Music Synced LRC",
	}, nil
}
