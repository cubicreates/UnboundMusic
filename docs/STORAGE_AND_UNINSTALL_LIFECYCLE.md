# Unbound Music: Storage Architecture, Hidden Engine Machinery & Uninstall Lifecycle

This document provides a comprehensive technical guide to Unbound Music's filesystem architecture, on-device AI model explosion, Android Scoped Storage compliance, and the automatic uninstallation lifecycle.

---

## 1. Storage Architecture

Unbound Music uses **App-Specific External Storage** located at:

```
/storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/Unbound/
```

### Why App-Specific External Storage?

1. **Complete Auto-Deletion on Uninstall**:
   - By Android OS security architecture, files placed in public shared storage (`/storage/emulated/0/`) are never removed when an app is uninstalled.
   - By placing the canonical root in `context.getExternalFilesDir(null)`, **Android OS automatically and completely deletes the entire directory tree upon app uninstallation**. No orphan databases, AI models, or logs remain behind.

2. **Clean Phone Experience & Hidden Engine Machinery**:
   - On Android 11–15, Android Scoped Storage restricts regular phone file managers and gallery scanners from indexing `/Android/data/`.
   - This keeps the internal `.backend/` engine folder (containing SQLite databases, daemon sockets, logs, and AI weights) completely hidden from day-to-day phone use, preventing messy dot-files from appearing in phone file managers.

3. **Full Laptop / PC Docking Accessibility**:
   - When you dock your phone to a laptop or PC via USB cable (using MTP file transfer), the `/Android/data/com.cubicreates.unboundmusic/files/Unbound/` directory is **fully visible, browsable, and writable** in Windows File Explorer, macOS (Android File Transfer), and Linux.
   - Users can drag and drop music into `Music/`, backup downloaded tracks from `Downloads/`, or inspect engine databases under `.backend/sqlite/`.

4. **Zero Special Permissions Required**:
   - App-specific external storage requires zero intrusive runtime permissions or `MANAGE_EXTERNAL_STORAGE` popups.

---

## 2. Directory Tree & Layout

```text
/storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/Unbound/
├── Downloads/                   <-- Physical offline downloads (Opus/MP3 with tags & artwork)
├── Music/                       <-- User music directory for offline playback
├── Playlists/                   <-- Local playlist definitions and exports
├── Recaps/                      <-- Yearly/monthly musical recap summaries
└── .backend/                    <-- Hidden daemon working directory (.nomedia protected)
    ├── .nomedia                 <-- Instructs Android MediaStore & Gallery to ignore backend files
    ├── daemon.sock              <-- High-speed Unix Domain Socket for Kotlin <-> Go daemon IPC
    ├── sqlite/
    │   └── unbound.db           <-- SQLite FTS5 database (tracks, playlists, lyrics, search index)
    ├── models/
    │   ├── smollm2_135m.gguf    <-- SmolLM2-135M Instruct local LLM weights (~100 MB)
    │   └── model_quantized.onnx <-- MiniLM-L6-v2 semantic search embeddings (~22 MB)
    ├── cache/                   <-- Engine streaming and artwork cache
    └── logs/                    <-- Native Go daemon runtime execution logs
```

---

## 3. On-Device AI Payload Explosion

Unbound Music bundles on-device AI models with **zero external cloud API dependencies**:

1. **Compressed Asset**: The APK bundles `models.zst` (~118 MB), compressed with **Zstandard Level 19**.
2. **First-Boot Extraction**:
   - `StorageInitializer` extracts `models.zst` into `.backend/models/models.zst`.
   - The Go engine's streaming Zstandard decompressor (`gatekeeper.DecompressZstdTarStream`) extracts:
     - `smollm2_135m.gguf` (135M-parameter SLM for natural language vibe queries and smart recommendations)
     - `model_quantized.onnx` (MiniLM semantic vector embeddings)
3. **Automatic Cleanup**: Immediately after successful decompression, the temporary `models.zst` archive is purged, recovering ~118 MB of disk space.

---

## 4. Legacy Public Storage Auto-Cleanup

If an older version of Unbound Music created `/storage/emulated/0/Unbound` in public shared storage, `StorageInitializer` automatically invokes `UnboundStorageManager.cleanupLegacyPublicStorage()` on app launch to recursively delete the legacy folder, ensuring zero clutter.

---

## 5. Uninstallation Lifecycle

When you uninstall Unbound Music via Android Launcher or Android Settings:
- Android OS detects the package removal.
- The operating system purges `/storage/emulated/0/Android/data/com.cubicreates.unboundmusic/`.
- **Result**: The entire `Unbound/` directory, including all models, databases, downloads, and `.backend` machinery, is wiped from the device automatically.
