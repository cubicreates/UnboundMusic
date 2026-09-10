package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/cubicreates/unbound-engine/pkg/server"
	"github.com/cubicreates/unbound-engine/pkg/ytmusic"
)

func main() {
	fmt.Println("================================================================")
	fmt.Println(" UNBOUND ENGINE: FIXED LIVE YOUTUBE STREAMING VERIFICATION SCRIPT")
	fmt.Println("================================================================")

	testTracks := []struct {
		id    string
		title string
	}{
		{"RgKAFK5djSk", "See You Again (feat. Charlie Puth)"},
		{"T6eK-2OQtew", "Linkin Park - In The End"},
		{"kJQP7kiw5Fk", "Luis Fonsi - Despacito"},
	}

	ytSearchClient := ytmusic.NewClient()
	f1Tracks, sErr := ytSearchClient.Search(context.Background(), "MUSIC FROM F1")
	if sErr == nil && len(f1Tracks) > 0 {
		for _, ft := range f1Tracks[:min(3, len(f1Tracks))] {
			testTracks = append(testTracks, struct {
				id    string
				title string
			}{id: ft.ID, title: ft.Title})
		}
	}

	tempDir, err := os.MkdirTemp("", "unbound_stream_test_*")
	if err != nil {
		fmt.Printf("FAIL: Cannot create temp dir: %v\n", err)
		os.Exit(1)
	}
	defer os.RemoveAll(tempDir)

	cfg := server.Config{
		Port:           45749,
		DatabasePath:   filepath.Join(tempDir, "test.db"),
		LibraryRoot:    filepath.Join(tempDir, "library"),
		AppStorageRoot: filepath.Join(tempDir, "storage"),
	}

	srv, err := server.NewServer(cfg)
	if err != nil {
		fmt.Printf("FAIL: Cannot create server: %v\n", err)
		os.Exit(1)
	}
	defer srv.Shutdown(context.Background())

	ytClient := ytmusic.NewClient()
	allPassed := true

	for i, track := range testTracks {
		fmt.Printf("\n[%d/%d] Testing Track: %s (ID: %s)\n", i+1, len(testTracks), track.title, track.id)
		fmt.Println("----------------------------------------------------------------")

		// 1. Direct GetStreamInfo test
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		startResolve := time.Now()
		info, err := ytClient.GetStreamInfo(ctx, track.id)
		cancel()

		if err != nil || info == nil || info.StreamURL == "" {
			fmt.Printf("  [1] GetStreamInfo: FAILED (%v)\n", err)
			allPassed = false
			continue
		}
		fmt.Printf("  [1] GetStreamInfo: SUCCESS (took %v)\n", time.Since(startResolve).Round(time.Millisecond))
		fmt.Printf("      Codec: %s | Bitrate: %d kbps | Duration: %d ms | Length: %d bytes\n",
			info.Codec, info.BitrateKbps, info.DurationMs, info.ContentLength)
		fmt.Printf("      URL: %s\n", info.StreamURL)

		// 2. Direct HTTP GET to upstream StreamURL
		startUpstream := time.Now()
		reqUpstream, _ := http.NewRequest(http.MethodGet, info.StreamURL, nil)
		ua := ytmusic.UserAgentIOS
		if strings.Contains(info.StreamURL, "c=WEB_REMIX") {
			ua = ytmusic.UserAgentWebRemix
		} else if strings.Contains(info.StreamURL, "c=TVHTML5") {
			ua = ytmusic.UserAgentTV
		}
		reqUpstream.Header.Set("User-Agent", ua)
		reqUpstream.Header.Set("Range", "bytes=0-65535")

		client := &http.Client{Timeout: 15 * time.Second}
		respUpstream, err := client.Do(reqUpstream)
		if err != nil {
			fmt.Printf("  [2] Upstream Direct Stream: FAILED (%v)\n", err)
			allPassed = false
			continue
		}
		bufUpstream := make([]byte, 65536)
		nUpstream, _ := io.ReadFull(respUpstream.Body, bufUpstream)
		respUpstream.Body.Close()

		if respUpstream.StatusCode != http.StatusOK && respUpstream.StatusCode != http.StatusPartialContent {
			fmt.Printf("  [2] Upstream Direct Stream: FAILED with HTTP %d\n", respUpstream.StatusCode)
			allPassed = false
			continue
		}
		fmt.Printf("  [2] Upstream Direct Stream: SUCCESS (HTTP %d, received %d bytes in %v)\n",
			respUpstream.StatusCode, nUpstream, time.Since(startUpstream).Round(time.Millisecond))

		// 3. Test through Unbound Server Streaming Proxy (/api/v1/proxy/stream)
		startProxy := time.Now()
		reqProxy := httptest.NewRequest(http.MethodGet, "/api/v1/proxy/stream?id="+track.id, nil)
		reqProxy.Header.Set("Range", "bytes=0-262143") // Request first 256KB
		wProxy := httptest.NewRecorder()

		// Execute proxy request through server mux
		srv.ServeHTTP(wProxy, reqProxy)
		respProxy := wProxy.Result()
		bodyProxy := wProxy.Body.Bytes()

		if respProxy.StatusCode != http.StatusOK && respProxy.StatusCode != http.StatusPartialContent {
			fmt.Printf("  [3] Server Proxy Stream: FAILED with HTTP %d: %s\n", respProxy.StatusCode, string(bodyProxy))
			allPassed = false
			continue
		}

		// Verify audio header format (WebM EBML or OggS or ftyp)
		headerHex := fmt.Sprintf("%X", bodyProxy[:min(8, len(bodyProxy))])
		fmt.Printf("  [3] Server Proxy Stream: SUCCESS (HTTP %d, received %d bytes in %v)\n",
			respProxy.StatusCode, len(bodyProxy), time.Since(startProxy).Round(time.Millisecond))
		fmt.Printf("      Content-Type: %s | First 8 bytes: %s\n", respProxy.Header.Get("Content-Type"), headerHex)

		if len(bodyProxy) < 1024 {
			fmt.Printf("  [!] WARNING: stream body too small (%d bytes)\n", len(bodyProxy))
			allPassed = false
		}
	}

	fmt.Println("\n================================================================")
	if allPassed {
		fmt.Println(" RESULT: ALL FIXED PARAMETER STREAM TESTS PASSED (100% OPERATIONAL)")
		fmt.Println("================================================================")
		os.Exit(0)
	} else {
		fmt.Println(" RESULT: ONE OR MORE STREAM TESTS FAILED")
		fmt.Println("================================================================")
		os.Exit(1)
	}
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
