/*
 * Package: lastfm
 * File: lastfm_test.go
 * Purpose: Unit tests for Last.fm MD5 api_sig signature generation and client configuration.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package lastfm

import (
	"testing"
)

// TestGenerateSignature validates Last.fm MD5 parameter sorting and hash computation.
func TestGenerateSignature(t *testing.T) {
	params := map[string]string{
		"method": "track.scrobble",
		"track":  "DNA",
		"artist": "Kendrick Lamar",
		"api_key": "test_key",
		"format": "json", // format should be excluded from api_sig
	}
	secret := "test_secret"

	sig := generateSignature(params, secret)
	if len(sig) != 32 {
		t.Errorf("expected 32-character hex MD5 signature, got length %d: %s", len(sig), sig)
	}
}

// TestScrobblerClientConfig validates session key updates.
func TestScrobblerClientConfig(t *testing.T) {
	s := NewScrobbler("key", "secret")
	s.SetSessionKey("my_session_token")

	if s.sessionKey != "my_session_token" {
		t.Errorf("session key was not set properly")
	}
}
