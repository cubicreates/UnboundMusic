package gatekeeper

import (
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
)

func TestIsMusicTrack(t *testing.T) {
	tests := []struct {
		name     string
		track    models.Track
		expected bool
	}{
		{
			name: "Authentic Song",
			track: models.Track{
				ID:         "abc123",
				Title:      "Blinding Lights",
				Artist:     "The Weeknd",
				DurationMs: 200000,
			},
			expected: true,
		},
		{
			name: "Remix / Live music",
			track: models.Track{
				ID:         "def456",
				Title:      "Starboy (Kygo Remix)",
				Artist:     "The Weeknd feat. Daft Punk",
				DurationMs: 240000,
			},
			expected: true,
		},
		{
			name: "Gameplay Video - Reject",
			track: models.Track{
				ID:         "vid001",
				Title:      "Minecraft Survival Episode 1 [Gameplay Walkthrough]",
				Artist:     "RandomGamer",
				DurationMs: 900000,
			},
			expected: false,
		},
		{
			name: "Tech Review - Reject",
			track: models.Track{
				ID:         "vid002",
				Title:      "iPhone 16 Pro Max Unboxing & Product Review",
				Artist:     "TechChannel",
				DurationMs: 700000,
			},
			expected: false,
		},
		{
			name: "Tutorial - Reject",
			track: models.Track{
				ID:         "vid003",
				Title:      "How to code in Go: Complete Tutorial",
				Artist:     "CodingGuide",
				DurationMs: 600000,
			},
			expected: false,
		},
		{
			name: "Vlog - Reject",
			track: models.Track{
				ID:         "vid004",
				Title:      "My day in the life - Daily Vlog #42",
				Artist:     "Vlogger1",
				DurationMs: 800000,
			},
			expected: false,
		},
		{
			name: "Short Sound Effect < 15s - Reject",
			track: models.Track{
				ID:         "sfx01",
				Title:      "Vine Boom Sound Effect",
				Artist:     "SFX",
				DurationMs: 3000,
			},
			expected: false,
		},
		{
			name: "Long Form Full Album - Allowed",
			track: models.Track{
				ID:         "alb01",
				Title:      "Interstellar OST (Full Album Soundtrack)",
				Artist:     "Hans Zimmer",
				DurationMs: 3600000, // 1 hour
			},
			expected: true,
		},
		{
			name: "Long Video 45 mins without music marker - Reject",
			track: models.Track{
				ID:         "stream01",
				Title:      "Talking about philosophy and existence",
				Artist:     "Podcaster",
				DurationMs: 2700000,
			},
			expected: false,
		},
		{
			name: "Empty Title - Reject",
			track: models.Track{
				ID:         "empty",
				Title:      "",
				Artist:     "Unknown",
				DurationMs: 180000,
			},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IsMusicTrack(tt.track)
			if got != tt.expected {
				t.Errorf("IsMusicTrack() = %v, want %v for track: %+v", got, tt.expected, tt.track)
			}
		})
	}
}

func TestFilterMusicTracks(t *testing.T) {
	tracks := []models.Track{
		{ID: "1", Title: "Song 1", Artist: "Artist 1", DurationMs: 180000},
		{ID: "2", Title: "Fortnite Gameplay Walkthrough", Artist: "Gamer", DurationMs: 600000},
		{ID: "3", Title: "Song 2", Artist: "Artist 2", DurationMs: 210000},
	}

	filtered := FilterMusicTracks(tracks)
	if len(filtered) != 2 {
		t.Fatalf("expected 2 tracks, got %d", len(filtered))
	}
	if filtered[0].ID != "1" || filtered[1].ID != "3" {
		t.Errorf("unexpected filtered tracks: %+v", filtered)
	}
}
