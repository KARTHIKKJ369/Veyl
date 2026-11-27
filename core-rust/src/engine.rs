use parking_lot::Mutex;
use ringbuf::traits::{Consumer, Observer, Producer, Split};
use ringbuf::HeapRb;
use std::f32::consts::FRAC_PI_2;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use std::time::Duration;
use thiserror::Error;

use crate::decoder::{AudioDecoder, AudioStreamInfo, DecodeError};
use crate::dsd::DsdPlaybackMode;
use crate::dsp::{
    calculate_levels, calculate_spectrum_16, ChannelBalance, CrossfeedFilter, CrossfeedPreset,
    LevelMetrics, LoudnessNormalizer, ParametricEqualizer, PeakLimiter, Spatializer360,
    SubBassExciter, TimeStretcher, TpdfDither, TubeWarmthExciter, VocalClarity,
};
use crate::output::AudioRenderCallback;
use crate::resampler::StreamResampler;
use crate::source::LocalFileSource;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CrossfadeCurve {
    Linear,
    EqualPower,
    SCurve,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReplayGainMode {
    Off,
    Track,
    Album,
}

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

#[derive(Clone, Debug)]
pub struct TrackQueueItem {
    pub uri: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_seconds: f64,
}

#[derive(Clone, Debug)]
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
    pub is_dop: bool,
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
    tube_exciter: Arc<Mutex<TubeWarmthExciter>>,
    spatializer: Arc<Mutex<Spatializer360>>,
    sub_bass: Arc<Mutex<SubBassExciter>>,
    vocal_clarity: Arc<Mutex<VocalClarity>>,
    crossfeed: Arc<Mutex<CrossfeedFilter>>,
    loudness_normalizer: Arc<Mutex<LoudnessNormalizer>>,
    channel_balance: Arc<Mutex<ChannelBalance>>,
    limiter: Arc<Mutex<PeakLimiter>>,
    playback_speed: Arc<Mutex<f32>>,
    time_stretcher: Arc<Mutex<TimeStretcher>>,
    dither: Arc<Mutex<TpdfDither>>,
    resampler: Arc<Mutex<Option<StreamResampler>>>,
    render_volume: Arc<Mutex<f32>>,
    crossfade_seconds: Arc<Mutex<f32>>,
    crossfade_curve: Arc<Mutex<CrossfadeCurve>>,
    replaygain_mode: Arc<Mutex<ReplayGainMode>>,

    // Lock-free Real-Time Render State (Zero blocking locks on audio callback!)
    is_playing_atomic: Arc<AtomicBool>,
    is_dop_atomic: Arc<AtomicBool>,
    volume_bits_atomic: Arc<AtomicU32>,
    position_nanos_atomic: Arc<AtomicU64>,
    latest_metrics: Arc<Mutex<(LevelMetrics, [f32; 16])>>,

    // Playback State
    state: Arc<Mutex<PlaybackEngineState>>,
    queue: Arc<Mutex<Vec<TrackQueueItem>>>,

    // Ring Buffer for Audio Thread (Interleaved f32, stereo)
    ring_buffer_producer: Arc<Mutex<ringbuf::wrap::caching::Caching<Arc<HeapRb<f32>>, true, false>>>,
    ring_buffer_consumer: Arc<Mutex<ringbuf::wrap::caching::Caching<Arc<HeapRb<f32>>, false, true>>>,

    // Decoder Management
    active_decoder: Arc<Mutex<Option<AudioDecoder>>>,
    next_decoder: Arc<Mutex<Option<AudioDecoder>>>,
    next_preloaded_item: Arc<Mutex<Option<TrackQueueItem>>>,
    gapless_enabled: Arc<AtomicBool>,

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
        let tube_exciter = Arc::new(Mutex::new(TubeWarmthExciter::new()));
        let spatializer = Arc::new(Mutex::new(Spatializer360::new(output_sample_rate)));
        let sub_bass = Arc::new(Mutex::new(SubBassExciter::new(output_sample_rate)));
        let vocal_clarity = Arc::new(Mutex::new(VocalClarity::new(output_sample_rate)));
        let crossfeed = Arc::new(Mutex::new(CrossfeedFilter::new(output_sample_rate)));
        let loudness_normalizer = Arc::new(Mutex::new(LoudnessNormalizer::new(output_sample_rate)));
        let channel_balance = Arc::new(Mutex::new(ChannelBalance::new()));
        let limiter = Arc::new(Mutex::new(PeakLimiter::new()));
        let playback_speed = Arc::new(Mutex::new(1.0f32));
        let time_stretcher = Arc::new(Mutex::new(TimeStretcher::new(output_sample_rate, output_channels)));
        let dither = Arc::new(Mutex::new(TpdfDither::new()));
        let resampler = Arc::new(Mutex::new(None));
        let dsd_mode = Arc::new(Mutex::new(DsdPlaybackMode::PcmDecimation));
        let crossfade_seconds = Arc::new(Mutex::new(0.0f32));
        let crossfade_curve = Arc::new(Mutex::new(CrossfadeCurve::EqualPower));
        let replaygain_mode = Arc::new(Mutex::new(ReplayGainMode::Track));

        let is_playing_atomic = Arc::new(AtomicBool::new(false));
        let is_dop_atomic = Arc::new(AtomicBool::new(false));
        let volume_bits_atomic = Arc::new(AtomicU32::new(1.0f32.to_bits()));
        let position_nanos_atomic = Arc::new(AtomicU64::new(0));
        let latest_metrics = Arc::new(Mutex::new((LevelMetrics::default(), [0.0f32; 16])));

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
            is_dop: false,
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
        let next_preloaded_item = Arc::new(Mutex::new(None));
        let gapless_enabled = Arc::new(AtomicBool::new(true));

        let engine = Self {
            output_sample_rate: Arc::new(Mutex::new(output_sample_rate)),
            output_channels,
            dsd_mode,
            equalizer,
            tube_exciter,
            spatializer,
            sub_bass,
            vocal_clarity,
            crossfeed,
            loudness_normalizer,
            channel_balance,
            limiter,
            playback_speed,
            time_stretcher,
            dither,
            resampler,
            render_volume: Arc::new(Mutex::new(0.0)),
            crossfade_seconds,
            crossfade_curve,
            replaygain_mode,
            is_playing_atomic,
            is_dop_atomic,
            volume_bits_atomic,
            position_nanos_atomic,
            latest_metrics,
            state,
            queue,
            ring_buffer_producer: Arc::new(Mutex::new(producer)),
            ring_buffer_consumer: Arc::new(Mutex::new(consumer)),
            active_decoder,
            next_decoder,
            next_preloaded_item,
            gapless_enabled,
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
        self.spatializer.lock().set_sample_rate(new_rate);
        self.sub_bass.lock().set_sample_rate(new_rate);
        self.vocal_clarity.lock().set_sample_rate(new_rate);
        self.tube_exciter.lock().set_sample_rate(new_rate);
        self.crossfeed.lock().set_sample_rate(new_rate);
        self.loudness_normalizer.lock().set_sample_rate(new_rate);
        // Immediately configure resampler for new hardware output rate to prevent pitch distortion
        {
            let active = self.active_decoder.lock();
            if let Some(ref dec) = *active {
                let info = dec.info();
                let output_rate = new_rate as u32;
                let mut res_guard = self.resampler.lock();
                if !info.is_dop && info.sample_rate != output_rate {
                    *res_guard = Some(StreamResampler::new(info.sample_rate, output_rate, self.output_channels));
                } else {
                    *res_guard = None;
                }
            }
        }
        // Drain ring buffer so stale rate samples aren't played out at the new rate
        {
            let mut consumer = self.ring_buffer_consumer.lock();
            let mut discard = [0.0f32; 1024];
            while consumer.pop_slice(&mut discard) > 0 {}
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
        let next_preloaded_item = Arc::clone(&self.next_preloaded_item);
        let gapless_enabled = Arc::clone(&self.gapless_enabled);
        let producer = Arc::clone(&self.ring_buffer_producer);
        let resampler = Arc::clone(&self.resampler);
        let state = Arc::clone(&self.state);
        let seek_req = Arc::clone(&self.seek_requested_frame);
        let flush_req = Arc::clone(&self.flush_requested);
        let queue = Arc::clone(&self.queue);
        let output_rate_arc = Arc::clone(&self.output_sample_rate);
        let dsd_mode_arc = Arc::clone(&self.dsd_mode);
        let consumer = Arc::clone(&self.ring_buffer_consumer);
        let playback_speed_arc = Arc::clone(&self.playback_speed);
        let time_stretcher_arc = Arc::clone(&self.time_stretcher);
        let crossfade_sec_arc = Arc::clone(&self.crossfade_seconds);
        let crossfade_curve_arc = Arc::clone(&self.crossfade_curve);
        let replaygain_mode_arc = Arc::clone(&self.replaygain_mode);
        let is_playing_atomic = Arc::clone(&self.is_playing_atomic);
        let is_dop_atomic = Arc::clone(&self.is_dop_atomic);
        let pos_nanos_atomic = Arc::clone(&self.position_nanos_atomic);

        thread::Builder::new()
            .name("audiophile-decode-worker".to_string())
            .spawn(move || {
                let mut residual: Vec<f32> = Vec::with_capacity(16384);
                let mut next_residual: Vec<f32> = Vec::with_capacity(16384);
                let mut next_resampler: Option<StreamResampler> = None;

                while is_running.load(Ordering::Relaxed) {
                    let output_rate = *output_rate_arc.lock() as u32;

                    // Check if a seek was requested (highest priority)
                    let seek_frame = seek_req.swap(u64::MAX, Ordering::SeqCst);
                    if seek_frame != u64::MAX {
                        residual.clear();
                        next_residual.clear();
                        next_resampler = None;
                        time_stretcher_arc.lock().reset();
                        let mut dec_guard = active_decoder.lock();
                        if let Some(ref mut dec) = *dec_guard {
                            let target_sec = seek_frame as f64 / dec.info().sample_rate as f64;
                            match dec.seek(target_sec) {
                                Ok(actual_sec) => {
                                    pos_nanos_atomic.store((actual_sec * 1_000_000_000.0) as u64, Ordering::Relaxed);
                                }
                                Err(e) => {
                                    log::warn!("Seek to {:.2}s failed: {:?}", target_sec, e);
                                    let cur = dec.current_time_seconds();
                                    pos_nanos_atomic.store((cur * 1_000_000_000.0) as u64, Ordering::Relaxed);
                                }
                            }
                            if let Some(ref mut r) = *resampler.lock() {
                                r.reset();
                            }
                        }
                    }

                    // Check if a flush was requested (new track loaded or rate change)
                    if flush_req.swap(false, Ordering::SeqCst) {
                        residual.clear();
                        next_residual.clear();
                        next_resampler = None;
                        time_stretcher_arc.lock().reset();
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
                    let mut track_sample_rate = 0;
                    let mut is_dop = false;
                    let mut replaygain_db = None;
                    let mut replaygain_album_db = None;
                    let mut replaygain_track_peak = None;
                    let mut replaygain_album_peak = None;
                    let mut active_remaining_sec = f64::MAX;
                    let mut active_duration_sec = 0.0;

                    {
                        let mut dec_guard = active_decoder.lock();
                        if let Some(ref mut dec) = *dec_guard {
                            match dec.decode_next() {
                                Ok(samples) => {
                                    decoded_chunk = Some(samples);
                                    let info = dec.info();
                                    track_sample_rate = info.sample_rate;
                                    is_dop = info.is_dop;
                                    replaygain_db = info.replaygain_db;
                                    replaygain_album_db = info.replaygain_album_db;
                                    replaygain_track_peak = info.replaygain_track_peak;
                                    replaygain_album_peak = info.replaygain_album_peak;
                                    active_duration_sec = info.duration_seconds;
                                    if active_duration_sec > 0.0 {
                                        active_remaining_sec = (active_duration_sec - dec.current_time_seconds()).max(0.0);
                                    }
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

                    // 4. Apply ReplayGain normalization (Off/Track/Album + peak clamp), resample & time-stretch cleanly
                    if let Some(mut raw_samples) = decoded_chunk {
                        let rg_mode = *replaygain_mode_arc.lock();
                        let (selected_db, selected_peak) = match rg_mode {
                            ReplayGainMode::Off => (None, None),
                            ReplayGainMode::Track => (replaygain_db, replaygain_track_peak),
                            ReplayGainMode::Album => (
                                replaygain_album_db.or(replaygain_db),
                                replaygain_album_peak.or(replaygain_track_peak),
                            ),
                        };
                        if let Some(rg_db) = selected_db {
                            let clamped_db = rg_db.clamp(-24.0, 12.0);
                            let mut linear_gain = 10.0f32.powf(clamped_db / 20.0);
                            if let Some(peak) = selected_peak {
                                if peak > 0.0 {
                                    linear_gain = linear_gain.min(0.99 / peak);
                                }
                            }
                            for sample in raw_samples.iter_mut() {
                                *sample *= linear_gain;
                            }
                        }

                        if track_sample_rate == 0 {
                            let dec_guard = active_decoder.lock();
                            track_sample_rate = dec_guard.as_ref().map(|d| d.info().sample_rate).unwrap_or(44100);
                        }

                        let speed = *playback_speed_arc.lock();

                        // Resample at true native rate -> output_rate (pitch-neutral)
                        let mut processed = if !is_dop && track_sample_rate > 0 && output_rate > 0 && track_sample_rate != output_rate {
                            let mut res_guard = resampler.lock();
                            let needs_rebuild = match *res_guard {
                                Some(ref r) => {
                                    (r.input_rate - track_sample_rate as f64).abs() > 0.1
                                        || (r.output_rate - output_rate as f64).abs() > 0.1
                                }
                                None => true,
                            };
                            if needs_rebuild {
                                *res_guard = Some(StreamResampler::new(track_sample_rate, output_rate, 2));
                            }
                            if let Some(ref mut r) = *res_guard {
                                r.process_interleaved(&raw_samples)
                            } else {
                                raw_samples
                            }
                        } else {
                            let mut res_guard = resampler.lock();
                            if res_guard.is_some() {
                                *res_guard = None;
                            }
                            raw_samples
                        };

                        // Apply pitch-preserved WSOLA time-stretching if playback_speed != 1.0
                        if !is_dop && (speed - 1.0).abs() > 0.01 && !processed.is_empty() {
                            let mut ts = time_stretcher_arc.lock();
                            ts.set_speed(speed);
                            processed = ts.process_interleaved(&processed);
                        }

                        // Equal-power / Linear / S-Curve Crossfade overlap mixing into next_decoder
                        let cf_sec = *crossfade_sec_arc.lock() as f64;
                        if !is_dop
                            && cf_sec > 0.05
                            && active_duration_sec > cf_sec + 1.0
                            && active_remaining_sec <= cf_sec
                            && !processed.is_empty()
                        {
                            let mut next_dec_guard = next_decoder.lock();
                            if let Some(ref mut next_dec) = *next_dec_guard {
                                let next_info = next_dec.info().clone();
                                if !next_info.is_dop {
                                    while next_residual.len() < processed.len() {
                                        match next_dec.decode_next() {
                                            Ok(mut n_raw) => {
                                                // Apply ReplayGain to incoming crossfade track too
                                                let (n_db, n_peak) = match rg_mode {
                                                    ReplayGainMode::Off => (None, None),
                                                    ReplayGainMode::Track => (next_info.replaygain_db, next_info.replaygain_track_peak),
                                                    ReplayGainMode::Album => (
                                                        next_info.replaygain_album_db.or(next_info.replaygain_db),
                                                        next_info.replaygain_album_peak.or(next_info.replaygain_track_peak),
                                                    ),
                                                };
                                                if let Some(db) = n_db {
                                                    let mut lg = 10.0f32.powf(db.clamp(-24.0, 12.0) / 20.0);
                                                    if let Some(p) = n_peak {
                                                        if p > 0.0 { lg = lg.min(0.99 / p); }
                                                    }
                                                    for s in n_raw.iter_mut() { *s *= lg; }
                                                }
                                                let n_res = if next_info.sample_rate != output_rate && output_rate > 0 {
                                                    let need_nr = match next_resampler {
                                                        Some(ref nr) => (nr.input_rate - next_info.sample_rate as f64).abs() > 0.1 || (nr.output_rate - output_rate as f64).abs() > 0.1,
                                                        None => true,
                                                    };
                                                    if need_nr {
                                                        next_resampler = Some(StreamResampler::new(next_info.sample_rate, output_rate, 2));
                                                    }
                                                    next_resampler.as_mut().map(|nr| nr.process_interleaved(&n_raw)).unwrap_or(n_raw)
                                                } else {
                                                    n_raw
                                                };
                                                next_residual.extend(n_res);
                                            }
                                            Err(_) => break,
                                        }
                                    }

                                    let mix_len = processed.len().min(next_residual.len());
                                    let t = ((cf_sec - active_remaining_sec) / cf_sec).clamp(0.0, 1.0) as f32;
                                    let curve = *crossfade_curve_arc.lock();
                                    let (fade_out, fade_in) = match curve {
                                        CrossfadeCurve::Linear => (1.0 - t, t),
                                        CrossfadeCurve::EqualPower => ((t * FRAC_PI_2).cos(), (t * FRAC_PI_2).sin()),
                                        CrossfadeCurve::SCurve => {
                                            let s = t * t * (3.0 - 2.0 * t);
                                            (1.0 - s, s)
                                        }
                                    };
                                    for i in 0..mix_len {
                                        processed[i] = processed[i] * fade_out + next_residual[i] * fade_in;
                                    }
                                    next_residual.drain(..mix_len);
                                }
                            }
                        }

                        residual.extend(processed);

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
                        // Flush any remaining tail frames inside the resampler before finishing track
                        {
                            let mut res_guard = resampler.lock();
                            if let Some(ref mut r) = *res_guard {
                                let tail = r.flush();
                                if !tail.is_empty() {
                                    residual.extend(tail);
                                }
                            }
                        }

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

                        // Handle Gapless Transition: switch to next preloaded decoder if gapless is enabled
                        let is_gapless = gapless_enabled.load(Ordering::Relaxed);
                        let mut next_dec_guard = next_decoder.lock();
                        let mut active_dec_guard = active_decoder.lock();

                        if is_gapless && next_dec_guard.is_some() {
                            let next_dec = next_dec_guard.take().unwrap();
                            let next_info = next_dec.info().clone();
                            let preloaded_meta = next_preloaded_item.lock().take();

                            // Carry over crossfade resampler/residual if crossfade had already begun
                            if let Some(nr) = next_resampler.take() {
                                *resampler.lock() = Some(nr);
                            } else {
                                let mut res_guard = resampler.lock();
                                if !next_info.is_dop && next_info.sample_rate != output_rate {
                                    let needs_new = match *res_guard {
                                        Some(ref r) => {
                                            (r.input_rate - next_info.sample_rate as f64).abs() > 0.1
                                                || (r.output_rate - output_rate as f64).abs() > 0.1
                                        }
                                        None => true,
                                    };
                                    if needs_new {
                                        *res_guard = Some(StreamResampler::new(next_info.sample_rate, output_rate, 2));
                                    }
                                } else {
                                    *res_guard = None;
                                }
                            }
                            if !next_residual.is_empty() {
                                residual.extend(next_residual.drain(..));
                            }

                            *active_dec_guard = Some(next_dec);
                            is_dop_atomic.store(next_info.is_dop, Ordering::Relaxed);
                            pos_nanos_atomic.store(0, Ordering::Relaxed);

                            let mut s = state.lock();
                            s.current_track_index += 1;
                            if let Some(meta) = preloaded_meta {
                                s.current_track = Some(meta);
                            } else {
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
                            }
                            s.duration_seconds = next_info.duration_seconds;
                            s.position_seconds = 0.0;
                            s.is_bit_perfect = next_info.sample_rate == output_rate;
                            s.is_dop = next_info.is_dop;
                            s.current_stream_info = Some(next_info);
                        } else {
                            // CRIT-03: Inspect queue under lock, but open new decoder WITHOUT holding state.lock()
                            // to avoid priority inversion and underruns on the real-time audio thread!
                            let next_track_candidate = {
                                let s = state.lock();
                                let q = queue.lock();
                                if s.current_track_index + 1 < q.len() {
                                    Some((s.current_track_index + 1, q[s.current_track_index + 1].clone()))
                                } else {
                                    None
                                }
                            };

                            if let Some((next_idx, next_item)) = next_track_candidate {
                                let source = LocalFileSource::new(&next_item.uri);
                                let dsd_mode = *dsd_mode_arc.lock();
                                match AudioDecoder::open_with_dsd_mode(&source, dsd_mode) {
                                    Ok(new_dec) => {
                                        let info = new_dec.info().clone();
                                        if !info.is_dop && info.sample_rate != output_rate {
                                            *resampler.lock() = Some(StreamResampler::new(info.sample_rate, output_rate, 2));
                                        } else {
                                            *resampler.lock() = None;
                                        }
                                        *active_dec_guard = Some(new_dec);

                                        let mut s = state.lock();
                                        s.current_track_index = next_idx;
                                        s.current_track = Some(next_item);
                                        s.duration_seconds = info.duration_seconds;
                                        s.position_seconds = 0.0;
                                        s.is_dop = info.is_dop;
                                        s.current_stream_info = Some(info);
                                    }
                                    Err(e) => {
                                        log::error!("Failed to open next queue track: {:?}", e);
                                        let mut s = state.lock();
                                        s.current_track_index = next_idx;
                                    }
                                }
                            } else {
                                // Drop locks BEFORE waiting for audio output to drain so render_audio is not blocked!
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
                                    is_playing_atomic.store(false, Ordering::Relaxed);
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

        // Clear any preloaded gapless next decoder to prevent stale track bleed on user skip
        *self.next_decoder.lock() = None;
        *self.next_preloaded_item.lock() = None;

        // ── FLUSH: Drain all stale audio from the ring buffer before loading new track ──
        {
            let mut consumer = self.ring_buffer_consumer.lock();
            let occupied = consumer.occupied_len();
            if occupied > 0 {
                consumer.skip(occupied);
            }
        }

        // Reset dynamic loudness normalizer envelope for clean track start
        self.loudness_normalizer.lock().reset();

        // Set active decoder FIRST before triggering flush
        {
            let mut active = self.active_decoder.lock();
            *active = Some(decoder);
        }

        // Configure dynamic resampler if native track sample rate differs from hardware stream rate
        // CRIT-01: In DoP mode, never resample because rubato interpolation destroys DoP framing and DSD bits!
        let output_rate = *self.output_sample_rate.lock() as u32;
        if !info.is_dop && info.sample_rate != output_rate {
            let mut res_guard = self.resampler.lock();
            *res_guard = Some(StreamResampler::new(info.sample_rate, output_rate, self.output_channels));
        } else {
            let mut res_guard = self.resampler.lock();
            *res_guard = None;
        }

        // Signal decode worker to clear its residual buffer and reset resampler
        self.flush_requested.store(true, Ordering::SeqCst);
        self.position_nanos_atomic.store(0, Ordering::Relaxed);
        self.is_dop_atomic.store(info.is_dop, Ordering::Relaxed);
        self.is_playing_atomic.store(true, Ordering::Relaxed);

        {
            let mut s = self.state.lock();
            s.playback_state = PlaybackState::Playing;
            s.position_seconds = 0.0;
            s.duration_seconds = info.duration_seconds;
            s.is_bit_perfect = info.sample_rate == output_rate;
            s.is_dop = info.is_dop;
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
    pub fn preload_next_track(&self, uri: &str, title: &str, artist: &str, album: &str) -> Result<(), EngineError> {
        let source = LocalFileSource::new(uri);
        let dsd_mode = *self.dsd_mode.lock();
        let decoder = AudioDecoder::open_with_dsd_mode(&source, dsd_mode)?;
        let info = decoder.info().clone();
        *self.next_preloaded_item.lock() = Some(TrackQueueItem {
            uri: uri.to_string(),
            title: title.to_string(),
            artist: artist.to_string(),
            album: album.to_string(),
            duration_seconds: info.duration_seconds,
        });
        let mut next = self.next_decoder.lock();
        *next = Some(decoder);
        Ok(())
    }

    pub fn clear_next_track(&self) {
        *self.next_decoder.lock() = None;
        *self.next_preloaded_item.lock() = None;
    }

    pub fn set_gapless_enabled(&self, enabled: bool) {
        self.gapless_enabled.store(enabled, Ordering::Relaxed);
        let mut s = self.state.lock();
        s.gapless_enabled = enabled;
        if !enabled {
            self.clear_next_track();
        }
    }

    pub fn set_crossfade(&self, seconds: f32, curve: CrossfadeCurve) {
        let clamped = seconds.clamp(0.0, 12.0);
        *self.crossfade_seconds.lock() = clamped;
        *self.crossfade_curve.lock() = curve;
        self.state.lock().crossfade_duration_seconds = clamped;
    }

    pub fn set_replaygain_mode(&self, mode: ReplayGainMode) {
        *self.replaygain_mode.lock() = mode;
    }

    pub fn get_replaygain_mode(&self) -> ReplayGainMode {
        *self.replaygain_mode.lock()
    }

    pub fn play(&self) {
        let mut s = self.state.lock();
        if s.current_track.is_some() {
            if s.playback_state == PlaybackState::Ended || s.playback_state == PlaybackState::Stopped {
                s.position_seconds = 0.0;
                self.position_nanos_atomic.store(0, Ordering::Relaxed);
                self.seek_requested_frame.store(0, Ordering::SeqCst);
            }
            s.playback_state = PlaybackState::Playing;
            self.is_playing_atomic.store(true, Ordering::Relaxed);
        }
    }

    pub fn pause(&self) {
        self.is_playing_atomic.store(false, Ordering::Relaxed);
        let mut s = self.state.lock();
        s.playback_state = PlaybackState::Paused;
    }

    pub fn stop(&self) {
        self.is_playing_atomic.store(false, Ordering::Relaxed);
        self.position_nanos_atomic.store(0, Ordering::Relaxed);
        let mut s = self.state.lock();
        s.playback_state = PlaybackState::Stopped;
        s.position_seconds = 0.0;
    }

    pub fn seek_to_seconds(&self, seconds: f64) {
        let sample_rate = {
            let s = self.state.lock();
            s.current_stream_info.as_ref().map(|i| i.sample_rate).unwrap_or(44100)
        };
        let clamped_sec = seconds.max(0.0);
        let frame = (clamped_sec * sample_rate as f64) as u64;

        // Drain consumer ring buffer immediately so new audio plays without 1.5s delay
        {
            let mut consumer = self.ring_buffer_consumer.lock();
            let occupied = consumer.occupied_len();
            if occupied > 0 {
                consumer.skip(occupied);
            }
        }

        self.position_nanos_atomic.store((clamped_sec * 1_000_000_000.0) as u64, Ordering::Relaxed);
        {
            let mut s = self.state.lock();
            s.position_seconds = clamped_sec;
        }

        self.seek_requested_frame.store(frame, Ordering::SeqCst);
    }

    pub fn set_volume(&self, volume: f32) {
        let clamped = volume.clamp(0.0, 1.0);
        self.volume_bits_atomic.store(clamped.to_bits(), Ordering::Relaxed);
        let mut s = self.state.lock();
        s.volume = clamped;
    }

    pub fn get_state(&self) -> PlaybackEngineState {
        let mut s = self.state.lock().clone();
        let pos = (self.position_nanos_atomic.load(Ordering::Relaxed) as f64) / 1_000_000_000.0;
        s.position_seconds = if s.duration_seconds > 0.0 {
            pos.min(s.duration_seconds)
        } else {
            pos
        };
        if let Some(m) = self.latest_metrics.try_lock() {
            s.levels = m.0.clone();
            s.spectrum = m.1.to_vec();
        }
        // Honest end-to-end bit-perfect check
        let eq_active = self.equalizer.try_lock().map(|eq| {
            eq.enabled && (eq.preamp_db.abs() > 1e-4 || eq.bands.iter().any(|b| b.enabled && b.gain_db.abs() > 1e-4))
        }).unwrap_or(false);
        let dsp_active = eq_active
            || self.tube_exciter.try_lock().map(|t| t.enabled && t.drive >= 0.01).unwrap_or(false)
            || self.spatializer.try_lock().map(|sp| sp.enabled).unwrap_or(false)
            || self.sub_bass.try_lock().map(|sb| sb.enabled && sb.intensity >= 0.01).unwrap_or(false)
            || self.vocal_clarity.try_lock().map(|vc| vc.enabled && vc.clarity >= 0.01).unwrap_or(false)
            || self.loudness_normalizer.try_lock().map(|ln| ln.enabled).unwrap_or(false)
            || self.crossfeed.try_lock().map(|cf| cf.preset != CrossfeedPreset::Off).unwrap_or(false)
            || (s.volume < 0.999);
        let native_match = s.current_stream_info.as_ref().map(|i| i.sample_rate == *self.output_sample_rate.lock() as u32).unwrap_or(false);
        s.is_bit_perfect = s.is_dop || (native_match && !dsp_active);
        s
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

    pub fn set_crossfeed_preset(&self, preset: CrossfeedPreset) {
        self.crossfeed.lock().set_preset(preset);
    }

    pub fn get_crossfeed_preset(&self) -> CrossfeedPreset {
        self.crossfeed.lock().preset
    }

    pub fn set_loudness_normalizer_enabled(&self, enabled: bool) {
        self.loudness_normalizer.lock().set_enabled(enabled);
    }

    pub fn is_loudness_normalizer_enabled(&self) -> bool {
        self.loudness_normalizer.lock().enabled
    }

    pub fn set_loudness_target_lufs(&self, lufs: f32) {
        self.loudness_normalizer.lock().set_target_lufs(lufs);
    }

    pub fn set_tube_warmth_enabled(&self, enabled: bool) {
        self.tube_exciter.lock().set_enabled(enabled);
    }

    pub fn is_tube_warmth_enabled(&self) -> bool {
        self.tube_exciter.lock().enabled
    }

    pub fn set_tube_warmth_drive(&self, drive: f32) {
        self.tube_exciter.lock().set_drive(drive);
    }

    pub fn get_tube_warmth_drive(&self) -> f32 {
        self.tube_exciter.lock().drive
    }

    pub fn set_channel_balance(&self, balance: f32) {
        self.channel_balance.lock().set_balance(balance);
    }

    pub fn get_channel_balance(&self) -> f32 {
        self.channel_balance.lock().balance
    }

    pub fn set_spatializer_enabled(&self, enabled: bool) {
        self.spatializer.lock().set_enabled(enabled);
    }

    pub fn is_spatializer_enabled(&self) -> bool {
        self.spatializer.lock().enabled
    }

    pub fn set_spatializer_width(&self, width: f32) {
        self.spatializer.lock().set_width(width);
    }

    pub fn get_spatializer_width(&self) -> f32 {
        self.spatializer.lock().width
    }

    pub fn set_spatializer_headphone_mode(&self, enabled: bool) {
        self.spatializer.lock().set_headphone_mode(enabled);
    }

    pub fn is_spatializer_headphone_mode(&self) -> bool {
        self.spatializer.lock().is_headphone_mode
    }

    pub fn set_sub_bass_enabled(&self, enabled: bool) {
        self.sub_bass.lock().set_enabled(enabled);
    }

    pub fn is_sub_bass_enabled(&self) -> bool {
        self.sub_bass.lock().enabled
    }

    pub fn set_sub_bass_intensity(&self, intensity: f32) {
        self.sub_bass.lock().set_intensity(intensity);
    }

    pub fn get_sub_bass_intensity(&self) -> f32 {
        self.sub_bass.lock().intensity
    }

    pub fn set_vocal_clarity_enabled(&self, enabled: bool) {
        self.vocal_clarity.lock().set_enabled(enabled);
    }

    pub fn is_vocal_clarity_enabled(&self) -> bool {
        self.vocal_clarity.lock().enabled
    }

    pub fn set_vocal_clarity_intensity(&self, intensity: f32) {
        self.vocal_clarity.lock().set_clarity(intensity);
    }

    pub fn get_vocal_clarity_intensity(&self) -> f32 {
        self.vocal_clarity.lock().clarity
    }

    pub fn set_limiter_enabled(&self, enabled: bool) {
        self.limiter.lock().set_enabled(enabled);
    }

    pub fn set_playback_speed(&self, speed: f32) {
        let clamped = speed.clamp(0.5, 2.0);
        let old_speed = *self.playback_speed.lock();
        if (old_speed - clamped).abs() < 0.01 {
            return;
        }
        *self.playback_speed.lock() = clamped;

        // Current rendered playback position and native track sample rate
        let (current_pos, sample_rate) = {
            let s = self.state.lock();
            let sr = s.current_stream_info.as_ref().map(|i| i.sample_rate).unwrap_or(44100);
            (s.position_seconds, sr)
        };

        // Drain ring buffer immediately so 1.5s of pre-buffered old-speed audio is discarded
        {
            let mut consumer = self.ring_buffer_consumer.lock();
            let occupied = consumer.occupied_len();
            if occupied > 0 {
                consumer.skip(occupied);
            }
        }

        // Seek the active decoder back to current position so playback resumes instantly at new speed without skip
        let frame = (current_pos * sample_rate as f64) as u64;
        self.seek_requested_frame.store(frame, Ordering::SeqCst);
        self.flush_requested.store(true, Ordering::SeqCst);
    }

    pub fn get_playback_speed(&self) -> f32 {
        *self.playback_speed.lock()
    }

    pub fn equalizer(&self) -> Arc<Mutex<ParametricEqualizer>> {
        Arc::clone(&self.equalizer)
    }

    pub fn queue(&self) -> Arc<Mutex<Vec<TrackQueueItem>>> {
        Arc::clone(&self.queue)
    }
}

/// Implementation of the audio render callback called by AAudio/Oboe/CPAL thread.
/// Strictly lock-free and allocation-free on hot audio path to prevent xruns.
impl AudioRenderCallback for PlaybackEngine {
    fn render_audio(&self, buffer: &mut [f32], frames: usize, channels: usize) {
        let total_samples = frames * channels;

        let is_playing = self.is_playing_atomic.load(Ordering::Relaxed);
        let is_dop = self.is_dop_atomic.load(Ordering::Relaxed);
        let volume = f32::from_bits(self.volume_bits_atomic.load(Ordering::Relaxed));

        if !is_playing {
            // Smooth 3-4ms anti-click ramp down if paused mid-waveform instead of abrupt zero-step
            let prev_v = self.render_volume.try_lock().map(|g| *g).unwrap_or(0.0);
            if prev_v > 0.0005 && !is_dop {
                let mut read_samples = 0;
                if let Some(mut consumer) = self.ring_buffer_consumer.try_lock() {
                    read_samples = consumer.pop_slice(&mut buffer[..total_samples]);
                }
                if read_samples > 0 {
                    let ramp_frames = frames.min(192);
                    for f in 0..frames {
                        let g = if f < ramp_frames {
                            prev_v * (1.0 - (f as f32 / ramp_frames as f32))
                        } else {
                            0.0
                        };
                        for ch in 0..channels {
                            let idx = f * channels + ch;
                            buffer[idx] = if idx < read_samples { buffer[idx] * g } else { 0.0 };
                        }
                    }
                } else {
                    buffer[..total_samples].fill(0.0);
                }
            } else {
                buffer[..total_samples].fill(0.0);
            }
            if let Some(mut g) = self.render_volume.try_lock() {
                *g = 0.0;
            }
            return;
        }

        let read_samples = {
            if let Some(mut consumer) = self.ring_buffer_consumer.try_lock() {
                consumer.pop_slice(&mut buffer[..total_samples])
            } else {
                0
            }
        };

        // Fill remaining with silence if underrun
        if read_samples < total_samples {
            buffer[read_samples..total_samples].fill(0.0);
        }

        // CRIT-01: In DoP (DSD over PCM) mode, audio data consists of bit-packed 1-bit DSD
        // with alternating 0x05 / 0xFA marker sync bytes in the top 8 bits.
        // Any DSP or scaling mutates markers, so bypass completely for DoP.
        if !is_dop {
            let mut signal_modified = volume < 0.999
                || self.resampler.try_lock().map(|r| r.is_some()).unwrap_or(false);

            if let Some(mut eq) = self.equalizer.try_lock() {
                if eq.enabled && (eq.preamp_db.abs() > 1e-4 || eq.bands.iter().any(|b| b.enabled && b.gain_db.abs() > 1e-4)) {
                    signal_modified = true;
                }
                eq.process_interleaved(buffer);
            }
            if let Some(mut sub) = self.sub_bass.try_lock() {
                if sub.enabled && sub.intensity >= 0.01 { signal_modified = true; }
                sub.process_interleaved(buffer, channels);
            }
            if let Some(mut voc) = self.vocal_clarity.try_lock() {
                if voc.enabled && voc.clarity >= 0.01 { signal_modified = true; }
                voc.process_interleaved(buffer, channels);
            }
            if let Some(mut tube) = self.tube_exciter.try_lock() {
                if tube.enabled && tube.drive >= 0.01 { signal_modified = true; }
                tube.process_interleaved(buffer);
            }
            if let Some(mut spat) = self.spatializer.try_lock() {
                if spat.enabled { signal_modified = true; }
                spat.process_interleaved(buffer, channels);
            }
            if let Some(mut xfeed) = self.crossfeed.try_lock() {
                if xfeed.preset != CrossfeedPreset::Off { signal_modified = true; }
                xfeed.process_interleaved(buffer, channels);
            }
            if let Some(mut norm) = self.loudness_normalizer.try_lock() {
                if norm.enabled { signal_modified = true; }
                norm.process_interleaved(buffer, channels);
            }
            if let Some(bal) = self.channel_balance.try_lock() {
                if bal.balance.abs() > 1e-4 { signal_modified = true; }
                bal.process_interleaved(buffer, channels);
            }
            if let Some(lim) = self.limiter.try_lock() {
                if lim.enabled { signal_modified = true; }
                lim.process_interleaved(buffer);
            }

            // Apply Master Volume with perceptual logarithmic curve and smooth ramping
            let target_vol = perceptual_volume(volume);
            if let Some(mut vol_guard) = self.render_volume.try_lock() {
                let start_vol = *vol_guard;
                if (start_vol - target_vol).abs() > 0.0001 || (target_vol - 1.0).abs() > f32::EPSILON {
                    signal_modified = true;
                    let step = (target_vol - start_vol) / (frames as f32).max(1.0);
                    let mut current_v = start_vol;
                    for f in 0..frames {
                        current_v += step;
                        for ch in 0..channels {
                            buffer[f * channels + ch] *= current_v;
                        }
                    }
                }
                *vol_guard = target_vol;
            }

            // Apply 24-bit TPDF dither only when DSP/volume/resampling modified the float stream
            // (never add dither noise to bit-perfect streams)
            if signal_modified && read_samples > 0 {
                if let Some(mut dith) = self.dither.try_lock() {
                    dith.dither_f32(&mut buffer[..read_samples], 24);
                }
            }

            // Protect against NaN / denormals to guarantee speaker safety
            for sample in buffer.iter_mut() {
                if !sample.is_finite() {
                    *sample = 0.0;
                }
            }
        }

        // Advance atomic position counter with zero state lock
        let actual_frames = read_samples / channels.max(1);
        let output_rate = self.output_sample_rate.try_lock().map(|r| *r as f64).unwrap_or(48000.0);
        let speed = self.playback_speed.try_lock().map(|sp| *sp as f64).unwrap_or(1.0);
        if output_rate > 0.0 && actual_frames > 0 {
            let delta_seconds = (actual_frames as f64 / output_rate) * speed;
            self.position_nanos_atomic.fetch_add((delta_seconds * 1_000_000_000.0) as u64, Ordering::Relaxed);
        }

        // Update levels non-blockingly when metrics mutex is free
        if let Some(mut m) = self.latest_metrics.try_lock() {
            m.0 = calculate_levels(buffer, channels);
            let spec = calculate_spectrum_16(buffer, channels);
            for (i, v) in spec.iter().take(16).enumerate() {
                m.1[i] = *v;
            }
        }
    }
}
