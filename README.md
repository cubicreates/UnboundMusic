<div align="center">
  <img src="https://raw.githubusercontent.com/maxrave-dev/SimpMusic/dev/fastlane/metadata/android/en-US/images/featureGraphic.png" alt="Unbound Music Banner" width="100%">
  
  <h1>Unbound Music</h1>
  <p><strong>A Next-Gen, Audio-Exclusive FOSS Platform with an Embedded Go Engine, On-Device Forced Lyric Alignment & Acoustic Fingerprinting.</strong></p>

  <p>
    <img src="https://img.shields.io/badge/License-GPL--3.0-0052CC?style=flat-square" alt="License">
    <img src="https://img.shields.io/badge/Frontend-Kotlin%20Multiplatform-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
    <img src="https://img.shields.io/badge/Engine-Embedded%20Go-00ADD8?style=flat-square&logo=go&logoColor=white" alt="Go Backend">
    <img src="https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
    <img src="https://img.shields.io/badge/Platforms-Android%20%7C%20Windows%20%7C%20macOS%20%7C%20Linux-222222?style=flat-square" alt="Platforms">
    <img src="https://img.shields.io/badge/Cloud%20Hosting-%240.00%2Fmo-00875A?style=flat-square" alt="Zero Cloud Cost">
  </p>
</div>

---

## Lineage & Acknowledgements

> **Built upon the visionary foundation of SimpMusic.**
>
> Unbound Music began as an ambitious architectural evolution of [SimpMusic](https://github.com/maxrave-dev/SimpMusic), created by [Nguyen Duc Tuan Minh (maxrave-dev)](https://github.com/maxrave-dev). We express our deepest gratitude to MaxRave and the open-source contributors who proved what modern Compose Multiplatform music experiences could be.

---

## What We Re-Engineered & Invented

While honoring the upstream UI inspirations, Unbound Music completely redesigns the core engine, media pipeline, data resolution, and lyric synchronization systems:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                        WHAT MAKES UNBOUND MUSIC DIFFERENT                     │
├───────────────────────────────┬───────────────────────────────────────────────┤
│ Upstream / Traditional Apps   │ Unbound Music's Custom Architecture           │
├───────────────────────────────┼───────────────────────────────────────────────┤
│ Relies on cloud scraper APIs  │ Embedded Go Daemon running locally on-device  │
│ Video player wrappers         │ Pure Audio-Exclusive Player (Spotify x VLC)   │
│ Static lyrics or paid APIs    │ Genius FOSS + On-Device CTC Forced Alignment  │
│ Separate local & cloud tracks │ Unified Zero-Data Stream Interception Router  │
│ Unsorted device audio files   │ Acoustic Fingerprint Ingestion & Noise Filter │
│ Standard linear queue only    │ Inverted "Reverse Play" for Concept Albums    │
│ Basic system equalizers       │ VLC-Grade 10-Band Parametric EQ + Preamp      │
└───────────────────────────────┴───────────────────────────────────────────────┘
```

---

## Core Architectural Pillars

### 1. Embedded Go Engine ("Device-as-Server")
* **Zero Cloud Costs**: By compiling an embedded Go engine via JNI / local loopback, the user's phone or desktop PC acts as its own private micro-server.
* **Immunity to IP Rate-Limiting**: Scraper requests originate from individual residential/mobile IPs rather than centralized VPS blocks that get blacklisted by streaming providers.

### 2. Genius FOSS Lyrics + On-Device CTC Forced Alignment
* **Verified Lyrics Scraping**: Extracts complete lyrics, song trivia, and verified annotations directly from Genius without requiring proprietary developer tokens.
* **Local Syllable Alignment**: A quantized, on-device CTC acoustic model ($\sim 20\text{MB}$) compares the raw audio PCM waveform against the Genius transcript in $\sim 1.5\text{s}$.
* **Apple Music-Style Kinetic Rendering**: Delivers word-by-word dynamic glowing text, fluid physics-based blurring, and millisecond tap-to-seek navigation with zero cloud AI API overhead.

### 3. Acoustic Fingerprinting & Intelligent File Ingestion
* **Chromaprint / AcoustID Integration**: Hashes local audio waveforms to identify exact song metadata regardless of broken file names or missing ID3 tags.
* **Noise & Voice Memo Classifier**: Uses duration thresholds ($>30\text{s}$) and spectral bandwidth analysis to automatically exclude WhatsApp voice notes, ringtones, and notification sounds.
* **Safe Ingestion Rules**:
  - **WhatsApp / Messaging Media**: Always **copied** to prevent breaking chat history and audio message bubbles.
  - **Downloads / Loose Files**: Safely **moved and sorted** into structured `Music/Artist/Album/Song.ext` directories.

### 4. Zero-Data Hybrid Playback Router
* Whenever an online album or playlist is selected, Unbound Music queries the local on-device fingerprint database first.
* If a high-quality local copy is present, Unbound Music seamlessly plays the local `file://` URI instead of consuming cellular data or bandwidth.

### 5. Sound Engineering Suite & "Reverse Play"
* **Spotify × VLC Hybrid UI**: A modern dark-mode aesthetic with sound engineering transparency: live sample rate badges ($44.1\text{kHz} - 192\text{kHz}$), bit depth ($16/24\text{-bit}$), and codec indicators (FLAC, Opus, AAC).
* **10-Band Parametric EQ & Preamp**: Low Shelf ($31\text{Hz}$), Bass ($62\text{Hz}, 125\text{Hz}$), Mids ($250\text{Hz} - 2\text{kHz}$), Treble ($4\text{kHz}, 8\text{kHz}$), High Shelf ($16\text{kHz}$), plus $+12\text{dB}$ preamp boost with soft-knee limiter.
* **Reverse Play Mode**: Inverts playlist queues ($N \to 1$) to enable playback of conceptual reverse-narrative albums (such as Kendrick Lamar's *DAMN.*) in their intended reverse sequence.

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
│  │  - Battery Exemption & Autostart Onboarding Wizard                    │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │ IPC / JNI / Local REST (127.0.0.1)    │
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
│  │  │  - Offline Synced Lyrics    │   │  - LRCLIB & SimpMusic Client  │  │  │
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
