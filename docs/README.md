# Unbound Music - Technical Documentation Hub

Welcome to the central technical documentation portal for **Unbound Music**. This knowledge base provides deep, production-grade architectural and implementation documentation for systems engineers, mobile developers, and audio DSP researchers.

---

## 1. Documentation Map & Role Directory

Following the **Living Docs Governance** standard, our documentation is partitioned into distinct canonical roles to ensure technical accuracy and prevent documentation drift:

```
docs/
├── README.md                                  # [THIS FILE] Master System Map & Navigation Portal
├── ARCHITECTURE.md                            # [MAP] Hybrid Systems Architecture, JNI, IPC & Dual-GC
├── MOBILE_ARCHITECTURE.md                     # [MAP] Android Jetpack Compose & Media3 Playback Pipeline
├── API_REFERENCE.md                           # [MAP] Authoritative 40+ REST & IPC Daemon Specification
├── AUDIO_ENGINE_AND_DSP.md                    # [MAP] Audio DSP, 16kHz FFT Shazam, AutoEq & Stream Proxy
├── STORAGE_AND_UNINSTALL_LIFECYCLE.md         # [MAP/RUNBOOK] Two-Folder Storage & Scoped Storage Compliance
├── DEVELOPER_GUIDE.md                         # [RUNBOOK] Contributor Setup, NDK Build & Verification
├── PRODUCTION_MAINTENANCE_AND_CANARY_PLAYBOOK.md # [RUNBOOK] CI Canary Triage, Memory Hardening & Ring Buffer
└── ROADMAP.md                                 # [STATUS] Milestones, Subsystem Progress & Future Phases
```

---

## 2. Reading Guides by Role

### For Mobile / Android Developers
1. Start with **[MOBILE_ARCHITECTURE.md](file:///d:/Github/MyMusic/docs/MOBILE_ARCHITECTURE.md)** to understand Jetpack Compose UI patterns, MVI/MVVM ViewModel state flow, and the AndroidX Media3 `UnboundPlaybackService`.
2. Review **[DEVELOPER_GUIDE.md](file:///d:/Github/MyMusic/docs/DEVELOPER_GUIDE.md)** for Android Studio setup, Gradle build targets (`./gradlew.bat assembleDebug`), and automated UAT APK deployment.
3. Read **[STORAGE_AND_UNINSTALL_LIFECYCLE.md](file:///d:/Github/MyMusic/docs/STORAGE_AND_UNINSTALL_LIFECYCLE.md)** for Android Scoped Storage compliance, WhatsApp safe-copy vs Downloads move, and zero-orphan uninstall lifecycle.

### For Backend / Systems Engineers
1. Start with **[ARCHITECTURE.md](file:///d:/Github/MyMusic/docs/ARCHITECTURE.md)** for an in-depth analysis of the embedded native Go engine (`libunbound_engine.so`), JNI dynamic linking, Unix Domain Socket IPC, and dual-GC harmony.
2. Review **[API_REFERENCE.md](file:///d:/Github/MyMusic/docs/API_REFERENCE.md)** for complete request/response schemas across all 40+ endpoints exposed on `127.0.0.1:45731`.
3. Consult **[PRODUCTION_MAINTENANCE_AND_CANARY_PLAYBOOK.md](file:///d:/Github/MyMusic/docs/PRODUCTION_MAINTENANCE_AND_CANARY_PLAYBOOK.md)** for YouTube cipher breakage triage, memory ceiling limits, and ring-buffer cache eviction.

### For Audio Engineers & DSP Specialists
1. Read **[AUDIO_ENGINE_AND_DSP.md](file:///d:/Github/MyMusic/docs/AUDIO_ENGINE_AND_DSP.md)** for mathematical derivations of 16kHz FFT peak constellation recognition (Shazam binary signature), 4,000+ AutoEq parametric biquad IIR filters, EBU R128 loudness normalization, and equal-power DJ crossfades.
2. Cross-reference with `frontend/.../audio/EqualizerAudioProcessor.kt` and `backend/pkg/shazam`.

---

## 3. Executive Technical Summary

| Subsystem Domain | Implementation Technology | Primary File Locations | Key Capabilities |
| :--- | :--- | :--- | :--- |
| **Android Presentation** | Jetpack Compose + Material 3 | `frontend/app/.../ui/` | Dynamic Palette theming, swipeable miniplayer, kinetic lyrics visualizer |
| **Media Playback Engine** | AndroidX Media3 ExoPlayer | `frontend/app/.../service/` | Foreground `MediaSessionService`, lockscreen controls, Bluetooth AVRCP |
| **Software Audio DSP** | Custom AudioProcessor Chain | `frontend/app/.../audio/` | 10-band IIR biquad EQ, AutoEq curves, equal-power crossfade, sleep decay |
| **Native Embedded Engine** | Pure Go 1.22+ (`c-shared`) | `backend/cmd/android/` | Single-process ELF library (`libunbound_engine.so`), JNI lifecycle bridge |
| **IPC Transport Layer** | Unix Domain Socket + TCP | `backend/pkg/server/` | `daemon.sock` ($<120\mu\text{s}$ latency) + TCP fallback `127.0.0.1:45731` |
| **Memory Management** | Dual-GC Coordinated Limits | `backend/pkg/server/` | 128 MiB soft heap ceiling, `GOGC=50`, JNI `onTrimMemory` sweep |
| **Database & Vector Bank** | SQLite in WAL Mode | `backend/pkg/database/` | Zero-CGO `modernc.org/sqlite`, concurrent readers, taste vectors |
| **Stream Extraction & Proxy**| Innertube + Dynamic JS Cipher | `backend/pkg/ytmusic/` | Pure Opus/AAC extraction, $<15\text{ms}$ cipher deobfuscation, 30s ring buffer |
| **Acoustic Recognition** | 16kHz FFT Constellation | `backend/pkg/shazam/` | Official binary signature encoder, $0.00 cloud cost, $<2\text{ms}$ local match |
| **Uncensored Lyrics** | Genius Scraper + Forced Aligner| `backend/pkg/genius/` | Chronological uncensored lyrics + RMS vocal energy word-by-word sync |
| **Storage Architecture** | Two-Folder Scoped Storage | `frontend/.../service/` | Public `Download/Unbound/` vs App-specific `.backend/`, zero-orphan wipe |
