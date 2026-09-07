/// DoP (DSD over PCM v1.1) Packetizer.
///
/// Encapsulates raw 1-bit DSD bitstreams into standard 24-bit PCM audio frames
/// with alternating 0x05 / 0xFA marker headers for bit-perfect hardware DSD playback
/// on DoP-compatible USB DACs.
///
/// Specification:
/// - Output PCM Rate: 176.4 kHz for DSD64, 352.8 kHz for DSD128, 705.6 kHz for DSD256.
/// - 16 bits of DSD audio per 24-bit PCM sample.
/// - Top 8 bits (bits 16..23): Marker byte alternating between 0x05 and 0xFA every frame.
/// - Lower 16 bits (bits 0..15): 16 bits of DSD audio data (MSB first).
pub struct DopEncoder {
    /// Alternating marker state: true -> 0x05, false -> 0xFA
    marker_state: bool,
}

impl DopEncoder {
    pub fn new() -> Self {
        Self {
            marker_state: true,
        }
    }

    /// Reset marker state (on track start or seek)
    pub fn reset(&mut self) {
        self.marker_state = true;
    }

    /// Encode raw DSD channel buffers into interleaved 32-bit float DoP frames.
    /// `channel_data`: slice of raw DSD bytes for each channel (e.g. [Left, Right]).
    /// `is_lsb_first`: true for DSF (requires bit reversal), false for DSDIFF (MSB first).
    /// Returns: Interleaved float samples representing bit-exact 24-bit DoP PCM frames.
    pub fn encode_interleaved(&mut self, channel_data: &[&[u8]], is_lsb_first: bool) -> Vec<f32> {
        let num_channels = channel_data.len();
        if num_channels == 0 {
            return Vec::new();
        }

        // Each 24-bit DoP PCM sample consumes 2 bytes (16 bits) of DSD per channel
        let min_bytes = channel_data.iter().map(|d| d.len()).min().unwrap_or(0);
        let num_pcm_samples = min_bytes / 2;
        let mut interleaved_output = Vec::with_capacity(num_pcm_samples * num_channels);

        for sample_idx in 0..num_pcm_samples {
            // Marker byte: 0x05 on even frames, 0xFA on odd frames
            let marker = if self.marker_state { 0x05u32 } else { 0xFAu32 };
            self.marker_state = !self.marker_state;

            let byte_offset = sample_idx * 2;

            for ch in 0..num_channels {
                let mut b0 = channel_data[ch][byte_offset];
                let mut b1 = channel_data[ch][byte_offset + 1];

                if is_lsb_first {
                    // DSF stores LSB first -> reverse to MSB first for DoP standard
                    b0 = b0.reverse_bits();
                    b1 = b1.reverse_bits();
                }

                // 24-bit DoP word: [Marker: 8 bits][DSD byte 0: 8 bits][DSD byte 1: 8 bits]
                let raw_24 = (marker << 16) | ((b0 as u32) << 8) | (b1 as u32);

                // Convert 24-bit unsigned word to 24-bit two's complement signed integer
                let i24 = if (raw_24 & 0x800000) != 0 {
                    (raw_24 as i32) - 0x1000000
                } else {
                    raw_24 as i32
                };

                // Normalize 24-bit signed int to 32-bit float [-1.0, 1.0)
                // When AAudio / Oboe converts float back to 24-bit int for USB DAC,
                // multiplying by 8388608.0 recovers the exact original 24-bit DoP word!
                let float_sample = (i24 as f64 / 8388608.0) as f32;
                interleaved_output.push(float_sample);
            }
        }

        interleaved_output
    }
}
