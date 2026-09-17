# Unbound Music - Contributor & Developer Runbook

This document provides a complete guide for setting up development environments, cross-compiling the native Go engine with the Android NDK, building Android APKs, running automated test suites, and adhering to engineering standards.

---

## 1. Prerequisites & Toolchain Requirements

Ensure the following tools are installed and available in your environment:

| Component | Required Version | Verification Command | Purpose |
| :--- | :--- | :--- | :--- |
| **Go Toolchain** | `1.22.0` or higher (tested with `1.25.0`) | `go version` | Embedded engine compilation & unit tests |
| **Java JDK** | OpenJDK or Azul Zulu `17` | `java -version` | Gradle daemon & Android build toolchain |
| **Android SDK** | Platform `android-36`, Build-Tools `36.0.0` | `adb version` | Android compilation & packaging |
| **Android NDK** | `r25c` or `r26b` (e.g. `26.1.10909125`) | Check `$ANDROID_HOME/ndk` | CGo cross-compilation of `libunbound_engine.so` |
| **PowerShell / Bash** | PowerShell 5.1+ (Windows) or Bash (Linux/macOS) | `$PSVersionTable` | Test harness and automation scripts |

Ensure your `local.properties` file inside `frontend/` correctly specifies the Android SDK path:
```properties
sdk.dir=C\:\\Users\\<Username>\\AppData\\Local\\Android\\Sdk
```

---

## 2. Repository Structure

```
d:\Github\MyMusic\
├── apk_test/                     # Automatic deployment directory for built UAT APKs
├── backend/                      # Pure-Go embedded engine source
│   ├── cmd/
│   │   ├── android/              # JNI export layer (main.go with CGo JNI bindings)
│   │   └── daemon/               # Standalone desktop CLI runner
│   ├── pkg/                      # 36 specialized domain packages (ytmusic, shazam, router, etc.)
│   └── go.mod                    # Module definition (github.com/cubicreates/unbound-engine)
├── docs/                         # Comprehensive engineering documentation
├── frontend/                     # Jetpack Compose Android application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── assets/           # On-device AI payload (models.zst)
│   │   │   ├── java/.../         # Kotlin presentation, viewmodel, service layers
│   │   │   └── jniLibs/          # Precompiled libunbound_engine.so (arm64-v8a, x86_64)
│   │   └── build.gradle.kts      # App module build definition
│   └── gradlew.bat               # Gradle wrapper
├── scripts/                      # Build utilities and AI asset downloaders
└── test/                         # Automated PowerShell subsystem verification scripts
```

---

## 3. Cross-Compiling the Native Go Engine (`libunbound_engine.so`)

When updating Go backend code in `backend/pkg/`, the native C-shared library must be recompiled for the target Android ABIs (`arm64-v8a` for physical 64-bit devices, `x86_64` for Android Studio emulators).

### 3.1 NDK Cross-Compilation Script (PowerShell)
Execute the following from the repository root:

```powershell
# Set NDK Toolchain paths
$NDK = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\ndk\26.1.10909125"
$TOOLCHAIN = "$NDK\toolchains\llvm\prebuilt\windows-x86_64\bin"

# 1. Compile for ARM64-v8a (Physical Devices)
$env:CGO_ENABLED = "1"
$env:GOOS = "android"
$env:GOARCH = "arm64"
$env:CC = "$TOOLCHAIN\aarch64-linux-android26-clang.cmd"
cd backend
go build -buildmode=c-shared -ldflags="-s -w" -o ../frontend/app/src/main/jniLibs/arm64-v8a/libunbound_engine.so ./cmd/android
cd ..

# 2. Compile for x86_64 (Emulators)
$env:CGO_ENABLED = "1"
$env:GOOS = "android"
$env:GOARCH = "amd64"
$env:CC = "$TOOLCHAIN\x86_64-linux-android26-clang.cmd"
cd backend
go build -buildmode=c-shared -ldflags="-s -w" -o ../frontend/app/src/main/jniLibs/x86_64/libunbound_engine.so ./cmd/android
cd ..
```

---

## 4. Building the Android Application (APK)

### 4.1 Debug Build & Automated UAT Deployment
From the `frontend/` directory, invoke the Gradle wrapper:

```powershell
cd frontend
.\gradlew.bat assembleDebug
```

> [!NOTE]
> The Gradle build script is configured with `finalizedBy("copyApkToTestFolder")`. On every successful compilation of `assembleDebug`, the APK is automatically copied and renamed into `apk_test/`:
> - `apk_test/unbound-music-debug.apk`
> - `apk_test/app-debug.apk`

### 4.2 Release Build (Signed)
To compile a signed release APK:
```powershell
$env:KEYSTORE_FILE = "C:\path\to\release.keystore"
$env:KEYSTORE_PASSWORD = "your_store_password"
$env:KEY_ALIAS = "your_key_alias"
$env:KEY_PASSWORD = "your_key_password"

cd frontend
.\gradlew.bat assembleRelease
```

---

## 5. Testing & Verification Runbook

### 5.1 Standalone Backend Execution on Desktop
You can run and test the embedded Go daemon directly on your workstation without launching an Android emulator:

```powershell
cd backend
go run ./cmd/daemon
```
The daemon will listen on `http://127.0.0.1:45731`. You can test endpoints via `curl` or browser:
```bash
curl http://127.0.0.1:45731/api/v1/status
curl "http://127.0.0.1:45731/api/v1/search?q=Daft+Punk"
```

### 5.2 Go Unit Test Suite
Execute all Go package unit tests:
```powershell
cd backend
go test -v ./...
```

### 5.3 Subsystem Integration Test Scripts
Unbound Music provides automated integration test scripts in the `test/` directory to verify individual pipelines end-to-end:

* **Search Engine**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File ./test/test_search.ps1
  ```
* **Lyrics Pipeline (Genius + Forced Aligner)**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File ./test/test_lyrics.ps1
  ```
* **Shazam Audio Recognition**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File ./test/test_shazam.ps1
  ```
* **Advanced Features & DSP**:
  ```powershell
  powershell -ExecutionPolicy Bypass -File ./test/test_advanced_features.ps1
  ```

---

## 6. Engineering Standards & Code Hygiene

### 6.1 Kotlin Presentation Layer
* **Unidirectional State Flow**: ViewModels must expose immutable `StateFlow` and avoid public mutable properties.
* **Non-Blocking Coroutines**: Never perform disk I/O, database access, or network calls on `Dispatchers.Main`. Always use `Dispatchers.IO`.
* **Resource Cleanup**: When listening to SSE streams or coroutines in Composable lifecycle scopes, ensure proper cancellation on disposal (`DisposableEffect` or `LaunchedEffect`).

### 6.2 Go Systems Layer
* **SafeGoroutines**: Never start an untracked, naked `go func()`. Always use `server.SafeGo(name, func)` to prevent unhandled panics from terminating the host process.
* **Memory Limits**: All new subsystems allocating large byte slices (DSP, FFT, audio decoding) must respect the 128 MiB soft heap ceiling and recycle buffers where possible.
* **WAL Mode SQLite**: Never disable SQLite WAL mode or execute long-running blocking transactions that tie up the single writer thread.
