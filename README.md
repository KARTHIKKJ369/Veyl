# Veyl — Rust-Powered Audiophile Android Music Player

**Veyl** is a reference-grade, bit-perfect native Android music player engineered for audiophiles. It combines a pure Rust audio DSP & decoding core with a precision Jetpack Compose interface, in-place synchronized lyrics, randomized library exploration, and bespoke audiophile typography.

### 📥 Latest Compiled APK
Download the compiled application directly onto your Android device:
- **[Download veyl-debug.apk](https://github.com/KARTHIKKJ369/Veyl/raw/main/veyl-debug.apk)**

---

## 🌟 Key Features

### 1. Pure Rust Audio Core (`audiophile-core`)
- **Multi-Format Decoder**: Powered by `symphonia` (FLAC, ALAC, WAV, AIFF, Vorbis, Opus, MP3, AAC).
- **10-Band Biquad Parametric EQ**: Direct Form II Transposed filter bank with real-time transfer function calculation:
  $$H(z) = \frac{b_0 + b_1 z^{-1} + b_2 z^{-2}}{1 + a_1 z^{-1} + a_2 z^{-2}}$$
- **Live Transfer Function Curve**: Dynamic frequency response computation ($20\text{ Hz} - 20\text{ kHz}$) rendered directly on an interactive Compose canvas with draggable band nodes.
- **High-Fidelity Sinc Resampling**: Wrapped `rubato` sinc interpolation with 64-point Blackman-Harris windowing.
- **TPDF Dither**: Triangular Probability Density Function noise decorrelation on bit-depth quantization.
- **Lock-Free Concurrency**: Atomic ring-buffer scheduler for zero-drop audio rendering with full buffer drainage before track termination.
- **Gapless & Crossfade**: Background track pre-buffering across track boundaries.
- **Lofty Tag Scanner**: Comprehensive tag and embedded album art parsing.

### 2. Android HAL & Output Layer
- **AAudio MMAP Exclusive Mode**: Bypasses Android AudioFlinger mixer for bit-perfect direct DAC output on supported hardware.
- **Graceful Shared Mode Fallback**: Automatically falls back to shared stream when exclusive mode is unsupported by OEM.
- **Background Playback & Control Panel**: Full foreground playback service supporting Android 11+ / HyperOS system media controls in notification center and lock screen.
- **Desktop Fallback (`cpal`)**: Run and test audio core headless on macOS and Linux.

### 3. Jetpack Compose Audiophile UI
- **In-Place Synchronized Lyrics**: Flip between album art and synchronized lyrics with ambient album art backdrop, smooth line tracking, word-level illumination, and tap-to-seek.
- **Randomized Library & Quick Shuffle**: Dynamic randomized "All Songs" view with dedicated shuffle playback in both home and library sections.
- **Dark-First Tactical Theme**: OLED Black (`#07090C`), Elevated Slate (`#171C26`), Signal Amber (`#FFB300`), Cyber Cyan (`#00E5FF`), and Spectral Green (`#00E676`).
- **Now Playing Cockpit**: Format badges (`FLAC 192kHz/24bit`, sample rate, bit depth), 16-band live spectrum visualizer, millisecond precision scrubber.
- **DSP Cockpit**: Live interactive frequency response curve, 10 parametric band controls, preamp fader, ReplayGain & Dither toggles.
- **Library & Search**: Instant filter by Hi-Res, Artists, Albums, and storage folders.
- **Playback Queue**: Gapless boundary indicators and reorderable track list.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│              Jetpack Compose UI (Presentation)            │
│  - Now Playing (Art, Spectrum, Format Badges, Scrubber)  │
│  - DSP Cockpit (Live Mathematical Biquad Curve Canvas)   │
│  - Library, Queue, and Audio Engine Settings             │
│  - Foreground PlaybackService & MediaSession             │
└────────────────────────────┬─────────────────────────────┘
                             │ UniFFI (Kotlin <-> Rust)
┌────────────────────────────▼─────────────────────────────┐
│                 Rust Core (`audiophile-core`)            │
│  ├─ decode: symphonia (FLAC, ALAC, WAV, MP3, AAC, Opus)  │
│  ├─ dsp: 10-band biquad EQ, curve generator, rubato sinc │
│  ├─ engine: atomic ring buffer, gapless scheduler        │
│  ├─ library: lofty-rs tag parsing & art extraction       │
│  └─ output: oboe-rs AAudio (MMAP exclusive / shared)     │
└──────────────────────────────────────────────────────────┘
```

---

## 🚀 Building & Running

### 1. Build and Test Rust Core
```bash
cd core-rust
cargo test
cargo build --release
```

### 2. Generate UniFFI Kotlin Bindings
```bash
cargo run --bin uniffi-bindgen generate \
  --library target/debug/libaudiophile_core.dylib \
  --language kotlin \
  --out-dir ../core-rust-bindings/src/main/java
```

### 3. Cross-Compile for Android Target ABIs
```bash
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
```

### 4. Build Android Application
```bash
gradle assembleDebug
```
