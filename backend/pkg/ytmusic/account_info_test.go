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

func TestParseAccountInfoWithRuns(t *testing.T) {
	rawJSON := `{
		"actions": [
			{
				"openPopupAction": {
					"popup": {
						"multiPageMenuRenderer": {
							"header": {
								"activeAccountHeaderRenderer": {
									"accountName": {
										"runs": [
											{ "text": "Computer Practicals" }
										]
									},
									"channelHandle": {
										"runs": [
											{ "text": "@computerpracticals" }
										]
									},
									"accountPhoto": {
										"thumbnails": [
											{
												"url": "https://lh3.googleusercontent.com/a/ACg8ocL=s96-c"
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
	if info.Name != "Computer Practicals" {
		t.Errorf("expected name 'Computer Practicals', got %q", info.Name)
	}
	if info.Handle != "@computerpracticals" {
		t.Errorf("expected handle '@computerpracticals', got %q", info.Handle)
	}
	if info.AvatarURL != "https://lh3.googleusercontent.com/a/ACg8ocL=s96-c" {
		t.Errorf("expected avatar url, got %q", info.AvatarURL)
	}
}

func TestParseAccountSwitcherEndpointJSON(t *testing.T) {
	rawJSON := `{
		"code": "SUCCESS",
		"data": {
			"contents": [
				{
					"accountSectionListRenderer": {
						"contents": [
							{
								"accountItemSectionRenderer": {
									"contents": [
										{
											"accountItem": {
												"accountName": {
													"simpleText": "Computer Practicals"
												},
												"channelHandle": {
													"simpleText": "@computerpracticals"
												},
												"accountPhoto": {
													"thumbnails": [
														{
															"url": "https://lh3.googleusercontent.com/a/ACg8ocL=s96-c"
														}
													]
												}
											}
										}
									]
								}
							}
						]
					}
				}
			]
		}
	}`

	var root map[string]interface{}
	if err := json.Unmarshal([]byte(rawJSON), &root); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}

	info := parseAccountInfoFromJSON(root)
	if info == nil {
		t.Fatalf("expected non-nil AccountInfo")
	}
	if info.Name != "Computer Practicals" {
		t.Errorf("expected name 'Computer Practicals', got %q", info.Name)
	}
	if info.Handle != "@computerpracticals" {
		t.Errorf("expected handle '@computerpracticals', got %q", info.Handle)
	}
	if info.AvatarURL != "https://lh3.googleusercontent.com/a/ACg8ocL=s96-c" {
		t.Errorf("expected avatar url, got %q", info.AvatarURL)
	}
}


func TestParseAccountSwitcherExtractsPageIDAndDataSyncID(t *testing.T) {
	rawJSON := `{
		"code": "SUCCESS",
		"data": {
			"contents": [
				{
					"accountSectionListRenderer": {
						"contents": [
							{
								"accountItemSectionRenderer": {
									"contents": [
										{
											"accountItem": {
												"isSelected": true,
												"onBehalfOfParameter": "104556677889900",
												"accountName": { "simpleText": "Studio Profile" },
												"channelHandle": { "simpleText": "@studioprofile" },
												"serviceEndpoint": {
													"selectActiveIdentityEndpoint": {
														"supportedTokens": [
															{ "pageIdToken": { "pageId": "104556677889900" } },
															{ "datasyncIdToken": { "datasyncIdToken": "sync_tok_abc123||" } }
														]
													}
												},
												"accountPhoto": {
													"thumbnails": [
														{ "url": "https://yt3.ggpht.com/a/studio=s88-c" }
													]
												}
											}
										}
									]
								}
							}
						]
					}
				}
			]
		}
	}`

	var root map[string]interface{}
	if err := json.Unmarshal([]byte(rawJSON), &root); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}

	info := parseAccountInfoFromJSON(root)
	if info == nil {
		t.Fatalf("expected non-nil AccountInfo")
	}
	if info.PageID != "104556677889900" {
		t.Errorf("expected PageID '104556677889900', got %q", info.PageID)
	}
	if info.DataSyncID != "sync_tok_abc123||" {
		t.Errorf("expected DataSyncID 'sync_tok_abc123||', got %q", info.DataSyncID)
	}
	if info.Name != "Studio Profile" {
		t.Errorf("expected Name 'Studio Profile', got %q", info.Name)
	}
}
