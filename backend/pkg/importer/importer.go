/*
 * Package: importer
 * File: importer.go
 * Purpose: Playlist migration engine: scrapes public Spotify playlists, parses and exports M3U, M3U8, CSV, and JSON formats for cross-platform portability.
 * Subsystem: Playlist Portability & Importers
 * Concurrency: Thread-safe pure parsers and HTTP scraper with timeout guards.
 */

package importer

import (
	"bufio"
	"context"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// ImportedTrack represents a song item extracted from an external playlist.
type ImportedTrack struct {
	Title       string `json:"title"`
	Artist      string `json:"artist"`
	Album       string `json:"album"`
	DurationSec int    `json:"duration_sec"`
	LocalPath   string `json:"local_path,omitempty"`
}

// ImportedPlaylist contains imported track items and metadata.
type ImportedPlaylist struct {
	Title       string          `json:"title"`
	Description string          `json:"description"`
	SourceType  string          `json:"source_type"` // "SPOTIFY", "M3U", "CSV", "JSON"
	TrackCount  int             `json:"track_count"`
	Tracks      []ImportedTrack `json:"tracks"`
}

// Importer coordinates playlist parsing and web scraping.
type Importer struct {
	httpClient *http.Client
}

// NewImporter creates a new playlist importer instance.
func NewImporter() *Importer {
	return &Importer{
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// ImportSpotifyPlaylist scrapes a public Spotify playlist link without API keys.
func (imp *Importer) ImportSpotifyPlaylist(ctx context.Context, spotifyURL string) (*ImportedPlaylist, error) {
	if !strings.Contains(spotifyURL, "spotify.com/playlist/") {
		return nil, fmt.Errorf("invalid Spotify playlist URL")
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, spotifyURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed creating Spotify request: %w", err)
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

	resp, err := imp.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("spotify request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("spotify returned status %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed reading Spotify page body: %w", err)
	}

	bodyStr := string(bodyBytes)

	// Extract title
	title := "Imported Spotify Playlist"
	titleRegex := regexp.MustCompile(`<title>(.*?)</title>`)
	if match := titleRegex.FindStringSubmatch(bodyStr); len(match) > 1 {
		clean := html.UnescapeString(match[1])
		clean = strings.TrimSuffix(clean, " | Spotify")
		title = clean
	}

	// Extract track items from meta/schema or DOM entities
	var tracks []ImportedTrack
	metaRegex := regexp.MustCompile(`name="music:song" content="([^"]+)"`)
	matches := metaRegex.FindAllStringSubmatch(bodyStr, -1)

	for _, m := range matches {
		if len(m) > 1 {
			tracks = append(tracks, ImportedTrack{
				Title:  html.UnescapeString(m[1]),
				Artist: "Unknown Artist",
			})
		}
	}

	// Fallback heuristic extraction from DOM if schema tags absent
	if len(tracks) == 0 {
		domRegex := regexp.MustCompile(`data-testid="track-item[^>]*>.*?dir="auto">([^<]+)</div>.*?dir="auto">([^<]+)</div>`)
		domMatches := domRegex.FindAllStringSubmatch(bodyStr, -1)
		for _, dm := range domMatches {
			if len(dm) > 2 {
				tracks = append(tracks, ImportedTrack{
					Title:  html.UnescapeString(dm[1]),
					Artist: html.UnescapeString(dm[2]),
				})
			}
		}
	}

	return &ImportedPlaylist{
		Title:       title,
		SourceType:  "SPOTIFY",
		TrackCount:  len(tracks),
		Tracks:      tracks,
	}, nil
}

// ParseM3U reads standard M3U and extended M3U8 content.
func ParseM3U(reader io.Reader) (*ImportedPlaylist, error) {
	scanner := bufio.NewScanner(reader)
	var tracks []ImportedTrack
	var currentTrack ImportedTrack

	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		if strings.HasPrefix(line, "#EXTINF:") {
			info := strings.TrimPrefix(line, "#EXTINF:")
			parts := strings.SplitN(info, ",", 2)
			if len(parts) >= 1 {
				duration, _ := strconv.Atoi(parts[0])
				currentTrack.DurationSec = duration
			}
			if len(parts) >= 2 {
				titleArtist := strings.SplitN(parts[1], " - ", 2)
				if len(titleArtist) == 2 {
					currentTrack.Artist = strings.TrimSpace(titleArtist[0])
					currentTrack.Title = strings.TrimSpace(titleArtist[1])
				} else {
					currentTrack.Title = strings.TrimSpace(parts[1])
				}
			}
		} else if !strings.HasPrefix(line, "#") {
			currentTrack.LocalPath = line
			if currentTrack.Title == "" {
				currentTrack.Title = line
			}
			tracks = append(tracks, currentTrack)
			currentTrack = ImportedTrack{}
		}
	}

	return &ImportedPlaylist{
		Title:      "Imported M3U Playlist",
		SourceType: "M3U",
		TrackCount: len(tracks),
		Tracks:     tracks,
	}, nil
}

// ExportM3U serializes a list of tracks into standard M3U8 string format.
func ExportM3U(playlistName string, tracks []ImportedTrack) string {
	var sb strings.Builder
	sb.WriteString("#EXTM3U\n")
	sb.WriteString(fmt.Sprintf("#PLAYLIST:%s\n", playlistName))

	for _, t := range tracks {
		artist := t.Artist
		if artist == "" {
			artist = "Unknown Artist"
		}
		sb.WriteString(fmt.Sprintf("#EXTINF:%d,%s - %s\n", t.DurationSec, artist, t.Title))
		if t.LocalPath != "" {
			sb.WriteString(fmt.Sprintf("%s\n", t.LocalPath))
		} else {
			sb.WriteString(fmt.Sprintf("%s.mp3\n", t.Title))
		}
	}
	return sb.String()
}

// ParseCSV reads a CSV playlist export with Title, Artist, Album columns.
func ParseCSV(reader io.Reader) (*ImportedPlaylist, error) {
	csvReader := csv.NewReader(reader)
	records, err := csvReader.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("failed reading CSV: %w", err)
	}

	var tracks []ImportedTrack
	for i, row := range records {
		if i == 0 && (strings.EqualFold(row[0], "title") || strings.EqualFold(row[0], "track")) {
			continue // skip header
		}
		if len(row) >= 2 {
			tracks = append(tracks, ImportedTrack{
				Title:  strings.TrimSpace(row[0]),
				Artist: strings.TrimSpace(row[1]),
			})
		}
	}

	return &ImportedPlaylist{
		Title:      "Imported CSV Playlist",
		SourceType: "CSV",
		TrackCount: len(tracks),
		Tracks:     tracks,
	}, nil
}

// ExportJSON serializes tracks into JSON for cloudless backups.
func ExportJSON(playlist *ImportedPlaylist) ([]byte, error) {
	return json.MarshalIndent(playlist, "", "  ")
}
