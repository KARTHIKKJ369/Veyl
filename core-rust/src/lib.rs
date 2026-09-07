pub mod decoder;
pub mod dsd;
pub mod dsp;
pub mod engine;
pub mod library;
pub mod network;
pub mod output;
pub mod resampler;
pub mod source;

use std::sync::Arc;
use parking_lot::Mutex;

uniffi::setup_scaffolding!();

#[derive(uniffi::Record, Clone, Debug)]
pub struct TrackInfo {
    pub uri: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: Option<String>,
    pub genre: Option<String>,
    pub year: Option<u32>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    pub duration_seconds: f64,
    pub sample_rate: u32,
    pub bit_depth: Option<u8>,
    pub bitrate: Option<u32>,
    pub channels: u8,
    pub format_name: String,
    pub has_artwork: bool,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct AlbumArtworkData {
    pub mime_type: String,
    pub data: Vec<u8>,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum PlaybackStateEnum {
    Stopped,
    Playing,
    Paused,
    Buffering,
    Seeking,
    Ended,
    Error,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum DsdModeEnum {
    /// High-precision Sinc-FIR decimation to PCM (88.2 kHz, 176.4 kHz, etc.) with SACD +6dB boost
    PcmDecimation,
    /// DoP (DSD over PCM v1.1) bit-perfect transmission for DoP-compatible USB DACs
    DoP,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct AudioMetrics {
    pub rms_left: f32,
    pub rms_right: f32,
    pub peak_left: f32,
    pub peak_right: f32,
    pub spectrum_16: Vec<f32>,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct EngineStatus {
    pub state: PlaybackStateEnum,
    pub current_track: Option<TrackInfo>,
    pub current_track_index: u32,
    pub position_seconds: f64,
    pub duration_seconds: f64,
    pub sample_rate: u32,
    pub bit_depth: Option<u32>,
    pub channels: u32,
    pub codec_name: String,
    pub volume: f32,
    pub is_bit_perfect: bool,
    pub is_exclusive_mmap: bool,
    pub metrics: AudioMetrics,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum FilterTypeEnum {
    Peaking,
    LowShelf,
    HighShelf,
    LowPass,
    HighPass,
    Notch,
    BandPass,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct EqBandInfo {
    pub index: u32,
    pub enabled: bool,
    pub filter_type: FilterTypeEnum,
    pub frequency: f32,
    pub gain_db: f32,
    pub q: f32,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct FrequencyPoint {
    pub frequency: f32,
    pub gain_db: f32,
}

#[derive(uniffi::Object)]
pub struct AudiophileEngineHandle {
    engine: Arc<engine::PlaybackEngine>,
    #[cfg(target_os = "android")]
    audio_output: Arc<Mutex<Option<output::android_oboe::AndroidAudioOutput>>>,
    #[cfg(not(target_os = "android"))]
    audio_output: Arc<Mutex<Option<output::desktop_cpal::DesktopAudioOutput>>>,
}

#[uniffi::export]
impl AudiophileEngineHandle {
    #[uniffi::constructor]
    pub fn new(sample_rate: f32, channels: u32) -> Self {
        #[cfg(target_os = "android")]
        android_logger::init_once(
            android_logger::Config::default().with_max_level(log::LevelFilter::Debug),
        );

        let eng = Arc::new(engine::PlaybackEngine::new(sample_rate, channels as usize));

        // Attempt to start hardware output stream
        #[cfg(target_os = "android")]
        let audio_output = {
            let config = output::AudioOutputConfig {
                sample_rate: sample_rate as u32,
                channels,
                sharing_mode: output::OutputSharingMode::Exclusive,
                sample_format: output::OutputSampleFormat::F32,
                buffer_size_frames: None,
                device_id: None,
            };
            let out = output::android_oboe::AndroidAudioOutput::open(config, Arc::clone(&eng) as Arc<dyn output::AudioRenderCallback>).ok();
            Arc::new(Mutex::new(out))
        };

        #[cfg(not(target_os = "android"))]
        let audio_output = {
            let config = output::AudioOutputConfig {
                sample_rate: sample_rate as u32,
                channels,
                sharing_mode: output::OutputSharingMode::Shared,
                sample_format: output::OutputSampleFormat::F32,
                buffer_size_frames: None,
                device_id: None,
            };
            let out = output::desktop_cpal::DesktopAudioOutput::open(config, Arc::clone(&eng) as Arc<dyn output::AudioRenderCallback>).ok();
            Arc::new(Mutex::new(out))
        };

        Self {
            engine: eng,
            audio_output,
        }
    }

    pub fn set_dsd_mode(&self, mode: DsdModeEnum) {
        let rust_mode = match mode {
            DsdModeEnum::PcmDecimation => dsd::DsdPlaybackMode::PcmDecimation,
            DsdModeEnum::DoP => dsd::DsdPlaybackMode::DoP,
        };
        self.engine.set_dsd_mode(rust_mode);
    }

    pub fn reconfigure_output(&self, sample_rate: u32, channels: u32, device_id: Option<i32>, exclusive: bool) -> bool {
        let sharing_mode = if exclusive {
            output::OutputSharingMode::Exclusive
        } else {
            output::OutputSharingMode::Shared
        };

        let config = output::AudioOutputConfig {
            sample_rate,
            channels,
            sharing_mode,
            sample_format: output::OutputSampleFormat::F32,
            buffer_size_frames: None,
            device_id,
        };

        self.engine.set_output_sample_rate(sample_rate as f32);

        #[cfg(target_os = "android")]
        {
            let mut out_guard = self.audio_output.lock();
            if let Some(mut old_stream) = out_guard.take() {
                let _ = old_stream.stop();
            }
            let new_stream = output::android_oboe::AndroidAudioOutput::open(
                config,
                Arc::clone(&self.engine) as Arc<dyn output::AudioRenderCallback>,
            );
            match new_stream {
                Ok(s) => {
                    *out_guard = Some(s);
                    true
                }
                Err(e) => {
                    log::error!("Failed to reconfigure AAudio output stream: {:?}", e);
                    false
                }
            }
        }

        #[cfg(not(target_os = "android"))]
        {
            let mut out_guard = self.audio_output.lock();
            out_guard.take();
            let new_stream = output::desktop_cpal::DesktopAudioOutput::open(
                config,
                Arc::clone(&self.engine) as Arc<dyn output::AudioRenderCallback>,
            );
            match new_stream {
                Ok(s) => {
                    *out_guard = Some(s);
                    true
                }
                Err(e) => {
                    log::error!("Failed to reconfigure desktop output stream: {:?}", e);
                    false
                }
            }
        }
    }

    pub fn get_output_sample_rate(&self) -> u32 {
        self.engine.output_sample_rate() as u32
    }

    pub fn is_exclusive_mmap(&self) -> bool {
        #[cfg(target_os = "android")]
        {
            let out = self.audio_output.lock();
            out.as_ref().map(|s| s.is_exclusive()).unwrap_or(false)
        }
        #[cfg(not(target_os = "android"))]
        {
            false
        }
    }

    pub fn play_track(&self, uri: String, title: String, artist: String, album: String) -> bool {
        self.engine.play_track(&uri, &title, &artist, &album).is_ok()
    }

    pub fn preload_next_track(&self, uri: String) -> bool {
        self.engine.preload_next_track(&uri).is_ok()
    }

    pub fn play(&self) {
        self.engine.play();
    }

    pub fn pause(&self) {
        self.engine.pause();
    }

    pub fn stop(&self) {
        self.engine.stop();
    }

    pub fn seek(&self, seconds: f64) {
        self.engine.seek_to_seconds(seconds);
    }

    pub fn set_volume(&self, volume: f32) {
        self.engine.set_volume(volume);
    }

    pub fn get_status(&self) -> EngineStatus {
        let s = self.engine.get_state();
        let state_enum = match s.playback_state {
            engine::PlaybackState::Stopped => PlaybackStateEnum::Stopped,
            engine::PlaybackState::Playing => PlaybackStateEnum::Playing,
            engine::PlaybackState::Paused => PlaybackStateEnum::Paused,
            engine::PlaybackState::Buffering => PlaybackStateEnum::Buffering,
            engine::PlaybackState::Seeking => PlaybackStateEnum::Seeking,
            engine::PlaybackState::Ended => PlaybackStateEnum::Ended,
            engine::PlaybackState::Error => PlaybackStateEnum::Error,
        };

        let current_track = s.current_track.map(|t| TrackInfo {
            uri: t.uri,
            title: t.title,
            artist: t.artist,
            album: t.album,
            album_artist: None,
            genre: None,
            year: None,
            track_number: None,
            disc_number: None,
            duration_seconds: t.duration_seconds,
            sample_rate: s.current_stream_info.as_ref().map(|i| i.sample_rate).unwrap_or(44100),
            bit_depth: s.current_stream_info.as_ref().and_then(|i| i.bit_depth).map(|b| b as u8),
            bitrate: None,
            channels: s.current_stream_info.as_ref().map(|i| i.channels as u8).unwrap_or(2),
            format_name: s.current_stream_info.as_ref().map(|i| i.codec_name.clone()).unwrap_or_else(|| "FLAC/PCM".to_string()),
            has_artwork: true,
        });

        let (sample_rate, bit_depth, channels, codec_name) = if let Some(ref info) = s.current_stream_info {
            (info.sample_rate, info.bit_depth, info.channels, info.codec_name.clone())
        } else {
            (44100, Some(16), 2, "PCM".to_string())
        };

        let exclusive_mmap = self.is_exclusive_mmap();

        EngineStatus {
            state: state_enum,
            current_track,
            current_track_index: s.current_track_index as u32,
            position_seconds: s.position_seconds,
            duration_seconds: s.duration_seconds,
            sample_rate,
            bit_depth,
            channels,
            codec_name,
            volume: s.volume,
            is_bit_perfect: s.is_bit_perfect,
            is_exclusive_mmap: exclusive_mmap,
            metrics: AudioMetrics {
                rms_left: s.levels.rms_left,
                rms_right: s.levels.rms_right,
                peak_left: s.levels.peak_left,
                peak_right: s.levels.peak_right,
                spectrum_16: s.spectrum,
            },
        }
    }

    pub fn set_eq_enabled(&self, enabled: bool) {
        self.engine.set_eq_enabled(enabled);
    }

    pub fn set_eq_preamp(&self, gain_db: f32) {
        self.engine.set_eq_preamp(gain_db);
    }

    pub fn get_eq_bands(&self) -> Vec<EqBandInfo> {
        let eq_lock = self.engine.equalizer();
        let eq = eq_lock.lock();
        eq.bands
            .iter()
            .enumerate()
            .map(|(i, b)| {
                let filter_type = match b.filter_type {
                    dsp::FilterType::Peaking => FilterTypeEnum::Peaking,
                    dsp::FilterType::LowShelf => FilterTypeEnum::LowShelf,
                    dsp::FilterType::HighShelf => FilterTypeEnum::HighShelf,
                    dsp::FilterType::LowPass => FilterTypeEnum::LowPass,
                    dsp::FilterType::HighPass => FilterTypeEnum::HighPass,
                    dsp::FilterType::Notch => FilterTypeEnum::Notch,
                    dsp::FilterType::BandPass => FilterTypeEnum::BandPass,
                };
                EqBandInfo {
                    index: i as u32,
                    enabled: b.enabled,
                    filter_type,
                    frequency: b.frequency,
                    gain_db: b.gain_db,
                    q: b.q,
                }
            })
            .collect()
    }

    pub fn set_eq_band(&self, index: u32, gain_db: f32, freq: f32, q: f32) {
        self.engine.set_eq_band(index as usize, gain_db, freq, q);
    }

    /// Calculate the real-time frequency response curve across N logarithmic points between 20 Hz and 20 kHz
    pub fn get_eq_curve(&self, num_points: u32) -> Vec<FrequencyPoint> {
        let count = num_points.clamp(20, 500) as usize;
        let mut freqs = Vec::with_capacity(count);

        let min_log = 20.0f32.log10();
        let max_log = 20000.0f32.log10();
        let step = (max_log - min_log) / (count - 1) as f32;

        for i in 0..count {
            let log_f = min_log + step * i as f32;
            freqs.push(10.0f32.powf(log_f));
        }

        let curve_gains = self.engine.get_eq_curve(&freqs);

        freqs
            .into_iter()
            .zip(curve_gains.into_iter())
            .map(|(frequency, gain_db)| FrequencyPoint { frequency, gain_db })
            .collect()
    }
}

#[derive(uniffi::Object)]
pub struct LibraryEngine;

#[uniffi::export]
impl LibraryEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self
    }

    pub fn scan_directory(&self, path: String) -> Vec<TrackInfo> {
        library::MetadataExtractor::scan_directory(&path)
            .into_iter()
            .map(|t| TrackInfo {
                uri: t.uri,
                title: t.title,
                artist: t.artist,
                album: t.album,
                album_artist: t.album_artist,
                genre: t.genre,
                year: t.year,
                track_number: t.track_number,
                disc_number: t.disc_number,
                duration_seconds: t.duration_seconds,
                sample_rate: t.sample_rate,
                bit_depth: t.bit_depth,
                bitrate: t.bitrate,
                channels: t.channels,
                format_name: t.format_name,
                has_artwork: t.has_artwork,
            })
            .collect()
    }

    pub fn extract_metadata(&self, path: String) -> Option<TrackInfo> {
        library::MetadataExtractor::extract_metadata(&path).ok().map(|t| TrackInfo {
            uri: t.uri,
            title: t.title,
            artist: t.artist,
            album: t.album,
            album_artist: t.album_artist,
            genre: t.genre,
            year: t.year,
            track_number: t.track_number,
            disc_number: t.disc_number,
            duration_seconds: t.duration_seconds,
            sample_rate: t.sample_rate,
            bit_depth: t.bit_depth,
            bitrate: t.bitrate,
            channels: t.channels,
            format_name: t.format_name,
            has_artwork: t.has_artwork,
        })
    }

    pub fn extract_artwork(&self, path: String) -> Option<AlbumArtworkData> {
        library::MetadataExtractor::extract_artwork(&path).ok().flatten().map(|a| AlbumArtworkData {
            mime_type: a.mime_type,
            data: a.data,
        })
    }
}
