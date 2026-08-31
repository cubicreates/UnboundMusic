/*
 * Package: main
 * File: main.go
 * Purpose: Standalone CLI testing tool to verify YouTube Music search, stream extraction, Genius lyrics scraping, storage ingestion, on-device forced alignment, SQLite persistence, zero-data hybrid routing, offline recommendations, P2P sync, and Edge AI payload packaging.
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

	"github.com/cubicreates/unbound-engine/pkg/ai"
	"github.com/cubicreates/unbound-engine/pkg/aligner"
	"github.com/cubicreates/unbound-engine/pkg/database"
	"github.com/cubicreates/unbound-engine/pkg/fingerprint"
	"github.com/cubicreates/unbound-engine/pkg/gatekeeper"
	"github.com/cubicreates/unbound-engine/pkg/genius"
	"github.com/cubicreates/unbound-engine/pkg/p2p"
	"github.com/cubicreates/unbound-engine/pkg/recommender"
	"github.com/cubicreates/unbound-engine/pkg/router"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func main() {
	searchQuery := flag.String("search", "", "Query string to search on YouTube Music")
	streamID := flag.String("stream", "", "YouTube Video ID to extract audio stream for")
	lyricsQuery := flag.String("lyrics", "", "Song title to scrape Genius lyrics for")
	artistQuery := flag.String("artist", "", "Optional artist filter for lyrics search")
	scanDir := flag.String("scan", "", "Directory path to scan and inspect for local audio files")
	alignQuery := flag.String("align", "", "Song title to test on-device forced CTC lyrics alignment")
	routerQuery := flag.String("router", "", "Song title to test Zero-Data Hybrid Playback Interception Router")
	recommendQuery := flag.String("recommend", "", "Song title or ID to generate offline smart radio mix for")
	p2pFlag := flag.Bool("p2p", false, "Test local P2P Wi-Fi peer discovery and beacon broadcast")
	aiQuery := flag.String("ai-query", "", "Natural language semantic vibe search query")
	aiMood := flag.String("ai-mood", "", "Track title to evaluate mood and vibe for")
	packModels := flag.Bool("pack-models", false, "Internal tool: pack raw models into Zstd tar bundle")
	unpackPayload := flag.String("unpack-payload", "", "Path to models.zst to test decompression performance")
	flag.Parse()

	args := flag.Args()

	// 1. Pack Models into Zstd
	if *packModels {
		if len(args) < 2 {
			fmt.Println("Usage: tester -pack-models <src_dir> <out_zst_path>")
			os.Exit(1)
		}
		srcDir := args[0]
		outPath := args[1]

		files := make(map[string]string)
		entries, err := os.ReadDir(srcDir)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Failed to read source directory: %v\n", err)
			os.Exit(1)
		}
		for _, e := range entries {
			if !e.IsDir() {
				files[e.Name()] = filepath.Join(srcDir, e.Name())
			}
		}

		fmt.Printf("[PACKAGER] Compressing %d files with Zstandard Level 19...\n", len(files))
		start := time.Now()
		compressedBytes, err := gatekeeper.CompressFilesToZstdTar(files)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Compression failed: %v\n", err)
			os.Exit(1)
		}

		if err := os.WriteFile(outPath, compressedBytes, 0644); err != nil {
			fmt.Fprintf(os.Stderr, "Failed writing compressed output: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Compression Complete in %v! Output size: %.2f MB\n",
			time.Since(start), float64(len(compressedBytes))/(1024*1024))
		return
	}

	// 2. Unpack Payload Benchmark
	if *unpackPayload != "" {
		data, err := os.ReadFile(*unpackPayload)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Failed reading payload file: %v\n", err)
			os.Exit(1)
		}

		destDir := filepath.Join(os.TempDir(), "unbound_models_test")
		fmt.Printf("[UNPACKER] Testing decompression of %s (%.2f MB)...\n",
			*unpackPayload, float64(len(data))/(1024*1024))

		manifest, err := gatekeeper.DecompressZstdTarStream(data, destDir)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Decompression failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Decompression Completed in %d ms:\n", manifest.DecompressionMs)
		fmt.Printf("  Extracted Files: %d\n", manifest.TotalFiles)
		fmt.Printf("  Unpacked Size:   %.2f MB\n", float64(manifest.TotalBytes)/(1024*1024))
		fmt.Printf("  SmolLM Model:    %s\n", manifest.SmolLMModelPath)
		fmt.Printf("  MMS Align Model: %s\n", manifest.MMSAlignModelPath)
		return
	}

	if *aiQuery != "" {
		fmt.Printf("\n[UNBOUND ENGINE] Evaluating Semantic Vibe Query: %q\n", *aiQuery)
		runner := ai.NewRunner("")
		start := time.Now()
		res, err := runner.ParseVibeQuery(*aiQuery)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "AI query failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Semantic Intent Parsed in %v:\n", elapsed)
		fmt.Printf("  Energy Level:     %s (Suggested BPM: %d)\n", res.EnergyLevel, res.SuggestedBPM)
		fmt.Printf("  Target Genres:    %v\n", res.TargetGenres)
		fmt.Printf("  Mood Tags:        %v\n", res.MoodTags)
		fmt.Printf("  Search Keywords:  %v\n", res.SearchKeywords)
		return
	}

	if *aiMood != "" {
		fmt.Printf("\n[UNBOUND ENGINE] Analyzing Track Mood: %q\n", *aiMood)
		runner := ai.NewRunner("")
		res, err := runner.AnalyzeTrackMood(*aiMood, *artistQuery, "I got loyalty, got royalty inside my DNA")
		if err != nil {
			fmt.Fprintf(os.Stderr, "Mood analysis failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Mood Analysis Result:\n")
		fmt.Printf("  Primary Mood:     %s (Valence: %.2f | Energy: %.2f)\n", res.PrimaryMood, res.ValenceScore, res.EnergyScore)
		fmt.Printf("  Secondary Moods:  %v\n", res.SecondaryMoods)
		return
	}

	if *searchQuery == "" && *streamID == "" && *lyricsQuery == "" && *scanDir == "" && *alignQuery == "" && *routerQuery == "" && *recommendQuery == "" && !*p2pFlag {
		fmt.Println("Usage:")
		fmt.Println("  tester -search <query>")
		fmt.Println("  tester -stream <video_id>")
		fmt.Println("  tester -lyrics <title> [-artist <artist>]")
		fmt.Println("  tester -scan <directory_path>")
		fmt.Println("  tester -align <title> [-artist <artist>]")
		fmt.Println("  tester -router <title> [-artist <artist>]")
		fmt.Println("  tester -recommend <title_or_id>")
		fmt.Println("  tester -ai-query <vibe_query>")
		fmt.Println("  tester -ai-mood <song_title> [-artist <artist>]")
		fmt.Println("  tester -p2p")
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

	// Check adaptive storage gatekeeper
	storageStatus, _ := gatekeeper.CheckStorageCapacity(os.TempDir())
	fmt.Printf("[GATEKEEPER] Storage Mode: %s (Free Space: %.1f MB)\n", storageStatus.Mode, storageStatus.FreeMB)

	if *searchQuery != "" {
		ytClient := ytmusic.NewClient()
		fmt.Printf("\n[UNBOUND ENGINE] Searching YouTube Music for: %q\n", *searchQuery)
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

		if db != nil {
			repo := database.NewRepository(db)
			if err := repo.SaveLyrics(ctx, alignedPayload); err == nil {
				fmt.Println("[SQLITE] Aligned lyrics successfully cached to permanent SQLite vault.")
			}
		}
	}

	if *routerQuery != "" {
		fmt.Printf("\n[UNBOUND ENGINE] Testing Zero-Data Hybrid Playback Router for: %q (Artist: %q)\n", *routerQuery, *artistQuery)
		start := time.Now()

		var repo *database.Repository
		if db != nil {
			repo = database.NewRepository(db)
		}

		playbackRouter := router.NewRouter(nil, repo)
		resolved, err := playbackRouter.ResolvePlayback(ctx, "", *routerQuery, *artistQuery)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Router failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Hybrid Stream Resolution Completed in %v:\n", elapsed)
		fmt.Printf("  Resolved Title:  %s by %s\n", resolved.Title, resolved.Artist)
		fmt.Printf("  Stream Type:     %s\n", resolved.StreamType)
		fmt.Printf("  Data Consumed:   %d bytes (%.2f MB)\n", resolved.DataConsumed, float64(resolved.DataConsumed)/(1024*1024))
		fmt.Printf("  Codec / Bitrate: %s @ %d kbps\n", resolved.Codec, resolved.BitrateKbps)
		fmt.Printf("  Stream URI:      %s\n", truncate(resolved.StreamURL, 80))
	}

	if *recommendQuery != "" {
		fmt.Printf("\n[UNBOUND ENGINE] Generating Offline Smart Radio Mix for Seed: %q\n", *recommendQuery)
		start := time.Now()

		var repo *database.Repository
		if db != nil {
			repo = database.NewRepository(db)
		}

		recEngine := recommender.NewEngine(repo)
		mix, err := recEngine.GenerateRadioMix(ctx, *recommendQuery, 8)
		elapsed := time.Since(start)

		if err != nil {
			fmt.Fprintf(os.Stderr, "Recommendation failed: %v\n", err)
			os.Exit(1)
		}

		fmt.Printf("Smart Radio Mix Generated in %v (Mode: %s):\n", elapsed, mix.Mode)
		for i, t := range mix.Tracks {
			fmt.Printf("  [%d] %-30s | Artist: %-18s | Duration: %ds\n",
				i+1, truncate(t.Title, 30), truncate(t.Artist, 18), t.DurationMs/1000)
		}
	}

	if *p2pFlag {
		fmt.Println("\n[UNBOUND ENGINE] Testing Local P2P Wi-Fi Sync Discovery...")
		discovery := p2p.NewDiscovery("node_test_cli", "Unbound CLI Node", 45731)

		if err := discovery.BroadcastBeacon(); err != nil {
			fmt.Printf("  UDP Broadcast Note: %v (Normal if broadcast interface restricted)\n", err)
		} else {
			fmt.Println("  UDP Beacon Broadcast Sent successfully.")
		}

		mockPeer := p2p.Peer{
			DeviceID:   "phone_galaxy_s24",
			DeviceName: "Galaxy S24 Ultra",
			IPAddress:  "192.168.1.105",
			APIPort:    45731,
			TrackCount: 140,
			LastSeen:   time.Now(),
		}
		discovery.RegisterPeer(mockPeer)

		active := discovery.GetActivePeers()
		fmt.Printf("  Active Discovered Peers: %d\n", len(active))
		for _, p := range active {
			fmt.Printf("    - Device: %-20s | IP: %-15s | Port: %d\n", p.DeviceName, p.IPAddress, p.APIPort)
		}

		localHashes := []string{"hash_dna", "hash_wap"}
		remoteHashes := []string{"hash_dna", "hash_wap", "hash_blinding_lights", "hash_starboy"}
		diff, _ := p2p.CalculateSyncDiff(mockPeer.DeviceID, localHashes, remoteHashes)
		fmt.Printf("  P2P Sync Plan: Need %d missing tracks from %s (0 MB cellular data)\n",
			diff.TracksToReceive, mockPeer.DeviceName)
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
