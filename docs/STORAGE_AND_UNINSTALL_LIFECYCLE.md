# Unbound Music: Storage Layout, AI Payload & Uninstall Lifecycle

This document provides a complete technical guide to Unbound Music's filesystem architecture, on-device AI model explosion, Android Scoped Storage compliance, and the uninstallation lifecycle.

---

## 1. Storage Root Architecture

Unbound Music uses a **Public Canonical Storage Root** located directly in your device's internal storage:

```
/storage/emulated/0/Unbound/
```

### Why Public Storage?
Unlike closed streaming services that trap audio in proprietary encrypted sandboxes or hide files in `/Android/data/` (which Android 11+ completely blocks standard file managers from accessing), Unbound Music treats you as the owner of your device:
- **File Manager Visibility:** The `Unbound` folder appears directly in your phone's file manager alongside `Download`, `Documents`, and `Music`.
- **Media Accessibility:** Downloaded audio tracks are saved as standard audio files (`.opus` / `.mp3`) with embedded cover art and metadata tags that you can copy, backup, or play in any external media player.
- **Android 11+ Permission:** To guarantee full visibility and read/write access to `/storage/emulated/0/Unbound/`, Unbound Music requests **All Files Access** (`MANAGE_EXTERNAL_STORAGE`). If denied, it safely falls back to app-specific external storage (`/storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/Unbound`).

---

## 2. Directory Tree & Layout

```text
/storage/emulated/0/Unbound/
├── Downloads/                   <-- Physical offline downloads (Opus/MP3 with artwork)
├── Music/                       <-- User music directory for offline playback
└── .backend/                    <-- Hidden daemon working directory (.nomedia protected)
    ├── .nomedia                 <-- Instructs Android MediaStore & Gallery to ignore backend files
    ├── daemon.sock              <-- High-speed Unix Domain Socket for Kotlin <-> Go daemon IPC
    ├── sqlite/
    │   └── unbound.db           <-- SQLite FTS5 database (tracks, playlists, lyrics, search index)
    ├── models/
    │   ├── smollm2_135m.gguf    <-- SmolLM2-135M Instruct local LLM weights (~100 MB)
    │   └── model_quantized.onnx <-- MiniLM-L6-v2 semantic search embeddings (~22 MB)
    └── logs/                    <-- Native Go daemon runtime execution logs
```

---

## 3. On-Device AI Payload Explosion

Unbound Music bundles on-device AI models with **zero external cloud API dependencies** and zero token costs:

1. **Compressed Asset:** The APK bundles `models.zst` (~118 MB), compressed with **Zstandard Level 19** via `klauspost/compress/zstd`.
2. **First-Boot Extraction:**
   - During first-boot hydration, `StorageInitializer` extracts `models.zst` into `/storage/emulated/0/Unbound/.backend/models/models.zst`.
   - The Go engine's streaming Zstandard decompressor (`gatekeeper.DecompressZstdTarStream`) decompresses the archive into:
     - `smollm2_135m.gguf` (135M-parameter SLM for natural language vibe queries and smart recommendations)
     - `model_quantized.onnx` (MiniLM semantic vector embeddings)
3. **Automatic Cleanup:** Immediately after successful decompression, the temporary `models.zst` archive is purged, recovering ~118 MB of disk space.

---

## 4. Uninstallation & Storage Cleanup Lifecycle

### How Android OS Handles Public Folders
On Android, any file placed in public shared storage (such as `/storage/emulated/0/Unbound/` or `/storage/emulated/0/Download/`) is **preserved across app uninstallation** by Android OS security design. Android only deletes files automatically if they are stored in private sandbox folders (`/data/data/` or `/Android/data/`), which are hidden from file managers.

### How to Cleanly Uninstall Unbound Music

You have two simple options:

#### Option A: In-App Clean Storage Helper (Recommended)
1. Open Unbound Music and tap the **Settings** gear icon (or your profile icon).
2. Scroll to the **STORAGE & MAINTENANCE** section.
3. Tap **"Clean Storage & Uninstall Helper"**.
4. This stops the native engine and completely deletes `/storage/emulated/0/Unbound/` (all models, database, cache, and downloads).
5. Uninstall the app normally from Android Settings / Launcher. Zero leftover files will remain.

#### Option B: Manual File Manager Deletion
1. Open your device's built-in **Files** or **File Manager** app.
2. In **Internal Storage**, locate the **Unbound** folder.
3. Long-press and tap **Delete**.
4. Uninstall the app.
