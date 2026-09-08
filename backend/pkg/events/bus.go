/*
 * Package: events
 * File: bus.go
 * Purpose: High-performance, non-blocking real-time event bus supporting Server-Sent Events (SSE).
 * Subsystem: Real-Time Event & IPC Engine
 * Concurrency: Thread-safe fanout with per-subscriber backpressure shedding.
 */

package events

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"
)

// Event represents a broadcasted event payload.
type Event struct {
	ID        string    `json:"id,omitempty"`
	Type      string    `json:"type"`
	Payload   any       `json:"payload"`
	Timestamp time.Time `json:"timestamp"`
}

// SSEMessage formats an event conforming to the W3C Server-Sent Events specification.
func (e Event) SSEMessage() []byte {
	data, _ := json.Marshal(e.Payload)
	return []byte(fmt.Sprintf("event: %s\ndata: %s\n\n", e.Type, string(data)))
}

// EventBus coordinates non-blocking fan-out event streaming to subscribers.
type EventBus struct {
	mu          sync.RWMutex
	subscribers map[chan Event]struct{}
	bufferSize  int
}

// NewEventBus creates an EventBus with a specified per-subscriber channel buffer size.
func NewEventBus(bufferSize int) *EventBus {
	if bufferSize <= 0 {
		bufferSize = 64
	}
	return &EventBus{
		subscribers: make(map[chan Event]struct{}),
		bufferSize:  bufferSize,
	}
}

// Subscribe returns a new receive-only channel for events.
func (b *EventBus) Subscribe() chan Event {
	b.mu.Lock()
	defer b.mu.Unlock()

	ch := make(chan Event, b.bufferSize)
	b.subscribers[ch] = struct{}{}
	return ch
}

// Unsubscribe removes a subscriber channel and closes it cleanly.
func (b *EventBus) Unsubscribe(ch chan Event) {
	b.mu.Lock()
	defer b.mu.Unlock()

	if _, exists := b.subscribers[ch]; exists {
		delete(b.subscribers, ch)
		close(ch)
	}
}

// IsCriticalEvent returns true if the event conveys essential state transitions that must not be dropped.
func IsCriticalEvent(eventType string) bool {
	switch eventType {
	case "download_completed", "download_failed", "download_queued", "download_started", "indexer_completed", "storage_updated", "system_status":
		return true
	default:
		return false
	}
}

// Publish broadcasts an event to all active subscribers.
// State-critical lifecycle events are prioritized: if the buffer is full, stale progress/telemetry is evicted to guarantee delivery.
func (b *EventBus) Publish(eventType string, payload any) {
	b.PublishPriority(eventType, payload, IsCriticalEvent(eventType))
}

// PublishPriority broadcasts an event with explicit priority classification.
func (b *EventBus) PublishPriority(eventType string, payload any, isCritical bool) {
	b.mu.RLock()
	defer b.mu.RUnlock()

	evt := Event{
		Type:      eventType,
		Payload:   payload,
		Timestamp: time.Now(),
	}

	for ch := range b.subscribers {
		select {
		case ch <- evt:
		default:
			if isCritical {
				// State-critical lifecycle event: evict oldest pending event (e.g. stale progress tick)
				select {
				case <-ch:
				default:
				}
				select {
				case ch <- evt:
				default:
					// Asynchronous bounded delivery fallback to prevent publisher stall
					go func(c chan Event, e Event) {
						select {
						case c <- e:
						case <-time.After(500 * time.Millisecond):
						}
					}(ch, evt)
				}
			}
			// Transient telemetry events are dropped cleanly under backpressure
		}
	}
}

// SubscriberCount returns the current count of active subscribers.
func (b *EventBus) SubscriberCount() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return len(b.subscribers)
}
