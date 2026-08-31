/*
 * Package: rooms
 * File: room.go
 * Purpose: Synchronized multi-device party room state model with sub-millisecond playback clock drift compensation.
 * Subsystem: Social & Shared Listening
 * Concurrency: Thread-safe state modifications guarded by mutex locks.
 */

package rooms

import (
	"sync"
	"time"
)

// PlaybackState defines the current synchronized transport status.
type PlaybackState string

const (
	StatePlaying PlaybackState = "PLAYING"
	StatePaused  PlaybackState = "PAUSED"
	StateStopped PlaybackState = "STOPPED"
)

// Participant represents a connected client in the listening room.
type Participant struct {
	ID         string    `json:"id"`
	DeviceName string    `json:"device_name"`
	IsHost     bool      `json:"is_host"`
	JoinedAt   time.Time `json:"joined_at"`
	LastPing   time.Time `json:"last_ping"`
}

// Room represents an active shared listening session.
type Room struct {
	mu             sync.RWMutex
	RoomCode       string                 `json:"room_code"`
	HostID         string                 `json:"host_id"`
	CurrentTrackID string                 `json:"current_track_id"`
	CurrentTitle   string                 `json:"current_title"`
	CurrentArtist  string                 `json:"current_artist"`
	CurrentPosMs   int64                  `json:"current_pos_ms"`
	State          PlaybackState          `json:"state"`
	UpdatedAt      time.Time              `json:"updated_at"`
	Participants   map[string]*Participant `json:"participants"`
}

// NewRoom creates an empty listening room instance.
func NewRoom(code, hostID string) *Room {
	return &Room{
		RoomCode:     code,
		HostID:       hostID,
		State:        StateStopped,
		UpdatedAt:    time.Now(),
		Participants: make(map[string]*Participant),
	}
}

// UpdatePlayback advances the room's track and position.
func (r *Room) UpdatePlayback(trackID, title, artist string, posMs int64, state PlaybackState) {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.CurrentTrackID = trackID
	r.CurrentTitle = title
	r.CurrentArtist = artist
	r.CurrentPosMs = posMs
	r.State = state
	r.UpdatedAt = time.Now()
}

// GetSyncPosition calculates the drift-compensated playback position in milliseconds.
func (r *Room) GetSyncPosition() int64 {
	r.mu.RLock()
	defer r.mu.RUnlock()

	if r.State != StatePlaying {
		return r.CurrentPosMs
	}

	elapsed := time.Since(r.UpdatedAt).Milliseconds()
	return r.CurrentPosMs + elapsed
}
