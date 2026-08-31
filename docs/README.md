# Unbound Music Documentation

Unbound Music is an offline-first, on-device intelligent music streaming engine and player.

## Backend Subsystem Architecture

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

## API Endpoint Reference

The backend daemon runs on `http://127.0.0.1:45731` with the following REST endpoints:

### 1. Core Streaming & Search
* `GET /api/v1/status`: Engine health, RAM usage, and storage gatekeeper mode.
* `GET /api/v1/search?q=<query>`: Innertube catalog search.
* `GET /api/v1/stream?id=<video_id>`: Pure Opus / AAC stream extractor with zero-data local match routing.
* `GET /api/v1/lyrics?id=<track_id>&title=<title>&artist=<artist>`: Uncensored Genius lyrics with word-level phonetic alignment.

### 2. Audio Intelligence & Recognition
* `POST /api/v1/shazam/recognize`: Audio snippet identification via Shazam binary signature protocol.
* `POST /api/v1/shazam/file`: Direct file recognition from local storage.
* `POST /api/v1/ai/query`: Natural language semantic vibe search.
* `POST /api/v1/ai/mood`: Audio valence and emotional mood evaluator.

### 3. Audio Enhancement & Calibration
* `GET /api/v1/autoeq/search?q=<headphone_name>`: Search 4,000+ calibrated headphone profiles.
* `GET /api/v1/autoeq/preset?id=<headphone_id>`: 10-band parametric EQ curves.
* `POST /api/v1/audio/normalize`: ReplayGain / EBU R128 loudness volume leveling.
* `POST /api/v1/audio/crossfade?progress=<0.0-1.0>`: DJ crossfade volume blend curves.

### 4. Personal Library & Analytics
* `POST /api/v1/analytics/log`: Record song listening event.
* `GET /api/v1/analytics/recap`: Complete on-device Unbound Recap with decade breakdowns and entropy scores.
* `POST /api/v1/account/sync`: Sync YouTube Liked Music and playlists via cookies.
* `GET /api/v1/account/liked`: Retrieve synced Liked Music tracks.

### 5. Discovery & Exploration
* `GET /api/v1/explore/moods`: 8 curated Moods & Moments categories.
* `GET /api/v1/explore/charts?country=<iso_code>`: Top 100 trending charts.
* `GET /api/v1/artist/profile?name=<artist_name>`: Complete artist discography and similar artists.
* `GET /api/v1/canvas?title=<title>&artist=<artist>`: Spotify Canvas 8-second looping video MP4.
* `GET /api/v1/podcasts/browse?id=<podcast_id>`: Podcast show episodes and chapters.

### 6. Ecosystem & Utilities
* `POST /api/v1/import/spotify`: Scrapes public Spotify playlist links.
* `POST /api/v1/rooms/create`: Create synchronized listening room code.
* `POST /api/v1/rooms/join`: Join listening room.
* `GET  /api/v1/rooms/sync?code=<code>`: Sub-millisecond synchronized playback position.
* `POST /api/v1/discord/presence`: Update desktop Discord Rich Presence.
* `GET  /api/v1/sponsorblock?id=<video_id>`: Video skip segments.
* `POST /api/v1/sleeptimer/start`: Set sleep timer countdown.
* `GET  /api/v1/updater/check`: Check GitHub for new version releases.

---

## Running Tests

To verify all 28 packages in the Go engine:
```powershell
cd backend
go test -v ./...
```

To run individual automated subsystem test scripts:
```powershell
powershell -ExecutionPolicy Bypass -File ./test/test_search.ps1
powershell -ExecutionPolicy Bypass -File ./test/test_lyrics.ps1
powershell -ExecutionPolicy Bypass -File ./test/test_shazam.ps1
powershell -ExecutionPolicy Bypass -File ./test/test_advanced_features.ps1
powershell -ExecutionPolicy Bypass -File ./test/test_day11_features.ps1
```
