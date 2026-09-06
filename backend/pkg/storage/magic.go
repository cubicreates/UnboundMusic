/*
 * Package: storage
 * File: magic.go
 * Purpose: 32-byte binary magic byte identification for raw audio formats without relying on file extensions.
 * Subsystem: Storage & Indexing Engine
 * Concurrency: Thread-safe pure function with zero allocations.
 */

package storage

import "bytes"

// DetectMagicBytes evaluates the first 32 bytes of a file header to determine the canonical audio format.
// Returns empty string if the format is not a recognized audio stream.
func DetectMagicBytes(header []byte) string {
	if len(header) < 4 {
		return ""
	}

	// 1. MP3 with ID3v2 container ("ID3")
	if header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33 {
		return "mp3"
	}

	// 2. Raw MP3 sync words without ID3 header (0xFF 0xFB, 0xFF 0xF3, 0xFF 0xF2)
	if header[0] == 0xFF && (header[1] == 0xFB || header[1] == 0xF3 || header[1] == 0xF2) {
		return "mp3"
	}

	// 3. Lossless FLAC ("fLaC")
	if header[0] == 0x66 && header[1] == 0x4C && header[2] == 0x61 && header[3] == 0x43 {
		return "flac"
	}

	// 4. OGG Container / Opus audio ("OggS") - commonly used by WhatsApp voice & audio notes
	if header[0] == 0x4F && header[1] == 0x67 && header[2] == 0x67 && header[3] == 0x53 {
		return "opus"
	}

	// 5. Windows Media Audio / ASF GUID (0x30 0x26 0xB2 0x75)
	if header[0] == 0x30 && header[1] == 0x26 && header[2] == 0xB2 && header[3] == 0x75 {
		return "wma"
	}

	// 6. WAV Audio (RIFF header with WAVE format identifier at offset 8)
	if len(header) >= 12 {
		if header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' {
			if header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E' {
				return "wav"
			}
		}
	}

	// 7. MP4 / M4A / AAC container ("ftyp" at offset 4)
	if len(header) >= 12 {
		if header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p' {
			brand := header[8:12]
			if bytes.Equal(brand, []byte("M4A ")) ||
				bytes.Equal(brand, []byte("M4B ")) ||
				bytes.Equal(brand, []byte("mp42")) ||
				bytes.Equal(brand, []byte("isom")) {
				return "m4a"
			}
		}
	}

	return ""
}
