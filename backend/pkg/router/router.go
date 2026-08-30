/*
 * Package: router
 * File: router.go
 * Purpose: Hybrid zero-data playback interception router that redirects online song requests to local high-res audio if available on disk.
 * Subsystem: Hybrid Playback Subsystem
 * Concurrency: Thread-safe; handles concurrent stream resolution requests.
 */

package router

import (
	"context"
	"fmt"
	"os"
	"strings"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// StreamType defines the origin of the resolved playback stream.
type StreamType string

const (
	// StreamTypeLocalZeroData indicates audio is served directly from local device storage (0 MB data consumed).
	StreamTypeLocalZeroData StreamType = "LOCAL_ZERO_DATA"

	// StreamTypeRemoteOpus indicates pure Opus audio is streamed from YouTube Music over the network.
	StreamTypeRemoteOpus StreamType = "REMOTE_OPUS_STREAM"
)

// ResolvedStream represents the final playback URL, technical format, and origin source.
type ResolvedStream struct {
	TrackID      string     `json:"track_id"`
	Title        string     `json:"title"`
	Artist       string     `json:"artist"`
	StreamURL    string     `json:"stream_url"`
	StreamType   StreamType `json:"stream_type"`
	Codec        string     `json:"codec"`
	BitrateKbps  int        `json:"bitrate_kbps"`
	DataConsumed int64      `json:"data_consumed_bytes"`
	LocalPath    string     `json:"local_path,omitempty"`
}

// Router orchestrates zero-data local playback interception and remote stream fallbacks.
type Router struct {
	ytClient *ytmusic.Client
	repo     *database.Repository
}

// NewRouter constructs a new hybrid playback router.
func NewRouter(ytClient *ytmusic.Client, repo *database.Repository) *Router {
	if ytClient == nil {
		ytClient = ytmusic.NewClient()
	}
	return &Router{
		ytClient: ytClient,
		repo:     repo,
	}
}

// ResolvePlayback attempts local storage zero-data matching before falling back to network streaming.
func (r *Router) ResolvePlayback(ctx context.Context, trackID, title, artist string) (*ResolvedStream, error) {
	if trackID == "" && title == "" {
		return nil, fmt.Errorf("must specify either trackID or song title")
	}

	// 1. Local Database & Storage Match (Zero Data Interception)
	if r.repo != nil {
		if trackID != "" {
			cachedTrack, err := r.repo.GetTrack(ctx, trackID)
			if err == nil && cachedTrack != nil && cachedTrack.IsLocal && cachedTrack.LocalPath != "" {
				if fileInfo, err := os.Stat(cachedTrack.LocalPath); err == nil && fileInfo.Size() > 0 {
					return &ResolvedStream{
						TrackID:      cachedTrack.ID,
						Title:        cachedTrack.Title,
						Artist:       cachedTrack.Artist,
						StreamURL:    fmt.Sprintf("file://%s", cachedTrack.LocalPath),
						StreamType:   StreamTypeLocalZeroData,
						Codec:        cachedTrack.Codec,
						BitrateKbps:  cachedTrack.BitrateKbps,
						DataConsumed: 0,
						LocalPath:    cachedTrack.LocalPath,
					}, nil
				}
			}
		}
	}

	// 2. Remote Pure Audio Stream Resolution
	videoID := trackID
	if videoID == "" || strings.HasPrefix(videoID, "local:") || len(videoID) != 11 {
		// If videoID is not a valid 11-char YouTube ID, search YouTube Music first
		searchQuery := title
		if artist != "" {
			searchQuery = fmt.Sprintf("%s %s", artist, title)
		}
		tracks, err := r.ytClient.Search(ctx, searchQuery)
		if err != nil || len(tracks) == 0 {
			return nil, fmt.Errorf("failed to locate online track for %q: %v", searchQuery, err)
		}
		videoID = tracks[0].ID
		if title == "" {
			title = tracks[0].Title
		}
		if artist == "" {
			artist = tracks[0].Artist
		}
	}

	streamInfo, err := r.ytClient.GetStreamInfo(ctx, videoID)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve audio stream for video %s: %w", videoID, err)
	}

	// Cache remote track metadata in SQLite if repo is present
	if r.repo != nil {
		_ = r.repo.SaveTrack(ctx, &models.Track{
			ID:          videoID,
			Title:       title,
			Artist:      artist,
			DurationMs:  streamInfo.DurationMs,
			StreamURL:   streamInfo.StreamURL,
			Codec:       streamInfo.Codec,
			BitrateKbps: streamInfo.BitrateKbps,
			IsLocal:     false,
		})
	}

	return &ResolvedStream{
		TrackID:      videoID,
		Title:        title,
		Artist:       artist,
		StreamURL:    streamInfo.StreamURL,
		StreamType:   StreamTypeRemoteOpus,
		Codec:        streamInfo.Codec,
		BitrateKbps:  streamInfo.BitrateKbps,
		DataConsumed: streamInfo.ContentLength,
	}, nil
}
