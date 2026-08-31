/*
 * Package: discord
 * File: discord_test.go
 * Purpose: Unit tests for Discord Rich Presence payload framing, connection handling, and fail-soft behavior.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package discord

import (
	"testing"
)

// TestDiscordClientCreationAndFailSoft validates fail-soft non-blocking behavior when Discord is closed.
func TestDiscordClientCreationAndFailSoft(t *testing.T) {
	client := NewClient("1234567890")
	if client == nil {
		t.Fatalf("failed to instantiate discord client")
	}

	// Attempt connection (should fail-soft gracefully without panicking if Discord desktop is closed)
	_ = client.Connect()

	// Setting activity when disconnected should return a clean error without crashing
	err := client.SetActivity(Activity{
		Details: "DNA.",
		State:   "Kendrick Lamar",
	})

	if !client.isConnected && err == nil {
		t.Errorf("expected error when setting activity on disconnected client")
	}

	_ = client.Close()
}
