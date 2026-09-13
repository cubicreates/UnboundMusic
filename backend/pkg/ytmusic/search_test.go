package ytmusic

import (
	"context"
	"testing"
)

func TestSearchCascadeStructure(t *testing.T) {
	client := NewClient()

	// Empty query returns clean empty result or error
	_, err := client.SearchCascade(context.Background(), "")
	if err == nil {
		t.Fatalf("expected error for empty search query")
	}

	// Constants check
	if FilterSong == "" || FilterPodcast == "" || FilterAlbum == "" || FilterArtist == "" {
		t.Errorf("expected filter constants to be defined")
	}
}

func TestParseSearchResponse_AlbumsAndPlaylists(t *testing.T) {
	mockJSON := `{
		"contents": {
			"tabbedSearchResultsRenderer": {
				"tabs": [{
					"tabRenderer": {
						"content": {
							"sectionListRenderer": {
								"contents": [{
									"musicShelfRenderer": {
										"contents": [
											{
												"musicResponsiveListItemRenderer": {
													"playlistItemData": { "videoId": "song_vid_123" },
													"flexColumns": [
														{
															"musicResponsiveListItemFlexColumnRenderer": {
																"text": { "runs": [{ "text": "Blinding Lights" }] }
															}
														},
														{
															"musicResponsiveListItemFlexColumnRenderer": {
																"text": { "runs": [
																	{ "text": "The Weeknd" },
																	{ "text": " • " },
																	{ "text": "3:20" }
																]}
															}
														}
													]
												}
											},
											{
												"musicResponsiveListItemRenderer": {
													"navigationEndpoint": {
														"browseEndpoint": {
															"browseId": "MPREb_album_xyz789",
															"browseEndpointContextSupportedConfigs": {
																"browseEndpointContextMusicConfig": {
																	"pageType": "MUSIC_PAGE_TYPE_ALBUM"
																}
															}
														}
													},
													"flexColumns": [
														{
															"musicResponsiveListItemFlexColumnRenderer": {
																"text": { "runs": [{ "text": "After Hours" }] }
															}
														},
														{
															"musicResponsiveListItemFlexColumnRenderer": {
																"text": { "runs": [
																	{ "text": "Album" },
																	{ "text": " • " },
																	{ "text": "The Weeknd" },
																	{ "text": " • " },
																	{ "text": "2020" }
																]}
															}
														}
													]
												}
											}
										]
									}
								}]
							}
						}
					}
				}]
			}
		}
	}`

	tracks, err := parseSearchResponse([]byte(mockJSON))
	if err != nil {
		t.Fatalf("failed to parse search response: %v", err)
	}

	if len(tracks) != 2 {
		t.Fatalf("expected 2 items, got %d", len(tracks))
	}

	// First item: Song
	song := tracks[0]
	if song.ID != "song_vid_123" || song.Title != "Blinding Lights" || song.ItemType != "song" {
		t.Errorf("expected song item, got %+v", song)
	}

	// Second item: Album
	album := tracks[1]
	if album.ItemType != "album" {
		t.Errorf("expected album ItemType, got %q", album.ItemType)
	}
	if album.BrowseID != "MPREb_album_xyz789" || album.ID != "MPREb_album_xyz789" {
		t.Errorf("expected album ID MPREb_album_xyz789, got id=%q browseId=%q", album.ID, album.BrowseID)
	}
	if album.Title != "After Hours" || album.Artist != "The Weeknd" || album.Year != "2020" {
		t.Errorf("unexpected album metadata: %+v", album)
	}
}
