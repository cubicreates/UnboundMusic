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
	if !c.HasCredentials() {
		return nil, fmt.Errorf("unauthenticated: no session credentials set")
	}

	// 0. Primary attempt for YouTube Music: FEmusic_home browse endpoint (proven SimpMusic pattern)
	homeBody := map[string]interface{}{
		"context":  c.buildContext(ConfigWebRemix),
		"browseId": "FEmusic_home",
	}
	if respBytes, err := c.post(ctx, "browse", homeBody, ConfigWebRemix); err == nil {
		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err == nil {
			if info := parseAccountInfoFromMusicHome(root); info != nil && (info.Name != "" || info.AvatarURL != "") {
				return info, nil
			}
			if info := parseAccountInfoFromJSON(root); info != nil && (info.Name != "" || info.AvatarURL != "") {
				return info, nil
			}
		}
	}

	// 1. Second attempt: account/account_menu across TV and Web configurations
	for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigTVHTML5Simply, ConfigWeb, ConfigWebRemix} {
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

	// 2. Second attempt: guide endpoint (natively used on TV and Web navigation bars)
	for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb} {
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
	for _, cfg := range []ClientConfig{ConfigTVHTML5, ConfigWeb} {
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

// parseAccountInfoFromMusicHome extracts user profile name and avatar from FEmusic_home header
func parseAccountInfoFromMusicHome(root map[string]interface{}) *AccountInfo {
	contents, _ := root["contents"].(map[string]interface{})
	singleCol, _ := contents["singleColumnBrowseResultsRenderer"].(map[string]interface{})
	tabs, _ := singleCol["tabs"].([]interface{})
	if len(tabs) == 0 {
		return nil
	}
	tab0, _ := tabs[0].(map[string]interface{})
	tabRenderer, _ := tab0["tabRenderer"].(map[string]interface{})
	content, _ := tabRenderer["content"].(map[string]interface{})
	sectionList, _ := content["sectionListRenderer"].(map[string]interface{})
	sectionContents, _ := sectionList["contents"].([]interface{})
	if len(sectionContents) == 0 {
		return nil
	}
	shelf0, _ := sectionContents[0].(map[string]interface{})
	carousel, _ := shelf0["musicCarouselShelfRenderer"].(map[string]interface{})
	header, _ := carousel["header"].(map[string]interface{})
	basicHeader, _ := header["musicCarouselShelfBasicHeaderRenderer"].(map[string]interface{})

	info := &AccountInfo{}
	if strapline, ok := basicHeader["strapline"].(map[string]interface{}); ok {
		if runs, ok := strapline["runs"].([]interface{}); ok && len(runs) > 0 {
			if r0, ok := runs[0].(map[string]interface{}); ok {
				info.Name, _ = r0["text"].(string)
			}
		}
	}

	if thumbRenderer, ok := basicHeader["thumbnail"].(map[string]interface{}); ok {
		if musicThumb, ok := thumbRenderer["musicThumbnailRenderer"].(map[string]interface{}); ok {
			if thumbObj, ok := musicThumb["thumbnail"].(map[string]interface{}); ok {
				if thumbs, ok := thumbObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}
	}

	if info.Name != "" || info.AvatarURL != "" {
		return info
	}
	return nil
}

