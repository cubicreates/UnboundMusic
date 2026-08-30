/*
 * Package: aligner
 * File: phonetics.go
 * Purpose: Splits words and phrases into phonetic syllable tokens and computes vowel weighting for smooth kinetic lyric glow.
 * Subsystem: On-Device Lyric Alignment Engine
 * Concurrency: Stateless pure functions safe for concurrent execution across worker goroutines.
 */

package aligner

import (
	"strings"
	"unicode"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

// VowelClusters defines primary English vowel nuclei for syllable counting and timing weights.
var vowelClusters = []string{"ai", "au", "aw", "ay", "ea", "ee", "ei", "eu", "ew", "ey", "ie", "oi", "oo", "ou", "ow", "oy", "a", "e", "i", "o", "u", "y"}

// PhoneticToken represents a single segmented phonetic unit with duration weight.
type PhoneticToken struct {
	Text   string  `json:"text"`
	Weight float64 `json:"weight"`
}

// TokenizeLinePhonetics segments a lyric line into phonetic word/syllable tokens with relative duration weights.
func TokenizeLinePhonetics(lineText string) []PhoneticToken {
	cleanLine := strings.TrimSpace(lineText)
	if cleanLine == "" {
		return nil
	}

	words := strings.Fields(cleanLine)
	var tokens []PhoneticToken

	for _, word := range words {
		cleaned := strings.TrimFunc(word, func(r rune) bool {
			return !unicode.IsLetter(r) && !unicode.IsNumber(r) && r != '\'' && r != '-'
		})

		if cleaned == "" {
			continue
		}

		syllables := splitWordSyllables(cleaned)
		for _, s := range syllables {
			weight := computeSyllableWeight(s)
			tokens = append(tokens, PhoneticToken{
				Text:   s,
				Weight: weight,
			})
		}
	}

	return tokens
}

// splitWordSyllables splits a word into approximate syllable segments.
func splitWordSyllables(word string) []string {
	lower := strings.ToLower(word)
	runes := []rune(word)
	if len(runes) <= 3 {
		return []string{word}
	}

	// Simple heuristic syllable splitter based on vowel nuclei
	var segments []string
	start := 0
	vowelCount := 0

	for i := 0; i < len(lower); i++ {
		isVowel := isVowelRune(rune(lower[i]))
		if isVowel {
			vowelCount++
		}

		// Split after vowel cluster followed by consonant cluster if word is long
		if vowelCount > 0 && i > start+1 && i < len(lower)-1 {
			if !isVowel && isVowelRune(rune(lower[i+1])) {
				segments = append(segments, string(runes[start:i+1]))
				start = i + 1
				vowelCount = 0
			}
		}
	}

	if start < len(runes) {
		if len(segments) > 0 && len(runes)-start <= 2 {
			// Append trailing small slice to previous segment
			segments[len(segments)-1] += string(runes[start:])
		} else {
			segments = append(segments, string(runes[start:]))
		}
	}

	if len(segments) == 0 {
		return []string{word}
	}

	return segments
}

// computeSyllableWeight calculates the relative time duration weight (longer for diphthongs and stressed vowels).
func computeSyllableWeight(syllable string) float64 {
	lower := strings.ToLower(syllable)
	weight := 1.0

	// Check for extended diphthongs/vowel clusters
	for _, cluster := range vowelClusters {
		if strings.Contains(lower, cluster) {
			if len(cluster) > 1 {
				weight += 0.8
			} else {
				weight += 0.4
			}
			break
		}
	}

	// Trailing punctuation or elongation
	if strings.ContainsAny(lower, "—~-") {
		weight += 0.5
	}

	return weight
}

// isVowelRune checks if a rune is a standard phonetic vowel.
func isVowelRune(r rune) bool {
	switch r {
	case 'a', 'e', 'i', 'o', 'u', 'y':
		return true
	default:
		return false
	}
}

// TokensToSyllableModels converts phonetic tokens into timed models.Syllable slices over an interval.
func TokensToSyllableModels(tokens []PhoneticToken, startMs, endMs int64) []models.Syllable {
	if len(tokens) == 0 {
		return nil
	}

	totalWeight := 0.0
	for _, t := range tokens {
		totalWeight += t.Weight
	}

	if totalWeight <= 0 {
		totalWeight = float64(len(tokens))
	}

	duration := float64(endMs - startMs)
	currentMs := float64(startMs)
	var result []models.Syllable

	for _, t := range tokens {
		syllableDuration := (t.Weight / totalWeight) * duration
		sStart := int64(currentMs)
		sEnd := int64(currentMs + syllableDuration)

		result = append(result, models.Syllable{
			Text:    t.Text,
			StartMs: sStart,
			EndMs:   sEnd,
		})

		currentMs += syllableDuration
	}

	return result
}
