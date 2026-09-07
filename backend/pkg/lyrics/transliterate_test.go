/*
 * Package: lyrics
 * File: transliterate_test.go
 * Purpose: Unit tests for pure Go phonetic transliteration of Japanese, Korean, and Indic scripts.
 * Subsystem: Lyrics & Typography Engine
 * Concurrency: Standard Go testing framework.
 */

package lyrics

import (
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestTransliterate_Japanese(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{
			input:    "ありがとう",
			expected: "arigatou",
		},
		{
			input:    "さようなら",
			expected: "sayounara",
		},
		{
			input:    "こんにちは",
			expected: "konnichiha",
		},
		{
			input:    "トーキョー",
			expected: "toukyou",
		},
		{
			input:    "ちょっと",
			expected: "chotto",
		},
	}

	for _, tt := range tests {
		actual := RomanizeText(tt.input)
		if actual != tt.expected {
			t.Errorf("RomanizeText(%q) = %q; want %q", tt.input, actual, tt.expected)
		}
	}
}

func TestTransliterate_Korean(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{
			input:    "안녕",
			expected: "annyeong",
		},
		{
			input:    "사랑해",
			expected: "saranghae",
		},
		{
			input:    "케이팝",
			expected: "keipap",
		},
		{
			input:    "음악",
			expected: "eumak",
		},
	}

	for _, tt := range tests {
		actual := RomanizeText(tt.input)
		if actual != tt.expected {
			t.Errorf("RomanizeText(%q) = %q; want %q", tt.input, actual, tt.expected)
		}
	}
}

func TestTransliterate_Devanagari(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{
			input:    "नमस्ते",
			expected: "namaste",
		},
		{
			input:    "गाना",
			expected: "gaanaa",
		},
		{
			input:    "प्यार",
			expected: "pyaar",
		},
	}

	for _, tt := range tests {
		actual := RomanizeText(tt.input)
		if actual != tt.expected {
			t.Errorf("RomanizeText(%q) = %q; want %q", tt.input, actual, tt.expected)
		}
	}
}

func TestTransliterate_EnglishPassthrough(t *testing.T) {
	english := "Never gonna give you up, never gonna let you down"
	actual := RomanizeText(english)
	if actual != english {
		t.Errorf("expected English text to pass through unchanged, got %q", actual)
	}
}

func TestRomanizeLyrics(t *testing.T) {
	lines := []models.LyricLine{
		{
			StartMs: 1000,
			EndMs:   3000,
			Text:    "안녕, my friend",
		},
		{
			StartMs: 3000,
			EndMs:   5000,
			Text:    "ありがとう!",
		},
		{
			StartMs: 5000,
			EndMs:   7000,
			Text:    "Just English lyrics here",
		},
	}

	romanized := RomanizeLyrics(lines)

	if len(romanized) != 3 {
		t.Fatalf("expected 3 lines, got %d", len(romanized))
	}

	// First line should have romanized text
	if romanized[0].Romanized == "" {
		t.Errorf("expected line 0 to have Romanized text, got empty")
	}

	// Second line should have romanized text
	if romanized[1].Romanized != "arigatou!" {
		t.Errorf("expected line 1 Romanized to be 'arigatou!', got %q", romanized[1].Romanized)
	}

	// Timestamps must remain unmodified
	if romanized[0].StartMs != 1000 || romanized[0].EndMs != 3000 {
		t.Errorf("timestamps modified unexpectedly on line 0")
	}
}
