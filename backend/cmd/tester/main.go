/*
 * Package: main
 * File: main.go
 * Purpose: Standalone CLI testing tool to verify YouTube Music search, stream extraction, and Genius lyrics scraping in pure Go.
 * Subsystem: Testing & Tooling
 * Concurrency: Single-threaded CLI entry point.
 */

package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func main() {
	searchQuery := flag.String("search", "", "Query string to search on YouTube Music")
	streamID := flag.String("stream", "", "YouTube Video ID to extract audio stream for")
	lyricsQuery := flag.String("lyrics", "", "Song title to scrape Genius and LRCLIB lyrics for")
	artistQuery := flag.String("artist", "", "Optional artist filter for lyrics search")
	flag.Parse()

	if *searchQuery == "" && *streamID == "" && *lyricsQuery == "" {
		fmt.Println("Usage:")
		fmt.Println("  tester -search <query>")
		fmt.Println("  tester -stream <video_id>")
		fmt.Println("  tester -lyrics <title> [-artist <artist>]")
		os.Exit(1)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	if *searchQuery != "" {
		ytClient := ytmusic.NewClient()
		fmt.Printf("[UNBOUND ENGINE] Searching YouTube Music for: %q\n", *searchQuery)
		start := time.Now()
		tracks, err := ytClient.Search(ctx, *searchQuery)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Search failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Found %d tracks in %v:\n", len(tracks), elapsed)
		for i, t := range tracks {
			if i >= 10 {
				break
			}
			fmt.Printf("  [%d] ID: %-11s | Title: %-30s | Artist: %-20s | Duration: %ds\n",
				i+1, t.ID, truncate(t.Title, 30), truncate(t.Artist, 20), t.DurationMs/1000)
		}
	}

	if *streamID != "" {
		ytClient := ytmusic.NewClient()
		fmt.Printf("\n[UNBOUND ENGINE] Resolving Pure Audio Stream for Video ID: %s\n", *streamID)
		start := time.Now()
		info, err := ytClient.GetStreamInfo(ctx, *streamID)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Stream resolution failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Stream Resolved in %v:\n", elapsed)
		fmt.Printf("  Codec:       %s\n", info.Codec)
		fmt.Printf("  Bitrate:     %d kbps\n", info.BitrateKbps)
		fmt.Printf("  Sample Rate: %d Hz\n", info.SampleRate)
		fmt.Printf("  Channels:    %d\n", info.AudioChannels)
		fmt.Printf("  Size:        %.2f MB\n", float64(info.ContentLength)/(1024*1024))
		fmt.Printf("  Stream URL:  %s\n", truncate(info.StreamURL, 80))
	}

	if *lyricsQuery != "" {
		geniusClient := genius.NewClient()
		fmt.Printf("\n[UNBOUND ENGINE] Searching Genius Lyrics for: %q (Artist: %q)\n", *lyricsQuery, *artistQuery)
		start := time.Now()

		// Step 1: Try LRCLIB first for pre-synced timestamps
		lrclibPayload, err := geniusClient.FetchLRCLIBSynced(ctx, *lyricsQuery, *artistQuery, 0)
		if err == nil && len(lrclibPayload.Lines) > 0 {
			elapsed := time.Since(start)
			fmt.Printf("LRCLIB Synced Lyrics Resolved in %v (%d lines):\n", elapsed, len(lrclibPayload.Lines))
			for i, line := range lrclibPayload.Lines {
				if i >= 8 {
					fmt.Printf("  ... and %d more synced lines\n", len(lrclibPayload.Lines)-8)
					break
				}
				fmt.Printf("  [%02d:%02d.%03d] %s\n", line.StartMs/60000, (line.StartMs%60000)/1000, line.StartMs%1000, line.Text)
			}
			return
		}

		// Step 2: Fallback to scraping verified lyrics from Genius HTML
		hit, err := geniusClient.SearchSong(ctx, *lyricsQuery, *artistQuery)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Genius search failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Found Genius Song Page: %s by %s\n", hit.Title, hit.Artist)
		payload, err := geniusClient.FetchLyrics(ctx, hit)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Genius lyrics scrape failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Genius Verified Lyrics Scraped in %v (%d lines):\n", elapsed, len(payload.Lines))
		for i, line := range payload.Lines {
			if i >= 12 {
				fmt.Printf("  ... and %d more lines\n", len(payload.Lines)-12)
				break
			}
			fmt.Printf("  %s\n", line.Text)
		}
	}
}

// truncate shortens a string with ellipsis if it exceeds max length.
func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max-3] + "..."
}
