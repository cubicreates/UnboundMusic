/*
 * Package: ai
 * File: canonical.go
 * Purpose: Deduce canonical original songs from remix/cover/slowed/reverb variations using deterministic heuristics and Edge AI.
 * Subsystem: Edge AI Engine
 * Concurrency: Thread-safe pure functions.
 */

package ai

import (
	"regexp"
	"strings"
)

var (
	// Keywords indicating an altered, derivative, or non-original version
	remixKeywordRegex = regexp.MustCompile(`(?i)\b(remix|re-mix|mix|club mix|extended mix|radio edit|dub mix|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|orchestral|instrumental|karaoke|lofi|lo-fi|drill|dj mix)\b`)

	// Parenthesized or bracketed remix/version descriptions to strip for canonical title
	parenRemixRegex = regexp.MustCompile(`(?i)\s*[\(\[\{][^\)\]\}]*(?:remix|re-mix|mix|edit|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|orchestral|version|instrumental|karaoke|lofi|lo-fi|drill|feat\.?|ft\.?|featuring)[^\)\]\}]*[\)\]\}]`)

	// Dashed remix suffixes: e.g. " - Lofi Remix", " - Acoustic Version"
	dashRemixRegex = regexp.MustCompile(`(?i)\s*-\s*.*(?:remix|re-mix|mix|edit|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|version|instrumental|karaoke|lofi|lo-fi|drill).*`)

	// Featured artist splits
	featRegex = regexp.MustCompile(`(?i)\s+(?:feat\.?|ft\.?|featuring|vs\.?|x|&)\s+.*`)
)

// CanonicalResolution represents the AI analysis of whether a song is a remix/variant,
// providing the deduced canonical title and primary original artist.
type CanonicalResolution struct {
	IsRemixOrCover  bool   `json:"is_remix_or_cover"`
	CanonicalTitle  string `json:"canonical_title"`
	CanonicalArtist string `json:"canonical_artist"`
	RemixDetail     string `json:"remix_detail,omitempty"`
}

// DeduceCanonicalOriginal analyzes the detected title and artist to determine whether
// the song is a remix, cover, slowed/reverb, or alternate edition. If so, it extracts
// the clean canonical original title and primary artist.
func DeduceCanonicalOriginal(rawTitle, rawArtist string) CanonicalResolution {
	t := strings.TrimSpace(rawTitle)
	a := strings.TrimSpace(rawArtist)

	isRemix := remixKeywordRegex.MatchString(t) || remixKeywordRegex.MatchString(a)

	cleanTitle := parenRemixRegex.ReplaceAllString(t, "")
	cleanTitle = dashRemixRegex.ReplaceAllString(cleanTitle, "")
	cleanTitle = strings.TrimSpace(cleanTitle)

	if cleanTitle == "" {
		cleanTitle = t
	}

	cleanArtist := parenRemixRegex.ReplaceAllString(a, "")
	cleanArtist = dashRemixRegex.ReplaceAllString(cleanArtist, "")
	cleanArtist = featRegex.ReplaceAllString(cleanArtist, "")
	cleanArtist = strings.TrimSpace(cleanArtist)
	if cleanArtist == "" {
		cleanArtist = a
	}

	// If the title changed after stripping remix brackets, it was definitely a remix/variant
	if !strings.EqualFold(cleanTitle, t) {
		isRemix = true
	}

	remixDetail := ""
	if isRemix {
		if loc := remixKeywordRegex.FindString(t); loc != "" {
			remixDetail = strings.Title(loc)
		} else if loc := remixKeywordRegex.FindString(a); loc != "" {
			remixDetail = strings.Title(loc)
		} else {
			remixDetail = "Remix / Alternative"
		}
	}

	return CanonicalResolution{
		IsRemixOrCover:  isRemix,
		CanonicalTitle:  cleanTitle,
		CanonicalArtist: cleanArtist,
		RemixDetail:     remixDetail,
	}
}

// IsCandidateRemixOrVariant checks if a candidate search result has any remix or variant indicators.
func IsCandidateRemixOrVariant(title string) bool {
	return remixKeywordRegex.MatchString(title)
}
