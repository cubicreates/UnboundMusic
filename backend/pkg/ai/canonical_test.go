package ai

import (
	"testing"
)

func TestDeduceCanonicalOriginal(t *testing.T) {
	tests := []struct {
		name            string
		rawTitle        string
		rawArtist       string
		wantRemix       bool
		wantTitle       string
		wantArtist      string
	}{
		{
			name:            "Clean Original Song",
			rawTitle:        "Jaiye Sajna",
			rawArtist:       "Sashwat Sachdev",
			wantRemix:       false,
			wantTitle:       "Jaiye Sajna",
			wantArtist:      "Sashwat Sachdev",
		},
		{
			name:            "Parenthesized Remix",
			rawTitle:        "Blinding Lights (Chromatics Remix)",
			rawArtist:       "The Weeknd",
			wantRemix:       true,
			wantTitle:       "Blinding Lights",
			wantArtist:      "The Weeknd",
		},
		{
			name:            "Dashed Lofi Mix",
			rawTitle:        "Jaiye Sajna - Lofi Mix",
			rawArtist:       "Sashwat Sachdev",
			wantRemix:       true,
			wantTitle:       "Jaiye Sajna",
			wantArtist:      "Sashwat Sachdev",
		},
		{
			name:            "Slowed + Reverb",
			rawTitle:        "Starboy (Slowed + Reverb)",
			rawArtist:       "The Weeknd",
			wantRemix:       true,
			wantTitle:       "Starboy",
			wantArtist:      "The Weeknd",
		},
		{
			name:            "Acoustic Version",
			rawTitle:        "Someone You Loved [Acoustic]",
			rawArtist:       "Lewis Capaldi",
			wantRemix:       true,
			wantTitle:       "Someone You Loved",
			wantArtist:      "Lewis Capaldi",
		},
		{
			name:            "Remix in Artist Name",
			rawTitle:        "Levitating",
			rawArtist:       "Dua Lipa (DJ Snake Remix)",
			wantRemix:       true,
			wantTitle:       "Levitating",
			wantArtist:      "Dua Lipa",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			res := DeduceCanonicalOriginal(tt.rawTitle, tt.rawArtist)
			if res.IsRemixOrCover != tt.wantRemix {
				t.Errorf("DeduceCanonicalOriginal() IsRemixOrCover = %v, want %v", res.IsRemixOrCover, tt.wantRemix)
			}
			if res.CanonicalTitle != tt.wantTitle {
				t.Errorf("DeduceCanonicalOriginal() CanonicalTitle = %q, want %q", res.CanonicalTitle, tt.wantTitle)
			}
			if res.CanonicalArtist != tt.wantArtist {
				t.Errorf("DeduceCanonicalOriginal() CanonicalArtist = %q, want %q", res.CanonicalArtist, tt.wantArtist)
			}
		})
	}
}

func TestIsCandidateRemixOrVariant(t *testing.T) {
	if !IsCandidateRemixOrVariant("Song Name (Club Mix)") {
		t.Errorf("expected true for Club Mix")
	}
	if !IsCandidateRemixOrVariant("Song Name - Sped Up") {
		t.Errorf("expected true for Sped Up")
	}
	if IsCandidateRemixOrVariant("Jaiye Sajna") {
		t.Errorf("expected false for clean song")
	}
}
