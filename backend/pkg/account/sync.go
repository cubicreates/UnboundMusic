/*
 * Package: account
 * File: sync.go
 * Purpose: YouTube Account synchronization engine: authenticates user cookies (SAPISIDHASH) to sync Liked Music (LM playlist), custom personal playlists, and subscriptions.
 * Subsystem: Account Integrations & Library Sync
 * Concurrency: Thread-safe state manager for user session tokens.
 */

package account

import (
	"context"
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"sync"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/models"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

// UserLibrary encapsulates synced personal library items.
type UserLibrary struct {
	AccountName       string         `json:"account_name"`
	LikedTracksCount  int            `json:"liked_tracks_count"`
	LikedTracks       []models.Track `json:"liked_tracks"`
	PlaylistsCount    int            `json:"playlists_count"`
	SubscribedArtists []string       `json:"subscribed_artists"`
	LastSynced        time.Time      `json:"last_synced"`
}

// AccountStatus summarizes the current YouTube connection state.
type AccountStatus struct {
	Connected         bool      `json:"connected"`
	AccountName       string    `json:"account_name"`
	SyncedTracksCount int       `json:"synced_tracks_count"`
	LastSynced        time.Time `json:"last_synced"`
}

// Syncer coordinates YouTube account authentication and playlist synchronization.
type Syncer struct {
	mu          sync.RWMutex
	cookieStr   string
	userLibrary *UserLibrary
	repo        *database.Repository
	ytClient    *ytmusic.Client
}

// NewSyncer initializes an account synchronization engine. Optional dependencies: repo (*database.Repository), ytClient (*ytmusic.Client).
func NewSyncer(deps ...interface{}) *Syncer {
	s := &Syncer{
		userLibrary: &UserLibrary{
			AccountName:       "Local Unbound User",
			LikedTracks:       make([]models.Track, 0),
			SubscribedArtists: make([]string, 0),
		},
	}

	for _, dep := range deps {
		switch v := dep.(type) {
		case *database.Repository:
			s.repo = v
		case *ytmusic.Client:
			s.ytClient = v
		}
	}

	// Restore stored credentials if database repository is available
	if s.repo != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		if savedCookie, err := s.repo.GetCredential(ctx, "yt_cookie"); err == nil && savedCookie != "" {
			s.cookieStr = savedCookie
			if s.ytClient != nil {
				s.ytClient.SetCredentials(savedCookie)
			}
			if tracks, err := s.repo.GetSyncedTracks(ctx); err == nil {
				s.userLibrary.LikedTracks = tracks
				s.userLibrary.LikedTracksCount = len(tracks)
				s.userLibrary.AccountName = "Connected User"
				s.userLibrary.LastSynced = time.Now()
			}
		}
	}

	return s
}

// SetCookie updates user authentication cookies for YouTube sync.
func (s *Syncer) SetCookie(cookie string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.cookieStr = cookie
	if s.ytClient != nil {
		s.ytClient.SetCredentials(cookie)
	}
}

// ConnectAccount validates cookies, saves them to SQLite, and performs an initial sync.
func (s *Syncer) ConnectAccount(ctx context.Context, rawCookie string) error {
	cookies := ytmusic.ParseCookies(rawCookie)
	sapisid := cookies["SAPISID"]
	if sapisid == "" {
		sapisid = cookies["__Secure-3PAPISID"]
	}
	if sapisid == "" {
		return fmt.Errorf("invalid cookie: missing SAPISID or __Secure-3PAPISID token")
	}

	s.mu.Lock()
	s.cookieStr = rawCookie
	if s.ytClient != nil {
		s.ytClient.SetCredentials(rawCookie)
	}
	s.userLibrary.AccountName = "Connected User"
	s.userLibrary.LastSynced = time.Now()
	s.mu.Unlock()

	if s.repo != nil {
		_ = s.repo.SaveCredential(ctx, "yt_cookie", rawCookie)
	}

	// Trigger library fetch if ytClient is active
	if s.ytClient != nil {
		tracks, err := s.ytClient.FetchLikedMusic(ctx)
		if err == nil && len(tracks) > 0 {
			s.mu.Lock()
			s.userLibrary.LikedTracks = tracks
			s.userLibrary.LikedTracksCount = len(tracks)
			s.userLibrary.LastSynced = time.Now()
			s.mu.Unlock()

			if s.repo != nil {
				_ = s.repo.SaveSyncedTracks(ctx, tracks)
			}
		}
	}

	return nil
}

// DisconnectAccount clears session credentials and purges synced library caches.
func (s *Syncer) DisconnectAccount(ctx context.Context) error {
	s.mu.Lock()
	s.cookieStr = ""
	if s.ytClient != nil {
		s.ytClient.SetCredentials("")
	}
	s.userLibrary = &UserLibrary{
		AccountName:       "Local Unbound User",
		LikedTracks:       make([]models.Track, 0),
		SubscribedArtists: make([]string, 0),
	}
	s.mu.Unlock()

	if s.repo != nil {
		return s.repo.ClearSyncedData(ctx)
	}
	return nil
}

// GetStatus returns the current connection state, account name, and track count.
func (s *Syncer) GetStatus() AccountStatus {
	s.mu.RLock()
	defer s.mu.RUnlock()

	return AccountStatus{
		Connected:         s.cookieStr != "",
		AccountName:       s.userLibrary.AccountName,
		SyncedTracksCount: len(s.userLibrary.LikedTracks),
		LastSynced:        s.userLibrary.LastSynced,
	}
}

// GetLikedTracks returns currently cached synced Liked Music tracks.
func (s *Syncer) GetLikedTracks() []models.Track {
	s.mu.RLock()
	defer s.mu.RUnlock()

	result := make([]models.Track, len(s.userLibrary.LikedTracks))
	copy(result, s.userLibrary.LikedTracks)
	return result
}

// SyncLibrary fetches the latest Liked Music and playlists from YouTube.
func (s *Syncer) SyncLibrary(ctx context.Context) (*UserLibrary, error) {
	s.mu.RLock()
	cookie := s.cookieStr
	s.mu.RUnlock()

	if cookie == "" {
		s.mu.RLock()
		defer s.mu.RUnlock()
		return s.userLibrary, nil
	}

	if s.ytClient != nil {
		tracks, err := s.ytClient.FetchLikedMusic(ctx)
		if err == nil {
			s.mu.Lock()
			s.userLibrary.LikedTracks = tracks
			s.userLibrary.LikedTracksCount = len(tracks)
			s.userLibrary.LastSynced = time.Now()
			s.mu.Unlock()

			if s.repo != nil {
				_ = s.repo.SaveSyncedTracks(ctx, tracks)
			}
		}
	}

	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.userLibrary, nil
}

// AddLikedTrack locally marks a track as liked.
func (s *Syncer) AddLikedTrack(track models.Track) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.userLibrary.LikedTracks = append(s.userLibrary.LikedTracks, track)
	s.userLibrary.LikedTracksCount = len(s.userLibrary.LikedTracks)
	s.userLibrary.LastSynced = time.Now()
}

// GenerateSAPISIDHash computes YouTube authorization header: SAPISIDHASH <timestamp>_<sha1(timestamp + " " + sapisid + " " + origin)>
func GenerateSAPISIDHash(sapisid, origin string) string {
	timestamp := time.Now().Unix()
	msg := fmt.Sprintf("%d %s %s", timestamp, sapisid, origin)
	hasher := sha1.New()
	hasher.Write([]byte(msg))
	hashHex := hex.EncodeToString(hasher.Sum(nil))
	return fmt.Sprintf("SAPISIDHASH %d_%s", timestamp, hashHex)
}
