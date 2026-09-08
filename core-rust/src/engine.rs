use parking_lot::Mutex;
use ringbuf::traits::{Consumer, Observer, Producer, Split};
use ringbuf::HeapRb;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use std::time::Duration;
use thiserror::Error;

use crate::decoder::{AudioDecoder, AudioStreamInfo, DecodeError};
use crate::dsd::DsdPlaybackMode;
use crate::dsp::{calculate_levels, calculate_spectrum_16, LevelMetrics, ParametricEqualizer, TpdfDither};
use crate::output::AudioRenderCallback;
use crate::resampler::StreamResampler;
use crate::source::LocalFileSource;

/// Apply perceptual (logarithmic) volume curve.
/// Maps a linear 0.0–1.0 slider to a power curve that matches human hearing.
#[inline(always)]
fn perceptual_volume(linear: f32) -> f32 {
    if linear <= 0.001 {
        0.0
    } else if linear >= 0.999 {
        1.0
    } else {
        linear * linear
    }
}

#[derive(Error, Debug)]
pub enum EngineError {
    #[error("Decode error: {0}")]
    Decode(#[from] DecodeError),
    #[error("Audio output error: {0}")]
    Output(String),
    #[error("No track loaded")]
    NoTrackLoaded,
    #[error("Invalid track index")]
    InvalidIndex,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PlaybackState {
    Stopped,
    Playing,
    Paused,
    Buffering,
    Seeking,
    Ended,
    Error,
}

pub struct TrackQueueItem {
    pub uri: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_seconds: f64,
}

pub struct PlaybackEngineState {
    pub playback_state: PlaybackState,
    pub current_track: Option<TrackQueueItem>,
    pub current_track_index: usize,
    pub current_stream_info: Option<AudioStreamInfo>,
    pub position_seconds: f64,
    pub duration_seconds: f64,
    pub volume: f32,
    pub is_bit_perfect: bool,
    pub is_exclusive_mmap: bool,
    pub crossfade_duration_seconds: f32,
    pub gapless_enabled: bool,
    pub levels: LevelMetrics,
    pub spectrum: Vec<f32>,
}

pub struct PlaybackEngine {
    // Hardware DAC config
    output_sample_rate: Arc<Mutex<f32>>,
    output_channels: usize,

    // DSD Playback Mode
    dsd_mode: Arc<Mutex<DsdPlaybackMode>>,

    // DSP & Audio Processing
    equalizer: Arc<Mutex<ParametricEqualizer>>,
    #[allow(dead_code)]
    dither: Arc<Mutex<TpdfDither>>,
    resampler: Arc<Mutex<Option<StreamResampler>>>,

    // Playback State
    state: Arc<Mutex<PlaybackEngineState>>,
    queue: Arc<Mutex<Vec<TrackQueueItem>>>,

    // Ring Buffer for Audio Thread (Interleaved f32, stereo)
    ring_buffer_producer: Arc<Mutex<ringbuf::wrap::caching::Caching<Arc<HeapRb<f32>>, true, false>>>,
    ring_buffer_consumer: Arc<Mutex<ringbuf::wrap::caching::Caching<Arc<HeapRb<f32>>, false, true>>>,

    // Decoder Management
    active_decoder: Arc<Mutex<Option<AudioDecoder>>>,
    next_decoder: Arc<Mutex<Option<AudioDecoder>>>,

    // Concurrency controls
    is_running: Arc<AtomicBool>,
    seek_requested_frame: Arc<AtomicU64>,
    flush_requested: Arc<AtomicBool>,
    _worker_handle: Option<JoinHandle<()>>,
}

impl PlaybackEngine {
    pub fn new(output_sample_rate: f32, output_channels: usize) -> Self {
        // 1.5 seconds ring buffer capacity
        let buffer_capacity = (output_sample_rate as usize * output_channels) * 3;
        let ring_buffer = HeapRb::<f32>::new(buffer_capacity.max(48000 * 2 * 2));
        let (producer, consumer) = ring_buffer.split();

        let equalizer = Arc::new(Mutex::new(ParametricEqualizer::default_10_band(output_sample_rate, output_channels)));
        let dither = Arc::new(Mutex::new(TpdfDither::new()));
        let resampler = Arc::new(Mutex::new(None));
        let dsd_mode = Arc::new(Mutex::new(DsdPlaybackMode::PcmDecimation));

        let state = Arc::new(Mutex::new(PlaybackEngineState {
            playback_state: PlaybackState::Stopped,
            current_track: None,
            current_track_index: 0,
            current_stream_info: None,
            position_seconds: 0.0,
            duration_seconds: 0.0,
            volume: 1.0,
            is_bit_perfect: true,
            is_exclusive_mmap: true,
            crossfade_duration_seconds: 0.0,
            gapless_enabled: true,
            levels: LevelMetrics::default(),
            spectrum: vec![0.0; 16],
        }));

        let is_running = Arc::new(AtomicBool::new(true));
        let seek_requested_frame = Arc::new(AtomicU64::new(u64::MAX));
        let flush_requested = Arc::new(AtomicBool::new(false));
        let queue = Arc::new(Mutex::new(Vec::new()));
        let active_decoder = Arc::new(Mutex::new(None));
        let next_decoder = Arc::new(Mutex::new(None));

        let engine = Self {
            output_sample_rate: Arc::new(Mutex::new(output_sample_rate)),
            output_channels,
            dsd_mode,
            equalizer,
            dither,
            resampler,
            state,
            queue,
            ring_buffer_producer: Arc::new(Mutex::new(producer)),
            ring_buffer_consumer: Arc::new(Mutex::new(consumer)),
            active_decoder,
            next_decoder,
            is_running,
            seek_requested_frame,
            flush_requested,
            _worker_handle: None,
        };

        engine.start_decode_worker();
        engine
    }

    pub fn set_dsd_mode(&self, mode: DsdPlaybackMode) {
        let mut d = self.dsd_mode.lock();
        *d = mode;
    }

    pub fn get_dsd_mode(&self) -> DsdPlaybackMode {
        *self.dsd_mode.lock()
    }

    pub fn set_output_sample_rate(&self, new_rate: f32) {
        {
            let mut sr = self.output_sample_rate.lock();
            *sr = new_rate;
        }
        {
            let mut eq = self.equalizer.lock();
            eq.sample_rate = new_rate;
            let bands = eq.bands.clone();
            for (i, b) in bands.iter().enumerate() {
                if let Some(band) = eq.bands.get_mut(i) {
                    band.update_params(b.filter_type, b.frequency, b.gain_db, b.q, new_rate);
                }
            }
        }
        // Signal flush so resamplers and buffers adjust to new hardware rate
        self.flush_requested.store(true, Ordering::SeqCst);
    }

    pub fn output_sample_rate(&self) -> f32 {
        *self.output_sample_rate.lock()
    }

    fn start_decode_worker(&self) {
        let is_running = Arc::clone(&self.is_running);
        let active_decoder = Arc::clone(&self.active_decoder);
        let next_decoder = Arc::clone(&self.next_decoder);
        let producer = Arc::clone(&self.ring_buffer_producer);
        let resampler = Arc::clone(&self.resampler);
        let state = Arc::clone(&self.state);
        let seek_req = Arc::clone(&self.seek_requested_frame);
        let flush_req = Arc::clone(&self.flush_requested);
        let queue = Arc::clone(&self.queue);
        let output_rate_arc = Arc::clone(&self.output_sample_rate);
        let dsd_mode_arc = Arc::clone(&self.dsd_mode);
        let consumer = Arc::clone(&self.ring_buffer_consumer);

        thread::Builder::new()
            .name("audiophile-decode-worker".to_string())
            .spawn(move || {
                let mut residual: Vec<f32> = Vec::with_capacity(16384);

                while is_running.load(Ordering::Relaxed) {
                    let output_rate = *output_rate_arc.lock() as u32;

                    // Check if a seek was requested (highest priority)
                    let seek_frame = seek_req.swap(u64::MAX, Ordering::SeqCst);
                    if seek_frame != u64::MAX {
                        residual.clear();
                        let mut dec_guard = active_decoder.lock();
                        if let Some(ref mut dec) = *dec_guard {
                            let target_sec = seek_frame as f64 / dec.info().sample_rate as f64;
                            let _ = dec.seek(target_sec);
                            if let Some(ref mut r) = *resampler.lock() {
                                r.reset();
                            }
                        }
                    }

                    // Check if a flush was requested (new track loaded or rate change)
                    if flush_req.swap(false, Ordering::SeqCst) {
                        residual.clear();
                        if let Some(ref mut r) = *resampler.lock() {
                            r.reset();
                        }
                    }

                    // Check playback state
                    let is_playing = {
                        let s = state.lock();
                        s.playback_state == PlaybackState::Playing
                    };

                    if !is_playing {
                        thread::sleep(Duration::from_millis(15));
                        continue;
                    }

                    // 1. Push residual samples into ring buffer
                    if !residual.is_empty() {
                        let mut prod = producer.lock();
                        let vacant = prod.vacant_len();
                        let can_push = (vacant / 2) * 2; // Always stereo frame-aligned
                        if can_push > 0 {
                            let push_count = residual.len().min(can_push);
                            prod.push_slice(&residual[..push_count]);
                            residual.drain(..push_count);
                        }
                    }

                    // If residual still holds data that couldn't fit, wait briefly
                    if residual.len() > 1024 {
                        thread::sleep(Duration::from_millis(2));
                        continue;
                    }

                    // 2. Check if ring buffer has space for new decoded audio
                    let vacant_len = {
                        let prod = producer.lock();
                        prod.vacant_len()
                    };

                    if vacant_len < 4096 {
                        thread::sleep(Duration::from_millis(2));
                        continue;
                    }

                    // 3. Decode next packet from active track
                    let mut decoded_chunk = None;
                    let mut track_ended = false;

                    {
                        let mut dec_guard = active_decoder.lock();
                        if let Some(ref mut dec) = *dec_guard {
                            match dec.decode_next() {
                                Ok(samples) => {
                                    decoded_chunk = Some(samples);
                                }
                                Err(DecodeError::EndOfStream) => {
                                    track_ended = true;
                                }
                                Err(e) => {
                                    log::warn!("Decode error: {:?}", e);
                                    track_ended = true;
                                }
                            }
                        }
                    }

                    // 4. Apply ReplayGain normalization, resample if necessary, push into ring buffer
                    if let Some(mut raw_samples) = decoded_chunk {
                        // Apply ReplayGain loudness normalization (if tags exist)
                        {
                            let dec_guard = active_decoder.lock();
                            if let Some(ref dec) = *dec_guard {
                                if let Some(rg_db) = dec.info().replaygain_db {
                                    let clamped_db = rg_db.clamp(-24.0, 12.0);
                                    let linear_gain = 10.0f32.powf(clamped_db / 20.0);
                                    for sample in raw_samples.iter_mut() {
                                        *sample *= linear_gain;
                                    }
                                }
                            }
                        }

                        let resampled = {
                            let mut res_guard = resampler.lock();
                            if let Some(ref mut r) = *res_guard {
                                r.process_interleaved(&raw_samples)
                            } else {
                                raw_samples
                            }
                        };

                        residual.extend(resampled);

                        let mut prod = producer.lock();
                        let vacant = prod.vacant_len();
                        let can_push = (vacant / 2) * 2;
                        if can_push > 0 {
                            let push_count = residual.len().min(can_push);
                            prod.push_slice(&residual[..push_count]);
                            residual.drain(..push_count);
                        }
                    }

                    // 5. Handle gapless boundary or track end
                    if track_ended {
                        // Drain any remaining residual before switching
                        while !residual.is_empty() {
                            let mut prod = producer.lock();
                            let vacant = prod.vacant_len();
                            let can_push = (vacant / 2) * 2;
                            if can_push > 0 {
                                let push_count = residual.len().min(can_push);
                                prod.push_slice(&residual[..push_count]);
                                residual.drain(..push_count);
                            }
                            if !residual.is_empty() {
                                thread::sleep(Duration::from_millis(2));
                            }
                        }

                        // Handle Gapless Transition: switch to next preloaded decoder
                        let mut next_dec_guard = next_decoder.lock();
                        let mut active_dec_guard = active_decoder.lock();

                        if let Some(next_dec) = next_dec_guard.take() {
                            let next_info = next_dec.info().clone();
                            if next_info.sample_rate != output_rate {
                                *resampler.lock() = Some(StreamResampler::new(next_info.sample_rate, output_rate, 2));
                            } else {
                                *resampler.lock() = None;
                            }

                            *active_dec_guard = Some(next_dec);

                            let mut s = state.lock();
                            s.current_track_index += 1;
                            let q = queue.lock();
                            if s.current_track_index < q.len() {
                                s.current_track = Some(TrackQueueItem {
                                    uri: q[s.current_track_index].uri.clone(),
                                    title: q[s.current_track_index].title.clone(),
                                    artist: q[s.current_track_index].artist.clone(),
                                    album: q[s.current_track_index].album.clone(),
                                    duration_seconds: q[s.current_track_index].duration_seconds,
                                });
                            }
                            s.duration_seconds = next_info.duration_seconds;
                            s.position_seconds = 0.0;
                            s.current_stream_info = Some(next_info);
                        } else {
                            // Check if more items exist in queue
                            let mut s = state.lock();
                            let q = queue.lock();
                            if s.current_track_index + 1 < q.len() {
                                s.current_track_index += 1;
                                let next_item = &q[s.current_track_index];
                                let source = LocalFileSource::new(&next_item.uri);
                                let dsd_mode = *dsd_mode_arc.lock();
                                if let Ok(new_dec) = AudioDecoder::open_with_dsd_mode(&source, dsd_mode) {
                                    let info = new_dec.info().clone();
                                    if info.sample_rate != output_rate {
                                        *resampler.lock() = Some(StreamResampler::new(info.sample_rate, output_rate, 2));
                                    } else {
                                        *resampler.lock() = None;
                                    }
                                    *active_dec_guard = Some(new_dec);
                                    s.current_track = Some(TrackQueueItem {
                                        uri: next_item.uri.clone(),
                                        title: next_item.title.clone(),
                                        artist: next_item.artist.clone(),
                                        album: next_item.album.clone(),
                                        duration_seconds: next_item.duration_seconds,
                                    });
                                    s.duration_seconds = info.duration_seconds;
                                    s.position_seconds = 0.0;
                                    s.current_stream_info = Some(info);
                                }
                            } else {
                                // Drop locks BEFORE waiting for audio output to drain so render_audio is not blocked!
                                drop(s);
                                drop(q);
                                drop(active_dec_guard);
                                drop(next_dec_guard);

                                // Wait for audio output callback to consume all remaining audio in ring buffer
                                loop {
                                    let occupied = {
                                        let cons = consumer.lock();
                                        cons.occupied_len()
                                    };
                                    if occupied == 0 || flush_req.load(Ordering::SeqCst) || !is_running.load(Ordering::Relaxed) {
                                        break;
                                    }
                                    thread::sleep(Duration::from_millis(15));
                                }
                                if !flush_req.load(Ordering::SeqCst) {
                                    let mut s = state.lock();
                                    s.playback_state = PlaybackState::Ended;
                                }
                            }
                        }
                    }
                }
            })
            .expect("Failed to spawn decode worker");
    }

    /// Load and play a specific track from URI
    pub fn play_track(&self, uri: &str, title: &str, artist: &str, album: &str) -> Result<(), EngineError> {
        let source = LocalFileSource::new(uri);
        let dsd_mode = *self.dsd_mode.lock();
        let decoder = AudioDecoder::open_with_dsd_mode(&source, dsd_mode)?;
        let info = decoder.info().clone();

        // ── FLUSH: Drain all stale audio from the ring buffer before loading new track ──
        {
            let mut consumer = self.ring_buffer_consumer.lock();
            let occupied = consumer.occupied_len();
            if occupied > 0 {
                consumer.skip(occupied);
            }
        }
        // Signal decode worker to clear its residual buffer
        self.flush_requested.store(true, Ordering::SeqCst);

        // Configure dynamic resampler if native track sample rate differs from hardware stream rate
        let output_rate = *self.output_sample_rate.lock() as u32;
        if info.sample_rate != output_rate {
            let mut res_guard = self.resampler.lock();
            *res_guard = Some(StreamResampler::new(info.sample_rate, output_rate, self.output_channels));
        } else {
            let mut res_guard = self.resampler.lock();
            *res_guard = None;
        }

        {
            let mut active = self.active_decoder.lock();
            *active = Some(decoder);
        }

        {
            let mut s = self.state.lock();
            s.playback_state = PlaybackState::Playing;
            s.position_seconds = 0.0;
            s.duration_seconds = info.duration_seconds;
            s.is_bit_perfect = info.sample_rate == output_rate;
            s.current_track = Some(TrackQueueItem {
                uri: uri.to_string(),
                title: title.to_string(),
                artist: artist.to_string(),
                album: album.to_string(),
                duration_seconds: info.duration_seconds,
            });
            s.current_stream_info = Some(info);
        }

        Ok(())
    }

    /// Preload the next track for gapless boundary switching
    pub fn preload_next_track(&self, uri: &str) -> Result<(), EngineError> {
        let source = LocalFileSource::new(uri);
        let dsd_mode = *self.dsd_mode.lock();
        let decoder = AudioDecoder::open_with_dsd_mode(&source, dsd_mode)?;
        let mut next = self.next_decoder.lock();
        *next = Some(decoder);
        Ok(())
    }

    pub fn play(&self) {
        let mut s = self.state.lock();
        if s.current_track.is_some() {
            if s.playback_state == PlaybackState::Ended || s.playback_state == PlaybackState::Stopped {
                s.position_seconds = 0.0;
                self.seek_requested_frame.store(0, Ordering::SeqCst);
            }
            s.playback_state = PlaybackState::Playing;
        }
    }

    pub fn pause(&self) {
        let mut s = self.state.lock();
        s.playback_state = PlaybackState::Paused;
    }

    pub fn stop(&self) {
        let mut s = self.state.lock();
        s.playback_state = PlaybackState::Stopped;
        s.position_seconds = 0.0;
    }

    pub fn seek_to_seconds(&self, seconds: f64) {
        let sample_rate = {
            let s = self.state.lock();
            s.current_stream_info.as_ref().map(|i| i.sample_rate).unwrap_or(44100)
        };
        let frame = (seconds * sample_rate as f64) as u64;

        // Drain consumer ring buffer immediately so new audio plays without 1.5s delay
        {
            let mut consumer = self.ring_buffer_consumer.lock();
            let occupied = consumer.occupied_len();
            if occupied > 0 {
                consumer.skip(occupied);
            }
        }

        {
            let mut s = self.state.lock();
            s.position_seconds = seconds;
        }

        self.seek_requested_frame.store(frame, Ordering::SeqCst);
    }

    pub fn set_volume(&self, volume: f32) {
        let mut s = self.state.lock();
        s.volume = volume.clamp(0.0, 1.0);
    }

    pub fn get_state(&self) -> PlaybackEngineState {
        let s = self.state.lock();
        PlaybackEngineState {
            playback_state: s.playback_state,
            current_track: s.current_track.as_ref().map(|t| TrackQueueItem {
                uri: t.uri.clone(),
                title: t.title.clone(),
                artist: t.artist.clone(),
                album: t.album.clone(),
                duration_seconds: t.duration_seconds,
            }),
            current_track_index: s.current_track_index,
            current_stream_info: s.current_stream_info.clone(),
            position_seconds: s.position_seconds,
            duration_seconds: s.duration_seconds,
            volume: s.volume,
            is_bit_perfect: s.is_bit_perfect,
            is_exclusive_mmap: s.is_exclusive_mmap,
            crossfade_duration_seconds: s.crossfade_duration_seconds,
            gapless_enabled: s.gapless_enabled,
            levels: s.levels.clone(),
            spectrum: s.spectrum.clone(),
        }
    }

    pub fn set_eq_enabled(&self, enabled: bool) {
        let mut eq = self.equalizer.lock();
        eq.enabled = enabled;
    }

    pub fn set_eq_preamp(&self, gain_db: f32) {
        let mut eq = self.equalizer.lock();
        eq.preamp_db = gain_db;
    }

    pub fn set_eq_band(&self, index: usize, gain_db: f32, freq: f32, q: f32) {
        let mut eq = self.equalizer.lock();
        let sr = eq.sample_rate;
        if let Some(band) = eq.bands.get_mut(index) {
            band.gain_db = gain_db;
            band.frequency = freq;
            band.q = q;
            band.update_params(band.filter_type, freq, gain_db, q, sr);
        }
    }

    pub fn get_eq_curve(&self, frequencies: &[f32]) -> Vec<f32> {
        let eq = self.equalizer.lock();
        eq.calculate_curve(frequencies)
    }

    pub fn equalizer(&self) -> Arc<Mutex<ParametricEqualizer>> {
        Arc::clone(&self.equalizer)
    }

    pub fn queue(&self) -> Arc<Mutex<Vec<TrackQueueItem>>> {
        Arc::clone(&self.queue)
    }
}

/// Implementation of the audio render callback called by AAudio/Oboe/CPAL thread
impl AudioRenderCallback for PlaybackEngine {
    fn render_audio(&self, buffer: &mut [f32], frames: usize, channels: usize) {
        let total_samples = frames * channels;

        let is_playing = {
            let s = self.state.lock();
            s.playback_state == PlaybackState::Playing
        };

        if !is_playing {
            buffer.fill(0.0);
            return;
        }

        let read_samples = {
            let mut consumer = self.ring_buffer_consumer.lock();
            consumer.pop_slice(&mut buffer[..total_samples])
        };

        // Fill remaining with silence if underrun
        if read_samples < total_samples {
            buffer[read_samples..total_samples].fill(0.0);
        }

        // Apply Volume with perceptual (logarithmic) curve
        let volume = {
            let s = self.state.lock();
            s.volume
        };
        let applied_volume = perceptual_volume(volume);

        if (applied_volume - 1.0).abs() > f32::EPSILON {
            for sample in buffer.iter_mut() {
                *sample *= applied_volume;
            }
        }

        // Process DSP Equalizer
        {
            let mut eq = self.equalizer.lock();
            eq.process_interleaved(buffer);
        }

        // Soft-clamp and protect against NaN / denormals to guarantee speaker safety
        for sample in buffer.iter_mut() {
            if !sample.is_finite() {
                *sample = 0.0;
            } else {
                *sample = sample.clamp(-1.0, 1.0);
            }
        }

        // Calculate visualizer metrics & spectrum
        let levels = calculate_levels(buffer, channels);
        let spectrum = calculate_spectrum_16(buffer, channels);

        let actual_frames = read_samples / channels.max(1);
        let output_rate = *self.output_sample_rate.lock() as f64;
        let delta_seconds = if output_rate > 0.0 {
            actual_frames as f64 / output_rate
        } else {
            0.0
        };

        {
            let mut s = self.state.lock();
            if delta_seconds > 0.0 {
                let new_pos = s.position_seconds + delta_seconds;
                s.position_seconds = if s.duration_seconds > 0.0 {
                    new_pos.min(s.duration_seconds)
                } else {
                    new_pos
                };
            }
            s.levels = levels;
            s.spectrum = spectrum;
        }
    }
}
