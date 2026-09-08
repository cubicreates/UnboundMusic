/*
 * Package: aligner
 * File: aligner_test.go
 * Purpose: Unit tests for phonetic syllable segmentation, vowel duration weighting, and forced lyric alignment.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package aligner

import (
	"testing"
)

// TestTokenizeLinePhonetics validates phonetic syllable splitting and duration weights.
func TestTokenizeLinePhonetics(t *testing.T) {
	line := "Loyalty, got royalty inside my DNA"
	tokens := TokenizeLinePhonetics(line)

	if len(tokens) == 0 {
		t.Fatalf("expected non-empty tokens slice")
	}

	foundDNA := false
	for _, tok := range tokens {
		if tok.Weight <= 0 {
			t.Errorf("token %q has invalid weight %f", tok.Text, tok.Weight)
		}
		if tok.Text == "DNA" || tok.Text == "NA" {
			foundDNA = true
		}
	}

	if !foundDNA {
		t.Errorf("expected DNA token in phonetics result")
	}
}

// TestAlignLyrics validates forced alignment of plain Genius lyrics across track duration.
func TestAlignLyrics(t *testing.T) {
	aligner := NewForcedAligner()

	plainText := `
I got, I got, I got, I got
Loyalty, got royalty inside my DNA
Cocaine quarter piece, got war and peace inside my DNA
`
	durationMs := int64(186000) // 3m 6s

	payload, err := aligner.AlignLyrics("kendrick_dna", "DNA", "Kendrick Lamar", plainText, durationMs)
	if err != nil {
		t.Fatalf("AlignLyrics failed: %v", err)
	}

	if !payload.IsWordSynced {
		t.Errorf("expected IsWordSynced to be true")
	}

	if len(payload.Lines) != 3 {
		t.Fatalf("expected 3 lines, got %d", len(payload.Lines))
	}

	// Verify chronological monotonicity: each line starts after the previous line
	for i := 0; i < len(payload.Lines); i++ {
		line := payload.Lines[i]
		if line.StartMs >= line.EndMs {
			t.Errorf("line %d has invalid timestamps [%d -> %d]", i, line.StartMs, line.EndMs)
		}

		if len(line.Syllables) == 0 {
			t.Errorf("line %d has no syllables", i)
		}

		// Verify syllable chronological continuity
		for s := 0; s < len(line.Syllables); s++ {
			syllable := line.Syllables[s]
			if syllable.StartMs >= syllable.EndMs {
				t.Errorf("syllable %d in line %d has invalid range [%d -> %d]", s, i, syllable.StartMs, syllable.EndMs)
			}
		}

		if i > 0 {
			prevLine := payload.Lines[i-1]
			if line.StartMs < prevLine.StartMs {
				t.Errorf("chronological order violated between line %d and %d", i-1, i)
			}
		}
	}
}

// TestEnergyGatedVADAlignment verifies that instrumental gaps (e.g. guitar solo between 20s and 60s)
// prevent lyrics and syllables from advancing across the quiet/instrumental interval.
func TestEnergyGatedVADAlignment(t *testing.T) {
	aligner := NewForcedAligner()

	plainText := "First vocal line before the guitar solo\nSecond vocal line after the guitar solo"
	durationMs := int64(100000) // 100 seconds

	// Define two vocal segments separated by a 40-second guitar solo [20000ms - 60000ms]
	vocalSegments := []VocalSegment{
		{StartMs: 5000, EndMs: 20000},  // First verse
		{StartMs: 60000, EndMs: 80000}, // Second verse after 40s solo
	}

	payload, err := aligner.AlignLyricsWithVAD("solo_track", "Guitar Track", "Artist", plainText, durationMs, vocalSegments)
	if err != nil {
		t.Fatalf("AlignLyricsWithVAD failed: %v", err)
	}

	if len(payload.Lines) != 2 {
		t.Fatalf("expected 2 lines, got %d", len(payload.Lines))
	}

	line1 := payload.Lines[0]
	line2 := payload.Lines[1]

	// Line 1 must finish by 20000ms (end of first vocal segment)
	if line1.EndMs > 20000 {
		t.Errorf("expected line 1 to end by 20000ms, got endMs=%d", line1.EndMs)
	}

	// Line 2 must not start before 60000ms (start of second vocal segment)
	if line2.StartMs < 60000 {
		t.Errorf("expected line 2 to start at or after 60000ms, got startMs=%d", line2.StartMs)
	}

	// Ensure no syllables in line 1 or line 2 land in the guitar solo window [20000, 60000]
	for _, syl := range line1.Syllables {
		if syl.StartMs >= 20000 {
			t.Errorf("line 1 syllable %q leaked into guitar solo window at %dms", syl.Text, syl.StartMs)
		}
	}
	for _, syl := range line2.Syllables {
		if syl.StartMs < 60000 {
			t.Errorf("line 2 syllable %q leaked into guitar solo window at %dms", syl.Text, syl.StartMs)
		}
	}
}

// TestComputeRMSWindowsAndVocalSegments verifies energy extraction from synthesized PCM samples.
func TestComputeRMSWindowsAndVocalSegments(t *testing.T) {
	sampleRate := 44100
	// 1 second silence, 1 second tone (vocal), 2 seconds silence (solo), 1 second tone
	totalSec := 5
	samples := make([]int16, sampleRate*totalSec)

	// Fill second 1..2 with loud 440Hz tone
	for i := sampleRate; i < sampleRate*2; i++ {
		samples[i] = 16000
	}
	// Fill second 4..5 with loud 440Hz tone
	for i := sampleRate * 4; i < sampleRate*5; i++ {
		samples[i] = 16000
	}

	windows := ComputeRMSWindows(samples, sampleRate, 100, 0.05)
	if len(windows) != totalSec*10 {
		t.Errorf("expected %d windows, got %d", totalSec*10, len(windows))
	}

	segments := ExtractVocalSegments(windows, 1000)
	if len(segments) != 2 {
		t.Fatalf("expected 2 distinct vocal segments separated by solo gap, got %d", len(segments))
	}

	// First segment should be ~1000ms - 2000ms
	if segments[0].StartMs < 900 || segments[0].EndMs > 2100 {
		t.Errorf("unexpected segment 0 bounds: [%d -> %d]", segments[0].StartMs, segments[0].EndMs)
	}
	// Second segment should be ~4000ms - 5000ms
	if segments[1].StartMs < 3900 || segments[1].EndMs > 5100 {
		t.Errorf("unexpected segment 1 bounds: [%d -> %d]", segments[1].StartMs, segments[1].EndMs)
	}
}
