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
	AvatarURL         string         `json:"avatar_url"`
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
	AvatarURL         string    `json:"avatar_url"`
	SyncedTracksCount int       `json:"synced_tracks_count"`
	LastSynced        time.Time `json:"last_synced"`
}

// Syncer coordinates YouTube account authentication and playlist synchronization.
type Syncer struct {
	mu           sync.RWMutex
	cookieStr    string
	accessToken  string
	refreshToken string
	userLibrary  *UserLibrary
	repo         *database.Repository
	ytClient     *ytmusic.Client
}

// NewSyncer initializes an account synchronization engine. Optional dependencies: repo (*database.Repository), ytClient (*ytmusic.Client).
func NewSyncer(deps ...interface{}) *Syncer {
	s := &Syncer{
		userLibrary: &UserLibrary{
			AccountName:       "Local Unbound User",
			AvatarURL:         "",
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

		savedToken, _ := s.repo.GetCredential(ctx, "yt_access_token")
		savedRefresh, _ := s.repo.GetCredential(ctx, "yt_refresh_token")
		savedCookie, _ := s.repo.GetCredential(ctx, "yt_cookie")

		if savedToken != "" {
			s.accessToken = savedToken
			s.refreshToken = savedRefresh
			if s.ytClient != nil {
				s.ytClient.SetAccessToken(savedToken)
			}
		} else if savedCookie != "" {
			s.cookieStr = savedCookie
			if s.ytClient != nil {
				s.ytClient.SetCredentials(savedCookie)
			}
		}

		if savedToken != "" || savedCookie != "" {
			if savedName, _ := s.repo.GetCredential(ctx, "yt_account_name"); savedName != "" {
				s.userLibrary.AccountName = savedName
			} else {
				s.userLibrary.AccountName = "Connected User"
			}
			if savedAvatar, _ := s.repo.GetCredential(ctx, "yt_avatar_url"); savedAvatar != "" {
				s.userLibrary.AvatarURL = savedAvatar
			}
			if tracks, err := s.repo.GetSyncedTracks(ctx); err == nil {
				s.userLibrary.LikedTracks = tracks
				s.userLibrary.LikedTracksCount = len(tracks)
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

	accountName := "Connected User"
	avatarURL := ""
	if s.ytClient != nil {
		s.ytClient.SetCredentials(rawCookie)
		if info, err := s.ytClient.FetchAccountInfo(ctx); err == nil && info != nil {
			if info.Name != "" {
				accountName = info.Name
			}
			avatarURL = info.AvatarURL
		}
	}

	s.mu.Lock()
	s.cookieStr = rawCookie
	s.userLibrary.AccountName = accountName
	s.userLibrary.AvatarURL = avatarURL
	s.userLibrary.LastSynced = time.Now()
	s.mu.Unlock()

	if s.repo != nil {
		_ = s.repo.SaveCredential(ctx, "yt_cookie", rawCookie)
		_ = s.repo.SaveCredential(ctx, "yt_account_name", accountName)
		_ = s.repo.SaveCredential(ctx, "yt_avatar_url", avatarURL)
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

// ConnectOAuthAccount validates and stores OAuth tokens, fetches user profile, and performs an initial sync.
func (s *Syncer) ConnectOAuthAccount(ctx context.Context, accessToken, refreshToken string) error {
	accountName := "Connected User"
	avatarURL := ""

	// Try fetching Google / YouTube profile details
	if profile, err := FetchGoogleUserProfile(ctx, accessToken); err == nil && profile != nil {
		if profile.Name != "" {
			accountName = profile.Name
		}
		if profile.Picture != "" {
			avatarURL = profile.Picture
		}
	}
	if (avatarURL == "" || accountName == "Connected User") && s.ytClient != nil {
		s.ytClient.SetAccessToken(accessToken)
		if info, err := s.ytClient.FetchAccountInfo(ctx); err == nil && info != nil {
			if info.Name != "" && accountName == "Connected User" {
				accountName = info.Name
			}
			if info.AvatarURL != "" && avatarURL == "" {
				avatarURL = info.AvatarURL
			}
		}
	}

	s.mu.Lock()
	s.accessToken = accessToken
	s.refreshToken = refreshToken
	if s.ytClient != nil {
		s.ytClient.SetAccessToken(accessToken)
	}
	s.userLibrary.AccountName = accountName
	s.userLibrary.AvatarURL = avatarURL
	s.userLibrary.LastSynced = time.Now()
	s.mu.Unlock()

	if s.repo != nil {
		_ = s.repo.SaveCredential(ctx, "yt_access_token", accessToken)
		_ = s.repo.SaveCredential(ctx, "yt_refresh_token", refreshToken)
		_ = s.repo.SaveCredential(ctx, "yt_account_name", accountName)
		_ = s.repo.SaveCredential(ctx, "yt_avatar_url", avatarURL)
	}

	// Trigger library fetch: first YouTube Music, then fallback to standard YouTube Liked Videos (LL)
	var tracks []models.Track
	if s.ytClient != nil {
		tracks, _ = s.ytClient.FetchLikedMusic(ctx)
	}

	if len(tracks) < 5 {
		if normalYTTracks, err := FetchYouTubeLikedVideosDataAPI(ctx, accessToken); err == nil && len(normalYTTracks) > 0 {
			existingIDs := make(map[string]bool)
			for _, t := range tracks {
				existingIDs[t.ID] = true
			}
			for _, nt := range normalYTTracks {
				if !existingIDs[nt.ID] {
					tracks = append(tracks, nt)
					existingIDs[nt.ID] = true
				}
			}
		}
	}

	if len(tracks) > 0 {
		s.mu.Lock()
		s.userLibrary.LikedTracks = tracks
		s.userLibrary.LikedTracksCount = len(tracks)
		s.userLibrary.LastSynced = time.Now()
		s.mu.Unlock()

		if s.repo != nil {
			_ = s.repo.SaveSyncedTracks(ctx, tracks)
		}
	}

	return nil
}

// DisconnectAccount clears session credentials and purges synced library caches.
func (s *Syncer) DisconnectAccount(ctx context.Context) error {
	s.mu.Lock()
	s.cookieStr = ""
	s.accessToken = ""
	s.refreshToken = ""
	if s.ytClient != nil {
		s.ytClient.SetCredentials("")
		s.ytClient.SetAccessToken("")
	}
	s.userLibrary = &UserLibrary{
		AccountName:       "Local Unbound User",
		AvatarURL:         "",
		LikedTracks:       make([]models.Track, 0),
		SubscribedArtists: make([]string, 0),
	}
	s.mu.Unlock()

	if s.repo != nil {
		_ = s.repo.SaveCredential(ctx, "yt_access_token", "")
		_ = s.repo.SaveCredential(ctx, "yt_refresh_token", "")
		_ = s.repo.SaveCredential(ctx, "yt_cookie", "")
		return s.repo.ClearSyncedData(ctx)
	}
	return nil
}

// GetStatus returns the current connection state, account name, and track count.
func (s *Syncer) GetStatus() AccountStatus {
	s.mu.RLock()
	defer s.mu.RUnlock()

	return AccountStatus{
		Connected:         s.cookieStr != "" || s.accessToken != "",
		AccountName:       s.userLibrary.AccountName,
		AvatarURL:         s.userLibrary.AvatarURL,
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
	hasAuth := s.cookieStr != "" || s.accessToken != ""
	refreshToken := s.refreshToken
	s.mu.RUnlock()

	if !hasAuth {
		s.mu.RLock()
		defer s.mu.RUnlock()
		return s.userLibrary, nil
	}

	if s.ytClient != nil {
		tracks, err := s.ytClient.FetchLikedMusic(ctx)
		// If OAuth token expired, attempt automatic refresh
		if err != nil && refreshToken != "" {
			if tResp, rErr := RefreshOAuthToken(ctx, refreshToken, "", ""); rErr == nil && tResp != nil {
				s.mu.Lock()
				s.accessToken = tResp.AccessToken
				if tResp.RefreshToken != "" {
					s.refreshToken = tResp.RefreshToken
				}
				s.ytClient.SetAccessToken(tResp.AccessToken)
				s.mu.Unlock()

				if s.repo != nil {
					_ = s.repo.SaveCredential(ctx, "yt_access_token", tResp.AccessToken)
				}
				// Retry fetch with fresh token
				tracks, err = s.ytClient.FetchLikedMusic(ctx)
			}
		}

		if err == nil {
			// If YouTube Music tracks are sparse (< 5), supplement with normal YouTube liked videos
			if len(tracks) < 5 && s.accessToken != "" {
				if normalYTTracks, nErr := FetchYouTubeLikedVideosDataAPI(ctx, s.accessToken); nErr == nil && len(normalYTTracks) > 0 {
					existingIDs := make(map[string]bool)
					for _, t := range tracks {
						existingIDs[t.ID] = true
					}
					for _, nt := range normalYTTracks {
						if !existingIDs[nt.ID] {
							tracks = append(tracks, nt)
							existingIDs[nt.ID] = true
						}
					}
				}
			}

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
