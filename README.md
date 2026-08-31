<div align="center">
  <img src="https://github.com/user-attachments/assets/f46e4a67-4ec6-4bdb-a4b8-f6e039005ce2" alt="Unbound Music Banner" width="100%">
  
  <h1>Unbound Music</h1>
  <p><strong>A Next-Gen, Audio-Exclusive FOSS Platform with an Embedded Go Engine, Adaptive Storage Gatekeeper, On-Device Forced Lyric Alignment & Acoustic Fingerprinting.</strong></p>

  <p>
    <img src="https://img.shields.io/badge/License-GPL--3.0-0052CC?style=flat-square" alt="License">
    <img src="https://img.shields.io/badge/Bundle-%3C%2050%20MB-brightgreen?style=flat-square" alt="Under 50MB">
    <img src="https://img.shields.io/badge/Frontend-Compose%20Multiplatform-7F52FF?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
    <img src="https://img.shields.io/badge/Engine-Pure%20Go-00ADD8?style=flat-square&logo=go&logoColor=white" alt="Go Backend">
    <img src="https://img.shields.io/badge/Cloud%20Cost-%240.00%2Fmo-00875A?style=flat-square" alt="Zero Cloud Cost">
  </p>
</div>

---

## Lineage & Acknowledgements

> **Built upon the visionary foundation of SimpMusic.**
>
> Unbound Music began as an ambitious architectural evolution of [SimpMusic](https://github.com/maxrave-dev/SimpMusic), created by [Nguyen Duc Tuan Minh (maxrave-dev)](https://github.com/maxrave-dev). We express our deepest gratitude to MaxRave and the open-source contributors who proved what modern Compose Multiplatform music experiences could be.

---

## What Makes Unbound Music Different

| Feature Domain | Traditional FOSS Clients | Unbound Music Hybrid Architecture |
| :--- | :--- | :--- |
| **Download Footprint** | Heavy multi-hundred MB bundles | **$\sim 36.5\text{ MB}$ Self-Contained Bundle** ($< 50\text{MB}$) with zero extra downloads |
| **Intelligence Engine** | Cloud LLM tokens or zero intelligence | **On-Device Micro-AI** (SmolLM2-135M GGUF + MiniLM ONNX + 128-dim Vector Search) |
| **Audio Recognition** | None or paid third-party APIs | **Pure-Go 16kHz FFT Peak Constellation Shazam Subsystem** ($0.00 cost) |
| **Scraper Architecture** | External cloud scraper APIs (prone to bans) | **Embedded Go Daemon** running on-device as a private micro-server (`127.0.0.1:45731`) |
| **Lyrics Pipeline** | Censored radio databases | **Genius FOSS (100% Uncensored) + On-Device CTC Forced Syllable Alignment** |
| **Visual Aesthetics** | Static album art | **Spotify Canvas 8-Second Vertical Looping Video Backgrounds** |
| **Storage Ingestion** | Unorganized file dumps | **Acoustic Fingerprinting** + WhatsApp-Safe Copy & Downloads Move |
| **Network Efficiency** | Duplicate network streams for local tracks | **Zero-Data Hybrid Router** (intercepts online requests $\to$ plays local file) |
| **Personal Analytics** | Basic play counts | **On-Device Unbound Recap** (Decade distribution + Taste Diversity entropy) |
| **Acoustic Control** | Generic system equalizers | **AutoEq 4,000+ Profiles + ReplayGain Normalization + DJ Crossfade** |

---

## Technical Documentation & Roadmap

* Detailed Roadmap & Daily Milestones: **[docs/ROADMAP.md](file:///d:/Github/MyMusic/docs/ROADMAP.md)**
* Subsystem Architecture & REST API Reference: **[docs/README.md](file:///d:/Github/MyMusic/docs/README.md)**

---

## Core Backend Subsystems (Days 1–11)

### 1. Audio Processing & Stream Engineering
| Package | Subsystem | Functionality & Capabilities |
| :--- | :--- | :--- |
| `backend/pkg/ytmusic` | YouTube Music Scraper | Pure-Go Innertube client, pure Opus/AAC extraction, dynamic JS cipher solver. |
| `backend/pkg/router` | Zero-Data Playback Router | Intercepts remote stream queries and serves local matching files with 0 MB data overhead. |
| `backend/pkg/dsp` | Pro Audio Signal Processing | EBU R128 / ReplayGain volume normalization, DJ crossfade curves, silence trimming. |
| `backend/pkg/autoeq` | Headphone Calibration | 4,000+ calibrated headphone database with 10-band parametric EQ curves. |

### 2. Lyrics, Phonetics & Visual Aesthetics
| Package | Subsystem | Functionality & Capabilities |
| :--- | :--- | :--- |
| `backend/pkg/genius` | Uncensored Lyrics Scraper | Scrapes complete uncensored lyrics chronologically (Intro $\to$ Outro) with LRCLIB fallback. |
| `backend/pkg/aligner` | On-Device Forced Aligner | Phonetic and syllable tokenizer providing Apple Music-style kinetic glowing timestamps. |
| `backend/pkg/canvas` | Spotify Canvas Video Engine | Fetches official 8-second vertical looping MP4 canvas video backgrounds. |

### 3. On-Device Intelligence & Audio Recognition
| Package | Subsystem | Functionality & Capabilities |
| :--- | :--- | :--- |
| `backend/pkg/ai` | Edge AI & Semantic Vibe | SmolLM2-135M GGUF, MiniLM ONNX, natural language intent parser, lyric mood analyzer. |
| `backend/pkg/shazam` | Shazam Audio Recognition | 16kHz FFT peak picker, landmark hashing, official binary signature encoder, $0.00 discovery. |
| `backend/pkg/vector` | Vector RAG Engine | 128-dimensional cosine similarity calculator executing in $< 550\mu\text{s}$. |
| `backend/pkg/recommender` | Offline Smart Radio | Algorithmic radio mix generator based on audio acoustic proximity and taste vectors. |
| `backend/pkg/analytics` | Listening Analytics & Recap | On-device "Unbound Recap" with play rankings, decade distribution, and Shannon entropy. |

### 4. Storage, Ecosystem & Secondary Services
| Package | Subsystem | Functionality & Capabilities |
| :--- | :--- | :--- |
| `backend/pkg/database` | SQLite Memory Bank | Pure-Go zero-CGO SQLite engine (`modernc.org/sqlite`) running in WAL mode. |
| `backend/pkg/fingerprint` | Ingestion & Fingerprinting | WhatsApp COPY vs Downloads MOVE safe rules, $<30\text{s}$ voice memo filter, acoustic hasher. |
| `backend/pkg/importer` | Playlist Portability | Spotify web playlist link scraper, M3U/M3U8 parser/exporter, CSV/JSON backup serializers. |
| `backend/pkg/account` | YouTube Account Sync | Generates SHA1 `SAPISIDHASH` headers to sync Liked Music (LM) and playlists. |
| `backend/pkg/explore` | Explore Feeds & Charts | 8 curated Moods & Moments categories and Top 100 regional/global charts. |
| `backend/pkg/artist` | Artist Discography | Partitioned artist discography (Albums, Singles, EPs, Live) and Fans Also Like graph. |
| `backend/pkg/p2p` | P2P Local Wi-Fi Mesh | UDP beacon peer discovery on port 45732 and 0-MB cellular catalog sync diff planner. |
| `backend/pkg/rooms` | Shared Listening Rooms | Real-time listening party hub with sub-millisecond clock drift compensation. |
| `backend/pkg/discord` | Discord Rich Presence | Native IPC named pipe (`discord-ipc-0`) desktop activity broadcaster. |
| `backend/pkg/sponsorblock` | SponsorBlock Skip Filter | Fetches and skips non-music intervals (sponsors, offtopic chatter, intro/outro). |
| `backend/pkg/lastfm` | Last.fm 2.0 Scrobbler | Authenticated client with MD5 `api_sig` for Now Playing, Loved Tracks, and scrobbling. |
| `backend/pkg/podcasts` | Podcasts Browser | YouTube Music podcast show and episode scraper with exact second resume timestamps. |
| `backend/pkg/sleeptimer` | Smart Sleep Timer | Customizable countdown timer with smooth 30-second logarithmic volume fade-out. |
| `backend/pkg/updater` | In-App GitHub Updater | Queries GitHub Releases API for version tags, changelogs, and APK/binary assets. |
| `backend/pkg/gatekeeper` | Storage Gatekeeper | Dynamic mode switch ($\ge 100\text{MB} \to \text{Full AI}$, $< 100\text{MB} \to \text{0-MB Heuristic}$) & Zstd-19 decompressor. |
| `backend/pkg/server` | Localhost REST Daemon | Embedded HTTP micro-server on `http://127.0.0.1:45731` connecting frontend to all services. |

---

## Quickstart & Verification

### Running the Backend Daemon:
```powershell
cd backend
go run ./cmd/daemon
```

### Running Test Harness:
```powershell
cd backend
go test -v ./...
```

### Automated Subsystem Test Scripts:
* `powershell -ExecutionPolicy Bypass -File ./test/test_search.ps1`
* `powershell -ExecutionPolicy Bypass -File ./test/test_lyrics.ps1`
* `powershell -ExecutionPolicy Bypass -File ./test/test_shazam.ps1`
* `powershell -ExecutionPolicy Bypass -File ./test/test_advanced_features.ps1`
* `powershell -ExecutionPolicy Bypass -File ./test/test_day11_features.ps1`
