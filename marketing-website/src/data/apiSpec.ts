export type HttpMethod = "GET" | "POST" | "DELETE" | "SSE";

export interface ApiParam {
  name: string;
  location: "query" | "body" | "header";
  type: string;
  required: boolean;
  description: string;
}

export interface ApiEndpoint {
  id: string;
  method: HttpMethod;
  path: string;
  summary: string;
  details: string;
  params?: ApiParam[];
  requestJson?: string;
  responseJson: string;
}

export interface ApiCategory {
  id: string;
  group: string;
  title: string;
  description: string;
  endpoints: ApiEndpoint[];
}

export const API_CATEGORIES: ApiCategory[] = [
  {
    id: "getting-started",
    group: "Core",
    title: "Getting Started & Health",
    description:
      "Connect to Unbound Music's local engine on port 45731, check status, listen to live events, and manage app preferences.",
    endpoints: [
      {
        id: "ping",
        method: "GET",
        path: "/api/v1/ping",
        summary: "Quick connection check",
        details: "Returns a lightweight pong response to confirm the local music service is running.",
        responseJson: `{"status": "pong"}`,
      },
      {
        id: "health",
        method: "GET",
        path: "/api/v1/health",
        summary: "Readiness check",
        details: "Confirms both the local API server and storage are ready to accept requests.",
        responseJson: `{"healthy": true, "port": 45731}`,
      },
      {
        id: "status",
        method: "GET",
        path: "/api/v1/status",
        summary: "App & library status",
        details: "Returns version, uptime, and how many songs are currently indexed in your library.",
        responseJson: `{
  "status": "online",
  "version": "2.0.0",
  "uptime_seconds": 3482,
  "database": {
    "track_count": 1420
  }
}`,
      },
      {
        id: "events",
        method: "SSE",
        path: "/api/v1/events",
        summary: "Live progress & discovery stream",
        details: "Streams real-time updates such as download progress and nearby peer discovery.",
        params: [
          { name: "Accept", location: "header", type: "string", required: true, description: "text/event-stream" },
        ],
        responseJson: `event: download_progress
data: {"track_id": "5NV6RFTu24j", "percent": 64.5}`,
      },
      {
        id: "settings",
        method: "GET",
        path: "/api/v1/settings",
        summary: "Read or save app settings",
        details: "GET reads saved playback quality and audio toggles; POST updates them.",
        responseJson: `{
  "streaming_quality": "HIGH",
  "sponsorblock_enabled": true,
  "normalize_volume": false
}`,
      },
      {
        id: "sleeptimer",
        method: "POST",
        path: "/api/v1/sleeptimer/start",
        summary: "Start bedtime sleep timer",
        details: "Fades music down gently after the chosen minutes or at the end of the current song.",
        requestJson: `{"minutes": 30, "end_of_song": false}`,
        responseJson: `{"active": true, "remaining_ms": 1800000}`,
      },
      {
        id: "updater",
        method: "GET",
        path: "/api/v1/updater/check",
        summary: "Check for app updates",
        details: "Checks whether a newer release of Unbound Music is available.",
        responseJson: `{"has_update": false, "current_version": "2.0.0", "latest_version": "2.0.0"}`,
      },
    ],
  },
  {
    id: "streaming",
    group: "Playback",
    title: "Streaming & Playback",
    description:
      "Resolve audio streams, play local copies without spending mobile data, and buffer ahead for smooth listening.",
    endpoints: [
      {
        id: "stream",
        method: "GET",
        path: "/api/v1/stream",
        summary: "Get audio stream or local file",
        details: "Checks if the song is already saved on your phone first; otherwise returns a clean audio stream link.",
        params: [
          { name: "id", location: "query", type: "string", required: true, description: "Track video ID" },
          { name: "format", location: "query", type: "string", required: false, description: "opus (default) or aac" },
        ],
        responseJson: `{
  "stream_url": "https://...",
  "is_local": false,
  "codec": "opus",
  "bitrate_kbps": 160
}`,
      },
      {
        id: "proxy-stream",
        method: "GET",
        path: "/api/v1/proxy/stream",
        summary: "Buffered playback stream",
        details: "Buffers ~30 seconds ahead and supports seeking so weak cellular spots don't pause playback.",
        params: [
          { name: "id", location: "query", type: "string", required: true, description: "Track video ID" },
        ],
        responseJson: `Binary audio stream with Range support (HTTP 206 Partial Content)`,
      },
      {
        id: "fallback",
        method: "GET",
        path: "/api/v1/fallback/resolve",
        summary: "Find backup audio source",
        details: "Automatically locates an alternate source if a stream link expires or fails.",
        params: [
          { name: "id", location: "query", type: "string", required: true, description: "Track ID" },
          { name: "title", location: "query", type: "string", required: false, description: "Song title" },
          { name: "artist", location: "query", type: "string", required: false, description: "Artist name" },
        ],
        responseJson: `{"resolved": true, "stream_url": "http://127.0.0.1:45731/api/v1/proxy/stream?id=..."}`,
      },
      {
        id: "canvas",
        method: "GET",
        path: "/api/v1/canvas",
        summary: "Get looping background art",
        details: "Fetches short looping visual artwork clips for the Now Playing screen.",
        params: [
          { name: "title", location: "query", type: "string", required: true, description: "Track title" },
          { name: "artist", location: "query", type: "string", required: true, description: "Artist name" },
        ],
        responseJson: `{"canvas_url": "https://canvaz.scdn.co/...mp4"}`,
      },
    ],
  },
  {
    id: "search",
    group: "Discovery",
    title: "Search & Mood Vibe",
    description:
      "Search songs, albums, artists, and podcasts—or describe a mood in plain English to build a playlist.",
    endpoints: [
      {
        id: "search-all",
        method: "GET",
        path: "/api/v1/search",
        summary: "Search the music catalog",
        details: "Searches songs, albums, artists, playlists, and podcasts in one call.",
        params: [
          { name: "q", location: "query", type: "string", required: true, description: "Search keywords" },
          { name: "filter", location: "query", type: "string", required: false, description: "songs | albums | artists | playlists | podcasts" },
        ],
        responseJson: `{
  "query": "Daft Punk",
  "results": [
    {
      "id": "5NV6RFTu24j",
      "title": "Get Lucky",
      "artist": "Daft Punk feat. Pharrell Williams",
      "album": "Random Access Memories",
      "duration_ms": 248000
    }
  ]
}`,
      },
      {
        id: "search-music",
        method: "GET",
        path: "/api/v1/search/music",
        summary: "Search music with local + online merge",
        details: "Combines results from your saved offline library and online catalog.",
        params: [
          { name: "q", location: "query", type: "string", required: true, description: "Search query" },
          { name: "category", location: "query", type: "string", required: false, description: "all | songs | albums | artists | playlists" },
        ],
        responseJson: `{"tracks": [], "albums": [], "artists": [], "playlists": []}`,
      },
      {
        id: "search-podcasts",
        method: "GET",
        path: "/api/v1/search/podcasts",
        summary: "Search podcast shows & episodes",
        details: "Finds shows and individual episodes matching your topic.",
        params: [
          { name: "q", location: "query", type: "string", required: true, description: "Podcast topic or title" },
        ],
        responseJson: `{"shows": [], "episodes": []}`,
      },
      {
        id: "search-history",
        method: "GET",
        path: "/api/v1/search/history",
        summary: "View or clear recent searches",
        details: "GET lists recent searches; POST saves a query; DELETE /api/v1/search/history/delete removes items.",
        responseJson: `{"history": ["lofi rain", "daft punk", "midnight drive"]}`,
      },
      {
        id: "search-vibe",
        method: "POST",
        path: "/api/v1/search/vibe",
        summary: "Generate a mood-based playlist",
        details: "Turns a plain description like 'late night rain drive' into matching genres, BPM, and a ready-to-play track list.",
        requestJson: `{"prompt": "Late night rain drive", "region": "IN", "language": "en"}`,
        responseJson: `{
  "vibe_result": {
    "original_prompt": "Late night rain drive",
    "target_genres": ["Synthwave", "Lo-Fi", "Indie"],
    "mood_tags": ["Chill", "Atmospheric"],
    "suggested_bpm": 98
  },
  "radio_tracks": []
}`,
      },
    ],
  },
  {
    id: "account",
    group: "Account",
    title: "Account & YouTube Sync",
    description:
      "Connect or disconnect a YouTube Music account, read profile info, and sync liked songs.",
    endpoints: [
      {
        id: "account-sync",
        method: "POST",
        path: "/api/v1/account/sync",
        summary: "Connect YouTube Music session",
        details: "Syncs your profile name, photo, liked songs, and personal mixes cleanly.",
        requestJson: `{"cookie": "SAPISID=...; __Secure-3PAPISID=..."}`,
        responseJson: `{
  "connected": true,
  "account_name": "Listener Name",
  "account_handle": "@listener",
  "liked_count": 312
}`,
      },
      {
        id: "account-status",
        method: "GET",
        path: "/api/v1/account/status",
        summary: "Check signed-in status",
        details: "Returns whether an account is connected along with profile name, avatar, and history flags.",
        responseJson: `{
  "connected": true,
  "account_name": "Listener Name",
  "avatar_url": "https://...",
  "has_watch_history": true
}`,
      },
      {
        id: "account-disconnect",
        method: "POST",
        path: "/api/v1/account/disconnect",
        summary: "Sign out & clear synced cache",
        details: "Disconnects the active account immediately and removes cached session data.",
        responseJson: `{"connected": false, "cleared": true}`,
      },
      {
        id: "account-liked",
        method: "GET",
        path: "/api/v1/account/liked",
        summary: "Get synced Liked Music & mixes",
        details: "Returns your synced YouTube Liked Music tracks and personalized mixes.",
        responseJson: `{"tracks": [], "mixes": [], "total": 312}`,
      },
      {
        id: "device-start",
        method: "POST",
        path: "/api/v1/account/device/start",
        summary: "Start code-based browser sign-in",
        details: "Generates a short device activation code for signing in via your mobile browser.",
        responseJson: `{"user_code": "ABCD-EFGH", "verification_url": "https://www.google.com/device"}`,
      },
    ],
  },
  {
    id: "explore",
    group: "Discovery",
    title: "Home, Charts & Catalog",
    description:
      "Build the Home feed, browse regional and global charts, and inspect artists, albums, or playlists.",
    endpoints: [
      {
        id: "explore-home",
        method: "GET",
        path: "/api/v1/explore/home",
        summary: "Get personalized Home feed",
        details: "Returns Jump Back In tracks, mixes, regional charts, and global charts for both Guest and Signed-In modes.",
        params: [
          { name: "country", location: "query", type: "string", required: false, description: "2-letter country code (e.g. IN, US)" },
          { name: "lang", location: "query", type: "string", required: false, description: "Language code (e.g. en)" },
        ],
        responseJson: `{
  "jump_back_mode": "watch_history",
  "jump_back_tracks": [],
  "home_mixes": [],
  "regional_charts": [],
  "global_charts": []
}`,
      },
      {
        id: "explore-charts",
        method: "GET",
        path: "/api/v1/explore/charts",
        summary: "Get Top Charts by country",
        details: "Returns trending songs for a given region or worldwide.",
        params: [
          { name: "country", location: "query", type: "string", required: false, description: "Country code" },
        ],
        responseJson: `{"country": "IN", "tracks": []}`,
      },
      {
        id: "explore-moods",
        method: "GET",
        path: "/api/v1/explore/moods",
        summary: "Browse Moods & Genres",
        details: "Lists curated mood and genre categories (Chill, Focus, Workout, Party, Sleep, and more).",
        responseJson: `{"categories": [{"title": "Chill", "color": "#00E5FF"}]}`,
      },
      {
        id: "artist-profile",
        method: "GET",
        path: "/api/v1/artist/profile",
        summary: "Get artist albums & related artists",
        details: "Returns an artist's albums, singles, and similar artists.",
        params: [
          { name: "name", location: "query", type: "string", required: true, description: "Artist name" },
        ],
        responseJson: `{"name": "Daft Punk", "albums": [], "singles": [], "related_artists": []}`,
      },
      {
        id: "playlist-album",
        method: "GET",
        path: "/api/v1/playlist",
        summary: "Load playlist or album tracks",
        details: "Retrieves the full track list and cover art for any playlist (/api/v1/playlist) or album (/api/v1/album).",
        params: [
          { name: "id", location: "query", type: "string", required: true, description: "Playlist or album ID" },
        ],
        responseJson: `{"title": "Random Access Memories", "tracks": []}`,
      },
    ],
  },
  {
    id: "lyrics-recognition",
    group: "Playback",
    title: "Lyrics & Song Finder",
    description:
      "Fetch live word-synced lyrics with pronunciation guides and identify songs playing nearby.",
    endpoints: [
      {
        id: "lyrics",
        method: "GET",
        path: "/api/v1/lyrics",
        summary: "Get synced & Romanized lyrics",
        details: "Fetches complete lyrics with timestamps and optional phonetic pronunciation for international tracks.",
        params: [
          { name: "title", location: "query", type: "string", required: true, description: "Song title" },
          { name: "artist", location: "query", type: "string", required: true, description: "Artist name" },
          { name: "duration_ms", location: "query", type: "number", required: false, description: "Song length in ms" },
        ],
        responseJson: `{
  "source": "genius_aligned",
  "is_word_synced": true,
  "lines": [
    {"start_time_ms": 14200, "end_time_ms": 18500, "text": "Like the legend of the phoenix"}
  ]
}`,
      },
      {
        id: "lyrics-recognize",
        method: "POST",
        path: "/api/v1/lyrics/recognize",
        summary: "Find a song from spoken lyrics",
        details: "Matches a few remembered words or hummed lyrics to candidate songs.",
        requestJson: `{"transcript": "we are up all night to get lucky"}`,
        responseJson: `{"candidates": [{"id": "5NV6RFTu24j", "title": "Get Lucky", "artist": "Daft Punk"}]}`,
      },
      {
        id: "shazam-recognize",
        method: "POST",
        path: "/api/v1/shazam/recognize",
        summary: "Identify ambient music playing nearby",
        details: "Accepts a short recorded audio clip and returns the matching song title and artist.",
        requestJson: `<Recorded 16kHz audio clip>`,
        responseJson: `{"match": true, "title": "Starboy", "artist": "The Weeknd ft. Daft Punk"}`,
      },
      {
        id: "shazam-file",
        method: "POST",
        path: "/api/v1/shazam/file",
        summary: "Auto-tag an unnamed audio file",
        details: "Identifies an untagged file on your phone so it can be cleanly added to your music library.",
        requestJson: `{"file_path": "/storage/emulated/0/Download/track.mp3"}`,
        responseJson: `{"matched": true, "title": "Midnight City", "artist": "M83"}`,
      },
    ],
  },
  {
    id: "sound-eq",
    group: "Sound",
    title: "Equalizer & Sound",
    description:
      "Look up headphone-specific equalizer profiles, balance track volume, and skip non-music video intros.",
    endpoints: [
      {
        id: "autoeq-search",
        method: "GET",
        path: "/api/v1/autoeq/search",
        summary: "Search 4,000+ headphone profiles",
        details: "Find calibrated sound profiles matching your exact headphones or earbuds.",
        params: [
          { name: "q", location: "query", type: "string", required: true, description: "Headphone brand or model" },
        ],
        responseJson: `{"matches": [{"id": "sony_wh_1000xm4", "name": "Sony WH-1000XM4"}]}`,
      },
      {
        id: "autoeq-preset",
        method: "GET",
        path: "/api/v1/autoeq/preset",
        summary: "Load 10-band EQ curve for a headphone",
        details: "Returns the frequency band gains for the selected headphone profile.",
        params: [
          { name: "id", location: "query", type: "string", required: true, description: "Headphone profile ID" },
        ],
        responseJson: `{"id": "sony_wh_1000xm4", "bands": [{"freq_hz": 31, "gain_db": -2.4, "q": 1.41}]}`,
      },
      {
        id: "eq-presets",
        method: "GET",
        path: "/api/v1/eq/presets",
        summary: "List or save custom EQ presets",
        details: "GET lists your saved equalizer presets; POST saves a new custom curve.",
        responseJson: `{"presets": [{"name": "Warm Bass", "bands": [3, 2, 1, 0, 0, 0, 1, 2, 2, 1]}]}`,
      },
      {
        id: "audio-normalize",
        method: "POST",
        path: "/api/v1/audio/normalize",
        summary: "Balance volume across songs",
        details: "Calculates consistent loudness adjustment so quiet and loud tracks play at the same level.",
        requestJson: `{"measured_lufs": -9.2, "target_lufs": -14.0}`,
        responseJson: `{"gain_db": -4.8}`,
      },
      {
        id: "sponsorblock",
        method: "GET",
        path: "/api/v1/sponsorblock",
        summary: "Get non-music intro/outro skip segments",
        details: "Returns timestamps of spoken intros or promos so playback jumps straight to the music.",
        params: [
          { name: "id", location: "query", type: "string", required: true, description: "Track video ID" },
        ],
        responseJson: `{"segments": [{"category": "music_offtopic", "start_ms": 0, "end_ms": 18400}]}`,
      },
    ],
  },
  {
    id: "radio",
    group: "Discovery",
    title: "Smart Radio & Picks",
    description:
      "Keep music playing automatically with smart radio queues tailored to what you're listening to.",
    endpoints: [
      {
        id: "radio-magic",
        method: "GET",
        path: "/api/v1/radio/magic",
        summary: "Start endless radio from a song",
        details: "Creates a continuous queue of similar songs starting from any track.",
        params: [
          { name: "seed_id", location: "query", type: "string", required: true, description: "Starting track ID" },
        ],
        responseJson: `{"seed_id": "5NV6RFTu24j", "tracks": []}`,
      },
      {
        id: "radio-next",
        method: "GET",
        path: "/api/v1/radio/next",
        summary: "Load next recommended songs",
        details: "Fetches the next batch of tracks while keeping artists varied.",
        params: [
          { name: "current_id", location: "query", type: "string", required: true, description: "Current track ID" },
        ],
        responseJson: `{"tracks": []}`,
      },
      {
        id: "radio-guest",
        method: "GET",
        path: "/api/v1/radio/guest",
        summary: "Guest discovery radio",
        details: "Generates fresh track recommendations without needing a signed-in account.",
        responseJson: `{"tracks": []}`,
      },
      {
        id: "feed-smart",
        method: "GET",
        path: "/api/v1/feed/smart",
        summary: "Time-of-day quick picks",
        details: "Returns personalized recommendations based on your recent plays and time of day.",
        responseJson: `{"tracks": []}`,
      },
    ],
  },
  {
    id: "library-downloads",
    group: "Library",
    title: "Offline Library, Downloads & Podcasts",
    description:
      "Scan local folders, download tracks for offline listening, manage playlists, and discover nearby peers.",
    endpoints: [
      {
        id: "storage-scan",
        method: "POST",
        path: "/api/v1/storage/scan",
        summary: "Scan phone storage for audio files",
        details: "Indexes Music, Downloads, WhatsApp, and Telegram audio while separating voice memos from music.",
        responseJson: `{"scanned": 412, "music_tracks": 389, "voice_notes_filtered": 23}`,
      },
      {
        id: "storage-library",
        method: "GET",
        path: "/api/v1/storage/library",
        summary: "Browse indexed local songs & folders",
        details: "Returns your organized local music library and separate voice-clip list.",
        responseJson: `{"music_tracks": [], "mixed_audio_tracks": [], "folders": []}`,
      },
      {
        id: "storage-purge",
        method: "POST",
        path: "/api/v1/storage/purge_cache",
        summary: "Clear temporary cached files",
        details: "Frees up cached stream space without deleting your saved offline downloads.",
        responseJson: `{"freed_mb": 148.4, "cache_size_mb": 0.0}`,
      },
      {
        id: "download-start",
        method: "POST",
        path: "/api/v1/download/start",
        summary: "Download a track for offline play",
        details: "Saves the audio file with cover art and track tags; pair with /api/v1/download/list, /pause, /resume, and /cancel.",
        requestJson: `{"id": "5NV6RFTu24j", "title": "Get Lucky", "artist": "Daft Punk"}`,
        responseJson: `{"task_id": "5NV6RFTu24j", "status": "QUEUED"}`,
      },
      {
        id: "playlists",
        method: "GET",
        path: "/api/v1/playlists",
        summary: "Manage playlists, favorites & likes",
        details: "Also supports /api/v1/favorites and /api/v1/track/like (syncs likes to YouTube when signed in).",
        responseJson: `{"playlists": [{"id": "pl_1", "title": "Late Night", "track_count": 24}]}`,
      },
      {
        id: "podcasts",
        method: "GET",
        path: "/api/v1/podcasts/discover",
        summary: "Browse & manage podcasts",
        details: "Returns featured shows and episodes; pair with /api/v1/podcasts/library and /api/v1/podcasts/resume.",
        responseJson: `{"featured_shows": [], "recent_episodes": []}`,
      },
      {
        id: "recap",
        method: "GET",
        path: "/api/v1/analytics/recap",
        summary: "View personal listening recap",
        details: "Summarizes your top artists and listening minutes stored privately on your device.",
        responseJson: `{"top_artists": ["Daft Punk"], "total_minutes": 4820}`,
      },
      {
        id: "peers",
        method: "GET",
        path: "/api/v1/peers",
        summary: "Find nearby offline Wi-Fi peers",
        details: "Lists listeners on the same Wi-Fi network for offline playlist sharing.",
        responseJson: `{"peers": [{"peer_id": "node_b83f", "ip": "192.168.1.45"}]}`,
      },
    ],
  },
];
