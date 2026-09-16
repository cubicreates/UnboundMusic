/*
 * Package: storage
 * File: tags.go
 * Purpose: Embedded ID3 metadata parsing, folder categorization, and default Android storage root discovery.
 * Subsystem: Storage & Indexing Engine
 * Concurrency: Thread-safe pure functions.
 */

package storage

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"unicode"
)

// GetDefaultStorageScanRoots returns common physical audio storage directories on Android,
// including chat media folders (WhatsApp, Telegram), public downloads, and mounted SD cards.
func GetDefaultStorageScanRoots() []string {
	roots := []string{
		"/storage/emulated/0/Music",
		"/storage/emulated/0/Download",
		"/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
		"/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
		"/storage/emulated/0/WhatsApp/Media/WhatsApp Audio",
		"/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes",
		"/storage/emulated/0/Telegram/Telegram Audio",
		"/storage/emulated/0/Android/data/org.telegram.messenger/files/Telegram/Telegram Audio",
		"/storage/emulated/0/Podcasts",
		"/storage/emulated/0/Audiobooks",
		"/storage/emulated/0/Recordings",
		"/storage/emulated/0/Ringtones",
		"/storage/emulated/0/Notifications",
	}

	// Discover any mounted external SD cards under /storage/
	if entries, err := os.ReadDir("/storage"); err == nil {
		for _, entry := range entries {
			if entry.IsDir() && entry.Name() != "emulated" && entry.Name() != "self" {
				sdRoot := filepath.Join("/storage", entry.Name())
				roots = append(roots, sdRoot)
			}
		}
	}

	return roots
}

// InferFolderCategory inspects path and directory name to categorize audio folders.
func InferFolderCategory(path, fallback string) string {
	dirName := filepath.Base(filepath.Dir(path))
	lowerPath := strings.ToLower(path)
	lowerDir := strings.ToLower(dirName)

	switch {
	case strings.Contains(lowerPath, "whatsapp voice") || strings.Contains(lowerDir, "whatsapp voice") || strings.Contains(lowerPath, "voice notes"):
		return "WhatsApp Voice Notes"
	case strings.Contains(lowerPath, "whatsapp") || strings.Contains(lowerDir, "whatsapp"):
		return "WhatsApp Audio"
	case strings.Contains(lowerPath, "telegram audio") || strings.Contains(lowerDir, "telegram audio"):
		return "Telegram Audio"
	case strings.Contains(lowerPath, "telegram") || strings.Contains(lowerDir, "telegram"):
		return "Telegram Audio"
	case strings.Contains(lowerPath, "download") || strings.Contains(lowerDir, "download"):
		return "Downloads"
	case strings.Contains(lowerPath, "unbound"):
		return "Unbound Downloads"
	case strings.Contains(lowerPath, "music") || strings.Contains(lowerDir, "music"):
		return "Music"
	case strings.Contains(lowerPath, "podcast") || strings.Contains(lowerDir, "podcast"):
		return "Podcasts"
	case strings.Contains(lowerPath, "audiobook") || strings.Contains(lowerDir, "audiobook"):
		return "Audiobooks"
	case strings.Contains(lowerPath, "recording") || strings.Contains(lowerDir, "recording"):
		return "Recordings"
	case dirName != "" && dirName != "." && dirName != "/" && dirName != "\\" && dirName != "0":
		return dirName
	case fallback != "":
		return fallback
	default:
		return "Device Audio"
	}
}

// ExtractID3Metadata parses embedded ID3v1 or ID3v2 tags from an audio file.
// Falls back to baseName and defaultAlbum if tags are absent.
func ExtractID3Metadata(path, baseName, defaultAlbum string) (title, artist, album string) {
	title = baseName
	artist = "Unknown Artist"
	album = defaultAlbum

	f, err := os.Open(path)
	if err != nil {
		return title, artist, album
	}
	defer f.Close()

	// 1. Try reading ID3v2 header from start of file
	header := make([]byte, 10)
	if _, err := io.ReadFull(f, header); err == nil {
		if header[0] == 'I' && header[1] == 'D' && header[2] == '3' {
			tagSize := (int(header[6]) << 21) | (int(header[7]) << 14) | (int(header[8]) << 7) | int(header[9])
			if tagSize > 0 && tagSize < 2*1024*1024 { // max 2MB tag buffer
				tagData := make([]byte, tagSize)
				if _, err := io.ReadFull(f, tagData); err == nil {
					t, a, alb := parseID3v2Frames(tagData)
					if t != "" {
						title = t
					}
					if a != "" {
						artist = a
					}
					if alb != "" {
						album = alb
					}
					if t != "" || a != "" {
						return title, artist, album
					}
				}
			}
		}
	}

	// 2. Fall back to ID3v1 trailer (last 128 bytes of file)
	info, err := f.Stat()
	if err == nil && info.Size() >= 128 {
		trailer := make([]byte, 128)
		if _, err := f.ReadAt(trailer, info.Size()-128); err == nil {
			if string(trailer[0:3]) == "TAG" {
				t := cleanString(trailer[3:33])
				a := cleanString(trailer[33:63])
				alb := cleanString(trailer[63:93])
				if t != "" {
					title = t
				}
				if a != "" {
					artist = a
				}
				if alb != "" {
					album = alb
				}
			}
		}
	}

	return title, artist, album
}

func parseID3v2Frames(data []byte) (title, artist, album string) {
	pos := 0
	for pos+10 <= len(data) {
		frameID := string(data[pos : pos+4])
		if frameID[0] == 0 {
			break
		}

		// Frame size (syncsafe integer in ID3v2.4 or standard int in ID3v2.3)
		frameSize := int(data[pos+4])<<24 | int(data[pos+5])<<16 | int(data[pos+6])<<8 | int(data[pos+7])
		if frameSize <= 0 || pos+10+frameSize > len(data) {
			break
		}

		frameBody := data[pos+10 : pos+10+frameSize]
		pos += 10 + frameSize

		val := cleanFrameText(frameBody)
		if val == "" {
			continue
		}

		switch frameID {
		case "TIT2": // Title
			if title == "" {
				title = val
			}
		case "TPE1", "TPE2": // Artist / Band
			if artist == "" {
				artist = val
			}
		case "TALB": // Album
			if album == "" {
				album = val
			}
		}
	}
	return title, artist, album
}

func cleanFrameText(body []byte) string {
	if len(body) <= 1 {
		return ""
	}
	// Skip encoding byte at offset 0
	textBytes := body[1:]
	// Strip null padding
	textBytes = bytes.Trim(textBytes, "\x00")
	var sb strings.Builder
	for _, b := range textBytes {
		if b >= 32 && b < 127 {
			sb.WriteByte(b)
		} else if b > 127 {
			sb.WriteRune(rune(b))
		}
	}
	return strings.TrimSpace(sb.String())
}

func cleanString(b []byte) string {
	b = bytes.Trim(b, "\x00 ")
	var sb strings.Builder
	for _, c := range b {
		if unicode.IsGraphic(rune(c)) {
			sb.WriteByte(c)
		}
	}
	return strings.TrimSpace(sb.String())
}
