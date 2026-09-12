package ytmusic

import (
	"context"
	"testing"
)

func TestSearchWithToken(t *testing.T) {
	client := NewClient()
	// Set an OAuth bearer token (simulating TV login)
	client.SetAccessToken("invalid_tv_bearer_token_12345")

	tracks, err := client.SearchWithCategory(context.Background(), "Adele Hello", "music")
	if err != nil {
		t.Fatalf("Search failed when access token was set: %v", err)
	}
	if len(tracks) == 0 {
		t.Fatalf("Expected at least 1 track, got 0")
	}
}
