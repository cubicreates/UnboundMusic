/*
 * Package: shazam
 * File: signature.go
 * Purpose: Native Go SigX binary encoder and decoder implementing the reverse-engineered Shazam protocol.
 * Subsystem: Shazam Audio Recognition
 * Concurrency: Pure functions and thread-safe binary serialization.
 */

package shazam

import (
	"bytes"
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"hash/crc32"
	"sort"
)

const (
	// DataURIPrefix is the official Shazam signature data URI prefix.
	DataURIPrefix = "data:audio/vnd.shazam.sig;base64,"

	// Core SigX protocol header constants
	MagicHeader1 uint32 = 0xcafe2580
	MagicHeader2 uint32 = 0x94119c00
	ChunkTag40   uint32 = 0x40000000
	BandTagBase  uint32 = 0x60030040
)

// FrequencyBand represents Shazam's 4 core frequency bands.
const (
	Band250_520   = 0 // Band 0: 250 - 520 Hz (Bass & Low-Mid fundamentals)
	Band520_1450  = 1 // Band 1: 520 - 1450 Hz (Vocal range & Mid harmonics)
	Band1450_3500 = 2 // Band 2: 1450 - 3500 Hz (Upper-mid timbre)
	Band3500_5500 = 3 // Band 3: 3500 - 5500 Hz (Treble & Cymbals)
)

// FrequencyPeak represents an identified acoustic spectral peak.
type FrequencyPeak struct {
	FFTNumber    int     `json:"fft_number"`
	Magnitude    int     `json:"magnitude"`
	CorrectedBin int     `json:"corrected_bin"`
	SampleRateHz int     `json:"sample_rate_hz"`
	FrequencyHz  float64 `json:"frequency_hz"`
	TimeMs       int64   `json:"time_ms"`
	Band         int     `json:"band"`
}

// DecodedSignature represents the unpacked Shazam SigX data structure.
type DecodedSignature struct {
	SampleRateHz  int                     `json:"sample_rate_hz"`
	NumberSamples int                     `json:"number_samples"`
	Bands         map[int][]FrequencyPeak `json:"bands"`
}

// SignaturePayload encapsulates the encoded binary Shazam payload and base64 URI.
type SignaturePayload struct {
	SampleRate    int            `json:"sample_rate"`
	DurationMs    int64          `json:"duration_ms"`
	LandmarkCount int            `json:"landmark_count"`
	Base64URI     string         `json:"base64_uri"`
	BinaryData    []byte         `json:"-"`
}

// EncodeToBinary packs frequency peaks into Shazam's exact reverse-engineered 48-byte header SigX format.
func (s *DecodedSignature) EncodeToBinary() ([]byte, error) {
	if s.SampleRateHz <= 0 {
		s.SampleRateHz = 16000
	}

	var contentsBuf bytes.Buffer

	// Process bands in sorted order: 0, 1, 2, 3
	for band := 0; band <= 3; band++ {
		peaks := s.Bands[band]
		if len(peaks) == 0 {
			continue
		}

		// Ensure peaks are sorted by FFTNumber
		sortedPeaks := make([]FrequencyPeak, len(peaks))
		copy(sortedPeaks, peaks)
		sort.Slice(sortedPeaks, func(i, j int) bool {
			return sortedPeaks[i].FFTNumber < sortedPeaks[j].FFTNumber
		})

		var peaksBuf bytes.Buffer
		fftPassNumber := 0

		for _, p := range sortedPeaks {
			if p.FFTNumber < fftPassNumber {
				continue
			}

			diff := p.FFTNumber - fftPassNumber
			if diff >= 255 {
				peaksBuf.WriteByte(0xFF)
				_ = binary.Write(&peaksBuf, binary.LittleEndian, uint32(p.FFTNumber))
				fftPassNumber = p.FFTNumber
				diff = 0
			}

			peaksBuf.WriteByte(byte(diff))
			_ = binary.Write(&peaksBuf, binary.LittleEndian, uint16(p.Magnitude))
			_ = binary.Write(&peaksBuf, binary.LittleEndian, uint16(p.CorrectedBin))
			fftPassNumber = p.FFTNumber
		}

		peaksBytes := peaksBuf.Bytes()
		bandHeaderTag := BandTagBase + uint32(band)
		_ = binary.Write(&contentsBuf, binary.LittleEndian, bandHeaderTag)
		_ = binary.Write(&contentsBuf, binary.LittleEndian, uint32(len(peaksBytes)))
		contentsBuf.Write(peaksBytes)

		// 4-byte alignment padding
		padLen := (-len(peaksBytes)) & 3
		if padLen > 0 {
			contentsBuf.Write(make([]byte, padLen))
		}
	}

	contentsBytes := contentsBuf.Bytes()
	sizeMinusHeader := uint32(len(contentsBytes) + 8)

	// Build 48-byte header + first TLV chunk (8 bytes) + contents
	var fullBuf bytes.Buffer

	// Header:
	// 0..3: magic1 = 0xcafe2580
	_ = binary.Write(&fullBuf, binary.LittleEndian, MagicHeader1)
	// 4..7: crc32 placeholder
	_ = binary.Write(&fullBuf, binary.LittleEndian, uint32(0))
	// 8..11: size_minus_header
	_ = binary.Write(&fullBuf, binary.LittleEndian, sizeMinusHeader)
	// 12..15: magic2 = 0x94119c00
	_ = binary.Write(&fullBuf, binary.LittleEndian, MagicHeader2)
	// 16..27: void1 (12 bytes zeros)
	fullBuf.Write(make([]byte, 12))
	// 28..31: shifted_sample_rate_id (3 << 27 = 0x18000000 for 16kHz)
	_ = binary.Write(&fullBuf, binary.LittleEndian, uint32(3<<27))
	// 32..39: void2 (8 bytes zeros)
	fullBuf.Write(make([]byte, 8))
	// 40..43: number_samples + sample_rate * 0.24
	numSamplesWithOffset := uint32(s.NumberSamples + int(float64(s.SampleRateHz)*0.24))
	_ = binary.Write(&fullBuf, binary.LittleEndian, numSamplesWithOffset)
	// 44..47: fixed_value = (15 << 19) + 0x40000 = 0x007c0000
	_ = binary.Write(&fullBuf, binary.LittleEndian, uint32((15<<19)+0x40000))

	// First TLV chunk (8 bytes)
	_ = binary.Write(&fullBuf, binary.LittleEndian, ChunkTag40)
	_ = binary.Write(&fullBuf, binary.LittleEndian, sizeMinusHeader)

	// Contents
	fullBuf.Write(contentsBytes)

	data := fullBuf.Bytes()
	// Compute IEEE CRC-32 on data[8:]
	checksum := crc32.ChecksumIEEE(data[8:])
	binary.LittleEndian.PutUint32(data[4:8], checksum)

	return data, nil
}

// EncodeToURI returns the base64 Shazam data URI string.
func (s *DecodedSignature) EncodeToURI() (string, error) {
	bin, err := s.EncodeToBinary()
	if err != nil {
		return "", err
	}
	return DataURIPrefix + base64.StdEncoding.EncodeToString(bin), nil
}

// DecodeFromBinary parses raw SigX bytes back into a DecodedSignature with full CRC and format validation.
func DecodeFromBinary(data []byte) (*DecodedSignature, error) {
	if len(data) < 56 {
		return nil, fmt.Errorf("signature data too short: %d bytes (minimum 56 bytes)", len(data))
	}

	magic1 := binary.LittleEndian.Uint32(data[0:4])
	if magic1 != MagicHeader1 {
		return nil, fmt.Errorf("invalid magic1: 0x%x (expected 0x%x)", magic1, MagicHeader1)
	}

	expectedCRC := binary.LittleEndian.Uint32(data[4:8])
	actualCRC := crc32.ChecksumIEEE(data[8:])
	if actualCRC != expectedCRC {
		return nil, fmt.Errorf("CRC-32 checksum mismatch: expected 0x%x, got 0x%x", expectedCRC, actualCRC)
	}

	sizeMinusHeader := binary.LittleEndian.Uint32(data[8:12])
	if int(sizeMinusHeader) != len(data)-48 {
		return nil, fmt.Errorf("size_minus_header mismatch: %d vs actual %d", sizeMinusHeader, len(data)-48)
	}

	magic2 := binary.LittleEndian.Uint32(data[12:16])
	if magic2 != MagicHeader2 {
		return nil, fmt.Errorf("invalid magic2: 0x%x (expected 0x%x)", magic2, MagicHeader2)
	}

	shiftedSR := binary.LittleEndian.Uint32(data[28:32])
	srID := shiftedSR >> 27
	sampleRateHz := 16000
	if srID == 3 {
		sampleRateHz = 16000
	}

	numSamplesWithOffset := binary.LittleEndian.Uint32(data[40:44])
	numSamples := int(numSamplesWithOffset) - int(float64(sampleRateHz)*0.24)

	chunkTag := binary.LittleEndian.Uint32(data[48:52])
	if chunkTag != ChunkTag40 {
		return nil, fmt.Errorf("invalid first chunk tag: 0x%x (expected 0x%x)", chunkTag, ChunkTag40)
	}

	bands := make(map[int][]FrequencyPeak)
	offset := 56

	for offset+8 <= len(data) {
		bandTag := binary.LittleEndian.Uint32(data[offset : offset+4])
		bandLen := int(binary.LittleEndian.Uint32(data[offset+4 : offset+8]))
		offset += 8

		if offset+bandLen > len(data) {
			break
		}

		bandID := int(bandTag - BandTagBase)
		peaksData := data[offset : offset+bandLen]
		offset += bandLen

		// Skip 4-byte alignment padding
		padLen := (-bandLen) & 3
		offset += padLen

		var peaks []FrequencyPeak
		pOff := 0
		fftPassNumber := 0

		for pOff < len(peaksData) {
			rawByte := peaksData[pOff]
			pOff++

			if rawByte == 0xFF {
				if pOff+4 > len(peaksData) {
					break
				}
				fftPassNumber = int(binary.LittleEndian.Uint32(peaksData[pOff : pOff+4]))
				pOff += 4
				continue
			}

			fftPassNumber += int(rawByte)
			if pOff+4 > len(peaksData) {
				break
			}
			mag := int(binary.LittleEndian.Uint16(peaksData[pOff : pOff+2]))
			pOff += 2
			correctedBin := int(binary.LittleEndian.Uint16(peaksData[pOff : pOff+2]))
			pOff += 2

			freqHz := float64(correctedBin) * (float64(sampleRateHz) / 2.0 / 1024.0 / 64.0)
			timeMs := int64((fftPassNumber * 128 * 1000) / sampleRateHz)

			peaks = append(peaks, FrequencyPeak{
				FFTNumber:    fftPassNumber,
				Magnitude:    mag,
				CorrectedBin: correctedBin,
				SampleRateHz: sampleRateHz,
				FrequencyHz:  freqHz,
				TimeMs:       timeMs,
				Band:         bandID,
			})
		}

		bands[bandID] = peaks
	}

	return &DecodedSignature{
		SampleRateHz:  sampleRateHz,
		NumberSamples: numSamples,
		Bands:         bands,
	}, nil
}

// EncodeConstellationToSignature packs ConstellationMap into a SignaturePayload.
func EncodeConstellationToSignature(cmap *ConstellationMap) (*SignaturePayload, error) {
	if cmap == nil {
		return nil, fmt.Errorf("cannot encode nil constellation map")
	}

	sampleRate := cmap.SampleRate
	if sampleRate <= 0 {
		sampleRate = 16000
	}

	bands := make(map[int][]FrequencyPeak)
	totalPeaks := 0

	for _, p := range cmap.Peaks {
		band := p.Band
		if band < 0 || band > 3 {
			continue
		}
		bands[band] = append(bands[band], p)
		totalPeaks++
	}

	numSamples := int(int64(sampleRate) * cmap.DurationMs / 1000)
	if numSamples <= 0 {
		numSamples = 16000 * 4
	}

	sig := &DecodedSignature{
		SampleRateHz:  sampleRate,
		NumberSamples: numSamples,
		Bands:         bands,
	}

	bin, err := sig.EncodeToBinary()
	if err != nil {
		return nil, err
	}

	uri := DataURIPrefix + base64.StdEncoding.EncodeToString(bin)

	return &SignaturePayload{
		SampleRate:    sampleRate,
		DurationMs:    cmap.DurationMs,
		LandmarkCount: totalPeaks,
		Base64URI:     uri,
		BinaryData:    bin,
	}, nil
}
