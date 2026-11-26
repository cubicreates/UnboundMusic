package lyrics

import (
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestScoreLRCLIBCandidatesAndMerge(t *testing.T) {
	query := "blinded by the lights"
	lrcRaw := []LRCLIBResponse{
		{
			TrackName:   "Blinding Lights",
			ArtistName:  "The Weeknd",
			AlbumName:   "After Hours",
			Duration:    200,
			PlainLyrics: "I said, ooh, I'm blinded by the lights\nNo, I can't sleep until I feel your touch",
		},
		{
			TrackName:   "Blinding Lights (Remix)",
			ArtistName:  "The Weeknd & ROSALÍA",
			AlbumName:   "Blinding Lights Remix",
			Duration:    215,
			PlainLyrics: "I'm blinded by the lights",
		},
	}

	lrcCandidates := ScoreLRCLIBCandidates(query, lrcRaw, 8)
	if len(lrcCandidates) != 2 {
		t.Fatalf("expected 2 LRCLIB candidates, got %d", len(lrcCandidates))
	}
	if lrcCandidates[0].MatchedLyric == "" {
		t.Errorf("expected matched lyric snippet to be extracted")
	}

	geniusHits := []genius.SongHit{
		{
			ID:           101,
			Title:        "Blinding Lights",
			Artist:       "The Weeknd",
			ThumbnailURL: "https://example.com/blinding.jpg",
			MatchedLyric: "Ooh, I'm blinded by the lights",
			SectionType:  "lyric",
		},
	}

	ytTracks := []models.Track{
		{
			ID:           "4NRXx6U8ABQ",
			Title:        "Blinding Lights",
			Artist:       "The Weeknd",
			Album:        "After Hours",
			DurationMs:   200000,
			ThumbnailURL: "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
		},
		{
			ID:           "fHI8X4OXluQ",
			Title:        "Blinding Lights (Live)",
			Artist:       "The Weeknd",
			DurationMs:   210000,
			ThumbnailURL: "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg",
		},
	}

	merged := MergeRecognitionCandidates(query, nil, lrcCandidates, geniusHits, ytTracks)
	if len(merged) < 2 {
		t.Fatalf("expected multiple merged candidates, got %d", len(merged))
	}

	top := merged[0]
	if top.ID != "4NRXx6U8ABQ" {
		t.Errorf("expected top candidate to be hydrated with playable YouTube ID 4NRXx6U8ABQ, got %s", top.ID)
	}
	if top.Confidence <= 0.90 || top.Confidence > 1.0 {
		t.Errorf("expected boosted confidence in (0.90, 1.0], got %f", top.Confidence)
	}
	if !top.IsOriginal {
		t.Errorf("expected canonical song to have IsOriginal=true")
	}
}
