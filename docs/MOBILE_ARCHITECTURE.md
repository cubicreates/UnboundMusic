# Unbound Music - Mobile Engineering & Frontend Architecture

This document provides a detailed technical specification of Unbound Music's Android frontend architecture, built with **Jetpack Compose**, **AndroidX Media3 ExoPlayer**, and a modern **MVI/MVVM unidirectional state flow**.

---

## 1. Architectural Principles & High-Level Design

The mobile frontend is architected according to Android Clean Architecture principles:
1. **Separation of Concerns**: Presentation (Compose UI), Business Logic (ViewModels), Media Playback (Media3 Service), and Data/IPC (`BackendClient` communicating with the embedded Go daemon).
2. **Unidirectional Data Flow (UDF)**: UI elements observe immutable `StateFlow` streams and emit user intents; state modifications originate strictly from ViewModels.
3. **Decoupled Playback Lifecycle**: Playback is hosted within an independent Android foreground `MediaSessionService` that survives Activity teardown, orientation changes, and configuration reloads.
4. **Non-Blocking JNI/IPC**: All network queries, daemon communication, and disk I/O are dispatched to `Dispatchers.IO` via Kotlin Coroutines.

```mermaid
graph TD
    subgraph Presentation Layer [Jetpack Compose UI]
        COMP[Composable Screens\n(Home, Player, Library, Search)]
        MINI[Persistent MiniPlayer Bar]
        THEME[Material 3 Dynamic Theme Engine]
    end

    subgraph State & Orchestration Layer [ViewModels]
        MVM[MainViewModel]
        SVM[SearchViewModel]
        EVM[EqualizerViewModel]
        DVM[DownloadViewModel]
    end

    subgraph Audio Service Layer [Foreground Service]
        CONN[ServiceConnection.kt]
        SERV[UnboundPlaybackService\n(MediaSessionService)]
        EXO[ExoPlayer Instance]
        PROC[AudioProcessor Chain\n(EQ -> Crossfade -> Sleep)]
    end

    subgraph Data & IPC Layer
        BC[BackendClient.kt\n(OkHttp HTTP/UDS)]
        DAEMON[DaemonManager.kt\n(Native JNI Lifecycle)]
    end

    COMP -->|User Intent| MVM
    MINI -->|Play/Pause/Skip| MVM
    MVM -->|StateFlow| COMP
    MVM -->|StateFlow| MINI
    MVM -->|MediaController| CONN
    CONN --> SERV
    SERV --> EXO
    EXO --> PROC
    MVM --> BC
    DAEMON -.->|Starts/Monitors| BC
```

---

## 2. Presentation Layer: Jetpack Compose & Theming

### 2.1 Screen Taxonomy
The application UI is structured as a single-activity architecture (`MainActivity.kt`) hosting Compose navigation:
* **Home (`ui/home/`)**: Dynamic discovery feed, recently played tracks, contextual smart radio playlists.
* **Player (`ui/player/`)**: Full-screen expandable playback sheet with animated album art, Spotify Canvas video backgrounds, and kinetic syllable-synced lyrics visualizer.
* **MiniPlayer (`ui/components/`)**: Persistent bottom audio controller with gesture drag-to-expand and swipe-to-skip.
* **Search (`ui/search/`)**: Instant autocomplete, semantic natural-language vibe search, and 16kHz Shazam audio identification.
* **Library (`ui/library/`)**: Unified view of local storage tracks, offline downloads, playlists, and favorites.
* **Equalizer (`ui/equalizer/`)**: Interactive 10-band parametric EQ frequency response curve visualizer and 4,000+ AutoEq profile importer.
* **Downloads (`ui/downloads/`)**: Active download queue manager with pause, resume, and retry actions.
* **Recap (`ui/recap/`)**: Animated personal music analytics dashboard (decade distribution, taste diversity entropy score).

### 2.2 Dynamic Material 3 Palette
* The theme extracts dominant and accent colors from the current playing track's album art using AndroidX Palette.
* Dynamically adapts between AMOLED Pure Black dark mode and ambient tinted glassmorphism depending on user settings.

---

## 3. Playback Architecture: AndroidX Media3 ExoPlayer

Media playback is powered by **AndroidX Media3**, implementing the `MediaSessionService` architecture for first-class Android platform integration.

### 3.1 `UnboundPlaybackService`
Located at `com.cubicreates.unboundmusic.service.UnboundPlaybackService`:
* **Foreground Service Lifecycle**: Promoted to an ongoing Android foreground service with a `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` declaration.
* **Notification Management**: Publishes rich media notifications displaying track title, artist, album art, progress bar, like button, and playback control actions.
* **Audio Focus Handling**:
  - Automatically ducks volume during navigation alerts.
  - Automatically pauses playback when focus is lost (e.g. incoming phone call).
  - Handles `ACTION_AUDIO_BECOMING_NOISY` broadcast to instantly pause playback when headphones are disconnected.
* **Bluetooth & Lockscreen**: Full AVRCP metadata synchronization for car dashboards, smartwatch controllers, and lockscreen media widgets.

### 3.2 Custom `AudioProcessor` Pipeline
Rather than relying on Android's legacy, device-dependent `android.media.audiofx.Equalizer` API (notoriously buggy across OEMs), Unbound Music injects a custom, high-precision software DSP pipeline directly into ExoPlayer's audio sink:

```mermaid
graph LR
    PCM[ExoPlayer 16-Bit PCM Stream] --> EQ[EqualizerAudioProcessor\n(10-Band Biquad IIR)]
    EQ --> XFADE[CrossfadeFilterAudioProcessor\n(DJ Logarithmic Curves)]
    XFADE --> SLEEP[SleepFadeAudioProcessor\n(Smooth Sleep Timer Fade)]
    SLEEP --> SINK[AudioTrack / Output Device]
```

1. **`EqualizerAudioProcessor.kt`**:
   - Implements 10-band parametric IIR (Infinite Impulse Response) biquad filters (Peaking, Low-Shelf, High-Shelf).
   - Frequency centers: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz.
   - Directly maps AutoEq calibration curves to real-time audio sample calculations.
2. **`CrossfadeFilterAudioProcessor.kt`**:
   - Manages track transition overlaps by computing crossfade attenuation curves.
3. **`SleepFadeAudioProcessor.kt`**:
   - Enforces a smooth 30-second logarithmic volume decay when the sleep timer expires, avoiding jarring abrupt audio cutoffs.

### 3.3 Anti-Clustering Shuffle Engine (`BetterShuffleOrder.kt`)
Standard PRNG shuffle often plays 3–4 songs by the same artist consecutively. Unbound Music implements a modified Fisher-Yates shuffle that enforces an **artist-separation distance constraint**, guaranteeing variety in large playlists.

---

## 4. State Management: ViewModel & MVI Pattern

ViewModels expose immutable state via Kotlin `StateFlow` and handle actions through defined intent sealed interfaces:

```kotlin
// Example StateFlow architecture in MainViewModel.kt
val uiState: StateFlow<PlayerUiState> = combine(
    playbackState,
    currentTrack,
    lyricsState,
    equalizerPreset
) { state, track, lyrics, eq ->
    PlayerUiState(
        isPlaying = state.isPlaying,
        track = track,
        lyrics = lyrics,
        equalizerPreset = eq
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState.Initial)
```

---

## 5. Storage Architecture & Scoped Storage Compliance

### 5.1 The Two-Folder Model
To respect Android Scoped Storage while keeping user music accessible, storage is strictly bifurcated:

```
/storage/emulated/0/
├── Download/Unbound/                 <-- Public User Media (Visible in Files App)
│   ├── Music/                       <-- Local user music for offline playback
│   ├── Downloads/                   <-- Opus/AAC downloads from streaming
│   ├── Playlists/                   <-- M3U/JSON playlist exports
│   └── Recaps/                      <-- Exported recap cards
│
└── Android/data/com.cubicreates.unboundmusic/files/.backend/  <-- App-Specific Internal
    ├── sqlite/unbound.db            <-- SQLite DB in WAL mode
    ├── models/                      <-- SmolLM2-135M GGUF & MiniLM ONNX
    ├── cache/                       <-- Stream proxy cache buffer
    ├── daemon.sock                  <-- Unix Domain Socket for IPC
    └── .nomedia                     <-- Prevents gallery pollution
```

### 5.2 Zero-Orphan Uninstall Lifecycle
Because all internal machinery, databases, models, and cache files reside under Android's standard `getExternalFilesDir(null)` path, **the Android OS automatically and completely deletes the entire `.backend` directory upon uninstallation**. No orphan databases or multi-hundred MB model weights remain on the user's device.

---

## 6. High-Performance Image Caching (Coil)

In `UnboundApplication.kt`, Coil is tuned specifically for fast scrolling in dense music catalogs:
* **Memory Cache**: Allocates 25% of total available application RAM to cache bitmap decodes in memory.
* **Disk Cache**: 50 MB dedicated disk cache in `cacheDir/image_cache`.
* **Header Override**: Ignores remote HTTP `Cache-Control: no-cache` headers on YouTube CDN image URLs, ensuring album covers remain persistently cached offline.
