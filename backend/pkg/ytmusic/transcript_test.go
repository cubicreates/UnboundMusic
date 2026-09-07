package ytmusic

import (
	"testing"
)

func TestParseTranscriptResponse(t *testing.T) {
	sampleJSON := []byte(`{
		"actions": [{
			"updateEngagementPanelAction": {
				"content": {
					"transcriptRenderer": {
						"content": {
							"transcriptSearchPanelRenderer": {
								"body": {
									"transcriptSegmentListRenderer": {
										"initialSegments": [
											{
												"transcriptSegmentRenderer": {
													"startMs": "1200",
													"endMs": "3400",
													"snippet": {
														"runs": [{"text": "First line of lyrics"}]
													}
												}
											},
											{
												"transcriptSegmentRenderer": {
													"startMs": "3500",
													"endMs": "6000",
													"snippet": {
														"runs": [{"text": "Second line of lyrics"}]
													}
												}
											}
										]
									}
								}
							}
						}
					}
				}
			}
		}]
	}`)

	lines, err := parseTranscriptJSON(sampleJSON)
	if err != nil {
		t.Fatalf("unexpected error parsing transcript: %v", err)
	}
	if len(lines) != 2 {
		t.Fatalf("expected 2 lyric lines, got %d", len(lines))
	}
	if lines[0].StartMs != 1200 || lines[0].Text != "First line of lyrics" {
		t.Errorf("unexpected line 0: %+v", lines[0])
	}
	if lines[1].StartMs != 3500 || lines[1].Text != "Second line of lyrics" {
		t.Errorf("unexpected line 1: %+v", lines[1])
	}
}
