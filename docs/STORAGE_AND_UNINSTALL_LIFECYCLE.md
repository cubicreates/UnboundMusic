# Unbound Music: Storage Architecture, Hidden Engine Machinery & Uninstall Lifecycle

This document provides a comprehensive technical guide to Unbound Music's filesystem architecture, on-device AI model explosion, Android Scoped Storage compliance, and the automatic uninstallation lifecycle.

---

## 1. Storage Architecture (Two-Folder Architecture)

Unbound Music uses a structured two-folder storage model separating user-facing media from internal engine machinery:

### 1. User-Facing Public Storage
```
/storage/emulated/0/Download/Unbound/
```
- **Location**: Anchored directly in standard system Downloads (`Environment.DIRECTORY_DOWNLOADS`).
- **Purpose**: Fully visible and accessible in Android's default Files app, phone file managers, and third-party media players.
- **Subdirectories**:
  - `Music/`: User-provided audio files for offline scanning and playback.
  - `Downloads/`: Offline downloaded YouTube Music tracks (Opus/AAC with tags and album art).
  - `Playlists/`: Exported and user playlist definitions.
  - `Recaps/`: Musical recap summaries.
  - `README.txt`: Helpful explanation of the folder structure.

### 2. Backend Engine Machinery (App-Specific Storage)
```
/storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/.backend/
```
- **Location**: Anchored in `context.getExternalFilesDir(null)/.backend`.
- **Purpose**: Stores heavy internal engine machinery, caches, and models.
- **Subdirectories**:
  - `sqlite/unbound.db`: SQLite FTS5 database (tracks, playlists, lyrics, vector index).
  - `models/`: SmolLM2-135M GGUF LLM weights (~100 MB) & MiniLM embeddings ONNX model.
  - `cache/`: Streaming proxy cache and audio chunks.
  - `logs/`: Native Go engine daemon execution logs.
  - `daemon.sock`: Unix Domain Socket for high-speed Kotlin <-> Go IPC.
  - `.nomedia`: Prevents phone gallery and media scanner from indexing backend files.

---

## 2. Uninstallation & Testing Lifecycle

### Automatic Deletion on Uninstall
1. **OS-Managed Cleanup**: Because the engine machinery is placed under app-specific external storage (`/Android/data/com.cubicreates.unboundmusic/`), **Android OS automatically and completely deletes the `.backend` folder upon app uninstall**.
2. **Zero Orphan Databases/Models**: No SQLite databases or 100MB+ AI model files remain on the device after uninstall.

### Development & Emulator Testing Workflow
- **Standard Reset**: Uninstall the app from the emulator/device. Android OS immediately wipes the entire `.backend` folder (all databases, models, caches).
- **Full Wipe (including downloaded music)**:
  - In Android Files app: delete the `Download/Unbound` folder.
  - Via ADB:
    ```bash
    adb shell rm -rf /sdcard/Download/Unbound
    ```

---

## 3. Legacy Storage Migration

On boot, `UnboundStorageManager.cleanupLegacyStorageFolders()` automatically purges old legacy paths from earlier development builds:
- `/storage/emulated/0/Music/Unbound`
- `/storage/emulated/0/Unbound` (redundant root mirror)
- Any orphaned `.backend/` directories residing inside public storage.

---

## 4. On-Device AI Payload Explosion

Unbound Music bundles on-device AI models with **zero external cloud API dependencies**:
1. **Compressed Asset**: The APK bundles `models.zst` (~118 MB), compressed with **Zstandard Level 19**.
2. **First-Boot Extraction**:
   - `StorageInitializer` extracts `models.zst` into `.backend/models/models.zst`.
   - The Go engine's streaming Zstandard decompressor (`gatekeeper.DecompressZstdTarStream`) extracts:
     - `smollm2_135m.gguf` (135M-parameter SLM for natural language vibe queries and smart recommendations)
     - `model_quantized.onnx` (MiniLM semantic vector embeddings)
3. **Automatic Archive Cleanup**: Immediately after successful decompression, the temporary `models.zst` archive is purged, recovering ~118 MB of disk space.
