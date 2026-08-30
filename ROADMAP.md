# Unbound Music Roadmap

This document outlines the development trajectory, planned releases, and architectural milestones for **Unbound Music**.

---

## 🚀 Version 1.0 (Core Foundation) — *Current Target*

- [x] **Lightweight Bundle (< 50 MB)**: Self-contained APK ($\sim 36.5\text{MB}$) bundling UI, Embedded Go Audio Engine, and CTC alignment models.
- [x] **Adaptive Storage Gatekeeper**: Dynamic runtime check enabling Zstd Micro-AI for $\ge 100\text{MB}$ storage or 0-MB BM25 heuristic fallback for constrained devices.
- [x] **Spotify × VLC Audio-First Interface**: Pure audio UI stripped of video decoders with live codec indicators (`FLAC 24-bit/96kHz`, `Opus 160kbps`).
- [x] **Embedded Go Engine**: "Device-as-Server" local daemon for stream scraping and signature decryption ($0/mo hosting cost).
- [x] **Genius FOSS Lyrics + On-Device CTC Forced Alignment**: Offline syllable-level timestamping in $\sim 1.5\text{s}$ with Apple Music-style kinetic glowing lyrics.
- [x] **Acoustic Fingerprinting (Chromaprint / AcoustID)**: Automatic song metadata hashing and voice note / noise filtration.
- [x] **Safe Storage Ingestion**: Non-destructive copy for WhatsApp media; safe move & sort for generic downloads.
- [x] **Zero-Data Hybrid Fallback Router**: Seamlessly routes online playlist requests to local high-res files when available.
- [x] **Reverse Play Mode**: Inverted queue playback for reverse-narrative conceptual albums (e.g. Kendrick Lamar's *DAMN.*).
- [x] **VLC-Grade Sound Engineering**: 10-band parametric equalizer, $+12\text{dB}$ preamp boost with soft-knee limiter.

---

## 🔮 Version 2.0 (Intelligence & Audio Refinements)

- [ ] **Headphone Acoustic Calibration (AutoEq Profiles)**: Integration of AutoEq database profiles tailored to 4,000+ headphone models.
- [ ] **Smart Offline Radio / Mood Queuing**: Local vector similarity mixing generating infinite offline radios based on acoustic tempo and BPM graphs.
- [ ] **Lossless Local Swap Detection**: Automatic prompt offering to upgrade low-bitrate local files when higher-quality FLAC/Opus streams are detected.
- [ ] **Enhanced Lyric Sharing**: Generate dynamic aesthetic lyric card images with background album art gradients.

---

## 🌐 Version 3.0 & 4.0 (Decentralized Ecosystem & P2P)

- [ ] **Local Wi-Fi P2P Sync (Phone ↔ Desktop PC)**: Direct local network sync for playlists, acoustic fingerprints, and downloaded FLAC tracks without cloud storage.
- [ ] **Multi-Device Synchronized Listening (Local Cast Rooms)**: Real-time low-latency synchronized playback across multiple phones/PCs on the same local network.
- [ ] **Community Lyric Verification**: Local cryptographic voting engine to submit corrected lyric alignments to the decentralized FOSS pool.
