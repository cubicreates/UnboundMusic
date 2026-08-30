/*
 * Package: main
 * File: main.go
 * Purpose: Standalone CLI testing tool to verify YouTube Music search, stream extraction, Genius lyrics scraping, storage ingestion, on-device forced alignment, and SQLite persistence.
 * Subsystem: Testing & Tooling
 * Concurrency: Single-threaded CLI entry point.
 */

package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/aligner"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/fingerprint"
	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func main() {
	searchQuery := flag.String("search", "", "Query string to search on YouTube Music")
	streamID := flag.String("stream", "", "YouTube Video ID to extract audio stream for")
	lyricsQuery := flag.String("lyrics", "", "Song title to scrape Genius lyrics for")
	artistQuery := flag.String("artist", "", "Optional artist filter for lyrics search")
	scanDir := flag.String("scan", "", "Directory path to scan and inspect for local audio files")
	alignQuery := flag.String("align", "", "Song title to test on-device forced CTC lyrics alignment")
	flag.Parse()

	if *searchQuery == "" && *streamID == "" && *lyricsQuery == "" && *scanDir == "" && *alignQuery == "" {
		fmt.Println("Usage:")
		fmt.Println("  tester -search <query>")
		fmt.Println("  tester -stream <video_id>")
		fmt.Println("  tester -lyrics <title> [-artist <artist>]")
		fmt.Println("  tester -scan <directory_path>")
		fmt.Println("  tester -align <title> [-artist <artist>]")
		os.Exit(1)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	// Initialize SQLite Database in temp directory for testing
	dbPath := filepath.Join(os.TempDir(), "unbound_music_test.db")
	db, err := database.Open(dbPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warning: SQLite init failed: %v\n", err)
	} else {
		defer db.Close()
	}

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

			// Persist search tracks into SQLite memory bank
			if db != nil {
				repo := database.NewRepository(db)
				_ = repo.SaveTrack(ctx, &t)
			}
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
		fmt.Printf("\n[UNBOUND ENGINE] Resolving Lyrics for: %q (Artist: %q)\n", *lyricsQuery, *artistQuery)
		start := time.Now()

		hit, err := geniusClient.SearchSong(ctx, *lyricsQuery, *artistQuery)
		if err == nil && hit != nil {
			payload, err := geniusClient.FetchLyrics(ctx, hit)
			if err == nil && len(payload.Lines) > 0 {
				elapsed := time.Since(start)
				fmt.Printf("[GENIUS] Verified Uncensored Lyrics Scraped in %v (%d lines) [Source: %s]:\n", elapsed, len(payload.Lines), hit.URL)
				for i, line := range payload.Lines {
					if i >= 16 {
						fmt.Printf("  ... and %d more uncensored lines\n", len(payload.Lines)-16)
						break
					}
					fmt.Printf("  %s\n", line.Text)
				}

				if db != nil {
					repo := database.NewRepository(db)
					_ = repo.SaveLyrics(ctx, payload)
				}
				return
			}
		}

		fmt.Printf("[GENIUS] Not found or error: %v. Falling back to LRCLIB...\n", err)
		lrclibPayload, lrcErr := geniusClient.FetchLRCLIBSynced(ctx, *lyricsQuery, *artistQuery, 0)
		elapsed := time.Since(start)

		if lrcErr != nil || len(lrclibPayload.Lines) == 0 {
			fmt.Fprintf(os.Stderr, "Failed to resolve lyrics from Genius and LRCLIB fallback: %v\n", lrcErr)
			os.Exit(1)
		}

		fmt.Printf("[LRCLIB] Fallback Synced Lyrics Resolved in %v (%d lines):\n", elapsed, len(lrclibPayload.Lines))
		for i, line := range lrclibPayload.Lines {
			if i >= 12 {
				fmt.Printf("  ... and %d more synced lines\n", len(lrclibPayload.Lines)-12)
				break
			}
			fmt.Printf("  [%02d:%02d.%03d] %s\n", line.StartMs/60000, (line.StartMs%60000)/1000, line.StartMs%1000, line.Text)
		}

		if db != nil {
			repo := database.NewRepository(db)
			_ = repo.SaveLyrics(ctx, lrclibPayload)
		}
	}

	if *alignQuery != "" {
		fmt.Printf("\n[UNBOUND ENGINE] Running On-Device Forced Aligner for: %q (Artist: %q)\n", *alignQuery, *artistQuery)
		start := time.Now()

		geniusClient := genius.NewClient()
		hit, err := geniusClient.SearchSong(ctx, *alignQuery, *artistQuery)
		if err != nil || hit == nil {
			fmt.Fprintf(os.Stderr, "Genius song lookup failed: %v\n", err)
			os.Exit(1)
		}

		plainPayload, err := geniusClient.FetchLyrics(ctx, hit)
		if err != nil || len(plainPayload.Lines) == 0 {
			fmt.Fprintf(os.Stderr, "Genius lyrics scrape failed: %v\n", err)
			os.Exit(1)
		}

		// Run On-Device Forced Aligner
		forcdAligner := aligner.NewForcedAligner()
		alignedPayload, err := forcdAligner.AlignLyrics(fmt.Sprintf("align:%d", hit.ID), hit.Title, hit.Artist, plainPayload.PlainLyrics, 186000)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Forced alignment failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("On-Device Forced Alignment Completed in %v (%d lines timed):\n", elapsed, len(alignedPayload.Lines))
		for i, line := range alignedPayload.Lines {
			if i >= 10 {
				fmt.Printf("  ... and %d more aligned kinetic lines\n", len(alignedPayload.Lines)-10)
				break
			}
			fmt.Printf("  [%02d:%02d.%03d -> %02d:%02d.%03d] %-35s (Syllables: %d)\n",
				line.StartMs/60000, (line.StartMs%60000)/1000, line.StartMs%1000,
				line.EndMs/60000, (line.EndMs%60000)/1000, line.EndMs%1000,
				truncate(line.Text, 35), len(line.Syllables),
			)
		}

		// Persist aligned lyrics to SQLite database
		if db != nil {
			repo := database.NewRepository(db)
			if err := repo.SaveLyrics(ctx, alignedPayload); err == nil {
				fmt.Println("[SQLITE] Aligned lyrics successfully cached to permanent SQLite vault.")
			}
		}
	}

	if *scanDir != "" {
		fmt.Printf("\n[UNBOUND ENGINE] Scanning Storage Directory: %s\n", *scanDir)
		start := time.Now()
		summary, err := fingerprint.ScanDirectory(ctx, *scanDir, 8)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Directory scan failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Directory Scan Completed in %v:\n", elapsed)
		fmt.Printf("  Total Audio Files Found: %d\n", summary.TotalFilesScanned)
		fmt.Printf("  Classified as Music:     %d\n", summary.MusicFilesCount)
		fmt.Printf("  Classified as Voice/SFX: %d\n", summary.NoiseFilesCount)

		for i, track := range summary.AudioTracks {
			if i >= 15 {
				fmt.Printf("  ... and %d more files\n", len(summary.AudioTracks)-15)
				break
			}
			class := fingerprint.ClassifyAudio(track)
			status := "MUSIC"
			if !class.IsMusic {
				status = "VOICE/NOISE"
			}
			isChat := fingerprint.IsProtectedChatMedia(track.FilePath)
			rule := "MOVE"
			if isChat {
				rule = "COPY"
			}

			fmt.Printf("  [%d] [%-11s | Rule: %-4s | Hash: %s] %s (%.1fs)\n",
				i+1, status, rule, track.AcousticHash, track.FilePath, float64(track.DurationMs)/1000)

			// Persist fingerprint to database
			if db != nil && class.IsMusic {
				repo := database.NewRepository(db)
				_ = repo.SaveFingerprint(ctx, track.AcousticHash, track.FilePath, track.DurationMs)
			}
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
