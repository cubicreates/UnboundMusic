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
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
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

// EncodeFLACPictureBlock serializes raw image data into a standard RFC 7845 / FLAC METADATA_BLOCK_PICTURE Base64 string.
func EncodeFLACPictureBlock(imageData []byte, mimeType string) string {
	if len(imageData) == 0 {
		return ""
	}
	if mimeType == "" {
		mimeType = "image/jpeg"
	}

	buf := new(bytes.Buffer)
	// 1. Picture type (uint32, 3 = Cover Front)
	_ = binary.Write(buf, binary.BigEndian, uint32(3))
	// 2. MIME type length (uint32) and MIME string
	_ = binary.Write(buf, binary.BigEndian, uint32(len(mimeType)))
	buf.WriteString(mimeType)
	// 3. Description length (uint32, 0)
	_ = binary.Write(buf, binary.BigEndian, uint32(0))
	// 4. Width (uint32, 0)
	_ = binary.Write(buf, binary.BigEndian, uint32(0))
	// 5. Height (uint32, 0)
	_ = binary.Write(buf, binary.BigEndian, uint32(0))
	// 6. Color depth (uint32, 0)
	_ = binary.Write(buf, binary.BigEndian, uint32(0))
	// 7. Indexed colors (uint32, 0)
	_ = binary.Write(buf, binary.BigEndian, uint32(0))
	// 8. Picture data length (uint32) and raw image bytes
	_ = binary.Write(buf, binary.BigEndian, uint32(len(imageData)))
	buf.Write(imageData)

	return base64.StdEncoding.EncodeToString(buf.Bytes())
}

// BuildID3v2Tag constructs a standard ID3v2.3 tag header and frames for MP3 audio files.
func BuildID3v2Tag(title, artist, album string, coverBytes []byte) []byte {
	framesBuf := new(bytes.Buffer)

	// Helper to write an ID3v2.3 text frame: FrameID (4 bytes) + Size (4 bytes uint32) + Flags (2 bytes) + Encoding (1 byte: 0x03 UTF-8) + Text
	writeTextFrame := func(frameID, text string) {
		if text == "" {
			return
		}
		textBytes := []byte(text)
		payload := append([]byte{0x03}, textBytes...) // 0x03 = UTF-8 encoding

		framesBuf.WriteString(frameID)
		_ = binary.Write(framesBuf, binary.BigEndian, uint32(len(payload)))
		framesBuf.Write([]byte{0x00, 0x00}) // Flags
		framesBuf.Write(payload)
	}

	writeTextFrame("TIT2", title)  // Title
	writeTextFrame("TPE1", artist) // Lead artist / performer
	writeTextFrame("TALB", album)  // Album
	writeTextFrame("TCON", "Music")
	writeTextFrame("TPUB", "Unbound Music")
	writeTextFrame("TENC", "Unbound Music Engine")

	// Comment frame: COMM
	commPayload := new(bytes.Buffer)
	commPayload.WriteByte(0x03)        // UTF-8
	commPayload.WriteString("eng")     // Language
	commPayload.WriteByte(0x00)        // Short description null terminator
	commPayload.WriteString("Downloaded via Unbound Music")

	framesBuf.WriteString("COMM")
	_ = binary.Write(framesBuf, binary.BigEndian, uint32(commPayload.Len()))
	framesBuf.Write([]byte{0x00, 0x00})
	framesBuf.Write(commPayload.Bytes())

	// Attached Picture frame: APIC
	if len(coverBytes) > 0 {
		apicPayload := new(bytes.Buffer)
		apicPayload.WriteByte(0x00)           // ISO-8859-1 for MIME and description
		apicPayload.WriteString("image/jpeg") // MIME type
		apicPayload.WriteByte(0x00)           // Null terminator for MIME
		apicPayload.WriteByte(0x03)           // Picture type: Cover Front
		apicPayload.WriteByte(0x00)           // Null terminator for empty description
		apicPayload.Write(coverBytes)         // Raw JPEG picture data

		framesBuf.WriteString("APIC")
		_ = binary.Write(framesBuf, binary.BigEndian, uint32(apicPayload.Len()))
		framesBuf.Write([]byte{0x00, 0x00})
		framesBuf.Write(apicPayload.Bytes())
	}

	framesLen := framesBuf.Len()
	if framesLen == 0 {
		return nil
	}

	// ID3v2.3 10-byte header
	header := []byte{
		'I', 'D', '3', // Identifier
		0x03, 0x00,    // Version 2.3.0
		0x00,          // Flags
		byte((framesLen >> 21) & 0x7F), // Synchsafe size byte 1
		byte((framesLen >> 14) & 0x7F), // Synchsafe size byte 2
		byte((framesLen >> 7) & 0x7F),  // Synchsafe size byte 3
		byte(framesLen & 0x7F),         // Synchsafe size byte 4
	}

	return append(header, framesBuf.Bytes()...)
}

// PrependID3v2TagToFile inserts or replaces the ID3v2 header and frames at the beginning of an MP3 file.
func PrependID3v2TagToFile(filePath string, id3Tag []byte) error {
	if len(id3Tag) == 0 {
		return nil
	}

	srcFile, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open source audio file: %w", err)
	}
	defer srcFile.Close()

	// Check if existing file already has an ID3v2 tag
	headerBuf := make([]byte, 10)
	n, _ := io.ReadFull(srcFile, headerBuf)

	var audioStartOffset int64 = 0
	if n == 10 && string(headerBuf[:3]) == "ID3" {
		// Existing tag size is a synchsafe integer in bytes 6..9
		tagSize := int64(headerBuf[6])<<21 | int64(headerBuf[7])<<14 | int64(headerBuf[8])<<7 | int64(headerBuf[9])
		audioStartOffset = 10 + tagSize
	}

	if _, err := srcFile.Seek(audioStartOffset, io.SeekStart); err != nil {
		return fmt.Errorf("failed to seek audio payload: %w", err)
	}

	tmpFile, err := os.CreateTemp(filepath.Dir(filePath), "unbound_id3_*.tmp")
	if err != nil {
		return fmt.Errorf("failed to create temp file for ID3 tag: %w", err)
	}
	tmpPath := tmpFile.Name()
	defer func() {
		_ = tmpFile.Close()
		_ = os.Remove(tmpPath)
	}()

	// Write the new ID3 tag first
	if _, err := tmpFile.Write(id3Tag); err != nil {
		return fmt.Errorf("failed writing ID3 tag: %w", err)
	}

	// Copy remaining audio stream
	if _, err := io.Copy(tmpFile, srcFile); err != nil {
		return fmt.Errorf("failed copying audio stream: %w", err)
	}

	_ = srcFile.Close()
	_ = tmpFile.Close()

	// Atomically replace target file
	if err := os.Rename(tmpPath, filePath); err != nil {
		return fmt.Errorf("failed atomic rename with ID3 tag: %w", err)
	}

	return nil
}

// InjectMetadataAndIndex harvests 1080x1080 master artwork, generates companion art, tags ID3v2/Vorbis, and indexes into SQLite local_tracks.
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
	var rawCoverBytes []byte
	if task.ArtworkURL != "" {
		upscaledURL := UpscaleThumbnailMasterArt(task.ArtworkURL)
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, upscaledURL, nil)
		if err == nil {
			resp, err := httpClient.Do(req)
			if err == nil && resp.StatusCode == http.StatusOK {
				defer resp.Body.Close()
				data, readErr := io.ReadAll(resp.Body)
				if readErr == nil && len(data) > 0 {
					rawCoverBytes = data
					coverPath := task.LocalPath + ".cover.jpg"
					_ = os.WriteFile(coverPath, data, 0644)
					if targetFile != task.LocalPath {
						targetCover := targetFile + ".cover.jpg"
						_ = os.WriteFile(targetCover, data, 0644)
					}
				}
			} else if resp != nil {
				_ = resp.Body.Close()
			}
		}
	}

	// 2. Tag file according to format: ID3v2.3 for MP3, Vorbis comment block for Opus/FLAC
	isMP3 := task.TargetFormat == "mp3" || strings.HasSuffix(strings.ToLower(task.LocalPath), ".mp3") || strings.HasSuffix(strings.ToLower(targetFile), ".mp3")
	if isMP3 {
		id3Tag := BuildID3v2Tag(task.Title, task.Artist, task.Album, rawCoverBytes)
		if len(id3Tag) > 0 {
			if err := PrependID3v2TagToFile(targetFile, id3Tag); err != nil {
				fmt.Printf("[DOWNLOADER] Warning: ID3 tagging error: %v\n", err)
			}
		}
	} else {
		// Generate Vorbis metadata comment block with RFC 7845 METADATA_BLOCK_PICTURE
		comments := map[string]string{
			"TITLE":       task.Title,
			"ARTIST":      task.Artist,
			"ALBUM":       task.Album,
			"SOURCE":      SourceFolderDownloads,
			"ENCODED_BY":  "Unbound Music Engine",
			"UNBOUND_VID": task.VideoID,
		}
		if len(rawCoverBytes) > 0 {
			comments["METADATA_BLOCK_PICTURE"] = EncodeFLACPictureBlock(rawCoverBytes, "image/jpeg")
		}
		_ = BuildVorbisCommentBlock("Unbound Engine v2.0", comments)
	}

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
		if isMP3 {
			format = "mp3"
		} else {
			format = "opus"
		}
	}

	coverURL := ""
	coverPath := task.LocalPath + ".cover.jpg"
	if fi, err := os.Stat(coverPath); err == nil && fi.Size() > 0 {
		coverURL = "file://" + filepath.ToSlash(coverPath)
	} else if fi, err := os.Stat(targetFile + ".cover.jpg"); err == nil && fi.Size() > 0 {
		coverURL = "file://" + filepath.ToSlash(targetFile + ".cover.jpg")
	} else if task.ArtworkURL != "" {
		coverURL = task.ArtworkURL
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
		CoverURL:     coverURL,
	}

	if repo != nil {
		if err := repo.UpsertLocalTrack(ctx, localTrack); err != nil {
			return fmt.Errorf("sqlite local_tracks index failed: %w", err)
		}
	}

	return nil
}

