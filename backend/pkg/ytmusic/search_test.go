package ytmusic

import (
	"context"
	"testing"
)

func TestSearchCascadeStructure(t *testing.T) {
	client := NewClient()

	// Empty query returns clean empty result or error
	_, err := client.SearchCascade(context.Background(), "")
	if err == nil {
		t.Fatalf("expected error for empty search query")
	}

	// Constants check
	if FilterSong == "" || FilterPodcast == "" {
		t.Errorf("expected FilterSong and FilterPodcast to be defined")
	}
}
