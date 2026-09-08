/*
 * Package: events
 * File: bus_test.go
 * Purpose: Unit tests for EventBus subscription, broadcast delivery, backpressure handling, and unsubscribe.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package events

import (
	"strings"
	"testing"
	"time"
)

func TestEventBus_Broadcast(t *testing.T) {
	bus := NewEventBus(10)

	ch1 := bus.Subscribe()
	ch2 := bus.Subscribe()
	defer bus.Unsubscribe(ch1)
	defer bus.Unsubscribe(ch2)

	if count := bus.SubscriberCount(); count != 2 {
		t.Fatalf("expected 2 subscribers, got %d", count)
	}

	payload := map[string]string{"track_id": "test123", "status": "COMPLETED"}
	bus.Publish("download_completed", payload)

	select {
	case evt := <-ch1:
		if evt.Type != "download_completed" {
			t.Errorf("ch1 expected event type download_completed, got %s", evt.Type)
		}
		sse := string(evt.SSEMessage())
		if !strings.Contains(sse, "event: download_completed") || !strings.Contains(sse, "test123") {
			t.Errorf("ch1 invalid SSE formatting: %s", sse)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("ch1 timed out waiting for event")
	}

	select {
	case evt := <-ch2:
		if evt.Type != "download_completed" {
			t.Errorf("ch2 expected event type download_completed, got %s", evt.Type)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("ch2 timed out waiting for event")
	}
}

func TestEventBus_Unsubscribe(t *testing.T) {
	bus := NewEventBus(10)
	ch := bus.Subscribe()

	bus.Unsubscribe(ch)
	if count := bus.SubscriberCount(); count != 0 {
		t.Errorf("expected 0 subscribers, got %d", count)
	}

	// Verify channel is closed
	select {
	case _, ok := <-ch:
		if ok {
			t.Error("expected channel to be closed after unsubscribe")
		}
	default:
	}
}

func TestEventBus_BackpressureDrop(t *testing.T) {
	// Small buffer of 1
	bus := NewEventBus(1)
	ch := bus.Subscribe()
	defer bus.Unsubscribe(ch)

	bus.Publish("evt1", "first")
	bus.Publish("evt2", "second") // Should drop cleanly without blocking or panicking

	select {
	case evt := <-ch:
		if evt.Type != "evt1" {
			t.Errorf("expected first event, got %s", evt.Type)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("timed out reading from channel")
	}
}
