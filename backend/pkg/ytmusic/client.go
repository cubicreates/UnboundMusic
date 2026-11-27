/*
 * Package: ytmusic
 * File: client.go
 * Purpose: High-performance HTTP client for YouTube Music and YouTube Innertube API communication.
 * Subsystem: Core Scraper Engine
 * Concurrency: Client is safe for concurrent access across multiple goroutines.
 */

package ytmusic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

const (
	// DefaultTimeout is the default HTTP request timeout for scraper calls.
	DefaultTimeout = 10 * time.Second

	// User Agents
	UserAgentWebRemix = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
	UserAgentAndroid  = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip"
	UserAgentIOS      = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
	UserAgentTV       = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15"
)

// ClientContext models the Innertube client identification payload.
type ClientContext struct {
	Client struct {
		ClientName        string `json:"clientName"`
		ClientVersion     string `json:"clientVersion"`
		Hl                string `json:"hl"`
		Gl                string `json:"gl"`
		ClientScreen      string `json:"clientScreen,omitempty"`
		DeviceMake        string `json:"deviceMake,omitempty"`
		DeviceModel       string `json:"deviceModel,omitempty"`
		AndroidSdkVersion int    `json:"androidSdkVersion,omitempty"`
		OsName            string `json:"osName,omitempty"`
		OsVersion         string `json:"osVersion,omitempty"`
		VisitorData       string `json:"visitorData,omitempty"`
	} `json:"client"`
}

// PlaybackTrackingURLs stores Innertube videostats reporting endpoints for a video.
type PlaybackTrackingURLs struct {
	PlaybackURL   string
	WatchtimeURL  string
	AtrURL        string
	Authenticated bool
	UpdatedAt     time.Time
}

const maxTrackingCacheEntries = 256

// Client provides authenticated and anonymous interaction with the YouTube Music Innertube API.
type Client struct {
	httpClient    *http.Client
	hl            string
	gl            string
	mu            sync.RWMutex
	cookieStr     string
	accessToken   string
	pageID        string
	dataSyncID    string
	visitorData   string
	poToken       string
	trackingMu    sync.RWMutex
	trackingCache map[string]PlaybackTrackingURLs
}

// NewClient instantiates a new YouTube Music scraper client with connection pooling and timeouts.
func NewClient() *Client {
	transport := &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 20,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression:  false,
	}

	return &Client{
		httpClient: &http.Client{
			Transport: transport,
			Timeout:   DefaultTimeout,
		},
		hl:            "en",
		gl:            "US",
		trackingCache: make(map[string]PlaybackTrackingURLs),
	}
}

// IsValidYouTubeVideoID returns true when id is a standard 11-character YouTube video ID.
func IsValidYouTubeVideoID(id string) bool {
	if len(id) != 11 || strings.HasPrefix(id, "local") {
		return false
	}
	for i := 0; i < len(id); i++ {
		ch := id[i]
		isValid := (ch >= 'a' && ch <= 'z') ||
			(ch >= 'A' && ch <= 'Z') ||
			(ch >= '0' && ch <= '9') ||
			ch == '-' || ch == '_'
		if !isValid {
			return false
		}
	}
	return true
}

// SetPlaybackTrackingWithAtr caches videostats + atr tracking URLs for a resolved YouTube video ID.
func (c *Client) SetPlaybackTrackingWithAtr(videoID, playbackURL, watchtimeURL, atrURL string, authenticated bool) {
	if !IsValidYouTubeVideoID(videoID) || (playbackURL == "" && watchtimeURL == "" && atrURL == "") {
		return
	}
	c.trackingMu.Lock()
	defer c.trackingMu.Unlock()
	if c.trackingCache == nil {
		c.trackingCache = make(map[string]PlaybackTrackingURLs)
	}
	if len(c.trackingCache) >= maxTrackingCacheEntries {
		var oldestKey string
		var oldestTime time.Time
		now := time.Now()
		for k, v := range c.trackingCache {
			if now.Sub(v.UpdatedAt) > 5*time.Hour {
				delete(c.trackingCache, k)
				continue
			}
			if oldestKey == "" || v.UpdatedAt.Before(oldestTime) {
				oldestKey = k
				oldestTime = v.UpdatedAt
			}
		}
		if len(c.trackingCache) >= maxTrackingCacheEntries && oldestKey != "" {
			delete(c.trackingCache, oldestKey)
		}
	}
	c.trackingCache[videoID] = PlaybackTrackingURLs{
		PlaybackURL:   playbackURL,
		WatchtimeURL:  watchtimeURL,
		AtrURL:        atrURL,
		Authenticated: authenticated,
		UpdatedAt:     time.Now(),
	}
}

// SetPlaybackTracking caches videostats tracking URLs for a resolved YouTube video ID.
func (c *Client) SetPlaybackTracking(videoID, playbackURL, watchtimeURL string, authenticated bool) {
	c.SetPlaybackTrackingWithAtr(videoID, playbackURL, watchtimeURL, "", authenticated)
}

// GetPlaybackTracking retrieves cached videostats tracking URLs for a video ID.
func (c *Client) GetPlaybackTracking(videoID string) (PlaybackTrackingURLs, bool) {
	c.trackingMu.RLock()
	defer c.trackingMu.RUnlock()
	entry, ok := c.trackingCache[videoID]
	return entry, ok
}

// SetCredentials configures YouTube authentication cookies for user library and mutation requests.
func (c *Client) SetCredentials(cookie string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.cookieStr = cookie
}

// GetCredentials returns the current session cookie string.
func (c *Client) GetCredentials() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.cookieStr
}

// SetAccessToken configures an OAuth Bearer token for YouTube API access.
func (c *Client) SetAccessToken(token string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.accessToken = token
}

// GetAccessToken returns the current OAuth access token.
func (c *Client) GetAccessToken() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.accessToken
}

// HasCredentials returns true if authentication cookies or OAuth tokens are active.
func (c *Client) HasCredentials() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.cookieStr != "" || c.accessToken != ""
}

// SetVisitorData configures YouTube guest visitorData token for full stream access.
func (c *Client) SetVisitorData(vd string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.visitorData = vd
}

// GetVisitorData returns the current visitorData token.
func (c *Client) GetVisitorData() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.visitorData
}

// SetPageID configures the X-Goog-Pageid header for YouTube Brand / Multi-Account channels.
func (c *Client) SetPageID(pageID string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.pageID = strings.TrimSpace(pageID)
}

// GetPageID returns the active YouTube pageId (X-Goog-Pageid).
func (c *Client) GetPageID() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.pageID
}

// SetDataSyncID configures the account datasyncId token returned by getAccountSwitcherEndpoint.
func (c *Client) SetDataSyncID(dataSyncID string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.dataSyncID = strings.TrimSpace(dataSyncID)
}

// GetDataSyncID returns the active datasyncId token.
func (c *Client) GetDataSyncID() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.dataSyncID
}

// FetchVisitorDataFromSWJS retrieves YouTube Music's canonical visitorData token from /sw.js_data,
// matching SimpMusic's session initialization pattern.
func (c *Client) FetchVisitorDataFromSWJS(ctx context.Context) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://music.youtube.com/sw.js_data", nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", UserAgentWebRemix)
	req.Header.Set("Origin", "https://music.youtube.com")
	req.Header.Set("Referer", "https://music.youtube.com/")

	c.mu.RLock()
	rawCookie := c.cookieStr
	c.mu.RUnlock()
	if rawCookie != "" {
		req.Header.Set("Cookie", rawCookie)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("sw.js_data status %d", resp.StatusCode)
	}

	rawBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	trimmed := strings.TrimSpace(string(rawBytes))
	trimmed = strings.TrimPrefix(trimmed, ")]}'")
	trimmed = strings.TrimSpace(trimmed)

	var arr []any
	if err := json.Unmarshal([]byte(trimmed), &arr); err != nil {
		return "", err
	}
	vd := findVisitorDataString(arr)
	if vd == "" {
		return "", fmt.Errorf("visitorData not found in sw.js_data")
	}
	c.SetVisitorData(vd)
	return vd, nil
}

func findVisitorDataString(val any) string {
	switch v := val.(type) {
	case string:
		if strings.HasPrefix(v, "Cgt") && len(v) >= 12 {
			return v
		}
	case []any:
		for _, child := range v {
			if found := findVisitorDataString(child); found != "" {
				return found
			}
		}
	}
	return ""
}

// EnsureVisitorData ensures the client has a valid visitorData token, bootstrapping via /sw.js_data or /browse if necessary.
func (c *Client) EnsureVisitorData(ctx context.Context) error {
	c.mu.RLock()
	if c.visitorData != "" {
		c.mu.RUnlock()
		return nil
	}
	c.mu.RUnlock()

	if _, err := c.FetchVisitorDataFromSWJS(ctx); err == nil {
		return nil
	}

	body := map[string]any{
		"context":  c.buildContext(ConfigAndroid),
		"browseId": "FEwhat_to_watch",
	}
	_, err := c.post(ctx, "browse", body, ConfigAndroid)
	return err
}

// SetPoToken configures YouTube Proof of Origin token for stream access.
func (c *Client) SetPoToken(pot string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.poToken = pot
}

// GetPoToken returns the current Proof of Origin token.
func (c *Client) GetPoToken() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.poToken
}

// ClientConfig holds endpoint configuration for specific Innertube clients.
type ClientConfig struct {
	Name              string
	Version           string
	APIKey            string
	UserAgent         string
	BaseURL           string
	XClientName       string
	DeviceMake        string
	DeviceModel       string
	AndroidSdkVersion int
	OSName            string
	OSVersion         string
}

var (
	ConfigAndroid = ClientConfig{
		Name:              "ANDROID",
		Version:           "21.26.364",
		APIKey:            "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
		UserAgent:         "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
		BaseURL:           "https://www.youtube.com/youtubei/v1",
		XClientName:       "3",
		DeviceMake:        "Google",
		DeviceModel:       "Pixel 7",
		AndroidSdkVersion: 30,
		OSName:            "Android",
		OSVersion:         "11",
	}

	ConfigWebRemix = ClientConfig{
		Name:        "WEB_REMIX",
		Version:     "1.20260304.03.00",
		APIKey:      "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
		UserAgent:   UserAgentWebRemix,
		BaseURL:     "https://music.youtube.com/youtubei/v1",
		XClientName: "67",
	}

	ConfigWeb = ClientConfig{
		Name:        "WEB",
		Version:     "2.20260304.01.00",
		APIKey:      "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
		UserAgent:   UserAgentWebRemix,
		BaseURL:     "https://www.youtube.com/youtubei/v1",
		XClientName: "1",
	}

	ConfigIOS = ClientConfig{
		Name:        "IOS",
		Version:     "21.26.4",
		APIKey:      "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
		UserAgent:   UserAgentIOS,
		BaseURL:     "https://www.youtube.com/youtubei/v1",
		XClientName: "5",
		DeviceMake:  "Apple",
		DeviceModel: "iPhone16,2",
		OSName:      "iPhone",
		OSVersion:   "18.3.2.22D82",
	}

	ConfigAndroidMusic = ClientConfig{
		Name:        "ANDROID_MUSIC",
		Version:     "7.27.52",
		APIKey:      "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
		UserAgent:   UserAgentAndroid,
		BaseURL:     "https://music.youtube.com/youtubei/v1",
		XClientName: "21",
		OSName:      "Android",
		OSVersion:   "14",
	}

	ConfigTVHTML5Simply = ClientConfig{
		Name:        "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
		Version:     "2.0",
		APIKey:      "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
		UserAgent:   UserAgentTV,
		BaseURL:     "https://www.youtube.com/youtubei/v1",
		XClientName: "85",
	}

	ConfigTVHTML5 = ClientConfig{
		Name:        "TVHTML5",
		Version:     "7.20260304.08.00",
		APIKey:      "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
		UserAgent:   UserAgentTV,
		BaseURL:     "https://www.youtube.com/youtubei/v1",
		XClientName: "85",
	}

	ConfigVisionOS = ClientConfig{
		Name:        "VISIONOS",
		Version:     "1.02",
		APIKey:      "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
		UserAgent:   "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
		BaseURL:     "https://www.youtube.com/youtubei/v1",
		XClientName: "101",
		DeviceMake:  "Apple",
		DeviceModel: "VisionPro",
		OSName:      "visionOS",
		OSVersion:   "1.02",
	}
)

// buildContext creates the appropriate Innertube client context header payload.
func (c *Client) buildContext(cfg ClientConfig) ClientContext {
	var ctx ClientContext
	ctx.Client.ClientName = cfg.Name
	ctx.Client.ClientVersion = cfg.Version
	ctx.Client.Hl = c.hl
	ctx.Client.Gl = c.gl
	ctx.Client.DeviceMake = cfg.DeviceMake
	ctx.Client.DeviceModel = cfg.DeviceModel
	ctx.Client.AndroidSdkVersion = cfg.AndroidSdkVersion
	ctx.Client.OsName = cfg.OSName
	ctx.Client.OsVersion = cfg.OSVersion
	c.mu.RLock()
	ctx.Client.VisitorData = c.visitorData
	c.mu.RUnlock()
	return ctx
}

type authOverrideKey struct{}
type authDisableKey struct{}

// WithForceAuth creates a context that forces authentication cookies/tokens to be attached,
// even for typically unauthenticated endpoints like player (e.g. for age-restricted content fallback).
func WithForceAuth(ctx context.Context) context.Context {
	return context.WithValue(ctx, authOverrideKey{}, true)
}

// WithDisableAuth creates a context that forces authentication cookies/tokens to be omitted,
// ensuring an endpoint executes as a clean unauthenticated guest request.
func WithDisableAuth(ctx context.Context) context.Context {
	return context.WithValue(ctx, authDisableKey{}, true)
}

// isAccountBoundEndpoint determines whether an Innertube endpoint requires user authentication cookies.
// Public catalog endpoints (player, search, get_transcript) MUST NOT attach user session cookies by default.
// Attaching session cookies to player binds the stream to a browser session resulting in HTTP 403 on Google CDN.
// Attaching session cookies to search subjects queries to account restrictions or empty/altered layouts.
func isAccountBoundEndpoint(endpoint string) bool {
	switch endpoint {
	case "player", "search", "get_transcript":
		return false
	default:
		return true
	}
}

func shouldAttachAuth(ctx context.Context, endpoint string) bool {
	if v, ok := ctx.Value(authDisableKey{}).(bool); ok && v {
		return false
	}
	if v, ok := ctx.Value(authOverrideKey{}).(bool); ok && v {
		return true
	}
	return isAccountBoundEndpoint(endpoint)
}

// post executes a POST request against a specified Innertube endpoint with JSON payload.
func (c *Client) post(ctx context.Context, endpoint string, body any, cfg ClientConfig) ([]byte, error) {
	jsonBytes, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request body: %w", err)
	}

	reqURL, err := url.JoinPath(cfg.BaseURL, endpoint)
	if err != nil {
		return nil, fmt.Errorf("invalid endpoint URL: %w", err)
	}

	if cfg.APIKey != "" {
		reqURL += "?key=" + cfg.APIKey + "&prettyPrint=false"
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, reqURL, bytes.NewReader(jsonBytes))
	if err != nil {
		return nil, fmt.Errorf("failed to create HTTP request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", cfg.UserAgent)
	if cfg.XClientName != "" {
		req.Header.Set("X-YouTube-Client-Name", cfg.XClientName)
	}
	req.Header.Set("X-YouTube-Client-Version", cfg.Version)

	c.mu.RLock()
	vd := c.visitorData
	c.mu.RUnlock()
	if vd != "" {
		req.Header.Set("X-Goog-Visitor-Id", vd)
	}

	origin := "https://www.youtube.com"
	if cfg.BaseURL == "https://music.youtube.com/youtubei/v1" {
		origin = "https://music.youtube.com"
		req.Header.Set("Origin", origin)
		req.Header.Set("Referer", "https://music.youtube.com/")
		req.Header.Set("x-origin", origin)
	} else if cfg.Name == "WEB" || cfg.Name == "WEB_REMIX" || cfg.Name == "MWEB" {
		req.Header.Set("Origin", origin)
		req.Header.Set("Referer", "https://www.youtube.com/")
	}

	// Attach authentication only for account-bound endpoints (library, likes, account menu, history)
	// or when explicitly forced (e.g. fallback for age-restricted content).
	if shouldAttachAuth(ctx, endpoint) {
		c.mu.RLock()
		rawCookie := c.cookieStr
		token := c.accessToken
		pageID := c.pageID
		c.mu.RUnlock()

		// YouTube Music (music.youtube.com) rejects Google TV Bearer tokens.
		// Bearer tokens should only be sent to standard YouTube (www.youtube.com) endpoints.
		if token != "" && cfg.BaseURL != "https://music.youtube.com/youtubei/v1" {
			req.Header.Set("Authorization", "Bearer "+token)
		} else if rawCookie != "" {
			req.Header.Set("Cookie", rawCookie)
			req.Header.Set("X-Goog-Authuser", "0")
			req.Header.Set("X-Goog-Api-Format-Version", "1")
			if pageID != "" {
				req.Header.Set("X-Goog-Pageid", pageID)
			}
			if authHeader := BuildCookieAuthorizationHeader(rawCookie, origin); authHeader != "" {
				req.Header.Set("Authorization", authHeader)
			}
		}
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("HTTP request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		// Fallback: If an authenticated search was rejected with 401/403, retry as unauthenticated guest
		if (resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden) && endpoint == "search" {
			guestReq, gErr := http.NewRequestWithContext(ctx, http.MethodPost, reqURL, bytes.NewReader(jsonBytes))
			if gErr == nil {
				guestReq.Header.Set("Content-Type", "application/json")
				guestReq.Header.Set("User-Agent", cfg.UserAgent)
				if cfg.XClientName != "" {
					guestReq.Header.Set("X-YouTube-Client-Name", cfg.XClientName)
				}
				guestReq.Header.Set("X-YouTube-Client-Version", cfg.Version)
				guestReq.Header.Set("Origin", "https://music.youtube.com")
				guestReq.Header.Set("Referer", "https://music.youtube.com/")
				guestReq.Header.Set("x-origin", "https://music.youtube.com")
				guestResp, gDoErr := c.httpClient.Do(guestReq)
				if gDoErr == nil {
					defer guestResp.Body.Close()
					if guestResp.StatusCode >= 200 && guestResp.StatusCode < 300 {
						return io.ReadAll(guestResp.Body)
					}
				}
			}
		}
		respBody, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API error status %d: %s", resp.StatusCode, string(respBody))
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	// Capture visitorData if returned in responseContext to maintain valid session identity
	var vResp struct {
		ResponseContext struct {
			VisitorData string `json:"visitorData"`
		} `json:"responseContext"`
	}
	if err := json.Unmarshal(bodyBytes, &vResp); err == nil && vResp.ResponseContext.VisitorData != "" {
		c.mu.Lock()
		if c.visitorData == "" {
			c.visitorData = vResp.ResponseContext.VisitorData
		}
		c.mu.Unlock()
	}

	return bodyBytes, nil
}

// get executes an authenticated GET request against a full URL (such as https://music.youtube.com/getAccountSwitcherEndpoint).
func (c *Client) get(ctx context.Context, targetURL string, cfg ClientConfig) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create GET request: %w", err)
	}

	req.Header.Set("User-Agent", cfg.UserAgent)
	if cfg.XClientName != "" {
		req.Header.Set("X-YouTube-Client-Name", cfg.XClientName)
	}
	req.Header.Set("X-YouTube-Client-Version", cfg.Version)
	req.Header.Set("Accept", "*/*")

	origin := "https://www.youtube.com"
	if strings.Contains(targetURL, "music.youtube.com") {
		origin = "https://music.youtube.com"
		req.Header.Set("Origin", origin)
		req.Header.Set("Referer", "https://music.youtube.com/")
		req.Header.Set("x-origin", origin)
	} else {
		req.Header.Set("Origin", origin)
		req.Header.Set("Referer", "https://www.youtube.com/")
	}

	c.mu.RLock()
	rawCookie := c.cookieStr
	token := c.accessToken
	pageID := c.pageID
	vd := c.visitorData
	c.mu.RUnlock()

	if vd != "" {
		req.Header.Set("X-Goog-Visitor-Id", vd)
	}

	if token != "" && !strings.Contains(targetURL, "music.youtube.com") {
		req.Header.Set("Authorization", "Bearer "+token)
	} else if rawCookie != "" {
		req.Header.Set("Cookie", rawCookie)
		req.Header.Set("X-Goog-Authuser", "0")
		req.Header.Set("X-Goog-Api-Format-Version", "1")
		if pageID != "" {
			req.Header.Set("X-Goog-Pageid", pageID)
		}
		if authHeader := BuildCookieAuthorizationHeader(rawCookie, origin); authHeader != "" {
			req.Header.Set("Authorization", authHeader)
		}
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("HTTP GET failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API error status %d: %s", resp.StatusCode, string(respBody))
	}

	return io.ReadAll(resp.Body)
}
