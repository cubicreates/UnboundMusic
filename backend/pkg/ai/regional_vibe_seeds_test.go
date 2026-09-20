package ai

import (
	"strings"
	"testing"
)

func TestGetRegionalSeeds(t *testing.T) {
	tests := []struct {
		name           string
		region         string
		expectedSample string
	}{
		{
			name:           "India Country Code IN",
			region:         "IN",
			expectedSample: "Chak De India",
		},
		{
			name:           "India Word Lowercase",
			region:         "india",
			expectedSample: "Zinda Bhaag Milkha Bhaag",
		},
		{
			name:           "Latin Region ES",
			region:         "ES",
			expectedSample: "Waka Waka",
		},
		{
			name:           "Latin Region MX",
			region:         "MX",
			expectedSample: "La Copa de la Vida",
		},
		{
			name:           "Default Global US",
			region:         "US",
			expectedSample: "We Are The Champions",
		},
		{
			name:           "Empty Region Fallback",
			region:         "",
			expectedSample: "We Are The Champions",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			seeds := GetRegionalSeeds(tc.region)
			if len(seeds.VictoryAnthems) == 0 {
				t.Fatalf("expected non-empty VictoryAnthems for region %s", tc.region)
			}
			if len(seeds.SleepAmbient) == 0 {
				t.Fatalf("expected non-empty SleepAmbient for region %s", tc.region)
			}
			if len(seeds.WorkoutHype) == 0 {
				t.Fatalf("expected non-empty WorkoutHype for region %s", tc.region)
			}
			if len(seeds.MelancholySad) == 0 {
				t.Fatalf("expected non-empty MelancholySad for region %s", tc.region)
			}

			found := false
			for _, anthem := range seeds.VictoryAnthems {
				if strings.Contains(anthem, tc.expectedSample) {
					found = true
					break
				}
			}
			if !found {
				t.Errorf("expected to find '%s' in VictoryAnthems for region '%s', got: %v",
					tc.expectedSample, tc.region, seeds.VictoryAnthems)
			}
		})
	}
}
