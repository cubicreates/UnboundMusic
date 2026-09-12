/*
 * Package: ytmusic
 * File: account_info.go
 * Purpose: Fetches authenticated user's YouTube account profile name and avatar image URL from InnerTube.
 * Subsystem: YouTube Integration Engine
 * Concurrency: Thread-safe client methods using context.Context.
 */

package ytmusic

import (
	"bytes"
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
	if !c.HasCredentials() {
		return nil, fmt.Errorf("unauthenticated: no session credentials set")
	}

	// 0. Primary attempt: getAccountSwitcherEndpoint on music.youtube.com (authentic YouTube Music account identity)
	if respBytes, err := c.get(ctx, "https://music.youtube.com/getAccountSwitcherEndpoint", ConfigWebRemix); err == nil {
		respBytes = bytes.TrimPrefix(respBytes, []byte(")]}'\n"))
		respBytes = bytes.TrimPrefix(respBytes, []byte(")]}'"))
		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err == nil {
			info := parseAccountInfoFromJSON(root)
			if info != nil && (info.Name != "" || info.AvatarURL != "") {
				return info, nil
			}
		}
	}

	// 1. Secondary attempt: account/account_menu on ConfigWebRemix, ConfigWeb, and TV configs
	for _, cfg := range []ClientConfig{ConfigWebRemix, ConfigWeb, ConfigTVHTML5, ConfigTVHTML5Simply} {
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
			return info, nil
		}
	}

	// 2. Secondary attempt: guide endpoint (natively used on TV and Web navigation bars)
	for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb, ConfigWebRemix} {
		body := map[string]interface{}{
			"context": c.buildContext(cfg),
		}

		respBytes, err := c.post(ctx, "guide", body, cfg)
		if err != nil {
			continue
		}

		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err != nil {
			continue
		}

		info := parseAccountInfoFromJSON(root)
		if info != nil && (info.Name != "" || info.AvatarURL != "") {
			return info, nil
		}
	}

	// 3. Third attempt: browse endpoint with FEaccount
	for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb, ConfigWebRemix} {
		body := map[string]interface{}{
			"context":  c.buildContext(cfg),
			"browseId": "FEaccount",
		}

		respBytes, err := c.post(ctx, "browse", body, cfg)
		if err != nil {
			continue
		}

		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err != nil {
			continue
		}

		info := parseAccountInfoFromJSON(root)
		if info != nil && (info.Name != "" || info.AvatarURL != "") {
			return info, nil
		}
	}

	return &AccountInfo{
		Name:      "",
		AvatarURL: "",
	}, nil
}

// parseAccountInfoFromJSON recursively extracts account header details from InnerTube response JSON.
func parseAccountInfoFromJSON(data interface{}) *AccountInfo {
	info := &AccountInfo{}
	findAccountDetails(data, info)
	if info.Name != "" || info.AvatarURL != "" {
		return info
	}
	return nil
}

func findAccountDetails(data interface{}, info *AccountInfo) {
	switch v := data.(type) {
	case map[string]interface{}:
		// 1. activeAccountHeaderRenderer
		if header, ok := v["activeAccountHeaderRenderer"].(map[string]interface{}); ok {
			if nameObj, ok := header["accountName"].(map[string]interface{}); ok {
				if s, ok := nameObj["simpleText"].(string); ok && s != "" && info.Name == "" {
					info.Name = s
				} else if runs, ok := nameObj["runs"].([]interface{}); ok && len(runs) > 0 && info.Name == "" {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Name, _ = r0["text"].(string)
					}
				}
			}
			if handleObj, ok := header["channelHandle"].(map[string]interface{}); ok {
				if s, ok := handleObj["simpleText"].(string); ok && s != "" {
					info.Handle = s
				} else if runs, ok := handleObj["runs"].([]interface{}); ok && len(runs) > 0 {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Handle, _ = r0["text"].(string)
					}
				}
			}
			if photoObj, ok := header["accountPhoto"].(map[string]interface{}); ok {
				if thumbs, ok := photoObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok && info.AvatarURL == "" {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}

		// 2. guideAccountRenderer / guideHeaderRenderer / accountItemRenderer
		if acc, ok := v["guideAccountRenderer"].(map[string]interface{}); ok {
			if titleObj, ok := acc["formattedTitle"].(map[string]interface{}); ok {
				if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 && info.Name == "" {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Name, _ = r0["text"].(string)
					}
				}
			}
			if thumbObj, ok := acc["thumbnail"].(map[string]interface{}); ok {
				if thumbs, ok := thumbObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok && info.AvatarURL == "" {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}

		if accItem, ok := v["accountItemRenderer"].(map[string]interface{}); ok {
			if titleObj, ok := accItem["accountName"].(map[string]interface{}); ok {
				if s, ok := titleObj["simpleText"].(string); ok && s != "" && info.Name == "" {
					info.Name = s
				} else if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 && info.Name == "" {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Name, _ = r0["text"].(string)
					}
				}
			}
			if photoObj, ok := accItem["accountPhoto"].(map[string]interface{}); ok {
				if thumbs, ok := photoObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok && info.AvatarURL == "" {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}

		// 2.1 accountItem (returned by getAccountSwitcherEndpoint)
		if accItem, ok := v["accountItem"].(map[string]interface{}); ok {
			if titleObj, ok := accItem["accountName"].(map[string]interface{}); ok {
				if s, ok := titleObj["simpleText"].(string); ok && s != "" && info.Name == "" {
					info.Name = s
				} else if runs, ok := titleObj["runs"].([]interface{}); ok && len(runs) > 0 && info.Name == "" {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Name, _ = r0["text"].(string)
					}
				}
			}
			if handleObj, ok := accItem["channelHandle"].(map[string]interface{}); ok {
				if s, ok := handleObj["simpleText"].(string); ok && s != "" && info.Handle == "" {
					info.Handle = s
				} else if runs, ok := handleObj["runs"].([]interface{}); ok && len(runs) > 0 && info.Handle == "" {
					if r0, ok := runs[0].(map[string]interface{}); ok {
						info.Handle, _ = r0["text"].(string)
					}
				}
			}
			if photoObj, ok := accItem["accountPhoto"].(map[string]interface{}); ok {
				if thumbs, ok := photoObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok && info.AvatarURL == "" {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}

		// 3. Modern YouTube avatarViewModel / decoratedAvatarViewModel
		if avm, ok := v["avatarViewModel"].(map[string]interface{}); ok {
			if img, ok := avm["image"].(map[string]interface{}); ok {
				if sources, ok := img["sources"].([]interface{}); ok && len(sources) > 0 {
					lastSource, _ := sources[len(sources)-1].(map[string]interface{})
					if rawURL, ok := lastSource["url"].(string); ok && info.AvatarURL == "" {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}

		// 4. Catch-all: check for any Google avatar url on yt3.ggpht.com or googleusercontent.com
		for k, val := range v {
			if strings.Contains(strings.ToLower(k), "avatar") || strings.Contains(strings.ToLower(k), "photo") {
				if thumbMap, ok := val.(map[string]interface{}); ok {
					if thumbs, ok := thumbMap["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
						lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
						if rawURL, ok := lastThumb["url"].(string); ok && info.AvatarURL == "" {
							if strings.Contains(rawURL, "yt3.ggpht.com") || strings.Contains(rawURL, "googleusercontent.com") {
								info.AvatarURL = upgradeAvatarResolution(rawURL)
							}
						}
					}
				}
			}
		}

		for _, child := range v {
			findAccountDetails(child, info)
		}
	case []interface{}:
		for _, child := range v {
			findAccountDetails(child, info)
		}
	}
}

// upgradeAvatarResolution cleans protocol-relative URLs and scales Google avatar dimensions to high res.
func upgradeAvatarResolution(rawURL string) string {
	if strings.HasPrefix(rawURL, "//") {
		rawURL = "https:" + rawURL
	}
	// Replace s88-c-k / s120-c-k with s352 or w240-h240 for crisp display
	if strings.Contains(rawURL, "=s88") {
		return strings.ReplaceAll(rawURL, "=s88", "=s352")
	}
	re := thumbRegex
	if re.MatchString(rawURL) {
		return re.ReplaceAllString(rawURL, "=w240-h240")
	}
	return rawURL
}

