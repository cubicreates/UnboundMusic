# Unbound Music - Embedded REST & IPC Daemon API Specification

The embedded Go micro-daemon exposes an HTTP/1.1 and Unix Domain Socket REST interface listening on:
- **TCP Loopback**: `http://127.0.0.1:45731`
- **Unix Domain Socket**: `unix:///storage/emulated/0/Android/data/com.cubicreates/files/.backend/daemon.sock`

All endpoints return JSON unless streaming binary audio or video data. Errors return standard HTTP status codes with a JSON error payload:
```json
{
  "error": "Descriptive error message",
  "code": 400
}
```

---

## 1. System & Engine Health

### `GET /api/v1/status`
Returns real-time health, memory metrics, and engine configuration.
* **Query Parameters**: None
* **Response `200 OK`**:
  ```json
  {
    "status": "online",
    "version": "1.0.0",
    "uptime_seconds": 3482,
    "memory_alloc_mb": 42.8,
    "memory_sys_mb": 68.2,
    "heap_ceiling_mb": 128,
    "storage_mode": "full_ai",
    "database": {
      "path": "/.../.backend/sqlite/unbound.db",
      "wal_mode": true,
      "track_count": 1420
    }
  }
  ```

### `GET /api/v1/events`
Server-Sent Events (SSE) stream broadcasting real-time asynchronous notifications to the frontend (download progress, storage scan updates, P2P peer joins).
* **Headers**: `Accept: text/event-stream`
* **Stream Events**:
  ```
  event: download_progress
  data: {"track_id":"dQw4w9WgXcQ","percent":64.5,"bytes_downloaded":2450000}

  event: peer_discovered
  data: {"peer_id":"node_b83f","ip":"192.168.1.45","port":45732}
  ```

### `POST /api/v1/system/unpack-payload`
Triggers streaming Zstandard-19 archive decompression of on-device AI models on initial launch.
* **Request Body**:
  ```json
  {
    "archive_path": "/.../.backend/models/models.zst",
    "destination_dir": "/.../.backend/models"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "success": true,
    "decompressed_files": ["smollm2_135m.gguf", "model_quantized.onnx"],
    "bytes_extracted": 248912800,
    "duration_ms": 1420
  }
  ```

---

## 2. Audio Streaming, Proxy & Playback

### `GET /api/v1/stream?id=<video_id>`
Extracts the highest-fidelity pure Opus (160 kbps) or AAC (256 kbps) stream from YouTube Music with dynamic JavaScript signature decryption in $< 15\,\text{ms}$. Also evaluates the Zero-Data Router: if an identical acoustic fingerprint exists on local storage, it returns the local `file://` URI directly.
* **Query Parameters**:
  * `id` (string, required): YouTube video ID.
  * `format` (string, optional): `opus` (default) or `aac`.
* **Response `200 OK`**:
  ```json
  {
    "stream_url": "https://rr---sn-....googlevideo.com/videoplayback?...",
    "is_local": false,
    "local_path": "",
    "codec": "opus",
    "bitrate_kbps": 160,
    "sample_rate": 48000,
    "loudness_db": -7.4,
    "expires_at": 1758192000
  }
  ```

### `GET /api/v1/proxy/stream?id=<video_id>`
Lookahead buffered proxy stream. Android Media3 ExoPlayer points directly to this URL. The engine ring-buffers ~30 seconds ahead into RAM/disk cache, handles HTTP `206 Partial Content` range requests, and eliminates mobile carrier playback drops.
* **Headers Supported**: `Range: bytes=0-`
* **Response**: Binary audio stream (`audio/ogg; codecs=opus` or `audio/mp4`) with `Content-Range` headers.

---

## 3. Search, Catalog & Metadata

### `GET /api/v1/search?q=<query>&filter=<filter>`
Searches the YouTube Music catalog with pure-Go Innertube queries.
* **Query Parameters**:
  * `q` (string, required): Search query.
  * `filter` (string, optional): `songs`, `albums`, `artists`, `playlists`, `podcasts`.
* **Response `200 OK`**:
  ```json
  {
    "query": "Daft Punk",
    "results": [
      {
        "id": "5NV6RFTu24j",
        "title": "Get Lucky",
        "artist": "Daft Punk feat. Pharrell Williams",
        "album": "Random Access Memories",
        "duration_ms": 248000,
        "thumbnail_url": "https://lh3.googleusercontent.com/...",
        "is_explicit": false
      }
    ]
  }
  ```

### `GET /api/v1/artist/profile?name=<artist_name>`
Retrieves an artist's discography, partitioned into Albums, Singles, EPs, Live Performances, and a "Fans Also Like" graph.

### `GET /api/v1/playlist?id=<playlist_id>` / `GET /api/v1/album?id=<album_id>`
Retrieves tracks, header metadata, and author info for a curated playlist or album.

### `GET /api/v1/explore/moods`
Returns 8 curated Moods & Moments categories (Chill, Focus, Workout, Commute, Party, Romance, Sleep, Energy).

### `GET /api/v1/explore/charts?country=<iso_code>`
Retrieves Top 100 regional or global charts.

### `GET /api/v1/canvas?title=<title>&artist=<artist>`
Fetches official Spotify Canvas 8-second vertical looping MP4 video URLs.

### `GET /api/v1/sponsorblock?id=<video_id>`
Returns non-music skip segments (sponsorships, self-promos, intro/outro banter) for automatic background skipping.

### `GET /api/v1/ryd/votes?id=<video_id>`
Retrieves like and dislike counts from the Return YouTube Dislike API.

---

## 4. Lyrics & Syllable-Level Phonetic Alignment

### `GET /api/v1/lyrics?id=<track_id>&title=<title>&artist=<artist>&duration_ms=<duration>`
Fetches complete, uncensored lyrics from Genius, with automated fallback to NetEase Cloud Music and LRCLIB. When synced timestamps are present or computed, returns word-by-word kinetic timestamps.
* **Response `200 OK`**:
  ```json
  {
    "track_id": "5NV6RFTu24j",
    "source": "genius_aligned",
    "plain_lyrics": "Like the legend of the phoenix...",
    "is_word_synced": true,
    "lines": [
      {
        "start_time_ms": 14200,
        "end_time_ms": 18500,
        "text": "Like the legend of the phoenix",
        "syllables": [
          {"text": "Like", "start_ms": 14200, "end_ms": 14600},
          {"text": " the", "start_ms": 14600, "end_ms": 14850},
          {"text": " leg-", "start_ms": 14850, "end_ms": 15300},
          {"text": "end", "start_ms": 15300, "end_ms": 15700}
        ]
      }
    ]
  }
  ```

---

## 5. Audio Intelligence, Vector Search & Shazam Recognition

### `POST /api/v1/shazam/recognize`
Identifies an audio snippet using the official Shazam binary signature protocol.
* **Request Body**: Raw 16kHz PCM audio bytes or WAV header.
* **Processing**: 16kHz Hann windowing $\to$ FFT $\to$ spectral peak constellation pairing $(f_1, f_2, \Delta t) \to$ binary `SignatureRingBuffer`.
* **Response `200 OK`**:
  ```json
  {
    "match": true,
    "title": "Starboy",
    "artist": "The Weeknd ft. Daft Punk",
    "album": "Starboy",
    "shazam_id": "331828114",
    "accuracy": 0.98
  }
  ```

### `POST /api/v1/shazam/file`
Identifies an unknown audio file on local disk via its path.

### `POST /api/v1/ai/query`
Natural language semantic vibe parser running deterministic prompt parsing or the on-device SmolLM2-135M model depending on storage gatekeeper mode.
* **Request Body**:
  ```json
  {
    "prompt": "Late night cyberpunk synth drive with rain aesthetics",
    "limit": 25
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "vibe_tags": ["synthwave", "retrowave", "rain", "cyberpunk", "dark ambient"],
    "valence": 0.35,
    "arousal": 0.62,
    "suggested_tracks": [...]
  }
  ```

### `POST /api/v1/ai/mood`
Evaluates acoustic emotional valence (-1.0 to +1.0) and arousal (0.0 to 1.0) from track acoustic characteristics.

### `POST /api/v1/vector/similarity`
Calculates cosine similarity across 128-dimensional vectors in $< 550\,\mu\text{s}$.
* **Request Body**:
  ```json
  {
    "target_vector": [0.12, -0.45, ...],
    "candidate_vectors": [[0.10, -0.42, ...], [-0.8, 0.2, ...]]
  }
  ```

---

## 6. Smart Radio & Recommendation Engine

### `GET /api/v1/radio/magic?seed_id=<track_id>`
Generates an infinite algorithmic smart radio queue based on acoustic proximity, Markov user transition matrices, and vector embeddings.

### `GET /api/v1/radio/next?current_id=<track_id>`
Calculates the single next optimal track candidate with artist diversity constraints (enforcing no duplicate artist within 3 songs).

### `GET /api/v1/feed/smart`
Produces personalized home feed rails based on recent listening habits and time-of-day contextual vibes.

---

## 7. Audio DSP & Equalizer Calibration

### `GET /api/v1/autoeq/search?q=<headphone_name>`
Searches the bundled database of 4,000+ calibrated headphone models from the AutoEq project (Crinacle, Oratory1990, Rtings).
* **Response `200 OK`**:
  ```json
  {
    "matches": [
      {
        "id": "sony_wh_1000xm4",
        "name": "Sony WH-1000XM4",
        "source": "oratory1990",
        "target": "Harman Over-ear 2018"
      }
    ]
  }
  ```

### `GET /api/v1/autoeq/preset?id=<headphone_id>`
Retrieves the 10-band parametric IIR biquad filter parameters (frequency, gain dB, Q-factor) for the selected headphone.

### `POST /api/v1/audio/normalize`
Calculates EBU R128 / ReplayGain loudness adjustments to ensure consistent volume across mixed sources.

### `POST /api/v1/audio/crossfade`
Generates DJ logarithmic crossfade curves ($0.0 \to 1.0$) for gapless song blending.

---

## 8. Storage, Virtual Indexing & Downloads

### `GET /api/v1/storage/tree`
Returns the hierarchical tree of user audio in `Download/Unbound/` and public storage.

### `POST /api/v1/storage/scan`
Triggers parallel filesystem scanner running at 1,000+ files per second to discover local MP3, FLAC, M4A, OPUS, and WAV tracks.

### `POST /api/v1/storage/consolidate`
Enforces safe ingestion rules:
- **WhatsApp Audio**: Safely copied (never moved, preserving chat backup integrity).
- **Downloads Folder**: Moved to `Download/Unbound/Music` to conserve storage space.
- **Voice Memos**: Automatically discards audio $< 30$ seconds.

### `POST /api/v1/download/start`
Enqueues a physical stream download to `Download/Unbound/Downloads/<Artist> - <Title>.opus` with complete ID3/Vorbis tags and embedded album artwork.

### `GET /api/v1/download/active` / `GET /api/v1/download/status?id=<track_id>`
Inspects download worker queue state, transfer speeds, and progress percentages.

---

## 9. Personal Analytics & Recap

### `POST /api/v1/analytics/log`
Records a listening event with duration, completion ratio, and timestamp into SQLite.

### `GET /api/v1/analytics/recap`
Generates an on-device "Unbound Recap" (Spotify Wrapped equivalent):
* Top artists, top tracks, top genres.
* Decade distribution (e.g. 80s synth, 90s rock, 2010s EDM).
* Shannon Entropy score measuring musical taste diversity.

---

## 10. Ecosystem, P2P Mesh & Social

### `POST /api/v1/rooms/create` / `POST /api/v1/rooms/join` / `GET /api/v1/rooms/sync`
Shared listening room management with sub-millisecond clock drift NTP compensation across participants.

### `GET /api/v1/peers`
Lists active peer instances discovered on the local Wi-Fi subnet via UDP broadcast beacons on port `45732`.

### `POST /api/v1/discord/presence`
Broadcasts rich playing status over native IPC named pipe `discord-ipc-0` (on desktop or paired companion instances).

### `POST /api/v1/lastfm/nowplaying` / `POST /api/v1/lastfm/scrobble`
Scrobbles tracks to Last.fm using MD5 API signature authentication.

### `POST /api/v1/import/spotify`
Scrapes public Spotify playlist links without requiring a Spotify API key.
