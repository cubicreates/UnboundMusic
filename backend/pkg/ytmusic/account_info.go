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

// AccountInfo holds the authenticated user's profile metadata and InnerTube pageId/dataSyncId tokens.
type AccountInfo struct {
	Name       string `json:"name"`
	AvatarURL  string `json:"avatar_url"`
	Handle     string `json:"handle"`
	PageID     string `json:"page_id,omitempty"`
	DataSyncID string `json:"data_sync_id,omitempty"`
}

// FetchAccountInfo queries InnerTube for the authenticated user's account identity, profile picture,
// and Brand Account pageId / datasyncId (matching SimpMusic getAccountSwitcherEndpoint + FEmusic_home fallback).
func (c *Client) FetchAccountInfo(ctx context.Context) (*AccountInfo, error) {
	if !c.HasCredentials() {
		return nil, fmt.Errorf("unauthenticated: no session credentials set")
	}

	var bestInfo AccountInfo
	mergeInfo := func(candidate *AccountInfo) {
		if candidate == nil {
			return
		}
		if bestInfo.Name == "" && candidate.Name != "" {
			bestInfo.Name = candidate.Name
		}
		if bestInfo.AvatarURL == "" && candidate.AvatarURL != "" {
			bestInfo.AvatarURL = candidate.AvatarURL
		}
		if bestInfo.Handle == "" && candidate.Handle != "" {
			bestInfo.Handle = candidate.Handle
		}
		if bestInfo.PageID == "" && candidate.PageID != "" {
			bestInfo.PageID = candidate.PageID
		}
		if bestInfo.DataSyncID == "" && candidate.DataSyncID != "" {
			bestInfo.DataSyncID = candidate.DataSyncID
		}
	}

	// 0. Primary attempt: getAccountSwitcherEndpoint on music.youtube.com & www.youtube.com
	for _, switcher := range []struct {
		url string
		cfg ClientConfig
	}{
		{"https://music.youtube.com/getAccountSwitcherEndpoint", ConfigWebRemix},
		{"https://www.youtube.com/getAccountSwitcherEndpoint", ConfigWeb},
	} {
		if respBytes, err := c.get(ctx, switcher.url, switcher.cfg); err == nil {
			respBytes = bytes.TrimPrefix(respBytes, []byte(")]}'\n"))
			respBytes = bytes.TrimPrefix(respBytes, []byte(")]}'"))
			var root map[string]interface{}
			if err := json.Unmarshal(respBytes, &root); err == nil {
				info := parseAccountInfoFromJSON(root)
				mergeInfo(info)
				if bestInfo.PageID != "" {
					c.SetPageID(bestInfo.PageID)
				}
				if bestInfo.DataSyncID != "" {
					c.SetDataSyncID(bestInfo.DataSyncID)
				}
				if bestInfo.Name != "" && bestInfo.AvatarURL != "" && bestInfo.PageID != "" {
					return &bestInfo, nil
				}
			}
		}
	}

	// 1. Secondary attempt: account/account_menu on ConfigWeb, ConfigWebRemix, ConfigTVHTML5, ConfigTVHTML5Simply
	for _, cfg := range []ClientConfig{ConfigWebRemix, ConfigWeb, ConfigTVHTML5, ConfigTVHTML5Simply} {
		body := map[string]interface{}{
			"context":            c.buildContext(cfg),
			"deviceTheme":        "DEVICE_THEME_SUPPORTED",
			"userInterfaceTheme": "USER_INTERFACE_THEME_DARK",
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
		mergeInfo(info)
		if bestInfo.Name != "" && bestInfo.AvatarURL != "" {
			return &bestInfo, nil
		}
	}

	// 2. Google ListAccounts endpoint (extracts Google display name & avatar directly from Google cookies)
	if respBytes, err := c.get(ctx, "https://accounts.google.com/ListAccounts?gpsia=1&source=ogb&mo=1&origin=https://music.youtube.com", ConfigWeb); err == nil {
		rawText := string(bytes.TrimPrefix(respBytes, []byte(")]}'\n")))
		var arr []interface{}
		if err := json.Unmarshal([]byte(rawText), &arr); err == nil {
			info := parseAccountInfoFromJSON(arr)
			mergeInfo(info)
			if bestInfo.Name != "" && bestInfo.AvatarURL != "" {
				return &bestInfo, nil
			}
		}
	}

	// 3. Guide endpoint
	for _, cfg := range []ClientConfig{ConfigWeb, ConfigWebRemix, ConfigTVHTML5} {
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
		mergeInfo(info)
		if bestInfo.Name != "" && bestInfo.AvatarURL != "" {
			return &bestInfo, nil
		}
	}

	// 4. Browse FEmusic_home / FEaccount (contains topbarMenuButtonRenderer.avatar and account headers)
	for _, target := range []struct {
		browseID string
		cfg      ClientConfig
	}{
		{"FEmusic_home", ConfigWebRemix},
		{"FEaccount", ConfigWeb},
		{"FEaccount", ConfigTVHTML5},
	} {
		body := map[string]interface{}{
			"context":  c.buildContext(target.cfg),
			"browseId": target.browseID,
		}

		respBytes, err := c.post(ctx, "browse", body, target.cfg)
		if err != nil {
			continue
		}

		var root map[string]interface{}
		if err := json.Unmarshal(respBytes, &root); err != nil {
			continue
		}

		info := parseAccountInfoFromJSON(root)
		mergeInfo(info)
		if bestInfo.Name != "" && bestInfo.AvatarURL != "" {
			return &bestInfo, nil
		}
	}

	return &bestInfo, nil
}

// parseAccountInfoFromJSON recursively extracts account header details from InnerTube response JSON.
func parseAccountInfoFromJSON(data interface{}) *AccountInfo {
	info := &AccountInfo{}
	findAccountDetails(data, info)
	if info.Name != "" || info.AvatarURL != "" || info.PageID != "" || info.DataSyncID != "" {
		return info
	}
	return nil
}

func isGenericAccountLabel(s string) bool {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "", "sign in", "youtube", "youtube music", "switch account", "you", "connected user", "youtube user":
		return true
	default:
		return false
	}
}

func extractAccountText(obj interface{}) string {
	m, ok := obj.(map[string]interface{})
	if !ok {
		return ""
	}
	if s, ok := m["simpleText"].(string); ok && !isGenericAccountLabel(s) {
		return strings.TrimSpace(s)
	}
	if runs, ok := m["runs"].([]interface{}); ok && len(runs) > 0 {
		var sb strings.Builder
		for _, r := range runs {
			if rm, ok := r.(map[string]interface{}); ok {
				if t, ok := rm["text"].(string); ok {
					sb.WriteString(t)
				}
			}
		}
		res := strings.TrimSpace(sb.String())
		if !isGenericAccountLabel(res) {
			return res
		}
	}
	return ""
}

func findAccountDetails(data interface{}, info *AccountInfo) {
	switch v := data.(type) {
	case map[string]interface{}:
		// 1. activeAccountHeaderRenderer / googleAccountHeaderRenderer
		for _, hdrKey := range []string{"activeAccountHeaderRenderer", "googleAccountHeaderRenderer"} {
			if header, ok := v[hdrKey].(map[string]interface{}); ok {
				if name := extractAccountText(header["accountName"]); name != "" {
					info.Name = name
				} else if name := extractAccountText(header["name"]); name != "" && info.Name == "" {
					info.Name = name
				}
				if handle := extractAccountText(header["channelHandle"]); handle != "" && info.Handle == "" {
					info.Handle = handle
				}
				for _, photoKey := range []string{"accountPhoto", "avatar"} {
					if photoObj, ok := header[photoKey].(map[string]interface{}); ok {
						if thumbs, ok := photoObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
							lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
							if rawURL, ok := lastThumb["url"].(string); ok && rawURL != "" {
								info.AvatarURL = upgradeAvatarResolution(rawURL)
							}
						}
					}
				}
			}
		}

		// 1.1 topbarMenuButtonRenderer (YouTube Music home topbar avatar)
		if topbarBtn, ok := v["topbarMenuButtonRenderer"].(map[string]interface{}); ok {
			if avatarObj, ok := topbarBtn["avatar"].(map[string]interface{}); ok {
				if thumbs, ok := avatarObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
					lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
					if rawURL, ok := lastThumb["url"].(string); ok && info.AvatarURL == "" {
						info.AvatarURL = upgradeAvatarResolution(rawURL)
					}
				}
			}
		}

		// 1.2 FEmusic_home first carousel header strapline + thumbnail (SimpMusic HomeRepositoryImpl pattern)
		if hdr, ok := v["musicCarouselShelfBasicHeaderRenderer"].(map[string]interface{}); ok {
			if strap, ok := hdr["strapline"]; ok {
				if name := extractAccountText(strap); name != "" && info.Name == "" {
					info.Name = name
				}
			}
			if thumbObj, ok := hdr["thumbnail"].(map[string]interface{}); ok {
				if mtr, ok := thumbObj["musicThumbnailRenderer"].(map[string]interface{}); ok {
					if innerThumb, ok := mtr["thumbnail"].(map[string]interface{}); ok {
						if thumbs, ok := innerThumb["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
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
		}

		// 2. guideAccountRenderer / accountItemRenderer / accountItem
		if acc, ok := v["guideAccountRenderer"].(map[string]interface{}); ok {
			if name := extractAccountText(acc["formattedTitle"]); name != "" && info.Name == "" {
				info.Name = name
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

		for _, itemKey := range []string{"accountItemRenderer", "accountItem"} {
			if accItem, ok := v[itemKey].(map[string]interface{}); ok {
				isSel, hasSel := accItem["isSelected"].(bool)
				if !hasSel || isSel {
					if name := extractAccountText(accItem["accountName"]); name != "" && (isSel || info.Name == "") {
						info.Name = name
					}
					if handle := extractAccountText(accItem["channelHandle"]); handle != "" && (isSel || info.Handle == "") {
						info.Handle = handle
					}
					if onBehalf, ok := accItem["onBehalfOfParameter"].(string); ok && strings.TrimSpace(onBehalf) != "" && (isSel || info.PageID == "") {
						info.PageID = strings.TrimSpace(onBehalf)
					}
					if se, ok := accItem["serviceEndpoint"].(map[string]interface{}); ok {
						if saie, ok := se["selectActiveIdentityEndpoint"].(map[string]interface{}); ok {
							if tokens, ok := saie["supportedTokens"].([]interface{}); ok {
								for _, tok := range tokens {
									tm, _ := tok.(map[string]interface{})
									if pit, ok := tm["pageIdToken"].(map[string]interface{}); ok {
										if pid, ok := pit["pageId"].(string); ok && strings.TrimSpace(pid) != "" && (isSel || info.PageID == "") {
											info.PageID = strings.TrimSpace(pid)
										}
									}
									if dst, ok := tm["datasyncIdToken"].(map[string]interface{}); ok {
										if dsid, ok := dst["datasyncIdToken"].(string); ok && strings.TrimSpace(dsid) != "" && (isSel || info.DataSyncID == "") {
											info.DataSyncID = strings.TrimSpace(dsid)
										}
									}
								}
							}
						}
					}
					if photoObj, ok := accItem["accountPhoto"].(map[string]interface{}); ok {
						if thumbs, ok := photoObj["thumbnails"].([]interface{}); ok && len(thumbs) > 0 {
							lastThumb, _ := thumbs[len(thumbs)-1].(map[string]interface{})
							if rawURL, ok := lastThumb["url"].(string); ok && (isSel || info.AvatarURL == "") {
								info.AvatarURL = upgradeAvatarResolution(rawURL)
							}
						}
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

