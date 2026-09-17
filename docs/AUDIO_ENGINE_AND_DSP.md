# Unbound Music - Audio Signal Processing (DSP) & Recognition Engine

This document specifies the audio engineering mathematics, digital signal processing (DSP) algorithms, stream proxying architecture, and audio fingerprinting implementation in Unbound Music.

---

## 1. Complete End-to-End Audio Pipeline

```mermaid
graph TD
    subgraph Stream Ingestion Layer
        YT[YouTube Innertube Opus/AAC]
        LOC[Local Disk Storage PCM/File]
        CIPHER[JS Rolling Cipher Solver\n(< 15ms Execution)]
        YT --> CIPHER
    end

    subgraph Core Engine Routing
        ROUTER[Zero-Data Playback Router]
        CIPHER --> ROUTER
        LOC --> ROUTER
        PROXY[Lookahead Stream Proxy\n(30s Ring Buffer / Cache)]
        ROUTER -->|Remote Stream| PROXY
        ROUTER -->|Local Match Hit| FILE_URI[Direct file:// URI]
    end

    subgraph ExoPlayer DSP Sink (Android)
        EXO[AndroidX Media3 ExoPlayer AudioSink]
        PROXY --> EXO
        FILE_URI --> EXO

        subgraph AudioProcessor Pipeline
            EQ[EqualizerAudioProcessor\n(10-Band IIR Biquad Filters)]
            NORM[EBU R128 / ReplayGain Volume Leveler]
            XFADE[CrossfadeFilterAudioProcessor\n(Equal-Power Crossfade)]
            SLEEP[SleepFadeAudioProcessor\n(Logarithmic Decay)]
            
            EXO --> EQ
            EQ --> NORM
            NORM --> XFADE
            XFADE --> SLEEP
        end

        DAC[Android AudioTrack -> DAC Hardware]
        SLEEP --> DAC
    end

    subgraph Recognition & Sync Subsystem
        MIC[16kHz Audio Input / Stream Snippet]
        FFT[Hann Windowing + 16kHz FFT]
        PEAK[2D Constellation Peak Picker]
        LANDMARK[Landmark Pairing (f1, f2, dt)]
        RING[Binary SignatureRingBuffer]
        SHAZAM[Shazam Recognition Client / Local DB]

        MIC --> FFT
        FFT --> PEAK
        PEAK --> LANDMARK
        LANDMARK --> RING
        RING --> SHAZAM
    end
```

---

## 2. YouTube Stream Extraction & Dynamic JavaScript Cipher Solver

Standard YouTube Music streams protect high-bitrate Opus and AAC audio with a dynamic JavaScript rolling cipher.

### 2.1 Pure Opus and AAC Extraction
Unbound Music bypasses standard video container bloat by explicitly requesting pure audio streams:
* **Opus**: Itag 251, 160 kbps, 48.0 kHz sample rate, Opus codec in WebM container.
* **AAC**: Itag 140, 256 kbps, 44.1 kHz sample rate, AAC-LC in MP4 container.

### 2.2 JavaScript Cipher Deobfuscation
Located in `backend/pkg/ytmusic/player.go`:
1. **Player JS Extraction**: The engine retrieves YouTube's active `base.js` player script and caches the AST extraction locally.
2. **Transformation Pattern Detection**:
   - Identifies the core transformation object containing three primitive string operations:
     - **Reverse**: Inverts string characters.
     - **Swap**: Swaps character at index 0 with character at index $N \pmod{\text{length}}$.
     - **Slice**: Drops the first $N$ characters.
3. **Execution Time**: The cipher solver computes the transformation sequence in **$< 15\,\text{ms}$** using pure Go without requiring an external V8/Node.js runtime.

---

## 3. Lookahead Stream Proxy & Ring Buffer Hygiene

The streaming proxy (`/api/v1/proxy/stream`) buffers ahead to prevent playback drops on volatile mobile cellular networks.

### 3.1 Ring Buffer Mechanics
* When playback starts, the proxy opens an upstream HTTP connection to the YouTube CDN.
* The engine reads chunks and fills an on-disk ring buffer in `appStorageRoot/cache/`.
* Concurrently, it serves the cached bytes to ExoPlayer with standard HTTP `206 Partial Content` response headers.
* The buffer sustains a lookahead window of ~30 seconds of audio.

### 3.2 Storage Eviction (Ring-Buffer Hygiene)
To prevent unlimited cache growth on mobile storage:
* **Threshold**: When total cache size exceeds **750 MiB**, the engine triggers an automated LRU eviction sweep.
* **Target**: Deletes the oldest `.opus` cache segments until storage drops to **600 MiB (80%)**.
* **Protection**: Active tracks currently streaming or spooled by the user are marked protected and never evicted during playback.

---

## 4. Zero-Data Hybrid Playback Router

The Zero-Data Playback Router (`backend/pkg/router`) guarantees that if an audio file already exists on device storage, cellular data usage is **0 MB**:

1. When a user requests to play a remote song (e.g. video ID `5NV6RFTu24j`), the router queries the SQLite `fingerprints` table for an acoustic or metadata match.
2. If a local file with identical acoustic landmarks is indexed in `Download/Unbound/`:
   - Instead of streaming remote audio, the engine instantly returns the local `file:///storage/emulated/0/...` path.
   - Eliminates redundant mobile data consumption, saves battery, and provides instantaneous playback ($< 5\text{ms}$ latency).

---

## 5. Shazam Audio Recognition & 16kHz Constellation Picker

Located in `backend/pkg/shazam`, Unbound Music contains a complete, zero-dependency, public Shazam audio recognition implementation.

```mermaid
graph LR
    PCM[Raw Audio Signal] --> RESAMP[16kHz Mono Resampler]
    RESAMP --> HANN[Hann Windowing (2048 Samples, 50% Overlap)]
    HANN --> FFT[Fast Fourier Transform (FFT)]
    FFT --> SPEC[Spectrogram Matrix]
    SPEC --> BANDS[4 Frequency Bands (250Hz - 5kHz)]
    BANDS --> PEAKS[Local Maxima Constellation Peaks]
    PEAKS --> PAIR[Combinatorial Landmark Pairs]
    PAIR --> BIN[2.5KB Binary SignatureRingBuffer]
    BIN --> API[Public Shazam API / Local SQLite]
```

### 5.1 DSP Transformation Pipeline
1. **Downsampling & Windowing**: Audio is resampled to 16,000 Hz single-channel mono PCM. A Hann window of size $N = 2048$ with $50\%$ overlap ($1024$ samples) is applied to minimize spectral leakage:
   $$w(n) = 0.5 \left(1 - \cos\left(\frac{2\pi n}{N-1}\right)\right)$$
2. **Frequency Domain Representation**: Radix-2 Cooley-Tukey FFT transforms each window into a 1024-point complex spectral vector.
3. **Constellation Peak Picker**:
   - The spectrum is segmented into 4 logarithmic frequency bands:
     - Band 1: $250\,\text{Hz} - 520\,\text{Hz}$
     - Band 2: $520\,\text{Hz} - 1450\,\text{Hz}$
     - Band 3: $1450\,\text{Hz} - 3500\,\text{Hz}$
     - Band 4: $3500\,\text{Hz} - 5500\,\text{Hz}$
   - Local maxima exceeding the adaptive noise floor are selected as constellation points $(t_i, f_i)$.
4. **Combinatorial Landmark Pairing**:
   - Each anchor peak $(t_1, f_1)$ is paired with target peaks in a forward time window $[t_1 + 1, t_1 + 10]$ to produce landmark tuples $(f_1, f_2, \Delta t)$.
5. **Binary Signature Ring Buffer**:
   - The landmark tuples are packed into the official 2.5 KB binary Shazam signature format (`SignatureRingBuffer`), prepended with CRC32 checksums.
6. **Zero Cloud Cost**:
   - The binary buffer is posted to Shazam's public discovery endpoint. Zero developer tokens, API subscriptions, or third-party cloud costs are required ($0.00).
   - If offline, the landmarks are compared against the local SQLite fingerprint database using Hamming distance in $< 2\,\text{ms}$.

---

## 6. AutoEq 4,000+ Headphone Calibration & Parametric IIR Biquad Filters

Located in `frontend/.../audio/EqualizerAudioProcessor.kt` and `backend/pkg/autoeq`:

### 6.1 Biquad IIR Filter Mathematics
Parametric equalization operates in direct 16-bit PCM sample calculations using second-order digital Infinite Impulse Response (IIR) biquad filters:
$$y[n] = \frac{b_0 x[n] + b_1 x[n-1] + b_2 x[n-2] - a_1 y[n-1] - a_2 y[n-2]}{a_0}$$

For a peaking filter with center frequency $f_0$, sample rate $f_s$, boost/cut gain $A = 10^{\frac{\text{dB}}{40}}$, and quality factor $Q$:
$$\omega_0 = 2\pi \frac{f_0}{f_s}, \quad \alpha = \frac{\sin(\omega_0)}{2Q}$$
Coefficients are normalized such that $a_0 = 1 + \frac{\alpha}{A}$:
$$b_0 = \frac{1 + \alpha A}{a_0}, \quad b_1 = \frac{-2\cos(\omega_0)}{a_0}, \quad b_2 = \frac{1 - \alpha A}{a_0}$$
$$a_1 = \frac{-2\cos(\omega_0)}{a_0}, \quad a_2 = \frac{1 - \frac{\alpha}{A}}{a_0}$$

### 6.2 10-Band Parametric Topology
Filters are arranged in a 10-stage cascaded serial topology centered at:
$31\,\text{Hz}, 62\,\text{Hz}, 125\,\text{Hz}, 250\,\text{Hz}, 500\,\text{Hz}, 1\,\text{kHz}, 2\,\text{kHz}, 4\,\text{kHz}, 8\,\text{kHz}, 16\,\text{kHz}$.

---

## 7. Volume Normalization & DJ Crossfade Curves

### 7.1 EBU R128 / ReplayGain
Audio tracks have differing mastering levels. Unbound Music uses EBU R128 loudness scanning:
* Calculates integrated loudness in LUFS.
* Normalizes output gain to a target of $-14\,\text{LUFS}$, preventing harsh volume jumps between quiet acoustic tracks and heavily compressed modern pop.

### 7.2 Equal-Power DJ Crossfade
To prevent volume dips in the middle of a crossfade, crossfading employs an **equal-power curve** where total acoustic power remains constant across the fade duration $T$:
$$g_{\text{out}}(t) = \cos\left(\frac{\pi t}{2T}\right), \quad g_{\text{in}}(t) = \sin\left(\frac{\pi t}{2T}\right)$$
$$\left[g_{\text{out}}(t)\right]^2 + \left[g_{\text{in}}(t)\right]^2 = \cos^2\left(\frac{\pi t}{2T}\right) + \sin^2\left(\frac{\pi t}{2T}\right) = 1$$
