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
		ClientName    string `json:"clientName"`
		ClientVersion string `json:"clientVersion"`
		Hl            string `json:"hl"`
		Gl            string `json:"gl"`
		DeviceMake    string `json:"deviceMake,omitempty"`
		DeviceModel   string `json:"deviceModel,omitempty"`
		OsName        string `json:"osName,omitempty"`
		OsVersion     string `json:"osVersion,omitempty"`
	} `json:"client"`
}

// Client provides authenticated and anonymous interaction with the YouTube Music Innertube API.
type Client struct {
	httpClient *http.Client
	hl         string
	gl         string
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

// ClientConfig holds endpoint configuration for specific Innertube clients.
type ClientConfig struct {
	Name        string
	Version     string
	APIKey      string
	UserAgent   string
	BaseURL     string
	XClientName string
	DeviceMake  string
	DeviceModel string
	OSName      string
	OSVersion   string
}

var (
	ConfigWebRemix = ClientConfig{
		Name:        "WEB_REMIX",
		Version:     "1.20260304.03.00",
		APIKey:      "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
		UserAgent:   UserAgentWebRemix,
		BaseURL:     "https://music.youtube.com/youtubei/v1",
		XClientName: "67",
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
	ctx.Client.OsName = cfg.OSName
	ctx.Client.OsVersion = cfg.OSVersion
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
	} else {
		req.Header.Set("Origin", "https://www.youtube.com")
		req.Header.Set("Referer", "https://www.youtube.com/")
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

	return io.ReadAll(resp.Body)
}
