# Changelog

All notable changes to the Unbound Music project are documented in this file.

## [2.0.0] - 2026-09-30

### Architectural Overhaul & Remediation (50 Atomic Commits)

#### 🚀 Multi-Stage Fallback Engine (Spotify + P2P BitTorrent)
- **Spotify Open Metadata & MusicBrainz Verification**: Implemented verification gatekeeper validating track existence, ISRC, and canonical artist/album metadata prior to fallback resolution.
- **Multi-Indexer P2P Audio Search**: Added multi-tracker torrent search across 1337x, Nyaa, and LimeTorrents for lossless FLAC and 320kbps MP3 stream resolution.
- **Sequential Chunk Streaming Coordinator**: Integrated low-latency sequential piece prioritization in the Pure-Go daemon, enabling ExoPlayer playback within < 3 seconds over localhost proxy.
- **UI P2P Indicator**: Rendered animated glowing fallback badge in `NowPlayingScreen` indicating swarm seeder counts, audio format (FLAC/MP3), and buffer health.

#### 🧠 On-Demand AI Model Download (Post-Install APK Optimization)
- **Asset Purge**: Purged obsolete, non-compliant `arm64-v8a` duplicate binaries from APK assets to adhere to Android 15 W^X memory security.
- **StorageInitializer**: Reconfigured model extractor to support post-install, on-demand download of `models.zst` (dropping base APK footprint from ~198 MB to ~35 MB).

#### 🛡️ Daemon Reliability & Watchdog
- **Daemon Ping/Pong Health Monitor**: Exposed `/api/v1/daemon/ping` and `/health` with uptime and active goroutine counts.
- **Android DaemonWatchdog**: Added client-side watchdog with exponential backoff, auto-reconnect, and health verification.

#### 📦 Type-Safe Serialization (`kotlinx.serialization`)
- Configured Kotlin serialization Gradle plugin and runtime dependencies.
- Replaced manual error-prone `optString`/`JSONObject` parsers with `@Serializable` models for `SearchResultDto`, `TrackMetadataDto`, `LyricsPayloadDto`, `EQPresetDto`, and `AutoEqProfileDto`.

#### 🎨 Modular Player UI Architecture
- Decomposed monolithic `NowPlayingScreen.kt` (previously ~2,000 lines) into focused, reusable presentation components:
  - `PlayerTrackInfo.kt`: Centered title, marquee artist, and high-res format badge.
  - `PlayerProgressBar.kt`: 120 FPS hardware-accelerated seek slider with animated buffer progress.
  - `PlayerControlsBar.kt`: 5-button tactile deck with spring animations.
  - `SyncedLyricsSheet.kt`: Click-to-seek, auto-scrolling kinetic lyrics modal sheet.
  - `SpotifyCanvasPlayer.kt`: Immersive Spotify Canvas layout with video canvas background.
  - `AppleGlassPlayer.kt`: Frosted glass layout with large album art and instant lyrics toggle.
  - `M3ExpressivePlayer.kt`: Material 3 Expressive layout with squircle pill controls.

#### 🏗️ Domain-Driven ViewModels
- Decomposed monolithic `MainViewModel.kt` (previously ~4,200 lines) into specialized domain ViewModels:
  - `SearchViewModel.kt`: Search debouncing, autocomplete suggestions, category filtering, and AI Vibe search.
  - `EqualizerViewModel.kt`: 10-band IIR DSP curve state, AutoEq headphone compensation, Bass Boost, and Virtualizer.
  - `LyricsViewModel.kt`: Synchronized LRCLIB lyrics, word-synced syllable tracking, and timing offsets.
  - `DownloadsViewModel.kt`: Background download orchestration, task polling loop, and Scoped Storage cache tracking.
  - `ShazamViewModel.kt`: 16kHz PCM audio recording, acoustic fingerprinting, and Pure-Go Shazam identification.
  - `MainViewModel.kt`: Streamlined as top-level coordinator via lazy delegation with 100% backward compatibility.

#### 🎛️ Audio DSP & Headphone Target Calibration
- **Logarithmic Curve Interpolation**: Implemented continuous frequency response interpolation in Go `pkg/autoeq`.
- **Target Resampling**: Added arbitrary frequency resampling for 5-band, 10-band, and 31-band hardware equalizers.
- **Reference Headphone Database**: Added profiles for Sony WH-1000XM5, AirPods Pro 2, Sennheiser HD 650, Audio-Technica ATH-M50x, Moondrop Blessing 2, and Beyerdynamic DT 770 Pro.

#### 📂 Scoped Storage Partitioning & Cache Pruner
- **StorageManager**: Enforced strict boundary partition between private cache (`.backend/cache/`) and public exports (`Download/Unbound/`).
- **CachePruner**: Implemented automatic LRU quota eviction enforcing a 2 GB storage budget and 80% watermark pruning.

#### 🧪 Comprehensive Test Suites
- Added unit tests for Go system, stream, search, lyrics, equalizer, storage, and autoeq packages.
- Added Android unit and integration tests: `BackendClientContractIntegrationTest`, `DaemonWatchdogTest`, `SearchResultDtoTest`, `SyncedLyricsDtoTest`, `EqualizerPresetDtoTest`, `PlayerProgressBarTest`, and `FallbackStatusDtoTest`.
