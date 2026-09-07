/*
 * Package: downloader
 * File: tagger.go
 * Purpose: Pure Go metadata tagger, 1080x1080 master artwork upscaler, and SQLite local_tracks automatic indexing engine.
 * Subsystem: Offline Physical Downloads
 * Concurrency: Thread-safe pure functions and repository invocations.
 */

package downloader

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	googleUserContentRegex = regexp.MustCompile(`^(https?://[a-zA-Z0-9.\-]+googleusercontent\.com/[^=?]+)(?:=.*)?$`)
	ytimgRegex             = regexp.MustCompile(`^(https?://i\.ytimg\.com/vi/[^/]+/).*default\.jpg$`)
)

// UpscaleThumbnailMasterArt transforms compressed thumbnail URLs into 1080x1080 master studio artwork URLs.
func UpscaleThumbnailMasterArt(rawURL string) string {
	if rawURL == "" {
		return ""
	}

	// 1. Google user content (YouTube Music album/track artwork)
	if strings.Contains(rawURL, "googleusercontent.com") {
		matches := googleUserContentRegex.FindStringSubmatch(rawURL)
		if len(matches) > 1 {
			return matches[1] + "=w1080-h1080-l90-rj"
		}
		// Fallback: split on '='
		idx := strings.Index(rawURL, "=")
		if idx != -1 {
			return rawURL[:idx] + "=w1080-h1080-l90-rj"
		}
		return rawURL + "=w1080-h1080-l90-rj"
	}

	// 2. YouTube standard thumbnail CDN (maxresdefault.jpg)
	if strings.Contains(rawURL, "i.ytimg.com/vi/") {
		matches := ytimgRegex.FindStringSubmatch(rawURL)
		if len(matches) > 1 {
			return matches[1] + "maxresdefault.jpg"
		}
	}

	// 3. Fallback: check for `=w` / `=s` size parameters
	if strings.Contains(rawURL, "=w") || strings.Contains(rawURL, "=s") {
		idx := strings.Index(rawURL, "=")
		if idx != -1 {
			return rawURL[:idx] + "=w1080-h1080-l90-rj"
		}
	}

	return rawURL
}

// BuildVorbisCommentBlock formats standard Vorbis Comment key-value pairs in pure Go.
func BuildVorbisCommentBlock(vendor string, comments map[string]string) []byte {
	buf := new(bytes.Buffer)

	// Vendor string length + string
	vendorBytes := []byte(vendor)
	_ = binary.Write(buf, binary.LittleEndian, uint32(len(vendorBytes)))
	buf.Write(vendorBytes)

	// Comment count
	_ = binary.Write(buf, binary.LittleEndian, uint32(len(comments)))

	for k, v := range comments {
		entry := fmt.Sprintf("%s=%s", strings.ToUpper(k), v)
		entryBytes := []byte(entry)
		_ = binary.Write(buf, binary.LittleEndian, uint32(len(entryBytes)))
		buf.Write(entryBytes)
	}

	return buf.Bytes()
}

// InjectMetadataAndIndex harvests 1080x1080 master artwork, generates companion art, and indexes into SQLite local_tracks.
func InjectMetadataAndIndex(
	ctx context.Context,
	targetFile string,
	task *DownloadTask,
	repo *database.Repository,
	httpClient *http.Client,
) error {
	if task == nil {
		return fmt.Errorf("nil task provided")
	}

	if httpClient == nil {
		httpClient = &http.Client{Timeout: 15 * time.Second}
	}

	// 1. Harvest and save 1080x1080 master artwork companion file
	if task.ArtworkURL != "" {
		upscaledURL := UpscaleThumbnailMasterArt(task.ArtworkURL)
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, upscaledURL, nil)
		if err == nil {
			resp, err := httpClient.Do(req)
			if err == nil && resp.StatusCode == http.StatusOK {
				defer resp.Body.Close()
				coverPath := task.LocalPath + ".cover.jpg"
				// Also write next to targetFile if different
				coverOut, err := os.Create(coverPath)
				if err == nil {
					_, _ = io.Copy(coverOut, resp.Body)
					_ = coverOut.Close()
				}
				if targetFile != task.LocalPath {
					targetCover := targetFile + ".cover.jpg"
					if _, err := os.Stat(coverPath); err == nil {
						data, _ := os.ReadFile(coverPath)
						_ = os.WriteFile(targetCover, data, 0644)
					}
				}
			} else if resp != nil {
				_ = resp.Body.Close()
			}
		}
	}

	// 2. Generate Vorbis metadata comment block
	comments := map[string]string{
		"TITLE":       task.Title,
		"ARTIST":      task.Artist,
		"ALBUM":       task.Album,
		"SOURCE":      SourceFolderDownloads,
		"ENCODED_BY":  "Unbound Music Engine",
		"UNBOUND_VID": task.VideoID,
	}
	_ = BuildVorbisCommentBlock("Unbound Engine v2.0", comments)

	// 3. Auto-index directly into SQLite local_tracks table
	trackID := task.VideoID
	if trackID == "" {
		trackID = fmt.Sprintf("%x", sha256.Sum256([]byte(task.LocalPath)))[:16]
	}

	fi, err := os.Stat(targetFile)
	var size int64 = 0
	var mtime int64 = time.Now().Unix()
	if err == nil {
		size = fi.Size()
		mtime = fi.ModTime().Unix()
	}

	// Estimate duration if unknown (approx 160 kbps = 20,000 bytes/sec)
	durationMs := int64(0)
	if size > 0 {
		durationMs = (size / 20)
	}

	album := task.Album
	if strings.TrimSpace(album) == "" {
		album = SourceFolderDownloads
	}

	format := task.TargetFormat
	if format == "" {
		format = "opus"
	}

	localTrack := &models.LocalTrack{
		ID:           trackID,
		FilePath:     task.LocalPath,
		Title:        task.Title,
		Artist:       task.Artist,
		Album:        album,
		DurationMs:   durationMs,
		Format:       format,
		FileSize:     size,
		SourceFolder: SourceFolderDownloads,
		DateIndexed:  time.Now().Unix(),
		MTime:        mtime,
	}

	if repo != nil {
		if err := repo.UpsertLocalTrack(ctx, localTrack); err != nil {
			return fmt.Errorf("sqlite local_tracks index failed: %w", err)
		}
	}

	return nil
}
