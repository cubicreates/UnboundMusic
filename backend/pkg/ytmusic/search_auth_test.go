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

func TestSearchWithCookies(t *testing.T) {
	client := NewClient()
	client.SetCredentials("SAPISID=dummy_sapisid; __Secure-3PAPISID=dummy_sapisid")

	tracks, err := client.SearchWithCategory(context.Background(), "Adele Hello", "music")
	if err != nil {
		t.Fatalf("Search failed when cookie was set: %v", err)
	}
	if len(tracks) == 0 {
		t.Fatalf("Expected at least 1 track, got 0")
	}
}

func TestStreamInfoWithCookies(t *testing.T) {
	client := NewClient()
	// Configure credentials as if user was logged into YouTube Music
	client.SetCredentials("SAPISID=dummy_sapisid; __Secure-3PAPISID=dummy_sapisid; LOGIN_INFO=dummy")

	// GetStreamInfo for standard track (Adele Hello: JqFzhcWo3EU or similar)
	info, err := client.GetStreamInfo(context.Background(), "JqFzhcWo3EU")
	if err != nil {
		t.Fatalf("GetStreamInfo failed when user credentials were set: %v", err)
	}
	if info == nil || info.StreamURL == "" {
		t.Fatalf("Expected valid StreamURL, got nil or empty")
	}
}

func TestIsAccountBoundEndpoint(t *testing.T) {
	// Public catalog endpoints MUST NOT be account-bound
	if isAccountBoundEndpoint("player") {
		t.Errorf("expected player to be non-account-bound")
	}
	if isAccountBoundEndpoint("search") {
		t.Errorf("expected search to be non-account-bound")
	}
	if isAccountBoundEndpoint("get_transcript") {
		t.Errorf("expected get_transcript to be non-account-bound")
	}

	// Account/library endpoints MUST be account-bound
	if !isAccountBoundEndpoint("browse") {
		t.Errorf("expected browse to be account-bound")
	}
	if !isAccountBoundEndpoint("like/like") {
		t.Errorf("expected like/like to be account-bound")
	}
	if !isAccountBoundEndpoint("like/removelike") {
		t.Errorf("expected like/removelike to be account-bound")
	}
	if !isAccountBoundEndpoint("account/account_menu") {
		t.Errorf("expected account/account_menu to be account-bound")
	}
	if !isAccountBoundEndpoint("guide") {
		t.Errorf("expected guide to be account-bound")
	}
}
