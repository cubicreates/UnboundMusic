# Unbound Music - Technical Roadmap & Execution Milestones

## Project Vision & Core Constraints
Unbound Music is an offline-first, privacy-respecting, on-device intelligent music streaming and local audio manager.
* Zero Cloud Cost: Runs 100% on-device with $0.00 developer token or API subscription fees.
* Zero Cloud Database: All persistence is managed via pure-Go embedded SQLite in WAL mode.
* Uncensored Audio & Lyrics: Direct scraper pipelines preserving complete artist expression without radio censorship.
* Zero-Data Interception: Automatically maps streaming tracks to local storage to eliminate redundant cellular data usage.

---

## Phase 1: Engine Core & Uncensored Scrapers (Week 1, Days 1-3)

### Day 1: Pure-Go YouTube Music Engine
* Package: `backend/pkg/ytmusic`
* Key Deliverables:
  * YouTube Innertube client implementation in pure Go.
  * Direct extraction of pure Opus (160 kbps) and AAC (256 kbps) audio streams.
  * Rolling signature cipher solver capable of executing YouTube dynamic JS transformations in under 15ms.
  * Search, suggestions, and stream metadata extraction.

### Day 2: Uncensored Lyrics Engine
* Package: `backend/pkg/genius`
* Key Deliverables:
  * Depth-balanced Genius HTML lyrics parser capturing songs in chronological order (Intro, Verse 1, Chorus, Verse 2, Outro).
  * Elimination of self-censoring algorithms found in radio databases.
  * Failsafe fallback to LRCLIB synced lyrics when Genius does not contain text.

### Day 3: Local Storage Ingestion & Acoustic Fingerprinting
* Package: `backend/pkg/fingerprint`
* Key Deliverables:
  * Perceptual acoustic hashing generating deterministic 64-bit fingerprint hashes from PCM audio.
  * Safe Ingestion Rules: WhatsApp audio is copied (protecting chat backups), while Downloads folder audio is moved to save internal disk space.
  * Intelligent duration classifier dropping voice memos and sound effects under 30 seconds.
  * Concurrent directory scanner processing over 1,000 files per second.

---

## Phase 2: Intelligence, Alignment & Zero-Data Router (Week 1, Days 4-7)

### Day 4: On-Device Forced Aligner & SQLite Memory Bank
* Packages: `backend/pkg/aligner`, `backend/pkg/database`
* Key Deliverables:
  * Syllable and phoneme tokenizer computing kinetic word-by-word timing for Apple Music-style lyrics glow.
  * Embedded SQLite database (`modernc.org/sqlite`, zero CGO) with WAL mode.
  * Tables: `tracks`, `synced_lyrics`, `fingerprints`, `taste_vectors`.

### Day 5: Zero-Data Hybrid Playback Router & Localhost Daemon
* Packages: `backend/pkg/router`, `backend/pkg/server`, `backend/pkg/gatekeeper`
* Key Deliverables:
  * Hybrid playback router: intercepts stream requests and returns local `file://` URIs if acoustic fingerprint matches (0 MB cellular data consumed).
  * Storage gatekeeper dynamically switching between Full AI mode (>= 100MB free space) and 0-MB heuristic mode (< 100MB free space).
  * Embedded REST daemon listening on `http://127.0.0.1:45731`.

### Day 6: Pure-Go Vector Engine, Recommender & P2P Mesh Sync
* Packages: `backend/pkg/vector`, `backend/pkg/recommender`, `backend/pkg/p2p`
* Key Deliverables:
  * 128-dimensional mathematical vector engine with cosine similarity execution under 550 microseconds.
  * Offline Smart Radio mix generator clustering local library tracks.
  * Local Wi-Fi UDP beacon peer discovery (port 45732) and catalog diff synchronization planner.

### Day 7: Edge AI Payload Packager & Zstandard Decompressor
* Packages: `backend/pkg/ai`, `backend/pkg/gatekeeper`
* Key Deliverables:
  * Packaging and verified unauthenticated endpoints for SmolLM2-135M GGUF and MiniLM ONNX.
  * Zstandard Level 19 streaming decompressor unpacking models in under 120ms.
  * Natural language vibe search parser and lyric mood analyzer.

---

## Phase 3: Secondary Services & Advanced Ecosystem (Week 2, Days 8-11)

### Day 8: Secondary Services Audit
* Packages: `backend/pkg/autoeq`, `backend/pkg/discord`, `backend/pkg/sponsorblock`, `backend/pkg/rooms`
* Key Deliverables:
  * AutoEq 4,000+ calibrated headphone database with 10-band parametric EQ curves.
  * Discord Rich Presence IPC named-pipe connector.
  * SponsorBlock skip segment parser (music offtopic, sponsor, intro, outro).
  * Synchronized shared listening rooms with sub-millisecond clock drift compensation.

### Day 9: Shazam Audio Recognition Subsystem
* Package: `backend/pkg/shazam`
* Key Deliverables:
  * 16kHz audio DSP Fast Fourier Transform (FFT) and Hann windowing.
  * 2D spectral peak constellation picker across 4 frequency bands.
  * Combinatorial landmark pairing $(f_1, f_2, \Delta t)$ encoded into official 2.5KB binary `SignatureRingBuffer`.
  * Public unauthenticated Shazam discovery client with $0.00 cloud cost and zero API keys.
  * Instant offline fallback matching against local SQLite fingerprints in under 2ms.

### Day 10: Listening Analytics, Playlist Importers & Pro Audio DSP
* Packages: `backend/pkg/analytics`, `backend/pkg/importer`, `backend/pkg/dsp`, `backend/pkg/lastfm`, `backend/pkg/podcasts`
* Key Deliverables:
  * On-device "Unbound Recap" (Spotify Wrapped equivalent) with decade distribution and Shannon entropy taste diversity scoring.
  * Public Spotify playlist link scraper, M3U/M3U8 parser/exporter, and CSV/JSON backup serializers.
  * EBU R128 / ReplayGain volume normalization, DJ crossfade curve generator, and automatic silence trimmer.
  * Authenticated Last.fm 2.0 scrobbler with MD5 signature hashing.
  * YouTube Podcasts browser with exact-second playback resumption.

### Day 11: Spotify Canvas, Account Sync & Explore Feeds
* Packages: `backend/pkg/canvas`, `backend/pkg/account`, `backend/pkg/explore`, `backend/pkg/artist`, `backend/pkg/sleeptimer`, `backend/pkg/updater`
* Key Deliverables:
  * Spotify Canvas 8-second vertical looping MP4 video scraper.
  * YouTube Account cookie authentication (`SAPISIDHASH`) syncing Liked Music and playlists.
  * Explore Feeds covering 8 Moods & Moments and Global Top 100 Charts.
  * Artist deep-dive discography and Fans Also Like recommendation graph.
  * Smart Sleep Timer with 30-second volume fade-out.
  * In-App GitHub Releases auto-updater.

---

## Phase 4: Frontend UI & Client Integration (Upcoming: Weeks 2-3)

### Week 2: Compose Multiplatform Shared UI Architecture
* Milestones:
  * Design Token System: OLED pure black palette, glassmorphism surface styling, and typography.
  * Core Navigation: Bottom navigation bar, player overlay sheet, library tabs, and search bar.
  * Localhost Daemon Client: High-performance Ktor HTTP client communicating with `http://127.0.0.1:45731`.
  * Now Playing Screen: Apple Music-style kinetic synchronized lyrics glow and Spotify Canvas video background.

### Week 3: Multiplatform Deployment (Android & Desktop)
* Milestones:
  * Android ExoPlayer MediaSession integration with lockscreen controls and notification center.
  * Desktop (Windows, macOS, Linux) system tray, media hotkeys, and window management.
  * End-to-end integration validation across all 28 backend subsystems.
