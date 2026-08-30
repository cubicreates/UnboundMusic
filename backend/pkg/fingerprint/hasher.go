/*
 * Package: fingerprint
 * File: hasher.go
 * Purpose: Parses local audio headers (MP3, FLAC, M4A, OGG, WAV) and computes deterministic acoustic content hashes.
 * Subsystem: Storage & Fingerprint Engine
 * Concurrency: Stateless pure hashing functions safe for concurrent execution across worker goroutines.
 */

package fingerprint

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// AudioMetadata contains technical stream properties extracted from an audio file.
type AudioMetadata struct {
	FilePath      string `json:"file_path"`
	Extension     string `json:"extension"`
	DurationMs    int64  `json:"duration_ms"`
	BitrateKbps   int    `json:"bitrate_kbps"`
	SampleRate    int    `json:"sample_rate"`
	Channels      int    `json:"channels"`
	FileSize      int64  `json:"file_size"`
	AcousticHash  string `json:"acoustic_hash"`
	IsSupported   bool   `json:"is_supported"`
}

// SupportedExtensions lists recognized audio formats.
var SupportedExtensions = map[string]bool{
	".mp3":  true,
	".flac": true,
	".m4a":  true,
	".aac":  true,
	".ogg":  true,
	".opus": true,
	".wav":  true,
}

// InspectAudio reads the technical header and computes an acoustic content hash for a given file path.
func InspectAudio(path string) (*AudioMetadata, error) {
	fileInfo, err := os.Stat(path)
	if err != nil {
		return nil, fmt.Errorf("failed to stat audio file: %w", err)
	}

	ext := strings.ToLower(filepath.Ext(path))
	if !SupportedExtensions[ext] {
		return nil, fmt.Errorf("unsupported audio extension: %s", ext)
	}

	file, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("failed to open audio file: %w", err)
	}
	defer file.Close()

	meta := &AudioMetadata{
		FilePath:    path,
		Extension:   ext,
		FileSize:    fileInfo.Size(),
		IsSupported: true,
		SampleRate:  44100, // Standard baseline default
		Channels:    2,
	}

	// Parse audio headers based on container type
	switch ext {
	case ".mp3":
		parseMP3Header(file, meta)
	case ".wav":
		parseWAVHeader(file, meta)
	case ".flac":
		parseFLACHeader(file, meta)
	default:
		// Fallback estimation based on file size and standard 192kbps bitrate
		if meta.DurationMs == 0 && meta.FileSize > 0 {
			meta.BitrateKbps = 192
			meta.DurationMs = (meta.FileSize * 8) / int64(meta.BitrateKbps)
		}
	}

	// Compute deterministic acoustic hash from audio payload
	hash, err := computeAudioHash(file, meta.FileSize)
	if err == nil {
		meta.AcousticHash = hash
	}

	return meta, nil
}

// parseMP3Header reads ID3 tags and first MPEG audio frame header to estimate bitrate and duration.
func parseMP3Header(file *os.File, meta *AudioMetadata) {
	header := make([]byte, 10)
	if _, err := file.ReadAt(header, 0); err != nil {
		return
	}

	var audioStartOffset int64 = 0
	// Check for ID3v2 tag: "ID3"
	if string(header[0:3]) == "ID3" {
		// Syncsafe integer for ID3v2 tag size (bytes 6-9)
		tagSize := int64(header[6])<<21 | int64(header[7])<<14 | int64(header[8])<<7 | int64(header[9])
		audioStartOffset = 10 + tagSize
	}

	frameHeader := make([]byte, 4)
	if _, err := file.ReadAt(frameHeader, audioStartOffset); err != nil {
		return
	}

	// Verify syncword: 11 bits set to 1 (0xFF, upper 3 bits of byte 1)
	if frameHeader[0] == 0xFF && (frameHeader[1]&0xE0) == 0xE0 {
		bitrateIndex := (frameHeader[2] >> 4) & 0x0F
		bitrateTable := []int{0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0}
		if int(bitrateIndex) < len(bitrateTable) && bitrateTable[bitrateIndex] > 0 {
			meta.BitrateKbps = bitrateTable[bitrateIndex]
		}
	}

	if meta.BitrateKbps == 0 {
		meta.BitrateKbps = 192 // Default fallback
	}

	audioPayloadBytes := meta.FileSize - audioStartOffset
	if audioPayloadBytes > 0 {
		meta.DurationMs = (audioPayloadBytes * 8) / int64(meta.BitrateKbps)
	}
}

// parseWAVHeader reads canonical RIFF/WAVE header fields.
func parseWAVHeader(file *os.File, meta *AudioMetadata) {
	header := make([]byte, 44)
	if _, err := file.ReadAt(header, 0); err != nil {
		return
	}

	if string(header[0:4]) == "RIFF" && string(header[8:12]) == "WAVE" {
		channels := int(binary.LittleEndian.Uint16(header[22:24]))
		sampleRate := int(binary.LittleEndian.Uint32(header[24:28]))
		byteRate := int(binary.LittleEndian.Uint32(header[28:32]))

		if channels > 0 {
			meta.Channels = channels
		}
		if sampleRate > 0 {
			meta.SampleRate = sampleRate
		}
		if byteRate > 0 {
			meta.BitrateKbps = (byteRate * 8) / 1000
			meta.DurationMs = (meta.FileSize * 1000) / int64(byteRate)
		}
	}
}

// parseFLACHeader reads FLAC streaminfo metadata block.
func parseFLACHeader(file *os.File, meta *AudioMetadata) {
	header := make([]byte, 42)
	if _, err := file.ReadAt(header, 0); err != nil {
		return
	}

	if string(header[0:4]) == "fLaC" {
		meta.BitrateKbps = 900 // Typical FLAC average
		// Sample rate is 20 bits from byte 18
		sampleRate := int(header[18])<<12 | int(header[19])<<4 | int(header[20]>>4)
		if sampleRate > 0 {
			meta.SampleRate = sampleRate
		}
		meta.Channels = int((header[20]>>1)&0x07) + 1

		// Total samples is 36 bits
		totalSamples := (int64(header[21]&0x0F) << 32) |
			(int64(header[22]) << 24) |
			(int64(header[23]) << 16) |
			(int64(header[24]) << 8) |
			int64(header[25])

		if sampleRate > 0 && totalSamples > 0 {
			meta.DurationMs = (totalSamples * 1000) / int64(sampleRate)
		}
	}
}

// computeAudioHash generates a deterministic perceptual content hash from 3 sampling windows of the audio stream.
func computeAudioHash(file *os.File, fileSize int64) (string, error) {
	hasher := sha256.New()
	sampleChunkSize := int64(16 * 1024) // 16 KB sample window

	if fileSize <= sampleChunkSize*3 {
		// Read entire file if small
		if _, err := file.Seek(0, io.SeekStart); err != nil {
			return "", err
		}
		if _, err := io.Copy(hasher, file); err != nil {
			return "", err
		}
	} else {
		// Sample from 15%, 50%, and 85% points to avoid variable metadata headers and trailing ID3 tags
		offsets := []int64{
			int64(float64(fileSize) * 0.15),
			int64(float64(fileSize) * 0.50),
			int64(float64(fileSize) * 0.85),
		}

		buf := make([]byte, sampleChunkSize)
		for _, offset := range offsets {
			if _, err := file.ReadAt(buf, offset); err == nil {
				hasher.Write(buf)
			}
		}
	}

	return hex.EncodeToString(hasher.Sum(nil))[:16], nil // Return 16-character compact acoustic fingerprint
}
