package ytmusic

import (
	"context"
	"testing"

	"github.com/cubicreates/unbound-engine/pkg/models"
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

func TestFilterStrictMusicTracksExcludesPodcasts(t *testing.T) {
	input := []models.Track{
		{ID: "song1", Title: "Midnight City", Artist: "M83", ItemType: "song", DurationMs: 243000},
		{ID: "MPSP_show1", Title: "Tech Talk", Artist: "Host", ItemType: "podcast", DurationMs: 1800000},
		{ID: "pod2", Title: "Full Episode #42 - AI", Artist: "Podcast Network", ItemType: "song", DurationMs: 3600000},
		{ID: "book1", Title: "Deep Work Audiobook", Artist: "Author", ItemType: "song", DurationMs: 2500000},
		{ID: "MPREb_alb1", Title: "Hurry Up, We're Dreaming", Artist: "M83", ItemType: "album", BrowseID: "MPREb_alb1"},
	}

	allMusic := filterStrictMusicTracks(input, "all")
	if len(allMusic) != 2 {
		t.Fatalf("expected 2 music items (song + album), got %d: %+v", len(allMusic), allMusic)
	}
	if allMusic[0].ID != "song1" || allMusic[1].ID != "MPREb_alb1" {
		t.Errorf("unexpected filtered music items: %+v", allMusic)
	}

	songsOnly := filterStrictMusicTracks(input, "songs")
	if len(songsOnly) != 1 || songsOnly[0].ID != "song1" {
		t.Errorf("expected only song1 for subtype 'songs', got %+v", songsOnly)
	}

	albumsOnly := filterStrictMusicTracks(input, "albums")
	if len(albumsOnly) != 1 || albumsOnly[0].ID != "MPREb_alb1" {
		t.Errorf("expected only MPREb_alb1 for subtype 'albums', got %+v", albumsOnly)
	}
}
