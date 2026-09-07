use num_complex::Complex;
use rand::Rng;
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

/// Triangular Probability Density Function (TPDF) Dither generator
pub struct TpdfDither {
    last_random: f32,
}

impl TpdfDither {
    pub fn new() -> Self {
        Self { last_random: 0.0 }
    }

    /// Generates triangular dither in range [-1.0, +1.0] LSB
    pub fn next_dither(&mut self) -> f32 {
        let mut rng = rand::thread_rng();
        let r1: f32 = rng.gen_range(0.0..1.0);
        let r2: f32 = rng.gen_range(0.0..1.0);
        let dither = r1 - r2;
        self.last_random = dither;
        dither
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
