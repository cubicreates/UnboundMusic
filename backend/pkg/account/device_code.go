/*
 * Package: account
 * File: device_code.go
 * Purpose: Implements OAuth 2.0 Device Authorization Grant (RFC 8628) for zero-typing YouTube account synchronization.
 * Subsystem: Account Integrations & OAuth Engine
 * Concurrency: Thread-safe HTTP operations with context cancellation.
 */

package account

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/models"
)

const (
	GoogleDeviceCodeURL = "https://oauth2.googleapis.com/device/code"
	GoogleTokenURL      = "https://oauth2.googleapis.com/token"
	GoogleUserInfoURL   = "https://www.googleapis.com/oauth2/v3/userinfo"

	// YouTube on TV / Limited-Input Device credentials (live extracted from youtube.com/tv)
	DefaultTVClientID     = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
	DefaultTVClientSecret = "SboVhoG9s0rNafixCSGGKXAT"
	DefaultScope          = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/userinfo.profile email openid"
)

// DeviceCodeResponse encapsulates the initial device handshake returned by Google.
type DeviceCodeResponse struct {
	DeviceCode      string `json:"device_code"`
	UserCode        string `json:"user_code"`
	VerificationURL string `json:"verification_url"`
	ExpiresIn       int    `json:"expires_in"`
	Interval        int    `json:"interval"`
}

// TokenResponse encapsulates OAuth access and refresh credentials.
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token,omitempty"`
	ExpiresIn    int    `json:"expires_in"`
	TokenType    string `json:"token_type"`
	Scope        string `json:"scope,omitempty"`
	Error        string `json:"error,omitempty"`
	ErrorDesc    string `json:"error_description,omitempty"`
}

// UserProfile holds basic Google profile details retrieved via OAuth access token.
type UserProfile struct {
	Name    string `json:"name"`
	Picture string `json:"picture"`
	Email   string `json:"email"`
}

// StartDeviceCodeFlow initiates the OAuth device flow with Google.
func StartDeviceCodeFlow(ctx context.Context, customClientID string) (*DeviceCodeResponse, error) {
	clientID := DefaultTVClientID
	if customClientID != "" {
		clientID = customClientID
	}

	data := url.Values{}
	data.Set("client_id", clientID)
	data.Set("scope", DefaultScope)
	data.Set("device_id", "9a25b399-5f11-4f91-8be8-75bdfc572b9a")
	data.Set("device_model", "ytlr::")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, GoogleDeviceCodeURL, strings.NewReader(data.Encode()))
	if err != nil {
		return nil, fmt.Errorf("failed to create device code request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("device code request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		// Attempt dynamic discovery from youtube.com/tv if default credentials were stale
		if liveID, _, fErr := FetchLiveTVCredentials(ctx); fErr == nil && liveID != "" {
			data.Set("client_id", liveID)
			retryReq, _ := http.NewRequestWithContext(ctx, http.MethodPost, GoogleDeviceCodeURL, strings.NewReader(data.Encode()))
			retryReq.Header.Set("Content-Type", "application/x-www-form-urlencoded")
			if retryResp, err := client.Do(retryReq); err == nil {
				defer retryResp.Body.Close()
				if retryResp.StatusCode == http.StatusOK {
					var dResp DeviceCodeResponse
					if err := json.NewDecoder(retryResp.Body).Decode(&dResp); err == nil {
						if dResp.VerificationURL == "" || strings.Contains(dResp.VerificationURL, "google.com/device") {
							dResp.VerificationURL = "https://www.youtube.com/activate"
						}
						if dResp.Interval <= 0 {
							dResp.Interval = 5
						}
						return &dResp, nil
					}
				}
			}
		}
		return nil, fmt.Errorf("google returned status %d: %s", resp.StatusCode, string(body))
	}

	var dResp DeviceCodeResponse
	if err := json.Unmarshal(body, &dResp); err != nil {
		return nil, fmt.Errorf("failed to parse device code response: %w", err)
	}

	// Prefer https://www.youtube.com/activate for optimal TV/YouTube branding in browser
	if dResp.VerificationURL == "" || strings.Contains(dResp.VerificationURL, "google.com/device") {
		dResp.VerificationURL = "https://www.youtube.com/activate"
	}

	if dResp.Interval <= 0 {
		dResp.Interval = 5
	}

	return &dResp, nil
}

// FetchLiveTVCredentials dynamically scrapes live OAuth credentials directly from youtube.com/tv
func FetchLiveTVCredentials(ctx context.Context) (string, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://www.youtube.com/tv", nil)
	if err != nil {
		return "", "", err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15")

	client := &http.Client{Timeout: 8 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", err
	}

	reBase := regexp.MustCompile(`<script\s+id="base-js"\s+src="([^"]+)"`)
	mBase := reBase.FindSubmatch(body)
	if len(mBase) < 2 {
		return "", "", fmt.Errorf("base-js not found in youtube.com/tv")
	}

	jsURL := "https://www.youtube.com" + string(mBase[1])
	jsReq, _ := http.NewRequestWithContext(ctx, http.MethodGet, jsURL, nil)
	jsResp, err := client.Do(jsReq)
	if err != nil {
		return "", "", err
	}
	defer jsResp.Body.Close()

	jsBody, _ := io.ReadAll(jsResp.Body)
	reClient := regexp.MustCompile(`clientId:"(?P<client_id>[^"]+)",[^"]*?:"(?P<client_secret>[^"]+)"`)
	mClient := reClient.FindSubmatch(jsBody)
	if len(mClient) < 3 {
		return "", "", fmt.Errorf("client credentials not found in base.js")
	}

	return string(mClient[1]), string(mClient[2]), nil
}

// CheckDeviceCodeToken polls Google's token endpoint once for the status of the device authorization.
// Returns (token, isPending, error). If user hasn't authorized yet, isPending will be true with nil error.
func CheckDeviceCodeToken(ctx context.Context, deviceCode, customClientID, customClientSecret string) (*TokenResponse, bool, error) {
	clientID := DefaultTVClientID
	clientSecret := DefaultTVClientSecret
	if customClientID != "" {
		clientID = customClientID
	}
	if customClientSecret != "" {
		clientSecret = customClientSecret
	}

	data := url.Values{}
	data.Set("client_id", clientID)
	data.Set("client_secret", clientSecret)
	data.Set("device_code", deviceCode)
	data.Set("grant_type", "urn:ietf:params:oauth:grant-type:device_code")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, GoogleTokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return nil, false, fmt.Errorf("failed to create token request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, false, fmt.Errorf("token poll failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, false, fmt.Errorf("failed to read token body: %w", err)
	}

	var tResp TokenResponse
	_ = json.Unmarshal(body, &tResp)

	if tResp.Error != "" {
		switch tResp.Error {
		case "authorization_pending", "slow_down":
			return nil, true, nil
		case "expired_token":
			return nil, false, fmt.Errorf("activation code expired, please try again")
		case "access_denied":
			return nil, false, fmt.Errorf("user denied authorization")
		default:
			return nil, false, fmt.Errorf("oauth error: %s (%s)", tResp.Error, tResp.ErrorDesc)
		}
	}

	if resp.StatusCode == http.StatusOK && tResp.AccessToken != "" {
		return &tResp, false, nil
	}

	return nil, false, fmt.Errorf("unexpected status %d: %s", resp.StatusCode, string(body))
}

// RefreshOAuthToken exchanges a valid refresh_token for a new access_token.
func RefreshOAuthToken(ctx context.Context, refreshToken, customClientID, customClientSecret string) (*TokenResponse, error) {
	clientID := DefaultTVClientID
	clientSecret := DefaultTVClientSecret
	if customClientID != "" {
		clientID = customClientID
	}
	if customClientSecret != "" {
		clientSecret = customClientSecret
	}

	data := url.Values{}
	data.Set("client_id", clientID)
	data.Set("client_secret", clientSecret)
	data.Set("refresh_token", refreshToken)
	data.Set("grant_type", "refresh_token")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, GoogleTokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return nil, fmt.Errorf("failed to create refresh request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("token refresh failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read refresh response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("google refresh returned status %d: %s", resp.StatusCode, string(body))
	}

	var tResp TokenResponse
	if err := json.Unmarshal(body, &tResp); err != nil {
		return nil, fmt.Errorf("failed to decode refresh response: %w", err)
	}

	return &tResp, nil
}

// FetchYouTubeChannelProfile queries the YouTube Data API v3 for the authenticated user's channel title and avatar.
func FetchYouTubeChannelProfile(ctx context.Context, accessToken string) (*UserProfile, error) {
	reqURL := "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("youtube channels API returned %d: %s", resp.StatusCode, string(body))
	}

	var data struct {
		Items []struct {
			Snippet struct {
				Title      string `json:"title"`
				Thumbnails struct {
					Default struct {
						URL string `json:"url"`
					} `json:"default"`
					Medium struct {
						URL string `json:"url"`
					} `json:"medium"`
					High struct {
						URL string `json:"url"`
					} `json:"high"`
				} `json:"thumbnails"`
			} `json:"snippet"`
		} `json:"items"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}

	if len(data.Items) > 0 {
		snip := data.Items[0].Snippet
		imgURL := snip.Thumbnails.High.URL
		if imgURL == "" {
			imgURL = snip.Thumbnails.Medium.URL
		}
		if imgURL == "" {
			imgURL = snip.Thumbnails.Default.URL
		}
		return &UserProfile{
			Name:    snip.Title,
			Picture: imgURL,
		}, nil
	}

	return nil, fmt.Errorf("no youtube channel found for user")
}

// FetchYouTubeLikedVideosDataAPI queries playlist LL (Liked Videos) on standard YouTube via Data API v3.
func FetchYouTubeLikedVideosDataAPI(ctx context.Context, accessToken string) ([]models.Track, error) {
	reqURL := "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=LL&maxResults=50"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)

	client := &http.Client{Timeout: 12 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("youtube playlistItems LL returned %d: %s", resp.StatusCode, string(body))
	}

	var data struct {
		Items []struct {
			ContentDetails struct {
				VideoID string `json:"videoId"`
			} `json:"contentDetails"`
			Snippet struct {
				Title                  string `json:"title"`
				VideoOwnerChannelTitle string `json:"videoOwnerChannelTitle"`
				Thumbnails             struct {
					High struct {
						URL string `json:"url"`
					} `json:"high"`
					Default struct {
						URL string `json:"url"`
					} `json:"default"`
				} `json:"thumbnails"`
				ResourceID struct {
					VideoID string `json:"videoId"`
				} `json:"resourceId"`
			} `json:"snippet"`
		} `json:"items"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}

	var tracks []models.Track
	for _, item := range data.Items {
		vid := item.ContentDetails.VideoID
		if vid == "" {
			vid = item.Snippet.ResourceID.VideoID
		}
		if vid == "" {
			continue
		}

		artist := item.Snippet.VideoOwnerChannelTitle
		artist = strings.TrimSuffix(artist, " - Topic")
		artist = strings.TrimSuffix(artist, "VEVO")
		if artist == "" {
			artist = "YouTube Artist"
		}

		thumb := item.Snippet.Thumbnails.High.URL
		if thumb == "" {
			thumb = item.Snippet.Thumbnails.Default.URL
		}

		tracks = append(tracks, models.Track{
			ID:           vid,
			Title:        item.Snippet.Title,
			Artist:       artist,
			ThumbnailURL: thumb,
		})
	}

	return gatekeeper.FilterMusicTracks(tracks), nil
}

// FetchYouTubePlaylistsDataAPI queries user's personal playlists on YouTube via Data API v3.
func FetchYouTubePlaylistsDataAPI(ctx context.Context, accessToken string) ([]models.Track, error) {
	reqURL := "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&mine=true&maxResults=10"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("playlists API returned %d", resp.StatusCode)
	}

	var data struct {
		Items []struct {
			ID string `json:"id"`
		} `json:"items"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		return nil, err
	}

	var allTracks []models.Track
	for _, p := range data.Items {
		if p.ID == "" {
			continue
		}
		pReqURL := fmt.Sprintf("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=%s&maxResults=25", p.ID)
		pReq, err := http.NewRequestWithContext(ctx, http.MethodGet, pReqURL, nil)
		if err != nil {
			continue
		}
		pReq.Header.Set("Authorization", "Bearer "+accessToken)
		pResp, err := client.Do(pReq)
		if err != nil {
			continue
		}
		var pData struct {
			Items []struct {
				ContentDetails struct {
					VideoID string `json:"videoId"`
				} `json:"contentDetails"`
				Snippet struct {
					Title                  string `json:"title"`
					VideoOwnerChannelTitle string `json:"videoOwnerChannelTitle"`
					Thumbnails             struct {
						High struct {
							URL string `json:"url"`
						} `json:"high"`
						Default struct {
							URL string `json:"url"`
						} `json:"default"`
					} `json:"thumbnails"`
					ResourceID struct {
						VideoID string `json:"videoId"`
					} `json:"resourceId"`
				} `json:"snippet"`
			} `json:"items"`
		}
		if err := json.NewDecoder(pResp.Body).Decode(&pData); err == nil {
			for _, item := range pData.Items {
				vid := item.ContentDetails.VideoID
				if vid == "" {
					vid = item.Snippet.ResourceID.VideoID
				}
				if vid == "" {
					continue
				}
				artist := item.Snippet.VideoOwnerChannelTitle
				artist = strings.TrimSuffix(artist, " - Topic")
				artist = strings.TrimSuffix(artist, "VEVO")
				if artist == "" {
					artist = "YouTube Artist"
				}
				thumb := item.Snippet.Thumbnails.High.URL
				if thumb == "" {
					thumb = item.Snippet.Thumbnails.Default.URL
				}
				allTracks = append(allTracks, models.Track{
					ID:           vid,
					Title:        item.Snippet.Title,
					Artist:       artist,
					ThumbnailURL: thumb,
				})
			}
		}
		pResp.Body.Close()
		if len(allTracks) >= 50 {
			break
		}
	}
	return gatekeeper.FilterMusicTracks(allTracks), nil
}

// FetchGoogleUserProfile queries Google userinfo v3/v1 and YouTube Data API v3 to obtain the user's profile picture and name.
func FetchGoogleUserProfile(ctx context.Context, accessToken string) (*UserProfile, error) {
	// 1. First attempt: Google userinfo v3 endpoint (gives the real Google account name and profile picture)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, GoogleUserInfoURL, nil)
	if err == nil {
		req.Header.Set("Authorization", "Bearer "+accessToken)
		client := &http.Client{Timeout: 8 * time.Second}
		if resp, err := client.Do(req); err == nil {
			defer resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				var profile UserProfile
				if err := json.NewDecoder(resp.Body).Decode(&profile); err == nil && (profile.Name != "" || profile.Picture != "") {
					return &profile, nil
				}
			}
		}
	}

	// 2. Second attempt: Google userinfo v1 API with alt=json
	v1URL := "https://www.googleapis.com/oauth2/v1/userinfo?alt=json"
	if req, err := http.NewRequestWithContext(ctx, http.MethodGet, v1URL, nil); err == nil {
		req.Header.Set("Authorization", "Bearer "+accessToken)
		client := &http.Client{Timeout: 8 * time.Second}
		if resp, err := client.Do(req); err == nil {
			defer resp.Body.Close()
			if resp.StatusCode == http.StatusOK {
				var p UserProfile
				if err := json.NewDecoder(resp.Body).Decode(&p); err == nil && (p.Picture != "" || p.Name != "") {
					return &p, nil
				}
			}
		}
	}

	// 3. Third attempt: YouTube Data API (channels?mine=true)
	if profile, err := FetchYouTubeChannelProfile(ctx, accessToken); err == nil && profile != nil && (profile.Picture != "" || profile.Name != "") {
		return profile, nil
	}

	return nil, fmt.Errorf("userinfo request failed")
}
