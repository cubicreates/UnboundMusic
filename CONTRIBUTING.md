# Contributing to Unbound Music

Thank you for your interest in contributing to **Unbound Music**! We welcome contributions from developers, designers, translators, and audio enthusiasts of all skill levels.

---

## 📋 Table of Contents
- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Project Architecture](#project-architecture)
- [Branching & Commit Guidelines](#branching--commit-guidelines)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Code Style & Formatting](#code-style--formatting)

---

## 📜 Code of Conduct
By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the project maintainers.

---

## 🛠️ How to Contribute

### 1. Reporting Bugs
- Search existing [GitHub Issues](https://github.com/cubicreates/UnboundMusic/issues) to ensure the bug has not already been reported.
- If not found, open a new issue using our **Bug Report** template.
- Include device specifications (Android version / OS, app version, reproduction steps, and relevant logcat output).

### 2. Suggesting Enhancements
- Check [ROADMAP.md](ROADMAP.md) to see if the feature is already planned.
- Open a feature proposal issue explaining the problem and your suggested solution.

### 3. Submitting Code
- Please open an issue to discuss significant architectural changes before writing code.
- Small bug fixes and documentation improvements can be submitted directly via Pull Request.

---

## 💻 Development Setup

### Prerequisites
- **JDK 17 or 21** (e.g., OpenJDK, Eclipse Temurin)
- **Android SDK** with build tools (API 34+)
- **Go 1.21+** (for building/modifying the embedded engine)
- **Android Studio** (Koala / Ladybug or newer) / IntelliJ IDEA

### Building Locally

```bash
# 1. Clone repository recursively
git clone --recursive https://github.com/cubicreates/UnboundMusic.git
cd UnboundMusic

# 2. Build Android APK
./gradlew :androidApp:assembleDebug

# 3. Run Desktop Application (JVM / Compose Multiplatform)
./gradlew :composeApp:run
```

---

## 🏗️ Project Architecture

Unbound Music is structured across two primary layers:

1. **Frontend Presentation (`composeApp`, `androidApp`, `desktopApp`)**:
   - Written in **Kotlin Multiplatform** using **Jetpack Compose / Compose Multiplatform**.
   - Implements Clean Architecture with MVVM, Coroutines, and Flows.
2. **Embedded Core Engine (`core/`)**:
   - **`core/media/`**: AndroidX Media3 (ExoPlayer) with 10-band equalizer audio processor and desktop libmpv bindings.
   - **`core/service/`**: Go embedded daemon & scrapers, Genius FOSS client, and CTC on-device forced alignment.
   - **`core/data/`**: Room Database schema, DataStore preferences, and `sqlite-vec` index.

---

## 🌿 Branching & Commit Guidelines

### Branch Naming Conventions
- `feature/<feature-name>` (e.g., `feature/reverse-play-mode`)
- `fix/<bug-description>` (e.g., `fix/whatsapp-audio-copy-leak`)
- `refactor/<module-name>` (e.g., `refactor/genius-ctc-alignment`)
- `docs/<documentation-update>` (e.g., `docs/update-architecture`)

### Commit Message Standards
We follow the **Conventional Commits** specification:

```
<type>(<scope>): <short summary>

[optional body]

[optional footer(s)]
```

**Allowed Types**:
- `feat`: A new user-facing feature.
- `fix`: A bug fix.
- `perf`: A code change that improves performance or memory usage.
- `refactor`: A code change that neither fixes a bug nor adds a feature.
- `docs`: Documentation only changes.
- `style`: Changes that do not affect the meaning of the code (formatting, white-space).
- `chore`: Changes to build process or auxiliary tools.

*Example*: `feat(media): add 10-band parametric equalizer audio processor`

---

## 🚀 Submitting a Pull Request

1. Fork the repository and create your branch from `main`.
2. Ensure your changes compile and pass all tests:
   ```bash
   ./gradlew test
   ./gradlew ktlintCheck
   ```
3. Push your branch to your fork.
4. Open a Pull Request against the `main` branch.
5. Fill out the **Pull Request Template** completely.

---

## 🎨 Code Style & Formatting

- **Kotlin**: Follow standard Kotlin coding conventions and Android Kotlin Style Guide. Run `./gradlew ktlintFormat` before committing.
- **Go**: Follow `gofmt` and standard Go idioms. Run `gofmt -s -w .` on all Go source files.
- **Privacy First**: Never introduce analytics trackers, telemetry libraries, or proprietary SDKs into the FOSS codebase.
