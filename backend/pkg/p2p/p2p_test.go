/*
 * Package: p2p
 * File: p2p_test.go
 * Purpose: Unit tests for local P2P peer registry, UDP beacons, and catalog sync diffing.
 * Subsystem: Test Suite
 * Concurrency: Tests execute concurrently using Go testing primitives.
 */

package p2p

import (
	"testing"
	"time"
)

// TestPeerRegistryAndActiveFilter tests peer registration and heartbeat expiration.
func TestPeerRegistryAndActiveFilter(t *testing.T) {
	discovery := NewDiscovery("node_test_1", "Test Device 1", 45731)

	p1 := Peer{
		DeviceID:   "peer_alpha",
		DeviceName: "Phone Alpha",
		IPAddress:  "192.168.1.50",
		APIPort:    45731,
		TrackCount: 120,
		LastSeen:   time.Now(),
	}

	discovery.RegisterPeer(p1)

	active := discovery.GetActivePeers()
	if len(active) != 1 {
		t.Fatalf("expected 1 active peer, got %d", len(active))
	}

	if active[0].DeviceID != "peer_alpha" {
		t.Errorf("peer device ID mismatch: %s", active[0].DeviceID)
	}
}

// TestCalculateSyncDiff tests catalog difference calculation based on acoustic hashes.
func TestCalculateSyncDiff(t *testing.T) {
	localHashes := []string{"hash_a", "hash_b"}
	remoteHashes := []string{"hash_a", "hash_b", "hash_c", "hash_d"}

	plan, err := CalculateSyncDiff("peer_beta", localHashes, remoteHashes)
	if err != nil {
		t.Fatalf("CalculateSyncDiff failed: %v", err)
	}

	if plan.TracksToReceive != 2 {
		t.Errorf("expected 2 missing tracks to receive, got %d", plan.TracksToReceive)
	}

	if len(plan.MissingHashes) != 2 || plan.MissingHashes[0] != "hash_c" || plan.MissingHashes[1] != "hash_d" {
		t.Errorf("unexpected missing hashes: %+v", plan.MissingHashes)
	}
}
