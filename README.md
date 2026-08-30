<div align="center">
  <img src="https://raw.githubusercontent.com/maxrave-dev/SimpMusic/dev/fastlane/metadata/android/en-US/images/featureGraphic.png" alt="Unbound Music Banner" width="100%">
  
  <h1>Unbound Music</h1>
  <p><strong>A Next-Gen, Audio-Exclusive FOSS Platform with an Embedded Go Engine, Adaptive Storage Gatekeeper, On-Device Forced Lyric Alignment & Acoustic Fingerprinting.</strong></p>

  <p>
    <img src="https://img.shields.io/badge/License-GPL--3.0-0052CC?style=flat-square" alt="License">
    <img src="https://img.shields.io/badge/APK%20Bundle-%3C%2050%20MB-brightgreen?style=flat-square" alt="Under 50MB">
    <img src="https://img.shields.io/badge/Frontend-Kotlin%20Multiplatform-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
    <img src="https://img.shields.io/badge/Engine-Embedded%20Go-00ADD8?style=flat-square&logo=go&logoColor=white" alt="Go Backend">
    <img src="https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
    <img src="https://img.shields.io/badge/Cloud%20Hosting-%240.00%2Fmo-00875A?style=flat-square" alt="Zero Cloud Cost">
  </p>
</div>

---

## Lineage & Acknowledgements

> **Built upon the visionary foundation of SimpMusic.**
>
> Unbound Music began as an ambitious architectural evolution of [SimpMusic](https://github.com/maxrave-dev/SimpMusic), created by [Nguyen Duc Tuan Minh (maxrave-dev)](https://github.com/maxrave-dev). We express our deepest gratitude to MaxRave and the open-source contributors who proved what modern Compose Multiplatform music experiences could be.

---

## What Makes Unbound Music Different

| Feature Domain | Upstream / Traditional FOSS Clients | Unbound Music Hybrid Architecture |
| :--- | :--- | :--- |
| **Download Footprint** | Heavy multi-hundred MB bundles | **$\sim 36.5\text{ MB}$ Self-Contained Bundle** ($< 50\text{MB}$) with zero extra downloads |
| **Intelligence Engine** | Cloud LLM tokens or zero intelligence | **Command-Only Micro-AI** + 0-MB BM25 mathematical heuristic fallback |
| **Scraper Architecture** | External cloud scraper APIs (prone to bans) | **Embedded Go Daemon** running on-device as a private micro-server |
| **User Interface** | Cluttered video player wrappers | **Pure Audio-Exclusive UI** (Spotify × VLC) with sandbox CDN thumbnail cache |
| **Lyrics Pipeline** | Static text walls or paid API tokens | **Genius FOSS + On-Device CTC Forced Alignment** ($\sim 1.5\text{s}$ sync, $0 cost) |
| **Storage Ingestion** | Unorganized file dumps / broken chat links | **Acoustic Fingerprinting** + WhatsApp-Safe Copy & Downloads Move |
| **Network Efficiency** | Duplicate network streams for local tracks | **Zero-Data Hybrid Router** (intercepts online requests to play local files) |
| **Playback Modes** | Standard linear / shuffle queues | **Reverse Play Mode** (inverts tracklist for reverse-narrative concept albums) |
| **Acoustic Control** | Generic system equalizers | **VLC-Grade 10-Band Parametric EQ** + $+12\text{dB}$ Preamp with soft limiter |

---

## Core Architectural Pillars

### 1. Self-Contained < 50 MB Bundle & Adaptive Storage Gatekeeper
* **Zero Post-Install Downloads**: The complete APK bundle weighs **$\sim 36.5\text{MB}$**, packaging the Kotlin UI, embedded Go audio engine, CTC alignment acoustic model, and compressed Micro-AI payload.
* **Storage Gatekeeper**:

| Device Storage State | Trigger Condition | Execution Strategy | Storage & Memory Impact |
| :--- | :--- | :--- | :--- |
| **Normal / High Storage** | Available space $\ge 100\text{MB}$ | Decompresses Zstd micro-AI payload ($<150\text{ms}$) | $\sim 26\text{MB}$ uncompressed RAM/cache; full vector taste search |
| **Constrained Storage** | Available space $< 100\text{MB}$ | Activates SQLite FTS5 / BM25 Mathematical Engine | **$0\text{ MB}$ additional storage**; 0 neural weights in RAM |

---

### 2. Command-Driven Deterministic Micro-AI & Local RAG
* **No Conversational Fluff**: The AI does not generate human-like text responses or chat dialog.
* **Acoustic & Vector Operations**:
  1. **Cosine Taste Vectors**: Computes compact 128-dimensional mathematical vectors stored in `sqlite-vec`.
  2. **Mood & Energy Clustering**: Maps tracks into acoustic vibe clusters directly on-device.
  3. **Local RAG on Fingerprint Misses**: When acoustic fingerprinting cannot identify an obscure local track, the reasoner extracts lyric fragments/tags to formulate structured queries and resolve metadata.

---

### 3. Safe Storage Ingestion & File Organization Rules

| Audio Source / Type | Ingestion Action | Architectural Rationale |
| :--- | :--- | :--- |
| **WhatsApp Audio** (`WhatsApp/Media/...`) | **COPY** | Preserves WhatsApp internal database pointers and in-chat voice message bubbles. |
| **Telegram / Messaging Media** | **COPY** | Prevents breaking media references in chat messaging applications. |
| **Downloads / Generic Folders** (`/Download`, `/Music`) | **MOVE & ORGANIZE** | Safely tidies loose files into structured `Music/Artist/Album/Song.ext` library. |
| **Non-Music Audio (< 30s / Voice Notes)** | **IGNORE** | Acoustic filter skips speech memos, ringtones, and notification sounds. |

---

### 4. Advanced Playback Modes & "Reverse Play"

| Queue Mode | Playback Sequence | Primary Use Case |
| :--- | :--- | :--- |
| **Normal / Sequential** | $1 \to N$ (Stops at end) | Standard album / playlist listening. |
| **Loop All** | $1 \to N \to 1 \to \dots$ | Continuous background playback. |
| **Loop Single** | $K \to K \to K$ | Single track repeat. |
| **Shuffle / Random** | Fisher-Yates permutation | Unbiased random playback with no immediate repeats. |
| **Reverse Play** | $N \to 1$ (Inverted Queue) | **Concept Albums** (e.g. Kendrick Lamar's *DAMN.*) designed with reverse narratives. |

---

### 5. Sound Engineering Suite

| Parameter | Specifications & Range | Description |
| :--- | :--- | :--- |
| **10-Band Parametric EQ** | $31\text{Hz}, 62\text{Hz}, 125\text{Hz}, 250\text{Hz}, 500\text{Hz}, 1\text{kHz}, 2\text{kHz}, 4\text{kHz}, 8\text{kHz}, 16\text{kHz}$ | Precision band-pass and shelving filters with headphone presets. |
| **Preamp Gain Stage** | $0\text{dB}$ to $+12\text{dB}$ | Boosts low-mastered vintage recordings. |
| **Soft-Knee Limiter** | Automatic dynamic threshold | Eliminates digital clipping and harsh audio distortion. |
| **Codec Transparency** | Real-time stream & file inspection | Live badge displaying format (`FLAC`, `Opus`, `AAC`), sample rate, and bit depth. |

---

## Technical Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CLIENT DEVICE (Phone / PC)                       │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    KOTLIN / COMPOSE MP FRONTEND                       │  │
│  │  - UI: "Spotify × VLC" Hybrid Audio-Exclusive Interface               │  │
│  │  - Media Output: Media3 (ExoPlayer on Android) / mpv (Desktop)        │  │
│  │  - Pro Audio: 10-Band Parametric EQ, Preamp, Gain Limiter             │  │
│  │  - Queue Engine: Normal | Loop All | Loop One | Shuffle | REVERSE     │  │
│  │  - Animated Canvas Apple Music-Style Kinetic Lyrics Renderer          │  │
│  │  - Storage Gatekeeper: Adaptive Micro-AI vs. 0-MB BM25 Fallback       │  │
│  │  - Battery Exemption & Autostart Onboarding Wizard                    │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │ IPC / JNI / Local REST (127.0.0.1)   │
│  ┌───────────────────────────────────▼───────────────────────────────────┐  │
│  │                   EMBEDDED GO ENGINE (Daemon / JNI)                   │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────┐   ┌───────────────────────────────┐  │  │
│  │  │   AUDIO FINGERPRINT & TAG   │   │     HYBRID STREAM ROUTER      │  │  │
│  │  │  - Chromaprint / AcoustID   │   │  - Intercepts stream requests │  │  │
│  │  │  - WhatsApp Copy / Move Dir │   │  - Local lossless prioritized │  │  │
│  │  │  - Noise & Speech Filter    │   │  - Fallback to YT/YTMusic     │  │  │
│  │  └──────────────┬──────────────┘   └───────────────┬───────────────┘  │  │
│  │                 │                                  │                  │  │
│  │  ┌──────────────▼──────────────┐   ┌───────────────▼───────────────┐  │  │
│  │  │   LOCAL SQLITE METADATA DB  │   │   GENIUS & LYRICS SUBSYSTEM   │  │  │
│  │  │  - Fingerprint Hash Index   │   │  - Genius FOSS Web Scraper    │  │  │
│  │  │  - Taste & Listening History│   │  - On-Device Forced Alignment │  │  │
│  │  │  - sqlite-vec Taste Index   │   │  - LRCLIB & SimpMusic Client  │  │  │
│  │  │  - Offline Synced Lyrics    │   │  - Deterministic Micro-AI RAG │  │  │
│  │  └─────────────────────────────┘   └───────────────────────────────┘  │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │
                Internet Connection    ▼
      ┌─────────────────────────────────────────────────────────┐
      │  YouTube Music CDN  │  Genius.com  │  LRCLIB API        │
      └─────────────────────────────────────────────────────────┘
```

---

## Privacy & Freedom

* **100% Free and Open Source**: Licensed under GNU General Public License v3.0.
* **No Telemetry / No Tracking**: FOSS builds contain zero trackers, third-party analytics, or data-collection SDKs.
* **On-Device Sovereignty**: Audio processing, fingerprint indexing, database records, and lyric alignments remain strictly on your hardware.

---

## Build & Development

### Requirements
* **JDK 17+**
* **Android SDK** (API 34+)
* **Go 1.21+** (with `gomobile` for shared library generation)

### Commands
```bash
# Build Android APK
./gradlew :androidApp:assembleDebug

# Run Desktop Application (JVM / Compose Desktop)
./gradlew :composeApp:run
```

---

## Legal Disclaimer

* Unbound Music is an open-source, non-commercial software project developed for educational and research purposes.
* Unbound Music does not host, upload, or distribute copyrighted media files. All network streams are handled dynamically from publicly accessible third-party endpoints.
* We strongly encourage all users to support musicians, songwriters, and creators by purchasing official music and subscribing to services such as [YouTube Premium](https://www.youtube.com/premium).
