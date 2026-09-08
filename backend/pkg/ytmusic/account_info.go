/*
 * Package: ytmusic
 * File: account_info.go
 * Purpose: Fetches authenticated user's YouTube account profile name and avatar image URL from InnerTube.
 * Subsystem: YouTube Integration Engine
 * Concurrency: Thread-safe client methods using context.Context.
 */

package ytmusic

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
)

// AccountInfo holds the authenticated user's profile metadata.
type AccountInfo struct {
	Name      string `json:"name"`
	AvatarURL string `json:"avatar_url"`
	Handle    string `json:"handle"`
}

// FetchAccountInfo queries InnerTube for the authenticated user's account identity and profile picture.
func (c *Client) FetchAccountInfo(ctx context.Context) (*AccountInfo, error) {
	if c.cookieStr == "" {
		return nil, fmt.Errorf("unauthenticated: no session cookies set")
	}

	// Try Web client first, then WebRemix
	for _, cfg := range []ClientConfig{ConfigWeb, ConfigWebRemix} {
		body := map[string]interface{}{
			"context": c.buildContext(cfg),
		}

		respBytes, err := c.post(ctx, "account/account_menu", body, cfg)
		if err != nil {
			continue
		}

		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err != nil {
			continue
		}

		info := parseAccountInfoFromJSON(root)
		if info != nil && (info.Name != "" || info.AvatarURL != "") {
			if info.Name == "" {
				info.Name = "YouTube User"
			}
			return info, nil
		}
	}

	return &AccountInfo{
		Name:      "YouTube User",
		AvatarURL: "",
	}, nil
}

// parseAccountInfoFromJSON recursively extracts account header details from InnerTube response JSON.
func parseAccountInfoFromJSON(data interface{}) *AccountInfo {
	switch v := data.(type) {
	case map[string]interface{}:
		if header, ok := v["activeAccountHeaderRenderer"].(map[string]interface{}); ok {
			info := &AccountInfo{}

			// Extract Account Name
			if nameObj, ok := header["accountName"].(map[string]interface{}); ok {
				if s, ok := nameObj["simpleText"].(string); ok && s != "" {
					info.Name = s
				} else if runs, ok := nameObj["runs"].([]interface{}); ok && len(runs) > 0 {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Name, _ = r0["text"].(string)
					}
				}
			}

			// Extract Channel Handle
			if handleObj, ok := header["channelHandle"].(map[string]interface{}); ok {
				if s, ok := handleObj["simpleText"].(string); ok && s != "" {
					info.Handle = s
				} else if runs, ok := handleObj["runs"].([]interface{}); ok && len(runs) > 0 {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Handle, _ = r0["text"].(string)
					}
				}
			}

			// Extract Account Photo / Avatar URL
			if photoObj, ok := header["accountPhoto"].(map[string]interface{}); ok {
				if thumbs, ok := photoObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok {
						// Upgrade resolution if query parameter s88 / s120 exists
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}

			if info.Name != "" || info.AvatarURL != "" {
				return info
			}
		}

		for _, child := range v {
			if res := parseAccountInfoFromJSON(child); res != nil {
				return res
			}
		}

	case []interface{}:
		for _, child := range v {
			if res := parseAccountInfoFromJSON(child); res != nil {
				return res
			}
		}
	}

	return nil
}

// upgradeAvatarResolution cleans protocol-relative URLs and scales Google avatar dimensions to high res.
func upgradeAvatarResolution(rawURL string) string {
	if strings.HasPrefix(rawURL, "//") {
		rawURL = "https:" + rawURL
	}
	// Replace s88-c-k / s120-c-k with s240-c-k for crisp display on high-DPI screens
	re := thumbRegex
	if re.MatchString(rawURL) {
		return re.ReplaceAllString(rawURL, "=w240-h240")
	}
	return rawURL
}
