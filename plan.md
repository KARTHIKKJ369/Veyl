# Audiophile — Rust-Powered Android Audio Player

**Design read:** premium consumer / audiophile tool, native Android, dark-first, high information density on the "cockpit" screens (DSP/EQ) but airy on Now Playing. No AI-slop defaults — no purple gradients, no generic Material 3 sample-app look, no stock icon packs used untouched.

---

## 1. Architecture

```
┌─────────────────────────────────────────────┐
│  Kotlin/Compose UI (Presentation)            │
│  - Now Playing, Library, EQ/DSP screens      │
│  - MediaSession + Android Auto/Wear surface  │
└───────────────┬───────────────────────────────┘
                │ UniFFI-generated Kotlin bindings
┌───────────────▼───────────────────────────────┐
│  Rust Core (via JNI/UniFFI)                   │
│  ├─ decode: symphonia + custom ALAC/APE glue  │
│  ├─ dsp: resampler, parametric EQ, dither     │
│  ├─ engine: ring-buffer scheduler, gapless,    │
│  │   crossfade, ReplayGain                    │
│  ├─ library: scanner, tag parsing (lofty-rs)  │
│  └─ network: UPnP/DLNA + SMB/WebDAV client    │
└───────────────┬───────────────────────────────┘
                │ AAudio (exclusive/MMAP) via oboe-rs bindings
┌───────────────▼───────────────────────────────┐
│  Android Audio HAL (AAudio exclusive stream)  │
└───────────────────────────────────────────────┘
```

**Why this split:** Rust owns everything perf/correctness-critical (decode, resample, EQ, buffer scheduling). Kotlin/Compose owns everything UI/lifecycle (MediaSession, notifications, permissions, Android Auto). UniFFI generates the FFI boilerplate so you're not hand-rolling JNI signatures.

---

## 2. Format & Decode Layer

| Format | Approach |
|---|---|
| FLAC, WAV, AIFF, Vorbis, Opus, MP3, AAC | `symphonia` (pure Rust, no C deps, actively maintained) |
| ALAC | `symphonia`'s ALAC decoder (works, but verify edge cases) or `alac-decoder` crate fallback |
| APE (Monkey's Audio) | `symphonia` doesn't support it — need `demac`/mac-port bindings or drop APE from v1 |
| DSD/DSF/DFF | Out of scope for v1 (needs DoP or native DSD path — flag as v2) |
| Tags | `lofty-rs` for reading/writing ID3/Vorbis Comments/APEv2 across all formats |

**Decision needed later:** whether APE/DSD support is a hard v1 requirement — recommend deferring both to keep v1 scope sane.

---

## 3. Audio Engine (Audiophile-grade)

- **Output path:** AAudio in **exclusive/MMAP mode** via `oboe-rs` bindings (Rust) — bypasses the Android mixer for bit-perfect output on supported devices; graceful fallback to shared mode with a UI indicator when exclusive isn't available (device/OS dependent).
- **Sample rate/bit depth passthrough:** detect device native rate, switch AAudio stream rate to match track (avoid unnecessary resampling) — "bit-perfect mode" toggle in settings.
- **Resampling (when needed):** `rubato` (Rust, high-quality sinc resampling) for upsampling/rate conversion.
- **DSP chain:** custom biquad-based parametric EQ (Rust), pluggable chain (EQ → compressor/limiter optional → dither on bit-depth reduction). Order matters — document signal flow explicitly in code.
- **Gapless & crossfade:** engine pre-buffers next track's decoded PCM before current track ends; crossfade is a configurable overlap-mix in the ring buffer.
- **ReplayGain:** parse RG tags via lofty-rs, apply gain in the DSP chain (not just volume scaling, to preserve headroom correctly).

---

## 4. Local + Network, Cleanly Separated

Core principle: **one unified `Track` model, two source implementations**, so UI never branches on "is this local or network."

```rust
trait AudioSource {
    fn open(&self) -> Result<Box<dyn MediaStream>>;
    fn metadata(&self) -> TrackMetadata;
}

struct LocalFileSource { path: PathBuf }
struct SmbSource { share: SmbHandle, path: String }
struct UpnpSource { device: UpnpDevice, item_id: String }
```

- **Local:** MediaStore-indexed scan (Android scoped storage) + manual folder watch via Rust-side file walker for SD cards/USB OTG.
- **Network:** UPnP/DLNA discovery (`ssdp-client` + custom DIDL-Lite parsing) for network shares/NAS; SMB via `pavao`/`smbclient` bindings; optionally WebDAV (`reqwest`-based).
- **UI separation:** Library screen has source tabs/filters (Local / NAS / DLNA), but Now Playing, queue, and DSP screens are 100% source-agnostic — this is the "mix cleanly" requirement.
- Network sources are read-through streamed into the same decode pipeline — no separate playback path.

---

## 5. UI (Kotlin + Jetpack Compose) — No AI-Slop Direction

Explicit anti-defaults for whoever builds this:
- No generic Material 3 dynamic-color sample-app look. Build a **custom theme**: pick 1 accent color deliberately (not auto-derived from wallpaper), define your own type scale (don't ship default Roboto — choose a distinct display font for Now Playing + a clean mono/grotesk for data-dense DSP screens).
- No stock "media player template" layout (giant centered art + generic transport bar). Design Now Playing with intent — real hierarchy between art, waveform/spectrum, and transport.
- EQ/DSP screens are the "cockpit" — dense, precise, real-time visual feedback (live frequency response curve redrawn as bands move), not sliders in a plain list.
- Icons: pick one icon family and stay consistent (don't mix Material icons with hand-drawn ones).
- Motion: purposeful only — track transitions, EQ curve updates, queue reordering should animate with physical/spring motion, not default fade-ins everywhere.

**Screens for v1:**
1. Now Playing (art, waveform/spectrum visualizer, transport, quick DSP toggle)
2. Library (Local/NAS/DLNA source tabs, folder + tag browse, search)
3. Queue (reorderable, drag handles)
4. EQ/DSP (parametric bands with live curve, ReplayGain toggle, bit-perfect indicator)
5. Settings (output mode, exclusive/shared, format-specific behavior)

---

## 6. Build/Tooling

- `cargo-ndk` to cross-compile Rust for arm64-v8a (primary), armeabi-v7a, x86_64 (emulator).
- `uniffi-rs` for Kotlin binding generation from a single `.udl`/proc-macro interface definition.
- Gradle module structure: `:app` (Compose UI) + `:core-rust` (native lib wrapper module) + `:core-rust-bindings` (generated).
- CI: GitHub Actions matrix build for each ABI, cargo test for Rust core in isolation (headless, no Android dependency where possible — keep DSP/decode logic testable on desktop).

---

## 7. Suggested Build Order

1. Rust core: decode + basic playback engine (symphonia + oboe-rs), prove bit-perfect output on one device.
2. UniFFI bindings + minimal Compose shell (play/pause/seek) — validate FFI plumbing end-to-end.
3. Gapless + crossfade + ReplayGain.
4. Parametric EQ + DSP chain + live curve UI.
5. Local library scanning + tag browsing UI.
6. Network sources (UPnP first, SMB second) behind the same `AudioSource` trait.
7. Polish pass: custom theme, visualizer, motion, Android Auto integration.

---

## Open Questions / Decisions Deferred
- APE and DSD support: v1 or v2?
- Minimum supported Android version (affects AAudio exclusive-mode availability — needs API 26+, MMAP needs specific OEM support)?
- Android Auto / Wear OS companion — in scope for v1 or later?
