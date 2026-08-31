/*
 * Package: shazam
 * File: local.go
 * Purpose: Offline fallback recognition matcher comparing acoustic fingerprints against local SQLite vault when device has no internet.
 * Subsystem: Shazam Audio Recognition
 * Concurrency: Thread-safe repository lookups.
 */

package shazam

import (
	"context"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
)

// MatchOffline searches the local SQLite database for matching acoustic fingerprints.
func MatchOffline(ctx context.Context, repo *database.Repository, acousticHash string) (*MatchResult, error) {
	if repo == nil || acousticHash == "" {
		return &MatchResult{Matched: false, Source: "LOCAL_OFFLINE_VAULT"}, nil
	}

	start := time.Now()
	localPath, err := repo.GetPathByFingerprint(ctx, acousticHash)
	elapsed := time.Since(start).Milliseconds()

	if err != nil || localPath == "" {
		return &MatchResult{
			Matched:   false,
			LatencyMs: elapsed,
			Source:    "LOCAL_OFFLINE_VAULT",
		}, nil
	}

	return &MatchResult{
		Matched:   true,
		TrackID:   acousticHash,
		Title:     localPath,
		Artist:    "Local Audio Vault",
		LatencyMs: elapsed,
		Source:    "LOCAL_OFFLINE_VAULT",
	}, nil
}
