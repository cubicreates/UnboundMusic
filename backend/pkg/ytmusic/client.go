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
	"sync"
	"time"
)

const (
	// DefaultTimeout is the default HTTP request timeout for scraper calls.
	DefaultTimeout = 10 * time.Second

	// User Agents
	UserAgentWebRemix = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
	UserAgentAndroid  = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip"
	UserAgentIOS      = "com.google.ios.youtube/20.08.3 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X;)"
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

// Client provides authenticated and anonymous interaction with the YouTube Music Innertube API.
type Client struct {
	httpClient  *http.Client
	hl          string
	gl          string
	mu          sync.RWMutex
	cookieStr   string
	accessToken string
	visitorData string
	poToken     string
}

// NewClient instantiates a new YouTube Music scraper client with connection pooling and timeouts.
func NewClient() *Client {
	transport := &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 20,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression: false,
	}

	return &Client{
		httpClient: &http.Client{
			Transport: transport,
			Timeout:   DefaultTimeout,
		},
		hl: "en",
		gl: "US",
	}
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
		Version:           "20.10.38",
		APIKey:            "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
		UserAgent:         "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
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
		Version:     "20.08.3",
		APIKey:      "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
		UserAgent:   UserAgentIOS,
		BaseURL:     "https://www.youtube.com/youtubei/v1",
		XClientName: "5",
		DeviceMake:  "Apple",
		DeviceModel: "iPhone16,2",
		OSName:      "iPhone",
		OSVersion:   "18.3.1.22D72",
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
	if cfg.BaseURL == "https://music.youtube.com/youtubei/v1" {
		req.Header.Set("Origin", "https://music.youtube.com")
		req.Header.Set("Referer", "https://music.youtube.com/")
		req.Header.Set("x-origin", "https://music.youtube.com")
	} else {
		req.Header.Set("Origin", "https://www.youtube.com")
		req.Header.Set("Referer", "https://www.youtube.com/")
	}

	// Attach authentication: OAuth Bearer token or cookies with dynamic SAPISIDHASH
	c.mu.RLock()
	rawCookie := c.cookieStr
	token := c.accessToken
	c.mu.RUnlock()

	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	} else if rawCookie != "" {
		req.Header.Set("Cookie", rawCookie)
		cookies := ParseCookies(rawCookie)
		sapisid := cookies["SAPISID"]
		if sapisid == "" {
			sapisid = cookies["__Secure-3PAPISID"]
		}
		if sapisid != "" {
			authHeader, _ := GenerateSAPISIDHash(sapisid, "https://music.youtube.com")
			req.Header.Set("Authorization", authHeader)
		}
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("HTTP request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
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
