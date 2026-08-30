/*
 * Package: p2p
 * File: sync.go
 * Purpose: Computes library diffs between peer devices using acoustic fingerprints and plans zero-data transfers.
 * Subsystem: P2P Wi-Fi Sync Engine
 * Concurrency: Stateless pure diff functions safe for concurrent execution across worker goroutines.
 */

package p2p

import (
	"fmt"
)

// SyncDiffPlan details missing tracks that need to be synchronized from a remote peer.
type SyncDiffPlan struct {
	RemotePeerID     string   `json:"remote_peer_id"`
	TotalLocalTracks int      `json:"total_local_tracks"`
	TotalPeerTracks  int      `json:"total_peer_tracks"`
	MissingHashes    []string `json:"missing_hashes"`
	TracksToReceive  int      `json:"tracks_to_receive"`
}

// CalculateSyncDiff compares local acoustic fingerprint hashes with a remote peer's catalog to find missing songs.
func CalculateSyncDiff(peerID string, localHashes []string, remoteHashes []string) (*SyncDiffPlan, error) {
	if peerID == "" {
		return nil, fmt.Errorf("peer ID cannot be empty")
	}

	localMap := make(map[string]bool, len(localHashes))
	for _, h := range localHashes {
		localMap[h] = true
	}

	var missing []string
	for _, rh := range remoteHashes {
		if !localMap[rh] {
			missing = append(missing, rh)
		}
	}

	return &SyncDiffPlan{
		RemotePeerID:     peerID,
		TotalLocalTracks: len(localHashes),
		TotalPeerTracks:  len(remoteHashes),
		MissingHashes:    missing,
		TracksToReceive:  len(missing),
	}, nil
}
