/*
 * Package: main
 * File: main.go
 * Purpose: Standalone CLI testing tool to verify YouTube Music search and audio stream extraction in pure Go.
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

	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func main() {
	searchQuery := flag.String("search", "", "Query string to search on YouTube Music")
	streamID := flag.String("stream", "", "YouTube Video ID to extract audio stream for")
	flag.Parse()

	if *searchQuery == "" && *streamID == "" {
		fmt.Println("Usage: tester -search <query> OR tester -stream <video_id>")
		os.Exit(1)
	}

	client := ytmusic.NewClient()
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	if *searchQuery != "" {
		fmt.Printf("[UNBOUND ENGINE] Searching YouTube Music for: %q\n", *searchQuery)
		start := time.Now()
		tracks, err := client.Search(ctx, *searchQuery)
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
		fmt.Printf("\n[UNBOUND ENGINE] Resolving Audio Stream for Video ID: %s\n", *streamID)
		start := time.Now()
		info, err := client.GetStreamInfo(ctx, *streamID)
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
}

// truncate shortens a string with ellipsis if it exceeds max length.
func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max-3] + "..."
}
