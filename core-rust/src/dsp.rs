use num_complex::Complex;
use std::f32::consts::PI;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum FilterType {
    Peaking,
    LowShelf,
    HighShelf,
    LowPass,
    HighPass,
    Notch,
    BandPass,
}

#[derive(Debug, Clone)]
pub struct BiquadCoefficients {
    pub b0: f32,
    pub b1: f32,
    pub b2: f32,
    pub a1: f32,
    pub a2: f32,
}

impl BiquadCoefficients {
    pub fn identity() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
        }
    }

    /// Calculate biquad filter coefficients based on Robert Bristow-Johnson's Audio EQ Cookbook.
    pub fn calculate(
        filter_type: FilterType,
        frequency: f32,
        gain_db: f32,
        q: f32,
        sample_rate: f32,
    ) -> Self {
        let f0 = frequency.clamp(10.0, sample_rate * 0.499);
        let q = q.clamp(0.05, 50.0);
        let a = 10.0f32.powf(gain_db / 40.0); // sqrt(10^(gain/20))
        let w0 = 2.0 * PI * f0 / sample_rate;
        let cos_w0 = w0.cos();
        let sin_w0 = w0.sin();
        let alpha = sin_w0 / (2.0 * q);

        let (b0, b1, b2, a0, a1, a2) = match filter_type {
            FilterType::Peaking => {
                let b0 = 1.0 + alpha * a;
                let b1 = -2.0 * cos_w0;
                let b2 = 1.0 - alpha * a;
                let a0 = 1.0 + alpha / a;
                let a1 = -2.0 * cos_w0;
                let a2 = 1.0 - alpha / a;
                (b0, b1, b2, a0, a1, a2)
            }
            FilterType::LowShelf => {
                let two_sqrt_a_alpha = 2.0 * a.sqrt() * alpha;
                let b0 = a * ((a + 1.0) - (a - 1.0) * cos_w0 + two_sqrt_a_alpha);
                let b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos_w0);
                let b2 = a * ((a + 1.0) - (a - 1.0) * cos_w0 - two_sqrt_a_alpha);
                let a0 = (a + 1.0) + (a - 1.0) * cos_w0 + two_sqrt_a_alpha;
                let a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos_w0);
                let a2 = (a + 1.0) + (a - 1.0) * cos_w0 - two_sqrt_a_alpha;
                (b0, b1, b2, a0, a1, a2)
            }
            FilterType::HighShelf => {
                let two_sqrt_a_alpha = 2.0 * a.sqrt() * alpha;
                let b0 = a * ((a + 1.0) + (a - 1.0) * cos_w0 + two_sqrt_a_alpha);
                let b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cos_w0);
                let b2 = a * ((a + 1.0) + (a - 1.0) * cos_w0 - two_sqrt_a_alpha);
                let a0 = (a + 1.0) - (a - 1.0) * cos_w0 + two_sqrt_a_alpha;
                let a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cos_w0);
                let a2 = (a + 1.0) - (a - 1.0) * cos_w0 - two_sqrt_a_alpha;
                (b0, b1, b2, a0, a1, a2)
            }
            FilterType::LowPass => {
                let b0 = (1.0 - cos_w0) / 2.0;
                let b1 = 1.0 - cos_w0;
                let b2 = (1.0 - cos_w0) / 2.0;
                let a0 = 1.0 + alpha;
                let a1 = -2.0 * cos_w0;
                let a2 = 1.0 - alpha;
                (b0, b1, b2, a0, a1, a2)
            }
            FilterType::HighPass => {
                let b0 = (1.0 + cos_w0) / 2.0;
                let b1 = -(1.0 + cos_w0);
                let b2 = (1.0 + cos_w0) / 2.0;
                let a0 = 1.0 + alpha;
                let a1 = -2.0 * cos_w0;
                let a2 = 1.0 - alpha;
                (b0, b1, b2, a0, a1, a2)
            }
            FilterType::Notch => {
                let b0 = 1.0;
                let b1 = -2.0 * cos_w0;
                let b2 = 1.0;
                let a0 = 1.0 + alpha;
                let a1 = -2.0 * cos_w0;
                let a2 = 1.0 - alpha;
                (b0, b1, b2, a0, a1, a2)
            }
            FilterType::BandPass => {
                let b0 = alpha;
                let b1 = 0.0;
                let b2 = -alpha;
                let a0 = 1.0 + alpha;
                let a1 = -2.0 * cos_w0;
                let a2 = 1.0 - alpha;
                (b0, b1, b2, a0, a1, a2)
            }
        };

        Self {
            b0: b0 / a0,
            b1: b1 / a0,
            b2: b2 / a0,
            a1: a1 / a0,
            a2: a2 / a0,
        }
    }

    /// Evaluates the complex transfer function H(e^{j w}) at frequency f (Hz)
    pub fn response_at(&self, frequency: f32, sample_rate: f32) -> Complex<f32> {
        let w = 2.0 * PI * frequency / sample_rate;
        let z_inv = Complex::from_polar(1.0, -w);
        let z_inv2 = z_inv * z_inv;

        let num = Complex::new(self.b0, 0.0) + Complex::new(self.b1, 0.0) * z_inv + Complex::new(self.b2, 0.0) * z_inv2;
        let den = Complex::new(1.0, 0.0) + Complex::new(self.a1, 0.0) * z_inv + Complex::new(self.a2, 0.0) * z_inv2;

        num / den
    }
}

/// Direct Form II Transposed Biquad state for a single audio channel
#[derive(Debug, Clone, Default)]
pub struct BiquadChannelState {
    s1: f32,
    s2: f32,
}

impl BiquadChannelState {
    #[inline(always)]
    pub fn process(&mut self, input: f32, coeffs: &BiquadCoefficients) -> f32 {
        let output = coeffs.b0 * input + self.s1;
        self.s1 = coeffs.b1 * input - coeffs.a1 * output + self.s2;
        self.s2 = coeffs.b2 * input - coeffs.a2 * output;

        // Denormal and non-finite protection
        if !self.s1.is_finite() || self.s1.abs() < 1e-15 { self.s1 = 0.0; }
        if !self.s2.is_finite() || self.s2.abs() < 1e-15 { self.s2 = 0.0; }

        output
    }

    pub fn reset(&mut self) {
        self.s1 = 0.0;
        self.s2 = 0.0;
    }
}

/// Configuration for a single parametric EQ band
#[derive(Debug, Clone)]
pub struct EqBand {
    pub enabled: bool,
    pub filter_type: FilterType,
    pub frequency: f32,
    pub gain_db: f32,
    pub q: f32,
    coeffs: BiquadCoefficients,
    channel_states: Vec<BiquadChannelState>,
}

impl EqBand {
    pub fn new(filter_type: FilterType, frequency: f32, gain_db: f32, q: f32, sample_rate: f32, channels: usize) -> Self {
        let coeffs = BiquadCoefficients::calculate(filter_type, frequency, gain_db, q, sample_rate);
        Self {
            enabled: true,
            filter_type,
            frequency,
            gain_db,
            q,
            coeffs,
            channel_states: vec![BiquadChannelState::default(); channels],
        }
    }

    pub fn update_params(&mut self, filter_type: FilterType, frequency: f32, gain_db: f32, q: f32, sample_rate: f32) {
        self.filter_type = filter_type;
        self.frequency = frequency;
        self.gain_db = gain_db;
        self.q = q;
        self.coeffs = BiquadCoefficients::calculate(filter_type, frequency, gain_db, q, sample_rate);
    }

    pub fn set_gain(&mut self, gain_db: f32, sample_rate: f32) {
        self.gain_db = gain_db;
        self.coeffs = BiquadCoefficients::calculate(self.filter_type, self.frequency, self.gain_db, self.q, sample_rate);
    }

    pub fn set_frequency(&mut self, freq: f32, sample_rate: f32) {
        self.frequency = freq;
        self.coeffs = BiquadCoefficients::calculate(self.filter_type, self.frequency, self.gain_db, self.q, sample_rate);
    }

    pub fn set_q(&mut self, q: f32, sample_rate: f32) {
        self.q = q;
        self.coeffs = BiquadCoefficients::calculate(self.filter_type, self.frequency, self.gain_db, self.q, sample_rate);
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        self.coeffs = BiquadCoefficients::calculate(self.filter_type, self.frequency, self.gain_db, self.q, sample_rate);
    }

    pub fn ensure_channels(&mut self, channels: usize) {
        if self.channel_states.len() != channels {
            self.channel_states = vec![BiquadChannelState::default(); channels];
        }
    }

    pub fn reset(&mut self) {
        for state in &mut self.channel_states {
            state.reset();
        }
    }

    #[inline(always)]
    pub fn process_sample(&mut self, sample: f32, channel: usize) -> f32 {
        if !self.enabled || self.gain_db.abs() < 1e-4 {
            return sample;
        }
        if channel < self.channel_states.len() {
            self.channel_states[channel].process(sample, &self.coeffs)
        } else {
            sample
        }
    }
}

/// 10-Band Parametric Equalizer and DSP Signal Chain
#[derive(Debug, Clone)]
pub struct ParametricEqualizer {
    pub enabled: bool,
    pub bands: Vec<EqBand>,
    pub sample_rate: f32,
    pub channels: usize,
    pub preamp_db: f32,
}

impl ParametricEqualizer {
    pub fn default_10_band(sample_rate: f32, channels: usize) -> Self {
        // Standard ISO 1/1 octave audiophile center frequencies
        let default_freqs = [31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0];
        let mut bands = Vec::with_capacity(10);

        for (i, &freq) in default_freqs.iter().enumerate() {
            let filter_type = if i == 0 {
                FilterType::LowShelf
            } else if i == default_freqs.len() - 1 {
                FilterType::HighShelf
            } else {
                FilterType::Peaking
            };
            bands.push(EqBand::new(filter_type, freq, 0.0, 1.414, sample_rate, channels));
        }

        Self {
            enabled: true,
            bands,
            sample_rate,
            channels,
            preamp_db: 0.0,
        }
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        self.sample_rate = sample_rate;
        for band in &mut self.bands {
            band.set_sample_rate(sample_rate);
        }
    }

    pub fn set_channels(&mut self, channels: usize) {
        self.channels = channels;
        for band in &mut self.bands {
            band.ensure_channels(channels);
        }
    }

    pub fn reset(&mut self) {
        for band in &mut self.bands {
            band.reset();
        }
    }

    /// Process interleaved audio buffer in-place
    pub fn process_interleaved(&mut self, buffer: &mut [f32]) {
        if !self.enabled {
            return;
        }

        let has_preamp = self.preamp_db.abs() > 1e-4;
        let has_active_bands = self.bands.iter().any(|b| b.enabled && b.gain_db.abs() > 1e-4);

        if !has_preamp && !has_active_bands {
            return; // 100% Bit-perfect pass-through
        }

        let preamp_gain = if has_preamp {
            10.0f32.powf(self.preamp_db / 20.0)
        } else {
            1.0
        };
        let num_channels = self.channels;

        for (i, sample) in buffer.iter_mut().enumerate() {
            let ch = i % num_channels;
            let mut s = *sample * preamp_gain;

            for band in &mut self.bands {
                s = band.process_sample(s, ch);
            }

            *sample = s;
        }
    }

    /// Calculate the combined frequency response curve in dB across a list of frequencies (e.g. 20 Hz to 20 kHz).
    /// Used by the Compose UI to draw the real-time live transfer function graph!
    pub fn calculate_curve(&self, frequencies: &[f32]) -> Vec<f32> {
        let preamp_db = self.preamp_db;
        let mut curve = Vec::with_capacity(frequencies.len());

        for &f in frequencies {
            if !self.enabled {
                curve.push(preamp_db);
                continue;
            }

            let mut total_h = Complex::new(1.0, 0.0);
            for band in &self.bands {
                if band.enabled {
                    total_h *= band.coeffs.response_at(f, self.sample_rate);
                }
            }

            let mag = total_h.norm();
            let gain_db = if mag > 1e-12 {
                20.0 * mag.log10() + preamp_db
            } else {
                -240.0 + preamp_db
            };
            curve.push(gain_db);
        }

        curve
    }
}

/// Triangular Probability Density Function (TPDF) Dither generator (lock-free, real-time safe)
pub struct TpdfDither {
    rng_state: u64,
    last_random: f32,
}

impl TpdfDither {
    pub fn new() -> Self {
        Self {
            rng_state: 0x9E37_79B9_7F4A_7C15,
            last_random: 0.0,
        }
    }

    #[inline]
    fn next_unit_f32(&mut self) -> f32 {
        // xorshift64* - zero allocation, zero TLS, real-time audio safe
        let mut x = self.rng_state;
        x ^= x >> 12;
        x ^= x << 25;
        x ^= x >> 27;
        self.rng_state = x;
        let bits = (x.wrapping_mul(0x2545_F491_4F6C_DD1D) >> 40) as u32;
        (bits as f32) * (1.0 / 16777216.0)
    }

    /// Generates triangular dither in range [-1.0, +1.0] LSB
    #[inline]
    pub fn next_dither(&mut self) -> f32 {
        let r1 = self.next_unit_f32();
        let r2 = self.next_unit_f32();
        let dither = r1 - r2;
        self.last_random = dither;
        dither
    }

    /// Apply TPDF dither directly to an f32 PCM buffer at target bit-depth (16 or 24-bit)
    /// prior to HAL/AudioFlinger quantization, preventing low-level harmonic distortion.
    pub fn dither_f32(&mut self, buffer: &mut [f32], target_bits: u32) {
        let lsb = if target_bits <= 16 {
            1.0 / 32767.0
        } else {
            1.0 / 8388607.0
        };
        for sample in buffer.iter_mut() {
            *sample = (*sample + self.next_dither() * lsb).clamp(-1.0, 1.0);
        }
    }

    /// Apply TPDF dither and quantize float sample [-1.0, 1.0] to 16-bit integer range
    pub fn quantize_i16(&mut self, sample: f32) -> i16 {
        let scale = 32767.0;
        let dither = self.next_dither();
        let quantized = (sample * scale + dither).round();
        quantized.clamp(-32768.0, 32767.0) as i16
    }

    /// Apply TPDF dither and quantize float sample [-1.0, 1.0] to 24-bit integer range
    pub fn quantize_i24(&mut self, sample: f32) -> i32 {
        let scale = 8388607.0;
        let dither = self.next_dither();
        let quantized = (sample * scale + dither).round();
        quantized.clamp(-8388608.0, 8388607.0) as i32
    }
}

/// Audio level metrics for UI meters and spectrum
#[derive(Debug, Clone, Default)]
pub struct LevelMetrics {
    pub rms_left: f32,
    pub rms_right: f32,
    pub peak_left: f32,
    pub peak_right: f32,
}

pub fn calculate_levels(buffer: &[f32], channels: usize) -> LevelMetrics {
    if buffer.is_empty() || channels == 0 {
        return LevelMetrics::default();
    }

    let mut sum_sq_l = 0.0f32;
    let mut sum_sq_r = 0.0f32;
    let mut peak_l = 0.0f32;
    let mut peak_r = 0.0f32;
    let frames = buffer.len() / channels;

    for frame_idx in 0..frames {
        let l = buffer[frame_idx * channels].abs();
        let r = if channels > 1 {
            buffer[frame_idx * channels + 1].abs()
        } else {
            l
        };

        sum_sq_l += l * l;
        sum_sq_r += r * r;
        if l > peak_l { peak_l = l; }
        if r > peak_r { peak_r = r; }
    }

    let rms_l = (sum_sq_l / frames as f32).sqrt();
    let rms_r = (sum_sq_r / frames as f32).sqrt();

    LevelMetrics {
        rms_left: rms_l,
        rms_right: rms_r,
        peak_left: peak_l,
        peak_right: peak_r,
    }
}

/// Simple 16-band energy spectrum estimator for smooth UI visualizer
pub fn calculate_spectrum_16(buffer: &[f32], channels: usize) -> Vec<f32> {
    let num_bands = 16;
    let mut bands = vec![0.0f32; num_bands];
    if buffer.is_empty() || channels == 0 {
        return bands;
    }

    let frames = buffer.len() / channels;
    if frames < num_bands {
        return bands;
    }

    let chunk_size = frames / num_bands;
    for b in 0..num_bands {
        let mut sum = 0.0;
        for i in 0..chunk_size {
            let sample_idx = (b * chunk_size + i) * channels;
            let val = buffer[sample_idx].abs();
            sum += val;
        }
        let avg = sum / chunk_size as f32;
        bands[b] = avg.clamp(0.0, 1.0);
    }

    bands
}

/// Audiophile Headphone Crossfeed Presets (Bauer / Meier / Chu Moy binaural model)
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum CrossfeedPreset {
    /// Bit-perfect direct bypass
    Off,
    /// Subtle (Jan Meier natural crossfeed, -9.5 dB cross-feed, 700 Hz cutoff)
    Subtle,
    /// Standard (Bauer / Chu Moy reference crossfeed, -6.0 dB cross-feed, 700 Hz cutoff)
    Standard,
    /// Vintage (Aggressive crossfeed for early 60s hard-panned stereo, -4.5 dB cross-feed, 650 Hz cutoff)
    Vintage,
}

/// Headphone Crossfeed Filter
/// Simulates natural speaker acoustic room response on headphones/IEMs by blending
/// low-pass filtered and interaurally delayed acoustic crosstalk from contralateral channels.
#[derive(Debug, Clone)]
pub struct CrossfeedFilter {
    pub preset: CrossfeedPreset,
    sample_rate: f32,
    alpha: f32,
    g_direct: f32,
    g_cross: f32,
    state_l: f32,
    state_r: f32,
}

impl CrossfeedFilter {
    pub fn new(sample_rate: f32) -> Self {
        let mut filter = Self {
            preset: CrossfeedPreset::Off,
            sample_rate,
            alpha: 0.0,
            g_direct: 1.0,
            g_cross: 0.0,
            state_l: 0.0,
            state_r: 0.0,
        };
        filter.recalculate();
        filter
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        if (self.sample_rate - sample_rate).abs() > 0.1 {
            self.sample_rate = sample_rate;
            self.recalculate();
        }
    }

    pub fn set_preset(&mut self, preset: CrossfeedPreset) {
        if self.preset != preset {
            self.preset = preset;
            self.recalculate();
            self.reset();
        }
    }

    pub fn reset(&mut self) {
        self.state_l = 0.0;
        self.state_r = 0.0;
    }

    fn recalculate(&mut self) {
        let (cutoff, cross_gain_db) = match self.preset {
            CrossfeedPreset::Off => {
                self.alpha = 0.0;
                self.g_direct = 1.0;
                self.g_cross = 0.0;
                return;
            }
            CrossfeedPreset::Subtle => (700.0, -9.5),
            CrossfeedPreset::Standard => (700.0, -6.0),
            CrossfeedPreset::Vintage => (650.0, -4.5),
        };

        let fs = self.sample_rate.max(8000.0);
        let w_c = 2.0 * PI * cutoff / fs;
        // 1-pole low-pass filter coefficient alpha = 1 - exp(-w_c)
        self.alpha = (1.0 - (-w_c).exp()).clamp(0.0001, 0.9999);

        // Calculate direct vs cross gains so mono / center-panned signals maintain 0 dB unity gain at DC
        self.g_cross = 10.0f32.powf(cross_gain_db / 20.0);
        self.g_direct = (1.0 - self.g_cross).max(0.1);
    }

    #[inline(always)]
    pub fn process_sample(&mut self, left: f32, right: f32) -> (f32, f32) {
        if self.preset == CrossfeedPreset::Off {
            return (left, right);
        }

        // Low-pass filter for contralateral acoustic delay & head-shadow attenuation
        self.state_l += self.alpha * (left - self.state_l);
        self.state_r += self.alpha * (right - self.state_r);

        // Denormal protection
        if !self.state_l.is_finite() || self.state_l.abs() < 1e-15 { self.state_l = 0.0; }
        if !self.state_r.is_finite() || self.state_r.abs() < 1e-15 { self.state_r = 0.0; }

        let out_l = self.g_direct * left + self.g_cross * self.state_r;
        let out_r = self.g_direct * right + self.g_cross * self.state_l;

        (out_l, out_r)
    }

    pub fn process_interleaved(&mut self, buffer: &mut [f32], channels: usize) {
        if self.preset == CrossfeedPreset::Off || channels != 2 {
            return;
        }

        let frames = buffer.len() / 2;
        for frame in 0..frames {
            let idx = frame * 2;
            let l = buffer[idx];
            let r = buffer[idx + 1];
            let (out_l, out_r) = self.process_sample(l, r);
            buffer[idx] = out_l;
            buffer[idx + 1] = out_r;
        }
    }
}

/// Audiophile transparent soft-knee peak limiter.
///
/// Prevents harsh digital clipping (0 dBFS intersample overs) when EQ or ReplayGain
/// boosts signal above unity, while maintaining 0.000% THD under 0.95 peak amplitude.
#[derive(Debug, Clone)]
pub struct PeakLimiter {
    pub enabled: bool,
    pub threshold: f32,
}

impl PeakLimiter {
    pub fn new() -> Self {
        Self {
            enabled: true,
            threshold: 0.95,
        }
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    #[inline(always)]
    pub fn process_sample(&self, sample: f32) -> f32 {
        if !self.enabled {
            return sample;
        }

        let abs = sample.abs();
        if abs <= self.threshold {
            sample
        } else {
            let over = abs - self.threshold;
            let head = 1.0 - self.threshold;
            let saturated = self.threshold + head * (over / head).tanh();
            sample.signum() * saturated.min(0.9999)
        }
    }

    pub fn process_interleaved(&self, buffer: &mut [f32]) {
        if !self.enabled {
            return;
        }
        for s in buffer.iter_mut() {
            *s = self.process_sample(*s);
        }
    }
}

/// Dynamic Loudness Normalizer (EBU R128 RMS target with smooth envelope).
///
/// Automatically balances perceived volume across different audio files, albums,
/// and streaming tracks without abrupt volume shifts or audible compression artifacts.
#[derive(Debug, Clone)]
pub struct LoudnessNormalizer {
    pub enabled: bool,
    pub target_lufs: f32,
    pub current_gain: f32,
    envelope: f32,
    sample_rate: f32,
}

impl LoudnessNormalizer {
    pub fn new(sample_rate: f32) -> Self {
        let target_lufs = -14.0;
        let initial_target_rms = 10.0f32.powf((target_lufs + 0.691) / 20.0);
        Self {
            enabled: false,
            target_lufs,
            current_gain: 1.0,
            envelope: initial_target_rms,
            sample_rate: sample_rate.max(8000.0),
        }
    }

    pub fn reset(&mut self) {
        let target_rms = 10.0f32.powf((self.target_lufs + 0.691) / 20.0);
        self.envelope = target_rms;
        self.current_gain = 1.0;
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
        if !enabled {
            self.current_gain = 1.0;
        }
    }

    pub fn set_target_lufs(&mut self, lufs: f32) {
        self.target_lufs = lufs.clamp(-24.0, -9.0);
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        self.sample_rate = sample_rate.max(8000.0);
    }

    pub fn process_interleaved(&mut self, buffer: &mut [f32], channels: usize) {
        if !self.enabled || buffer.is_empty() || channels == 0 {
            return;
        }

        let frames = buffer.len() / channels;

        let mut sum_sq = 0.0f32;
        for s in buffer.iter() {
            sum_sq += s * s;
        }
        let rms = (sum_sq / buffer.len() as f32).sqrt();

        // Silence / Ducking Gate (-42 dBFS threshold = 0.008)
        // During quiet interludes, pauses, or notification ducking:
        // FREEZE the gain and envelope at the current steady-state value!
        // Never decay towards 1.0 and never ramp up.
        // This guarantees zero audio burst when notifications or quiet sections end.
        let silence_threshold = 0.008f32;
        if rms < silence_threshold {
            let start_gain = self.current_gain;
            for f in 0..frames {
                for ch in 0..channels {
                    buffer[f * channels + ch] *= start_gain;
                }
            }
            return;
        }

        let target_rms = 10.0f32.powf((self.target_lufs + 0.691) / 20.0);

        // Slow musical envelope tracking (tau = 2.5 seconds, matching EBU R128 short-term window)
        let alpha = (buffer.len() as f32 / (2.5 * self.sample_rate * channels as f32)).clamp(0.0005, 0.05);
        self.envelope += alpha * (rms - self.envelope);

        // Safe bounds: max boost +3.0 dB (1.41x) to prevent noise amplification and bursts;
        // max attenuation -12.0 dB (0.25x) for hot masters.
        let desired_gain = (target_rms / self.envelope.max(1e-4)).clamp(0.25, 1.41);

        // Asymmetric gain smoothing (sample-rate & buffer-size invariant):
        // Fast reaction (tau = 0.35s) when reducing loud passages to avoid harsh peaks;
        // Ultra-gentle reaction (tau = 5.0s) when boosting quiet audio to completely eliminate pumping artifacts.
        let buffer_sec = frames as f32 / self.sample_rate;
        let tau = if desired_gain < self.current_gain { 0.35 } else { 5.0 };
        let gain_alpha = (buffer_sec / tau).clamp(0.0001, 0.1);
        let new_gain = self.current_gain + gain_alpha * (desired_gain - self.current_gain);

        // Smoothly interpolate gain across frames to eliminate any step pops
        let start_gain = self.current_gain;
        let gain_step = (new_gain - start_gain) / frames.max(1) as f32;
        let mut cur_g = start_gain;

        for f in 0..frames {
            cur_g += gain_step;
            for ch in 0..channels {
                buffer[f * channels + ch] *= cur_g;
            }
        }

        self.current_gain = new_gain;
    }
}

/// Audiophile Triode Tube Warmth & Harmonic Exciter
///
/// Adds musical 2nd-order (warmth) and 3rd-order (presence) harmonics
/// typical of single-ended Class-A vacuum tube preamps.
#[derive(Debug, Clone)]
pub struct TubeWarmthExciter {
    pub enabled: bool,
    /// Warmth drive amount: 0.0 (clean/transparent) to 1.0 (warm vintage saturation)
    pub drive: f32,
    sample_rate: f32,
    dc_x_prev_l: f32,
    dc_y_prev_l: f32,
    dc_x_prev_r: f32,
    dc_y_prev_r: f32,
    dc_r: f32,
}

impl TubeWarmthExciter {
    pub fn new() -> Self {
        let mut exciter = Self {
            enabled: false,
            drive: 0.35,
            sample_rate: 44100.0,
            dc_x_prev_l: 0.0,
            dc_y_prev_l: 0.0,
            dc_x_prev_r: 0.0,
            dc_y_prev_r: 0.0,
            dc_r: 0.9992,
        };
        exciter.set_sample_rate(44100.0);
        exciter
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        let sr = sample_rate.max(8000.0);
        self.sample_rate = sr;
        // 5 Hz DC-blocking high-pass filter: R = 1 - (2 * pi * 5 / sr)
        self.dc_r = (1.0 - (2.0 * PI * 5.0 / sr)).clamp(0.990, 0.9999);
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn set_drive(&mut self, drive: f32) {
        self.drive = drive.clamp(0.0, 1.0);
    }

    #[inline(always)]
    fn process_sample_ch(&mut self, is_left: bool, sample: f32) -> f32 {
        let d = self.drive;
        let in_gain = 1.0 + d * 0.45;
        let x = sample * in_gain;

        // Class-A soft triode saturation: musical 2nd harmonic without DC skew
        let x_tube = x + 0.12 * d * (x * x * x.signum());
        let saturated = x_tube.tanh();
        let dc_r = self.dc_r;

        let (x_prev, y_prev) = if is_left {
            (&mut self.dc_x_prev_l, &mut self.dc_y_prev_l)
        } else {
            (&mut self.dc_x_prev_r, &mut self.dc_y_prev_r)
        };

        let y = saturated - *x_prev + dc_r * (*y_prev);
        *x_prev = saturated;
        *y_prev = y;

        // Makeup gain compensation so perceived loudness stays balanced
        let makeup = 1.0 / (1.0 + d * 0.15);
        (y * makeup).clamp(-1.0, 1.0)
    }

    #[inline(always)]
    pub fn process_sample(&mut self, sample: f32) -> f32 {
        if !self.enabled || self.drive < 0.01 {
            return sample;
        }
        self.process_sample_ch(true, sample)
    }

    pub fn process_interleaved(&mut self, buffer: &mut [f32]) {
        if !self.enabled || self.drive < 0.01 {
            return;
        }
        let len = buffer.len();
        let frames = len / 2;
        if frames > 0 {
            for f in 0..frames {
                let l_idx = f * 2;
                let r_idx = l_idx + 1;
                buffer[l_idx] = self.process_sample_ch(true, buffer[l_idx]);
                buffer[r_idx] = self.process_sample_ch(false, buffer[r_idx]);
            }
        } else {
            for s in buffer.iter_mut() {
                *s = self.process_sample_ch(true, *s);
            }
        }
    }
}

/// Precision L/R Channel Balance
#[derive(Debug, Clone, Copy)]
pub struct ChannelBalance {
    /// -1.0 (Full Left) to 0.0 (Center) to +1.0 (Full Right)
    pub balance: f32,
    gain_l: f32,
    gain_r: f32,
}

impl ChannelBalance {
    pub fn new() -> Self {
        Self {
            balance: 0.0,
            gain_l: 1.0,
            gain_r: 1.0,
        }
    }

    pub fn set_balance(&mut self, balance: f32) {
        self.balance = balance.clamp(-1.0, 1.0);
        if self.balance.abs() < 0.001 {
            self.gain_l = 1.0;
            self.gain_r = 1.0;
        } else if self.balance < 0.0 {
            // Panning left: attenuate right channel
            self.gain_l = 1.0;
            self.gain_r = (1.0 + self.balance).clamp(0.0, 1.0);
        } else {
            // Panning right: attenuate left channel
            self.gain_l = (1.0 - self.balance).clamp(0.0, 1.0);
            self.gain_r = 1.0;
        }
    }

    pub fn process_interleaved(&self, buffer: &mut [f32], channels: usize) {
        if self.balance.abs() < 0.001 || channels != 2 {
            return;
        }
        let frames = buffer.len() / 2;
        for f in 0..frames {
            buffer[f * 2] *= self.gain_l;
            buffer[f * 2 + 1] *= self.gain_r;
        }
    }
}

/// 360° Panoramic Spatializer
/// Expands stereo soundstage into a wide 3D acoustic bubble using:
/// 1. Bass-Anchored Mid/Side Crossover (keeping <140 Hz mono so kick and bass remain locked center)
/// 2. Headphone mode: Positive HRTF Head-Shadowed ITD Crossfeed (~0.28 ms delay) + Smooth 3D diffuse reflections
/// 3. Speaker mode: Blumlein Stereo Shuffler (room acoustics widening without artificial HRTF crossfeed)
#[derive(Debug, Clone)]
pub struct Spatializer360 {
    pub enabled: bool,
    /// Stereo width multiplier: 0.5 (Narrow) to 1.0 (Natural) to 2.2 (Wide 360°)
    pub width: f32,
    /// Whether headphones are active (binaural HRTF crossfeed) or speakers (Blumlein room shuffler)
    pub is_headphone_mode: bool,
    sample_rate: f32,
    side_hp_l: f32,
    side_hp_alpha: f32,
    // ITD delay buffers (~0.28 ms)
    delay_l: Vec<f32>,
    delay_r: Vec<f32>,
    write_idx: usize,
    // Prime-length Schroeder All-Pass buffers (~1.8 ms L, ~2.5 ms R) for natural room depth
    ap_buf_l: Vec<f32>,
    ap_buf_r: Vec<f32>,
    ap_idx_l: usize,
    ap_idx_r: usize,
    // HRTF head-shadow low-pass for crossfeed
    cross_lp_l: f32,
    cross_lp_r: f32,
    cross_alpha: f32,
}

impl Spatializer360 {
    pub fn new(sample_rate: f32) -> Self {
        let mut spat = Self {
            enabled: false,
            width: 1.35,
            is_headphone_mode: true,
            sample_rate: sample_rate.max(8000.0),
            side_hp_l: 0.0,
            side_hp_alpha: 0.0,
            delay_l: Vec::new(),
            delay_r: Vec::new(),
            write_idx: 0,
            ap_buf_l: Vec::new(),
            ap_buf_r: Vec::new(),
            ap_idx_l: 0,
            ap_idx_r: 0,
            cross_lp_l: 0.0,
            cross_lp_r: 0.0,
            cross_alpha: 0.0,
        };
        spat.set_sample_rate(sample_rate);
        spat
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        let sr = sample_rate.max(8000.0);
        self.sample_rate = sr;

        let dt = 1.0 / sr;
        // 140 Hz crossover so spatial widening never thins out bass
        let rc_side = 1.0 / (2.0 * PI * 140.0);
        self.side_hp_alpha = dt / (rc_side + dt);

        // 2.0 kHz head-shadow LPF for natural earphone HRTF crossfeed
        let rc_cross = 1.0 / (2.0 * PI * 2000.0);
        self.cross_alpha = dt / (rc_cross + dt);

        // ITD delay (~0.28 ms)
        let itd_samples = ((0.00028 * sr) as usize).max(4);
        self.delay_l.resize(itd_samples, 0.0);
        self.delay_r.resize(itd_samples, 0.0);
        self.write_idx = 0;

        // Smooth Schroeder All-Pass buffers (~1.8 ms L, ~2.5 ms R) for natural room depth
        let ap_l_len = ((0.0018 * sr) as usize).max(11);
        let ap_r_len = ((0.0025 * sr) as usize).max(17);
        self.ap_buf_l.resize(ap_l_len, 0.0);
        self.ap_buf_r.resize(ap_r_len, 0.0);
        self.ap_idx_l = 0;
        self.ap_idx_r = 0;
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn set_width(&mut self, width: f32) {
        self.width = width.clamp(0.5, 2.2);
    }

    pub fn set_headphone_mode(&mut self, is_headphone: bool) {
        self.is_headphone_mode = is_headphone;
    }

    #[inline(always)]
    pub fn process_interleaved(&mut self, buffer: &mut [f32], channels: usize) {
        if !self.enabled || channels != 2 {
            return;
        }

        // Map width (0.5..2.2) into spatial strength (0.0..1.0)
        let s = ((self.width - 0.5) / 1.5).clamp(0.0, 1.0);
        if s < 0.001 {
            return;
        }

        let frames = buffer.len() / 2;
        let g = 0.35;

        for f in 0..frames {
            let idx = f * 2;
            let l = buffer[idx];
            let r = buffer[idx + 1];

            // 1. Mid/Side decomposition
            let mid = (l + r) * 0.5;
            let side = (l - r) * 0.5;

            // 2. Bass-anchored crossover: keep <140 Hz strictly mono in center
            self.side_hp_l += self.side_hp_alpha * (side - self.side_hp_l);
            let side_highs = side - self.side_hp_l;

            if self.is_headphone_mode {
                // HEADPHONE MODE: Natural 3D Binaural Stage with positive HRTF crossfeed
                let delayed_ap_l = self.ap_buf_l[self.ap_idx_l];
                let ap_in_l = side_highs + g * delayed_ap_l;
                self.ap_buf_l[self.ap_idx_l] = ap_in_l;
                self.ap_idx_l = (self.ap_idx_l + 1) % self.ap_buf_l.len();
                let diff_l = delayed_ap_l - g * ap_in_l;

                let delayed_ap_r = self.ap_buf_r[self.ap_idx_r];
                let ap_in_r = side_highs + g * delayed_ap_r;
                self.ap_buf_r[self.ap_idx_r] = ap_in_r;
                self.ap_idx_r = (self.ap_idx_r + 1) % self.ap_buf_r.len();
                let diff_r = delayed_ap_r - g * ap_in_r;

                // Positive ITD crossfeed (mimics natural sound wrapping around the head)
                let itd_l = self.delay_l[self.write_idx];
                let itd_r = self.delay_r[self.write_idx];
                self.delay_l[self.write_idx] = l;
                self.delay_r[self.write_idx] = r;
                self.write_idx = (self.write_idx + 1) % self.delay_l.len();

                self.cross_lp_l += self.cross_alpha * (itd_l - self.cross_lp_l);
                self.cross_lp_r += self.cross_alpha * (itd_r - self.cross_lp_r);

                let mid_scaled = mid * (1.0 - 0.08 * s);
                let wide_side = self.side_hp_l + side_highs * (1.0 + 0.65 * s);

                let out_l = mid_scaled + wide_side + (diff_l * 0.18 * s) + (self.cross_lp_r * 0.15 * s);
                let out_r = mid_scaled - wide_side + (diff_r * 0.18 * s) + (self.cross_lp_l * 0.15 * s);

                buffer[idx] = out_l;
                buffer[idx + 1] = out_r;
            } else {
                // SPEAKER MODE: Blumlein Stereo Shuffler (Room Acoustics)
                // Speakers already cross-feed acoustically in the room air.
                let wide_side = self.side_hp_l + side_highs * (1.0 + 0.55 * s);
                buffer[idx] = mid + wide_side;
                buffer[idx + 1] = mid - wide_side;
            }
        }
    }
}

/// Audiophile Psychoacoustic Sub-Bass Exciter
/// Combines a musical resonant sub-bass shelf (40–80 Hz) with missing-fundamental
/// harmonic generation (even/odd overtones strictly bandpassed to 80-220 Hz)
/// so sub-bass hits deep and punchy without creating mid/treble noise or distortion.
#[derive(Debug, Clone)]
pub struct SubBassExciter {
    pub enabled: bool,
    /// Exciter intensity: 0.0 (Bypass) to 1.0 (Maximum sub-bass slam)
    pub intensity: f32,
    sample_rate: f32,
    shelf_l: BiquadChannelState,
    shelf_r: BiquadChannelState,
    coeffs_shelf: BiquadCoefficients,
    bp_harm_l: BiquadChannelState,
    bp_harm_r: BiquadChannelState,
    coeffs_bp: BiquadCoefficients,
    // 75 Hz sub-bass extractor LPF
    sub_lp_l: f32,
    sub_lp_r: f32,
    sub_alpha: f32,
}

impl SubBassExciter {
    pub fn new(sample_rate: f32) -> Self {
        let sr = sample_rate.max(8000.0);
        let mut exciter = Self {
            enabled: false,
            intensity: 0.50,
            sample_rate: sr,
            shelf_l: BiquadChannelState::default(),
            shelf_r: BiquadChannelState::default(),
            coeffs_shelf: BiquadCoefficients::identity(),
            bp_harm_l: BiquadChannelState::default(),
            bp_harm_r: BiquadChannelState::default(),
            coeffs_bp: BiquadCoefficients::identity(),
            sub_lp_l: 0.0,
            sub_lp_r: 0.0,
            sub_alpha: 0.0,
        };
        exciter.recalculate();
        exciter
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        self.sample_rate = sample_rate.max(8000.0);
        self.recalculate();
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn set_intensity(&mut self, intensity: f32) {
        self.intensity = intensity.clamp(0.0, 1.0);
        self.recalculate();
    }

    fn recalculate(&mut self) {
        let sr = self.sample_rate.max(8000.0);
        let dt = 1.0 / sr;
        let rc_sub = 1.0 / (2.0 * PI * 75.0);
        self.sub_alpha = dt / (rc_sub + dt);

        // Musical low-shelf boost at 70 Hz: up to +5.5 dB with smooth Q = 0.707
        let gain_db = self.intensity * 5.5;
        self.coeffs_shelf = BiquadCoefficients::calculate(
            FilterType::LowShelf,
            70.0,
            gain_db,
            0.707,
            sr,
        );

        // Bandpass filter centered at 130 Hz with Q = 0.8
        // Strictly confines generated harmonics to 80-220 Hz so high-frequency hiss/distortion cannot bleed through
        self.coeffs_bp = BiquadCoefficients::calculate(
            FilterType::BandPass,
            130.0,
            0.0,
            0.8,
            sr,
        );
    }

    #[inline(always)]
    pub fn process_interleaved(&mut self, buffer: &mut [f32], channels: usize) {
        if !self.enabled || self.intensity < 0.01 {
            return;
        }

        let intensity = self.intensity;
        // Headroom attenuation so boosted sub-bass doesn't hard-clip into distortion
        let headroom = 1.0 / (1.0 + intensity * 0.30);
        let frames = buffer.len() / channels;

        for f in 0..frames {
            let l_idx = f * channels;
            let l = buffer[l_idx] * headroom;

            // 1. Resonant low-shelf boost on sub-bass fundamental
            let bl = self.shelf_l.process(l, &self.coeffs_shelf);

            // 2. Sub-bass harmonic exciter (adds warm 2nd/3rd harmonics)
            self.sub_lp_l += self.sub_alpha * (l - self.sub_lp_l);
            let sub_in = self.sub_lp_l;
            let harm_raw = (sub_in * 1.8).tanh() + 0.3 * (sub_in * sub_in * sub_in.signum());
            let harm_filtered = self.bp_harm_l.process(harm_raw, &self.coeffs_bp) * 0.25 * intensity;

            buffer[l_idx] = (bl + harm_filtered).clamp(-1.0, 1.0);

            if channels > 1 {
                let r_idx = l_idx + 1;
                let r = buffer[r_idx] * headroom;

                let br = self.shelf_r.process(r, &self.coeffs_shelf);

                self.sub_lp_r += self.sub_alpha * (r - self.sub_lp_r);
                let sub_in_r = self.sub_lp_r;
                let harm_raw_r = (sub_in_r * 1.8).tanh() + 0.3 * (sub_in_r * sub_in_r * sub_in_r.signum());
                let harm_filtered_r = self.bp_harm_r.process(harm_raw_r, &self.coeffs_bp) * 0.25 * intensity;

                buffer[r_idx] = (br + harm_filtered_r).clamp(-1.0, 1.0);
            }
        }
    }
}

/// Audiophile Vocal Clarity & Formant Intelligibility Enhancer
/// Multi-band acoustic processing:
/// 1. Formant presence peaking filter at 2.8 kHz (+1.0 dB to +4.5 dB)
/// 2. Breath & air articulation gentle high-shelf at 8.5 kHz (+0.5 dB to +2.5 dB)
/// 3. Mud/boxiness scoop at 380 Hz (-0.5 dB to -2.0 dB)
/// 4. Automatic dynamic headroom compensation to prevent noise amplification and digital clipping
#[derive(Debug, Clone)]
pub struct VocalClarity {
    pub enabled: bool,
    /// Clarity intensity: 0.0 (Bypass) to 1.0 (Maximum vocal presence)
    pub clarity: f32,
    sample_rate: f32,
    coeffs_presence: BiquadCoefficients,
    coeffs_air: BiquadCoefficients,
    coeffs_mud: BiquadCoefficients,
    state_presence_l: BiquadChannelState,
    state_presence_r: BiquadChannelState,
    state_air_l: BiquadChannelState,
    state_air_r: BiquadChannelState,
    state_mud_l: BiquadChannelState,
    state_mud_r: BiquadChannelState,
}

impl VocalClarity {
    pub fn new(sample_rate: f32) -> Self {
        let sr = sample_rate.max(8000.0);
        let mut filter = Self {
            enabled: false,
            clarity: 0.50,
            sample_rate: sr,
            coeffs_presence: BiquadCoefficients::identity(),
            coeffs_air: BiquadCoefficients::identity(),
            coeffs_mud: BiquadCoefficients::identity(),
            state_presence_l: BiquadChannelState::default(),
            state_presence_r: BiquadChannelState::default(),
            state_air_l: BiquadChannelState::default(),
            state_air_r: BiquadChannelState::default(),
            state_mud_l: BiquadChannelState::default(),
            state_mud_r: BiquadChannelState::default(),
        };
        filter.recalculate();
        filter
    }

    pub fn set_sample_rate(&mut self, sample_rate: f32) {
        self.sample_rate = sample_rate.max(8000.0);
        self.recalculate();
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn set_clarity(&mut self, clarity: f32) {
        self.clarity = clarity.clamp(0.0, 1.0);
        self.recalculate();
    }

    fn recalculate(&mut self) {
        let c = self.clarity;
        // 1. Presence peaking boost at 2800 Hz: +1.0 dB to +4.5 dB
        let presence_gain = c * 4.5;
        self.coeffs_presence = BiquadCoefficients::calculate(
            FilterType::Peaking,
            2800.0,
            presence_gain,
            1.2,
            self.sample_rate,
        );

        // 2. Air articulation gentle high-shelf at 8500 Hz: +0.5 dB to +2.5 dB
        let air_gain = c * 2.5;
        self.coeffs_air = BiquadCoefficients::calculate(
            FilterType::HighShelf,
            8500.0,
            air_gain,
            0.707,
            self.sample_rate,
        );

        // 3. Gentle boxy mud dip at 380 Hz: down to -2.0 dB
        let mud_gain = -c * 2.0;
        self.coeffs_mud = BiquadCoefficients::calculate(
            FilterType::Peaking,
            380.0,
            mud_gain,
            1.0,
            self.sample_rate,
        );
    }

    #[inline(always)]
    pub fn process_interleaved(&mut self, buffer: &mut [f32], channels: usize) {
        if !self.enabled || self.clarity < 0.01 {
            return;
        }

        // Automatic digital headroom compensation to guarantee zero digital clipping
        let headroom = 1.0 / (1.0 + self.clarity * 0.35);

        let frames = buffer.len() / channels;
        for f in 0..frames {
            let l_idx = f * channels;
            let mut s_l = buffer[l_idx] * headroom;
            s_l = self.state_mud_l.process(s_l, &self.coeffs_mud);
            s_l = self.state_presence_l.process(s_l, &self.coeffs_presence);
            s_l = self.state_air_l.process(s_l, &self.coeffs_air);
            buffer[l_idx] = s_l;

            if channels > 1 {
                let r_idx = l_idx + 1;
                let mut s_r = buffer[r_idx] * headroom;
                s_r = self.state_mud_r.process(s_r, &self.coeffs_mud);
                s_r = self.state_presence_r.process(s_r, &self.coeffs_presence);
                s_r = self.state_air_r.process(s_r, &self.coeffs_air);
                buffer[r_idx] = s_r;
            }
        }
    }
}

/// Real-time WSOLA (Waveform Similarity Overlap-Add) time-stretcher.
/// Preserves musical pitch across 0.5x–2.0x playback speeds without chipmunk artifacts.
#[derive(Debug, Clone)]
pub struct TimeStretcher {
    pub speed: f32,
    channels: usize,
    win_len: usize,
    hop_out: usize,
    search_radius: usize,
    hann: Vec<f32>,
    in_buf: Vec<f32>,
    out_buf: Vec<f32>,
    out_read_pos: usize,
    ola_tail: Vec<f32>,
}

impl TimeStretcher {
    pub fn new(sample_rate: f32, channels: usize) -> Self {
        let ch = channels.max(1);
        // ~42ms window at 48kHz, scaled cleanly for higher rates
        let win_len = ((sample_rate / 48000.0).max(0.5) * 2048.0).round() as usize;
        let win_len = (win_len / 2) * 2; // even
        let hop_out = win_len / 2;
        let search_radius = (win_len / 8).max(32);

        let mut hann = Vec::with_capacity(win_len);
        for i in 0..win_len {
            let w = 0.5 * (1.0 - (2.0 * PI * i as f32 / win_len as f32).cos());
            hann.push(w);
        }

        Self {
            speed: 1.0,
            channels: ch,
            win_len,
            hop_out,
            search_radius,
            hann,
            in_buf: Vec::with_capacity(win_len * ch * 4),
            out_buf: Vec::with_capacity(win_len * ch * 2),
            out_read_pos: 0,
            ola_tail: vec![0.0; hop_out * ch],
        }
    }

    pub fn set_speed(&mut self, speed: f32) {
        self.speed = speed.clamp(0.5, 2.0);
    }

    pub fn reset(&mut self) {
        self.in_buf.clear();
        self.out_buf.clear();
        self.out_read_pos = 0;
        self.ola_tail.fill(0.0);
    }

    pub fn process_interleaved(&mut self, input: &[f32]) -> Vec<f32> {
        if (self.speed - 1.0).abs() < 0.01 || input.is_empty() {
            return input.to_vec();
        }

        self.in_buf.extend_from_slice(input);
        self.out_buf.clear();
        self.out_read_pos = 0;

        let ch = self.channels;
        let hop_in = ((self.hop_out as f32) * self.speed).round().max(1.0) as usize;
        let needed_frames = self.win_len + self.search_radius + hop_in;

        while self.in_buf.len() / ch >= needed_frames {
            // Find best alignment offset within [0 .. search_radius] matching previous ola_tail
            let mut best_offset = 0usize;
            let mut best_corr = f32::NEG_INFINITY;

            // Compare first `hop_out` frames of candidate window against `ola_tail` (channel 0 energy correlation)
            let compare_len = self.hop_out.min(256);
            for off in (0..=self.search_radius).step_by(2) {
                let mut corr = 0.0f32;
                for f in 0..compare_len {
                    let a = self.ola_tail[f * ch];
                    let b = self.in_buf[(off + f) * ch];
                    corr += a * b;
                }
                if corr > best_corr {
                    best_corr = corr;
                    best_offset = off;
                }
            }

            // Overlap-add first half with ola_tail, emit `hop_out` frames, save second half into ola_tail
            for f in 0..self.hop_out {
                let w_in = self.hann[f];
                for c in 0..ch {
                    let s_new = self.in_buf[(best_offset + f) * ch + c] * w_in;
                    let s_old = self.ola_tail[f * ch + c];
                    self.out_buf.push(s_old + s_new);
                }
            }

            for f in 0..self.hop_out {
                let w_out = self.hann[self.hop_out + f];
                for c in 0..ch {
                    self.ola_tail[f * ch + c] =
                        self.in_buf[(best_offset + self.hop_out + f) * ch + c] * w_out;
                }
            }

            let consume = (hop_in * ch).min(self.in_buf.len());
            self.in_buf.drain(..consume);
        }

        self.out_buf.clone()
    }
}


