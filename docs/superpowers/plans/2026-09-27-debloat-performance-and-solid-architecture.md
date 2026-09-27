# Debloat, 120 FPS Premium UI & SOLID Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform Unbound Music into a lightweight, backend-heavy music player with a luxury 120 FPS sleek UI (using draw-phase skipping and spring physics with near-zero GPU/CPU cost), eliminate startup freezing/deadlocks in BlueStacks and emulators, and refactor the codebase to strictly adhere to SOLID principles.

**Architecture:** 
1. **Frontend ("Dumb & Sleek Shell"):** Strip continuous canvas math, infinite render loops, and 300ms CPU polling tickers. Adopt draw-phase skipping (`Modifier.graphicsLayer`) and event-driven spring physics for a premium tactile feel. Decompose the monolithic 4,220-line `MainViewModel` into focused domain ViewModels.
2. **Backend ("The Engine Room"):** The embedded Go daemon (`libunbound_engine.so`) handles storage scanning, SQLite caching, queue prediction, and stream resolution.
3. **Resilience & Emulator Compatibility:** Implement a 5-second startup watchdog in `deployDaemonAndHydrate` to prevent infinite splash screen hangs, add multi-ABI handling for binary assets, and protect against audio server IPC deadlocks.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer + MediaSession), Go 1.22 (embedded JNI daemon), modernc.org/sqlite, OkHttp3, Coil.

**Spec:** Audio discussion and architecture review for debloating, BlueStacks P64 hibernation freeze recovery, and SOLID principles.

---

### Task 1: Startup Watchdog & Multi-ABI Resilience (Fix BlueStacks Freezing)

**Files:**
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/service/StorageInitializer.kt`
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/viewmodel/MainViewModel.kt`

**Interfaces:**
- Consumes: `StorageInitializer.initialize(context)`, `DaemonManager.startDaemonAuto(force)`
- Produces: Safe startup with 5-second timeout watchdog and graceful offline fallback; safe multi-ABI asset extraction.

- [ ] **Step 1: Update StorageInitializer for dynamic ABI detection and non-blocking extraction**
  - Check `Build.SUPPORTED_ABIS`. Only extract `arm64-v8a` if the device supports 64-bit ARM. On pure x86_64, avoid failing or blocking if ARM binaries cannot execute.
  - Wrap asset extraction in safe try/catch that logs warnings without throwing fatal exceptions that stall startup.

- [ ] **Step 2: Add 5-second Watchdog Timer in MainViewModel.deployDaemonAndHydrate**
  - In `deployDaemonAndHydrate()`, wrap the daemon boot and health checks in `withTimeoutOrNull(5000L)`.
  - If the daemon does not respond within 5 seconds (e.g. hung audio server, port conflict, or SQLite lock in an emulator), log a warning, set `_startupPhase.value = "OFFLINE_MODE"`, set `_startupProgress.value = 1.0f`, and set `_isAppReady.value = true`.
  - Ensure the app ALWAYS enters the UI and NEVER freezes permanently on the splash screen.

- [ ] **Step 3: Verify compilation of Task 1**
  - Run `./gradlew compileDebugKotlin` to verify no syntax or type errors.

---

### Task 2: Ultra-Cheap 120 FPS Seekbar & Playback Controls

**Files:**
- Create: `frontend/app/src/main/java/com/cubicreates/unboundmusic/ui/player/SleekProgressBar.kt`
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/ui/player/NowPlayingScreen.kt`
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/ui/components/FloatingMiniPlayer.kt`

**Interfaces:**
- Consumes: `progressFraction: Float`, `bufferedFraction: Float`, `onSeek: (Float) -> Unit`, `onSeekFinished: () -> Unit`
- Produces: `SleekProgressBar` composable using `Modifier.graphicsLayer` with 0 continuous trigonometry, 0 recompositions during steady playback, and tactile press expansion.

- [ ] **Step 1: Create SleekProgressBar.kt**
  - Build a minimalist, luxury progress bar:
    - Default state: 3dp sleek accent bar with buffered progress indicator.
    - Touch interaction: Expands smoothly to 6dp rounded pill with tactile spring (`Spring.DampingRatioLowBouncy`).
    - Uses `Modifier.graphicsLayer` to handle touch scrub transformations in the GPU Draw phase without recomposing the parent screen.
    - Zero sine-wave calculations and zero infinite transitions.

- [ ] **Step 2: Replace WavySeekBar references in NowPlayingScreen and MiniPlayer**
  - Replace `WavySeekBar` with `SleekProgressBar`.
  - Ensure scrubbing, seeking, and position timestamps remain fully functional.

- [ ] **Step 3: Verify compilation of Task 2**
  - Run `./gradlew compileDebugKotlin`.

---

### Task 3: Splash Screen & Lyrics Rendering Optimization

**Files:**
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/ui/splash/SplashScreen.kt`
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/ui/player/KineticLyricsView.kt`

**Interfaces:**
- Consumes: `statusText: String`, `progress: Float`, `onInitializationComplete: () -> Unit`
- Produces: 0-overhead Splash UI with spring monogram entry; fluid line-level synced lyrics list.

- [ ] **Step 1: Optimize SplashScreen.kt**
  - Remove the 32dp canvas grid double loop (`while (x <= size.width)`) and infinite border glow transition.
  - Implement a clean, high-contrast monogram container with a single one-shot entry spring (`0.85f -> 1.0f`) that comes to a complete rest.
  - Keep the monospace telemetry readout and thin progress bar.

- [ ] **Step 2: Streamline KineticLyricsView.kt**
  - Remove per-word matrix scaling and heavy blur filters.
  - Retain smooth, Apple Music-style line-by-line synced lyrics using `LazyListState.animateScrollToItem()`.
  - Active line springs to high contrast (100% white, bold); inactive lines rest at 35% opacity.

- [ ] **Step 3: Verify compilation of Task 3**
  - Run `./gradlew compileDebugKotlin`.

---

### Task 4: Streamline NowPlayingScreen to Luxury Studio Layout

**Files:**
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/ui/player/NowPlayingScreen.kt`

**Interfaces:**
- Consumes: `currentTrack: TrackItem`, `playbackState: PlaybackUiState`, `MainViewModel`
- Produces: Consolidated, high-performance Now Playing screen (~400 lines) with hardware-accelerated album art, smooth spring transport controls, and swipe-to-dismiss.

- [ ] **Step 1: Simplify NowPlayingScreen architecture**
  - Strip redundant, heavy visual styles (spinning vinyl simulation, cassette reels, multiple blur layers).
  - Unify around a sleek, modern Studio Mode layout:
    - Edge-to-edge album artwork with rounded corners and static hardware gradient vignette.
    - Bold, readable track title and artist typography.
    - `SleekProgressBar` with remaining/elapsed timestamps.
    - Transport buttons with tactile press-scale feedback (`graphicsLayer { scaleX = pressScale; scaleY = pressScale }`).
    - Fluid spring-animated Play/Pause morph.

- [ ] **Step 2: Verify compilation of Task 4**
  - Run `./gradlew compileDebugKotlin`.

---

### Task 5: Eliminate 300ms Ticker & Optimize Playback State Flow

**Files:**
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/viewmodel/MainViewModel.kt`
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/service/ServiceConnection.kt`

**Interfaces:**
- Consumes: Media3 `Player.Listener`
- Produces: Event-driven position updates and reduced polling overhead.

- [ ] **Step 1: Optimize position updates**
  - In `MainViewModel.kt`, replace the tight 300ms `startPositionTicker()` loop with an event-driven mechanism:
    - Update progress smoothly via standard interpolation in UI components or relax ticker to 1,000ms.
    - Evaluate SponsorBlock segments only on media item transition or discrete milestone events rather than every 300ms.
  - Reduce unnecessary SharedPreferences write ticks during playback.

- [ ] **Step 2: Verify compilation of Task 5**
  - Run `./gradlew compileDebugKotlin`.

---

### Task 6: SOLID Refactoring — Domain ViewModel Decomposition

**Files:**
- Create: `frontend/app/src/main/java/com/cubicreates/unboundmusic/viewmodel/PlaybackViewModel.kt`
- Create: `frontend/app/src/main/java/com/cubicreates/unboundmusic/viewmodel/LibraryViewModel.kt`
- Modify: `frontend/app/src/main/java/com/cubicreates/unboundmusic/viewmodel/MainViewModel.kt`

**Interfaces:**
- Consumes: `ServiceConnection`, `BackendClient`
- Produces: Segregated ViewModels adhering to Single Responsibility Principle, with `MainViewModel` delegating cleanly to maintain backward compatibility.

- [ ] **Step 1: Create PlaybackViewModel.kt**
  - Extract playback state, queue management, repeat/shuffle mode, audio quality, and speed controls into `PlaybackViewModel`.
- [ ] **Step 2: Create LibraryViewModel.kt**
  - Extract local storage scanning, folders, downloads, and custom playlist logic into `LibraryViewModel`.
- [ ] **Step 3: Wire MainViewModel as a clean coordinator**
  - Delegate calls to the extracted ViewModels/UseCases so existing composables compile without breaking changes.

- [ ] **Step 4: Verify compilation of Task 6**
  - Run `./gradlew compileDebugKotlin`.

---

### Task 7: Full Build & APK Deployment Validation

**Files:**
- Target: Full clean build and test verification

- [ ] **Step 1: Run unit tests**
  - Run `./gradlew testDebugUnitTest` to verify no regressions in business logic.
- [ ] **Step 2: Assemble Debug APK**
  - Run `./gradlew assembleDebug`.
  - Verify `app-debug.apk` is generated and copied to `apk_test/unbound-music-debug.apk`.
