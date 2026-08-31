/*
 * Package: shazam
 * File: signature.go
 * Purpose: Encodes frequency peak landmarks into Shazam binary signature format (SignatureRingBuffer) and generates base64 payloads for discovery.
 * Subsystem: Shazam Audio Recognition
 * Concurrency: Thread-safe binary encoder with zero shared mutable state.
 */

package shazam

import (
	"bytes"
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"hash/crc32"
)

// LandmarkHash represents a paired time-frequency anchor: (freq1, freq2, delta_time).
type LandmarkHash struct {
	AnchorTimeMs int64  `json:"anchor_time_ms"`
	Freq1Hz      int    `json:"freq1_hz"`
	Freq2Hz      int    `json:"freq2_hz"`
	DeltaTimeMs  int64  `json:"delta_time_ms"`
	HashValue    uint32 `json:"hash_value"`
}

// SignaturePayload encapsulates the encoded binary Shazam payload.
type SignaturePayload struct {
	SampleRate   int            `json:"sample_rate"`
	DurationMs   int64          `json:"duration_ms"`
	LandmarkCount int           `json:"landmark_count"`
	Base64URI    string         `json:"base64_uri"`
	BinaryData   []byte         `json:"-"`
}

// EncodeConstellationToSignature pairs constellation peaks and produces a Shazam signature.
func EncodeConstellationToSignature(cmap *ConstellationMap) (*SignaturePayload, error) {
	if cmap == nil || len(cmap.Peaks) == 0 {
		return nil, fmt.Errorf("cannot encode empty constellation map")
	}

	// Pair landmarks
	landmarks := pairLandmarks(cmap.Peaks)
	if len(landmarks) == 0 {
		return nil, fmt.Errorf("no valid landmark pairs found in audio spectrum")
	}

	// Build binary signature
	var buf bytes.Buffer

	// 1. Header: Magic [0xcafe2801], SampleRate [uint32], DurationMs [uint32], LandmarkCount [uint32]
	const magicHeader uint32 = 0xcafe2801
	_ = binary.Write(&buf, binary.LittleEndian, magicHeader)
	_ = binary.Write(&buf, binary.LittleEndian, uint32(cmap.SampleRate))
	_ = binary.Write(&buf, binary.LittleEndian, uint32(cmap.DurationMs))
	_ = binary.Write(&buf, binary.LittleEndian, uint32(len(landmarks)))

	// 2. Body: Encode each landmark [AnchorTime:4][HashValue:4]
	for _, lm := range landmarks {
		_ = binary.Write(&buf, binary.LittleEndian, uint32(lm.AnchorTimeMs))
		_ = binary.Write(&buf, binary.LittleEndian, lm.HashValue)
	}

	// 3. CRC32 Checksum trailer
	rawBytes := buf.Bytes()
	checksum := crc32.ChecksumIEEE(rawBytes)
	_ = binary.Write(&buf, binary.LittleEndian, checksum)

	finalBinary := buf.Bytes()
	b64String := fmt.Sprintf("data:audio/vnd.shazam.sig;base64,%s", base64.StdEncoding.EncodeToString(finalBinary))

	return &SignaturePayload{
		SampleRate:    cmap.SampleRate,
		DurationMs:    cmap.DurationMs,
		LandmarkCount: len(landmarks),
		Base64URI:     b64String,
		BinaryData:    finalBinary,
	}, nil
}

// pairLandmarks combines peaks within a 3-second forward target window into combinatorial landmark hashes.
func pairLandmarks(peaks []FrequencyPeak) []LandmarkHash {
	var landmarks []LandmarkHash
	numPeaks := len(peaks)

	for i := 0; i < numPeaks; i++ {
		p1 := peaks[i]

		// Pair with up to 5 subsequent peaks within 3000ms
		pairCount := 0
		for j := i + 1; j < numPeaks && pairCount < 5; j++ {
			p2 := peaks[j]
			dt := p2.TimeMs - p1.TimeMs
			if dt <= 0 {
				continue
			}
			if dt > 3000 {
				break
			}

			f1 := int(p1.FrequencyHz)
			f2 := int(p2.FrequencyHz)

			// Combine into 32-bit hash: [f1:10 bits][f2:10 bits][dt:12 bits]
			hashVal := uint32(((f1 & 0x3FF) << 22) | ((f2 & 0x3FF) << 12) | (int(dt) & 0xFFF))

			landmarks = append(landmarks, LandmarkHash{
				AnchorTimeMs: p1.TimeMs,
				Freq1Hz:      f1,
				Freq2Hz:      f2,
				DeltaTimeMs:  dt,
				HashValue:    hashVal,
			})
			pairCount++
		}
	}

	return landmarks
}
