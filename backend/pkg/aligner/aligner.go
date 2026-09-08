/*
 * Package: aligner
 * File: aligner.go
 * Purpose: On-device forced audio alignment engine generating millisecond syllable and line timestamps from Genius text.
 * Subsystem: On-Device Lyric Alignment Engine
 * Concurrency: Thread-safe; handles multiple alignment tasks concurrently.
 */

package aligner

import (
	"fmt"
	"math"
	"regexp"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

var (
	// regexSectionMarker matches bracketed section labels like [Verse 1], [Chorus], [Intro], [Guitar Solo]
	regexSectionMarker = regexp.MustCompile(`^\[(.*)\]$`)
)

// AudioEnergyWindow represents an audio window with its calculated RMS energy and vocal classification.
type AudioEnergyWindow struct {
	StartMs int64   `json:"start_ms"`
	EndMs   int64   `json:"end_ms"`
	Energy  float64 `json:"energy"`
	IsVocal bool    `json:"is_vocal"`
}

// VocalSegment represents a continuous block of active vocalization.
type VocalSegment struct {
	StartMs int64 `json:"start_ms"`
	EndMs   int64 `json:"end_ms"`
}

// ComputeRMSWindows calculates root-mean-square energy windows across raw PCM samples.
func ComputeRMSWindows(samples []int16, sampleRate int, windowSizeMs int, vocalThreshold float64) []AudioEnergyWindow {
	if sampleRate <= 0 {
		sampleRate = 44100
	}
	if windowSizeMs <= 0 {
		windowSizeMs = 100 // 100ms default window
	}
	samplesPerWindow := (sampleRate * windowSizeMs) / 1000
	if samplesPerWindow <= 0 {
		samplesPerWindow = 1
	}

	var windows []AudioEnergyWindow
	numSamples := len(samples)

	for i := 0; i < numSamples; i += samplesPerWindow {
		end := i + samplesPerWindow
		if end > numSamples {
			end = numSamples
		}

		var sumSquares float64
		for j := i; j < end; j++ {
			val := float64(samples[j]) / 32768.0
			sumSquares += val * val
		}
		count := end - i
		rms := 0.0
		if count > 0 {
			rms = math.Sqrt(sumSquares / float64(count))
		}

		startMs := int64(i * 1000 / sampleRate)
		endMs := int64(end * 1000 / sampleRate)

		isVocal := rms >= vocalThreshold
		windows = append(windows, AudioEnergyWindow{
			StartMs: startMs,
			EndMs:   endMs,
			Energy:  rms,
			IsVocal: isVocal,
		})
	}
	return windows
}

// ExtractVocalSegments merges contiguous or near-contiguous active vocal windows,
// separating sections where silence or non-vocal instrumentals exceed minSilenceMs.
func ExtractVocalSegments(windows []AudioEnergyWindow, minSilenceMs int64) []VocalSegment {
	if len(windows) == 0 {
		return nil
	}

	var rawVocalSpans []VocalSegment
	var curStart int64 = -1
	var curEnd int64 = -1

	for _, w := range windows {
		if w.IsVocal {
			if curStart == -1 {
				curStart = w.StartMs
				curEnd = w.EndMs
			} else {
				curEnd = w.EndMs
			}
		} else {
			if curStart != -1 {
				rawVocalSpans = append(rawVocalSpans, VocalSegment{StartMs: curStart, EndMs: curEnd})
				curStart = -1
				curEnd = -1
			}
		}
	}
	if curStart != -1 {
		rawVocalSpans = append(rawVocalSpans, VocalSegment{StartMs: curStart, EndMs: curEnd})
	}

	if len(rawVocalSpans) == 0 {
		return nil
	}

	// Merge spans separated by brief pauses (< minSilenceMs)
	var merged []VocalSegment
	current := rawVocalSpans[0]

	for i := 1; i < len(rawVocalSpans); i++ {
		next := rawVocalSpans[i]
		gap := next.StartMs - current.EndMs
		if gap < minSilenceMs {
			current.EndMs = next.EndMs
		} else {
			if current.EndMs-current.StartMs >= 200 { // Discard micro-noise spikes < 200ms
				merged = append(merged, current)
			}
			current = next
		}
	}
	if current.EndMs-current.StartMs >= 200 {
		merged = append(merged, current)
	}

	return merged
}

// ForcedAligner coordinates on-device lyric timestamp generation.
type ForcedAligner struct{}

// NewForcedAligner instantiates a new on-device lyric alignment engine.
func NewForcedAligner() *ForcedAligner {
	return &ForcedAligner{}
}

// AlignLyrics maps plain text lyrics across an audio track duration to generate millisecond timestamps.
func (a *ForcedAligner) AlignLyrics(trackID, title, artist, plainText string, durationMs int64) (*models.LyricsPayload, error) {
	return a.AlignLyricsWithVAD(trackID, title, artist, plainText, durationMs, nil)
}

// AlignLyricsWithEnergy calculates energy windows and vocal segments from PCM audio before aligning lyrics.
func (a *ForcedAligner) AlignLyricsWithEnergy(trackID, title, artist, plainText string, durationMs int64, pcmSamples []int16, sampleRate int) (*models.LyricsPayload, error) {
	windows := ComputeRMSWindows(pcmSamples, sampleRate, 100, 0.02)
	segments := ExtractVocalSegments(windows, 1500)
	return a.AlignLyricsWithVAD(trackID, title, artist, plainText, durationMs, segments)
}

// AlignLyricsWithVAD anchors lyric lines and syllables exclusively to active vocal segments.
// If vocalSegments is nil or empty, it falls back to standard proportional distribution across track duration.
func (a *ForcedAligner) AlignLyricsWithVAD(trackID, title, artist, plainText string, durationMs int64, vocalSegments []VocalSegment) (*models.LyricsPayload, error) {
	if strings.TrimSpace(plainText) == "" {
		return nil, fmt.Errorf("plain text lyrics cannot be empty")
	}

	if durationMs <= 5000 {
		durationMs = 180000 // Fallback default to 3 minutes if duration is not provided
	}

	rawLines := strings.Split(plainText, "\n")
	var lyricLines []string
	var totalWeight float64

	for _, line := range rawLines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			continue
		}
		lyricLines = append(lyricLines, trimmed)
		tokens := TokenizeLinePhonetics(trimmed)
		for _, t := range tokens {
			totalWeight += t.Weight
		}
	}

	if len(lyricLines) == 0 {
		return nil, fmt.Errorf("no valid lyric lines to align")
	}

	if totalWeight <= 0 {
		totalWeight = float64(len(lyricLines))
	}

	// If no vocal segments provided, synthesize default active vocal window (5% intro, 5% outro)
	if len(vocalSegments) == 0 {
		introOffsetMs := int64(float64(durationMs) * 0.05)
		if introOffsetMs < 2000 {
			introOffsetMs = 2000
		}
		outroOffsetMs := int64(float64(durationMs) * 0.95)
		if outroOffsetMs <= introOffsetMs {
			outroOffsetMs = durationMs
		}
		vocalSegments = []VocalSegment{{StartMs: introOffsetMs, EndMs: outroOffsetMs}}
	}

	var totalVocalBudgetMs float64
	for _, seg := range vocalSegments {
		totalVocalBudgetMs += float64(seg.EndMs - seg.StartMs)
	}

	if totalVocalBudgetMs <= 0 {
		totalVocalBudgetMs = float64(durationMs)
		vocalSegments = []VocalSegment{{StartMs: 0, EndMs: durationMs}}
	}

	var structuredLines []models.LyricLine
	currentBudgetProgress := 0.0

	for _, lineStr := range lyricLines {
		tokens := TokenizeLinePhonetics(lineStr)
		lineWeight := 0.0
		for _, t := range tokens {
			lineWeight += t.Weight
		}
		if lineWeight <= 0 {
			lineWeight = 1.0
		}

		lineBudgetMs := (lineWeight / totalWeight) * totalVocalBudgetMs
		if lineBudgetMs < 800 {
			lineBudgetMs = 800
		}

		bStart := currentBudgetProgress
		bEnd := currentBudgetProgress + lineBudgetMs
		currentBudgetProgress = bEnd

		lineStart, segIdxStart := mapBudgetToReal(bStart, vocalSegments)
		lineEnd, segIdxEnd := mapBudgetToReal(bEnd, vocalSegments)

		// Prevent line from straddling across a long instrumental solo / silence gap
		if segIdxStart != segIdxEnd {
			gap := vocalSegments[segIdxEnd].StartMs - vocalSegments[segIdxStart].EndMs
			if gap >= 1000 {
				portionInSegStart := float64(vocalSegments[segIdxStart].EndMs - lineStart)
				if portionInSegStart < lineBudgetMs*0.4 {
					lineStart = vocalSegments[segIdxEnd].StartMs
					lineEnd = lineStart + int64(lineBudgetMs)
				} else {
					lineEnd = vocalSegments[segIdxStart].EndMs
				}
			}
		}

		if lineEnd <= lineStart {
			lineEnd = lineStart + 800
		}

		syllables := TokensToSyllableModels(tokens, lineStart, lineEnd)

		structuredLines = append(structuredLines, models.LyricLine{
			Text:      lineStr,
			StartMs:   lineStart,
			EndMs:     lineEnd,
			Syllables: syllables,
		})
	}

	return &models.LyricsPayload{
		TrackID:      trackID,
		Title:        title,
		Artist:       artist,
		PlainLyrics:  plainText,
		Lines:        structuredLines,
		IsWordSynced: true,
		Source:       "On-Device Phonetic Alignment (Energy-Gated VAD)",
	}, nil
}

// mapBudgetToReal maps a position in virtual vocal budget space to an actual audio millisecond timestamp.
func mapBudgetToReal(budgetMs float64, segments []VocalSegment) (int64, int) {
	var accumulated float64
	for i, seg := range segments {
		segDur := float64(seg.EndMs - seg.StartMs)
		if budgetMs <= accumulated+segDur || i == len(segments)-1 {
			offset := budgetMs - accumulated
			if offset < 0 {
				offset = 0
			}
			if offset > segDur {
				offset = segDur
			}
			return seg.StartMs + int64(offset), i
		}
		accumulated += segDur
	}
	last := segments[len(segments)-1]
	return last.EndMs, len(segments) - 1
}
