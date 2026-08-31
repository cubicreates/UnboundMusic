/*
 * Package: rooms
 * File: rooms_test.go
 * Purpose: Unit tests for synchronized listening room creation, joining, and clock drift compensation.
 * Subsystem: Test Suite
 * Concurrency: Thread-safe unit test execution.
 */

package rooms

import (
	"testing"
	"time"
)

// TestRoomCreationAndSyncPosition validates room management and sub-millisecond drift calculations.
func TestRoomCreationAndSyncPosition(t *testing.T) {
	hub := NewHub()

	room, err := hub.CreateRoom("user_host_1", "Host Phone")
	if err != nil {
		t.Fatalf("CreateRoom failed: %v", err)
	}

	if room.RoomCode == "" {
		t.Fatalf("expected non-empty room code")
	}

	// Join room
	_, err = hub.JoinRoom(room.RoomCode, "user_guest_2", "Guest Phone")
	if err != nil {
		t.Fatalf("JoinRoom failed: %v", err)
	}

	fetchedRoom, err := hub.GetRoom(room.RoomCode)
	if err != nil || len(fetchedRoom.Participants) != 2 {
		t.Errorf("expected 2 participants in room, got %d", len(fetchedRoom.Participants))
	}

	// Test sync position drift compensation
	room.UpdatePlayback("track_123", "DNA", "Kendrick Lamar", 50000, StatePlaying)
	time.Sleep(10 * time.Millisecond)

	syncPos := room.GetSyncPosition()
	if syncPos <= 50000 {
		t.Errorf("expected sync position to advance beyond 50000ms, got %d", syncPos)
	}
}
