/*
 * Package: rooms
 * File: hub.go
 * Purpose: In-memory listening room manager routing room creation, joining, and real-time state synchronization across peers.
 * Subsystem: Social & Shared Listening
 * Concurrency: Thread-safe map access with sync.RWMutex.
 */

package rooms

import (
	"fmt"
	"math/rand"
	"strings"
	"sync"
	"time"
)

// Hub coordinates all active shared listening rooms.
type Hub struct {
	mu    sync.RWMutex
	rooms map[string]*Room
}

// NewHub initializes a listening room hub.
func NewHub() *Hub {
	return &Hub{
		rooms: make(map[string]*Room),
	}
}

// CreateRoom generates a new room with a random 6-character alphanumeric code.
func (h *Hub) CreateRoom(hostID, hostDevice string) (*Room, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	code := generateRoomCode()
	room := NewRoom(code, hostID)
	room.Participants[hostID] = &Participant{
		ID:         hostID,
		DeviceName: hostDevice,
		IsHost:     true,
		JoinedAt:   time.Now(),
		LastPing:   time.Now(),
	}

	h.rooms[code] = room
	return room, nil
}

// JoinRoom adds a participant to an existing room code.
func (h *Hub) JoinRoom(code, participantID, deviceName string) (*Room, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	code = strings.ToUpper(strings.TrimSpace(code))
	room, exists := h.rooms[code]
	if !exists {
		return nil, fmt.Errorf("room code %s not found", code)
	}

	room.mu.Lock()
	room.Participants[participantID] = &Participant{
		ID:         participantID,
		DeviceName: deviceName,
		IsHost:     false,
		JoinedAt:   time.Now(),
		LastPing:   time.Now(),
	}
	room.mu.Unlock()

	return room, nil
}

// GetRoom retrieves an existing room state.
func (h *Hub) GetRoom(code string) (*Room, error) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	code = strings.ToUpper(strings.TrimSpace(code))
	room, exists := h.rooms[code]
	if !exists {
		return nil, fmt.Errorf("room not found: %s", code)
	}
	return room, nil
}

func generateRoomCode() string {
	const charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	r := rand.New(rand.NewSource(time.Now().UnixNano()))
	b := make([]byte, 6)
	for i := range b {
		b[i] = charset[r.Intn(len(charset))]
	}
	return fmt.Sprintf("UNBOUND-%s", string(b))
}
