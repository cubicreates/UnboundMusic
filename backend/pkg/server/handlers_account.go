/*
 * Package: server
 * File: handlers_account.go
 * Purpose: Google OAuth 2.0 Device Code, YouTube Music cookie sync, and feed streaming HTTP handlers.
 * Subsystem: Account & Cloud Sync
 */

package server

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/account"
)

// handleAccountSync syncs personal YouTube library.
func (s *Server) handleAccountSync(w http.ResponseWriter, r *http.Request) {
	type SyncReq struct {
		Cookie string `json:"cookie"`
	}
	var req SyncReq
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.Cookie != "" {
		if err := s.accountSync.ConnectAccount(r.Context(), req.Cookie); err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
	}

	status := s.accountSync.GetStatus()
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"connected":           status.Connected,
		"account_name":        status.AccountName,
		"avatar_url":          status.AvatarURL,
		"synced_tracks_count": status.SyncedTracksCount,
		"last_synced":         status.LastSynced,
	})
}

// handleAccountStatus returns the current YouTube connection state, user account name, avatar URL, and synced track count.
func (s *Server) handleAccountStatus(w http.ResponseWriter, r *http.Request) {
	status := s.accountSync.GetStatus()
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"connected":           status.Connected,
		"account_name":        status.AccountName,
		"avatar_url":          status.AvatarURL,
		"synced_tracks_count": status.SyncedTracksCount,
		"last_synced":         status.LastSynced,
	})
}

// handleAccountDisconnect logs out the user and clears credentials and synced library data.
func (s *Server) handleAccountDisconnect(w http.ResponseWriter, r *http.Request) {
	if err := s.accountSync.DisconnectAccount(r.Context()); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"status": "disconnected"})
}

// handleAccountLiked returns synced liked tracks.
func (s *Server) handleAccountLiked(w http.ResponseWriter, r *http.Request) {
	lib, _ := s.accountSync.SyncLibrary(r.Context())
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": lib.LikedTracks,
		"mixes":  lib.Mixes,
		"count":  len(lib.LikedTracks),
	})
}

// handleAccountFeedInfinite streams continuous music tracks and related radio recommendations for infinite home scroll.
func (s *Server) handleAccountFeedInfinite(w http.ResponseWriter, r *http.Request) {
	seed := r.URL.Query().Get("seed")
	if seed == "" {
		tracks := s.accountSync.GetLikedTracks()
		if len(tracks) > 0 {
			idx := int(time.Now().UnixNano() % int64(len(tracks)))
			seed = tracks[idx].ID
		}
	}

	tracks, err := s.ytClient.FetchInfinitePersonalizedFeed(r.Context(), seed)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"tracks": tracks,
		"count":  len(tracks),
	})
}

// handleSmartFeed generates dynamic on-device personalized shelves from playback history.
func (s *Server) handleSmartFeed(w http.ResponseWriter, r *http.Request) {
	if s.algoEngine == nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"has_personalization": false,
			"shelves":             []interface{}{},
		})
		return
	}

	feed, err := s.algoEngine.GenerateSmartFeed(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, feed)
}

// handleAccountDeviceStart initiates the zero-typing OAuth 2.0 Device Code flow.
func (s *Server) handleAccountDeviceStart(w http.ResponseWriter, r *http.Request) {
	type StartReq struct {
		ClientID string `json:"client_id,omitempty"`
	}
	var req StartReq
	_ = json.NewDecoder(r.Body).Decode(&req)

	dResp, err := account.StartDeviceCodeFlow(r.Context(), req.ClientID)
	if err != nil {
		writeError(w, http.StatusBadGateway, fmt.Sprintf("Failed to initiate device flow: %v", err))
		return
	}

	writeJSON(w, http.StatusOK, dResp)
}

// handleAccountDevicePoll checks whether the user has authorized the device in their browser.
func (s *Server) handleAccountDevicePoll(w http.ResponseWriter, r *http.Request) {
	type PollReq struct {
		DeviceCode   string `json:"device_code"`
		ClientID     string `json:"client_id,omitempty"`
		ClientSecret string `json:"client_secret,omitempty"`
	}
	var req PollReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.DeviceCode == "" {
		writeError(w, http.StatusBadRequest, "device_code is required")
		return
	}

	tResp, isPending, err := account.CheckDeviceCodeToken(r.Context(), req.DeviceCode, req.ClientID, req.ClientSecret)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	if isPending {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"status": "pending",
		})
		return
	}

	// User authorized! Connect account with OAuth tokens
	if err := s.accountSync.ConnectOAuthAccount(r.Context(), tResp.AccessToken, tResp.RefreshToken); err != nil {
		writeError(w, http.StatusInternalServerError, fmt.Sprintf("failed to connect account: %v", err))
		return
	}

	status := s.accountSync.GetStatus()
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":              "success",
		"connected":           status.Connected,
		"account_name":        status.AccountName,
		"avatar_url":          status.AvatarURL,
		"synced_tracks_count": status.SyncedTracksCount,
		"last_synced":         status.LastSynced,
	})
}
