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
