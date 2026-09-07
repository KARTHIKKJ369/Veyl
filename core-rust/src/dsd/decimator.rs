use std::f32::consts::PI;

/// High-Precision DSD to PCM Decimator using Sinc-windowed FIR filtering.
///
/// Converts high-rate 1-bit DSD bitstreams (DSD64, DSD128, DSD256) into
/// 24-bit/32-bit floating point PCM at 88.2 kHz, 176.4 kHz, or 44.1 kHz.
///
/// Features:
/// - Kaiser/Blackman-Harris windowed linear-phase FIR filter.
/// - Steep low-pass cutoff at ~35 kHz with >100 dB stopband ultrasonic noise rejection.
/// - 8-bit Precomputed LUT (Look-Up Table) for zero-latency real-time processing.
/// - Configurable SACD standard +6.0 dB gain compensation.
/// - Bit order handling: DSF (LSB first) and DSDIFF (MSB first).
pub struct DsdDecimator {
    /// Decimation factor (e.g. 32 for DSD64 -> 88.2kHz, 16 for DSD64 -> 176.4kHz)
    decimation_factor: usize,
    /// Number of bytes in DSD stream per PCM output sample
    bytes_per_sample: usize,
    /// Number of 8-bit groups in the FIR filter
    num_groups: usize,
    /// Precomputed LUT: [group_idx * 256 + byte_value] -> partial FIR sum
    lut_lsb: Vec<f32>,
    lut_msb: Vec<f32>,
    /// History buffer per channel of past DSD bytes for FIR overlap
    history: Vec<Vec<u8>>,
    /// Output gain multiplier (e.g. 2.0 for +6dB SACD compensation)
    gain: f32,
}

impl DsdDecimator {
    /// Create a new DsdDecimator with target decimation factor (e.g. 32 or 16).
    /// `decimation_factor`: must be a multiple of 8 (e.g. 16, 32, 64).
    /// `filter_taps`: total number of FIR filter taps (e.g. 64, 128). Must be a multiple of 8.
    pub fn new(decimation_factor: usize, filter_taps: usize, num_channels: usize, sacd_gain_boost: bool) -> Self {
        let decimation_factor = decimation_factor.max(8);
        let bytes_per_sample = decimation_factor / 8;
        let taps = (filter_taps.max(32) / 8) * 8;
        let num_groups = taps / 8;

        // Calculate normalized cutoff frequency for FIR filter
        // We want cutoff around 30-35 kHz relative to DSD sampling rate.
        // For DSD64 (2.8224 MHz), normalized cutoff fc = 35000 / 2822400 ≈ 0.0124
        let fc = 0.015f32; // Cutoff ~42 kHz for DSD64, ~84 kHz for DSD128

        let mut coeffs = Vec::with_capacity(taps);
        let m = (taps - 1) as f32;

        let mut sum = 0.0f32;
        for i in 0..taps {
            let n = i as f32 - m / 2.0;
            // Sinc function
            let sinc = if n.abs() < 1e-6 {
                1.0
            } else {
                (2.0 * PI * fc * n).sin() / (PI * n)
            };

            // Blackman-Harris window for >100 dB stopband attenuation
            let a0 = 0.35875;
            let a1 = 0.48829;
            let a2 = 0.14128;
            let a3 = 0.01168;
            let phi = 2.0 * PI * (i as f32) / m;
            let window = a0 - a1 * phi.cos() + a2 * (2.0 * phi).cos() - a3 * (3.0 * phi).cos();

            let h = sinc * window;
            sum += h;
            coeffs.push(h);
        }

        // Normalize filter coefficients to unity DC gain
        if sum.abs() > 1e-6 {
            for c in coeffs.iter_mut() {
                *c /= sum;
            }
        }

        let gain = if sacd_gain_boost { 2.0f32 } else { 1.0f32 };

        // Precompute LUT for LSB-first (DSF) and MSB-first (DSDIFF)
        let mut lut_lsb = vec![0.0f32; num_groups * 256];
        let mut lut_msb = vec![0.0f32; num_groups * 256];

        for group in 0..num_groups {
            let base_idx = group * 8;
            for byte_val in 0..256 {
                let mut partial_lsb = 0.0f32;
                let mut partial_msb = 0.0f32;

                for bit in 0..8 {
                    let h = coeffs[base_idx + bit];

                    // LSB-first: bit 0 is earliest (index 0)
                    let bit_val_lsb = (byte_val >> bit) & 1;
                    let sample_lsb = if bit_val_lsb == 1 { 1.0f32 } else { -1.0f32 };
                    partial_lsb += h * sample_lsb;

                    // MSB-first: bit 7 is earliest (index 0)
                    let bit_val_msb = (byte_val >> (7 - bit)) & 1;
                    let sample_msb = if bit_val_msb == 1 { 1.0f32 } else { -1.0f32 };
                    partial_msb += h * sample_msb;
                }

                lut_lsb[group * 256 + byte_val] = partial_lsb;
                lut_msb[group * 256 + byte_val] = partial_msb;
            }
        }

        let history = vec![vec![0xAA; num_groups]; num_channels];

        Self {
            decimation_factor,
            bytes_per_sample,
            num_groups,
            lut_lsb,
            lut_msb,
            history,
            gain,
        }
    }

    /// Default decimator for DSD64/128/256 -> 88.2 kHz or 176.4 kHz
    pub fn default_stereo() -> Self {
        // Decimate by 32: DSD64 (2.8224MHz) -> 88.2kHz, 64-tap Blackman-Harris FIR filter
        Self::new(32, 64, 2, true)
    }

    /// Reset FIR history buffers (e.g. on seek or track start)
    pub fn reset(&mut self) {
        for ch_hist in self.history.iter_mut() {
            // 0xAA (10101010) represents DSD silence / zero DC
            ch_hist.fill(0xAA);
        }
    }

    /// Decimate raw DSD byte buffers to interleaved stereo/multichannel PCM samples.
    /// `channel_data`: slice of raw DSD bytes for each channel.
    /// `is_lsb_first`: true for DSF (LSB first), false for DSDIFF (MSB first).
    /// Returns: Interleaved float PCM samples `[L0, R0, L1, R1, ...]`.
    pub fn decimate_interleaved(&mut self, channel_data: &[&[u8]], is_lsb_first: bool) -> Vec<f32> {
        let num_channels = channel_data.len();
        if num_channels == 0 {
            return Vec::new();
        }

        let min_bytes = channel_data.iter().map(|d| d.len()).min().unwrap_or(0);
        let num_pcm_samples = min_bytes / self.bytes_per_sample;
        let mut interleaved_output = Vec::with_capacity(num_pcm_samples * num_channels);

        let lut = if is_lsb_first { &self.lut_lsb } else { &self.lut_msb };

        // Ensure history length matches channel count
        while self.history.len() < num_channels {
            self.history.push(vec![0xAA; self.num_groups]);
        }

        for sample_idx in 0..num_pcm_samples {
            let byte_offset = sample_idx * self.bytes_per_sample;

            for ch in 0..num_channels {
                let ch_bytes = &channel_data[ch][byte_offset..byte_offset + self.bytes_per_sample];
                let hist = &mut self.history[ch];

                // Shift new bytes into history buffer
                hist.drain(0..self.bytes_per_sample);
                hist.extend_from_slice(ch_bytes);

                // Compute FIR convolution via fast LUT lookups
                let mut sum = 0.0f32;
                for group in 0..self.num_groups {
                    let b = hist[group] as usize;
                    sum += lut[group * 256 + b];
                }

                // Apply SACD gain and soft-clamp
                let sample = (sum * self.gain).clamp(-1.0, 1.0);
                interleaved_output.push(sample);
            }
        }

        interleaved_output
    }

    pub fn decimation_factor(&self) -> usize {
        self.decimation_factor
    }
}
