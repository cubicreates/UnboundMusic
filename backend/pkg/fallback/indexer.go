/*
 * Package: fallback
 * File: indexer.go
 * Purpose: Multi-indexer aggregator for P2P/torrent audio resolution. Discovers magnet links
 *          and parses torrent swarms for tracks unavailable on YouTube.
 * Subsystem: Multi-Stage Fallback Engine
 * Concurrency: Concurrent multi-source HTTP queries with timeout cancellation.
 */

package fallback

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// AudioTorrentItem represents an audio stream candidate discovered on the P2P network.
type AudioTorrentItem struct {
	Name        string `json:"name"`
	MagnetURI   string `json:"magnet_uri"`
	InfoHash    string `json:"info_hash"`
	Seeders     int    `json:"seeders"`
	Leechers    int    `json:"leechers"`
	SizeBytes   int64  `json:"size_bytes"`
	AudioFormat string `json:"audio_format"` // "FLAC", "MP3", "M4A", "OPUS"
}

// IndexerAggregator coordinates searches across public torrent indexers.
type IndexerAggregator struct {
	httpClient *http.Client
}

// NewIndexerAggregator creates a new torrent search coordinator.
func NewIndexerAggregator() *IndexerAggregator {
	return &IndexerAggregator{
		httpClient: &http.Client{
			Timeout: 8 * time.Second,
		},
	}
}

var (
	magnetRegex = regexp.MustCompile(`magnet:\?xt=urn:btih:([a-zA-Z0-9]+)(&[^"'\s<>]*)?`)
	hashRegex   = regexp.MustCompile(`urn:btih:([a-zA-Z0-9]+)`)
)

// SearchAudio queries indexers for audio matching the verified track.
func (idx *IndexerAggregator) SearchAudio(ctx context.Context, title, artist string) ([]AudioTorrentItem, error) {
	query := fmt.Sprintf("%s %s", strings.TrimSpace(artist), strings.TrimSpace(title))
	cleanQuery := strings.TrimSpace(query)

	if cleanQuery == "" {
		return nil, fmt.Errorf("search query cannot be empty")
	}

	results := make([]AudioTorrentItem, 0)

	// 1. Query public open torrent mirror (Apibay / TPB API)
	endpoint := fmt.Sprintf("https://apibay.org/q.php?q=%s&cat=100", url.QueryEscape(cleanQuery))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err == nil {
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
		resp, err := idx.httpClient.Do(req)
		if err == nil {
			defer resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				var items []struct {
					Name     string `json:"name"`
					InfoHash string `json:"info_hash"`
					Seeders  string `json:"seeders"`
					Leechers string `json:"leechers"`
					Size     string `json:"size"`
				}
				if jsonErr := decodeJSON(resp.Body, &items); jsonErr == nil {
					for _, it := range items {
						if it.InfoHash == "" || it.InfoHash == "0000000000000000000000000000000000000000" {
							continue
						}
						seeds, _ := strconv.Atoi(it.Seeders)
						leech, _ := strconv.Atoi(it.Leechers)
						size, _ := strconv.ParseInt(it.Size, 10, 64)

						format := detectAudioFormat(it.Name)
						magnet := fmt.Sprintf("magnet:?xt=urn:btih:%s&dn=%s", it.InfoHash, url.QueryEscape(it.Name))

						results = append(results, AudioTorrentItem{
							Name:        it.Name,
							MagnetURI:   magnet,
							InfoHash:    strings.ToLower(it.InfoHash),
							Seeders:     seeds,
							Leechers:    leech,
							SizeBytes:   size,
							AudioFormat: format,
						})
					}
				}
			}
		}
	}

	// 2. Secondary Indexer Failover (Nyaa Audio RSS for lossless FLAC and international tracks)
	if len(results) == 0 {
		nyaaURL := fmt.Sprintf("https://nyaa.si/?page=rss&q=%s&c=2_0", url.QueryEscape(cleanQuery))
		nReq, nErr := http.NewRequestWithContext(ctx, http.MethodGet, nyaaURL, nil)
		if nErr == nil {
			nReq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
			nResp, nDoErr := idx.httpClient.Do(nReq)
			if nDoErr == nil {
				defer nResp.Body.Close()
				if nResp.StatusCode == http.StatusOK {
					body, _ := io.ReadAll(nResp.Body)
					magnets := magnetRegex.FindAllString(string(body), -1)
					for _, mag := range magnets {
						match := hashRegex.FindStringSubmatch(mag)
						if len(match) > 1 {
							infoHash := strings.ToLower(match[1])
							results = append(results, AudioTorrentItem{
								Name:        cleanQuery,
								MagnetURI:   mag,
								InfoHash:    infoHash,
								Seeders:     5,
								Leechers:    1,
								SizeBytes:   15 * 1024 * 1024,
								AudioFormat: detectAudioFormat(cleanQuery),
							})
						}
					}
				}
			}
		}
	}

	return results, nil
}

// detectAudioFormat infers audio quality/format from torrent name.
func detectAudioFormat(name string) string {
	upper := strings.ToUpper(name)
	switch {
	case strings.Contains(upper, "FLAC"):
		return "FLAC"
	case strings.Contains(upper, "OPUS"):
		return "OPUS"
	case strings.Contains(upper, "M4A") || strings.Contains(upper, "AAC"):
		return "M4A"
	case strings.Contains(upper, "320") || strings.Contains(upper, "MP3"):
		return "MP3"
	default:
		return "AUDIO"
	}
}
