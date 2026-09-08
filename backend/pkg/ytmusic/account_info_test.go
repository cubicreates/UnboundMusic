package ytmusic

import (
	"encoding/json"
	"testing"
)

func TestParseAccountInfoFromJSON(t *testing.T) {
	rawJSON := `{
		"actions": [
			{
				"openPopupAction": {
					"popup": {
						"multiPageMenuRenderer": {
							"header": {
								"activeAccountHeaderRenderer": {
									"accountName": {
										"simpleText": "Alex Mercer"
									},
									"channelHandle": {
										"simpleText": "@alexmercer"
									},
									"accountPhoto": {
										"thumbnails": [
											{
												"url": "https://yt3.ggpht.com/ytc/testavatar=s88-c-k-c0x00ffffff-no-rj"
											},
											{
												"url": "https://yt3.ggpht.com/ytc/testavatar=w120-h120"
											}
										]
									}
								}
							}
						}
					}
				}
			}
		]
	}`

	var root map[string]interface{}
	if err := json.Unmarshal([]byte(rawJSON), &root); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}

	info := parseAccountInfoFromJSON(root)
	if info == nil {
		t.Fatalf("expected non-nil AccountInfo")
	}
	if info.Name != "Alex Mercer" {
		t.Errorf("expected name 'Alex Mercer', got %q", info.Name)
	}
	if info.Handle != "@alexmercer" {
		t.Errorf("expected handle '@alexmercer', got %q", info.Handle)
	}
	if info.AvatarURL != "https://yt3.ggpht.com/ytc/testavatar=w240-h240" {
		t.Errorf("expected high-res avatar url, got %q", info.AvatarURL)
	}
}
