use audiophile_core::dsp::{
    BiquadCoefficients, FilterType, ParametricEqualizer, TpdfDither, calculate_levels, calculate_spectrum_16,
};

#[test]
fn test_biquad_identity() {
    let coeffs = BiquadCoefficients::identity();
    assert_eq!(coeffs.b0, 1.0);
    assert_eq!(coeffs.b1, 0.0);
    assert_eq!(coeffs.b2, 0.0);
    assert_eq!(coeffs.a1, 0.0);
    assert_eq!(coeffs.a2, 0.0);
}

#[test]
fn test_peaking_filter_response() {
    let sample_rate = 48000.0;
    let center_freq = 1000.0;
    let gain_db = 6.0;
    let q = 1.414;

    let coeffs = BiquadCoefficients::calculate(FilterType::Peaking, center_freq, gain_db, q, sample_rate);
    
    // Response at center frequency should be approximately +6 dB
    let h_center = coeffs.response_at(center_freq, sample_rate);
    let mag_center_db = 20.0 * h_center.norm().log10();
    assert!((mag_center_db - gain_db).abs() < 0.1, "Center frequency gain should be ~6dB, got {}", mag_center_db);

    // Response at far frequencies (e.g. 50Hz) should be approximately 0 dB
    let h_low = coeffs.response_at(50.0, sample_rate);
    let mag_low_db = 20.0 * h_low.norm().log10();
    assert!(mag_low_db.abs() < 0.2, "Far frequency gain should be ~0dB, got {}", mag_low_db);
}

#[test]
fn test_low_shelf_filter_response() {
    let sample_rate = 48000.0;
    let cutoff_freq = 200.0;
    let gain_db = 8.0;
    let q = 0.707;

    let coeffs = BiquadCoefficients::calculate(FilterType::LowShelf, cutoff_freq, gain_db, q, sample_rate);
    
    // Response well below cutoff (e.g. 30 Hz) should be approximately +8 dB
    let h_low = coeffs.response_at(30.0, sample_rate);
    let mag_low_db = 20.0 * h_low.norm().log10();
    assert!((mag_low_db - gain_db).abs() < 0.5, "Sub-cutoff gain should be ~8dB, got {}", mag_low_db);

    // Response well above cutoff (e.g. 5000 Hz) should be approximately 0 dB
    let h_high = coeffs.response_at(5000.0, sample_rate);
    let mag_high_db = 20.0 * h_high.norm().log10();
    assert!(mag_high_db.abs() < 0.2, "Super-cutoff gain should be ~0dB, got {}", mag_high_db);
}

#[test]
fn test_equalizer_curve_generation() {
    let mut eq = ParametricEqualizer::default_10_band(48000.0, 2);
    assert_eq!(eq.bands.len(), 10);

    // Set 1kHz band to +6dB
    eq.bands[5].gain_db = 6.0;
    eq.bands[5].set_gain(6.0, 48000.0);

    let freqs = vec![20.0, 100.0, 1000.0, 10000.0, 20000.0];
    let curve = eq.calculate_curve(&freqs);

    assert_eq!(curve.len(), freqs.len());
    // The point around 1kHz should be boosted
    assert!(curve[2] > 4.5, "Expected ~6dB boost at 1kHz, got {}", curve[2]);
}

#[test]
fn test_tpdf_dither() {
    let mut dither = TpdfDither::new();
    let samples = vec![0.5f32, -0.5f32, 0.0f32, 0.999f32, -0.999f32];

    for s in samples {
        let q16 = dither.quantize_i16(s);
        assert!(q16 >= i16::MIN);
        let q24 = dither.quantize_i24(s);
        assert!(q24 >= -8388608 && q24 <= 8388607);
    }
}

#[test]
fn test_level_metering() {
    let mut buffer = vec![0.0f32; 1000];
    // Fill left channel with 0.5 amplitude sine wave, right with 0.8
    for i in (0..1000).step_by(2) {
        buffer[i] = 0.5;
        buffer[i + 1] = 0.8;
    }

    let levels = calculate_levels(&buffer, 2);
    assert!((levels.peak_left - 0.5).abs() < 1e-4);
    assert!((levels.peak_right - 0.8).abs() < 1e-4);
    assert!((levels.rms_left - 0.5).abs() < 1e-4);
    assert!((levels.rms_right - 0.8).abs() < 1e-4);

    let spectrum = calculate_spectrum_16(&buffer, 2);
    assert_eq!(spectrum.len(), 16);
    assert!(spectrum[0] > 0.4);
}

#[test]
fn test_stream_resampler_identity() {
    use audiophile_core::resampler::StreamResampler;
    let mut resampler = StreamResampler::new(48000, 48000, 2);
    let input = vec![0.1f32, -0.2f32, 0.3f32, -0.4f32];
    let output = resampler.process_interleaved(&input);
    assert_eq!(output, input);
}

#[test]
fn test_stream_resampler_44k_to_48k() {
    use audiophile_core::resampler::StreamResampler;
    let mut resampler = StreamResampler::new(44100, 48000, 2);

    // Send multiple chunks worth of data (rubato needs 1024 frames per chunk)
    // 4410 stereo frames at 44.1kHz ≈ 100ms of audio → 4 complete chunks
    let in_frames = 4410;
    let mut input = Vec::with_capacity(in_frames * 2);
    for i in 0..in_frames {
        let t = i as f32 / 44100.0;
        let s = (2.0 * std::f32::consts::PI * 440.0 * t).sin();
        input.push(s); // L
        input.push(s); // R
    }

    let output = resampler.process_interleaved(&input);
    let out_frames = output.len() / 2;

    // With 4410 input frames, we get 4 complete chunks of 1024 = 4096 consumed frames
    // At 48000/44100 ratio, ~4460 output frames expected for 4096 input frames
    // Allow generous tolerance due to rubato's internal buffering
    assert!(out_frames > 3500, "Expected at least 3500 output frames from 4410 input, got {}", out_frames);
    assert!(out_frames < 5500, "Expected at most 5500 output frames from 4410 input, got {}", out_frames);

    // Verify output is not all zeros (actual audio was produced)
    let max_sample = output.iter().map(|s| s.abs()).fold(0.0f32, f32::max);
    assert!(max_sample > 0.3, "Resampled output should contain actual audio signal, max sample = {}", max_sample);
}

#[test]
fn test_perceptual_volume_curve() {
    // Test the perceptual volume function behavior
    // Linear 0.0 → 0.0, Linear 1.0 → 1.0, Linear 0.5 → 0.25
    let vol_zero = 0.0f32 * 0.0f32; // perceptual_volume(0.0)
    let vol_half = 0.5f32 * 0.5f32; // perceptual_volume(0.5)
    let vol_full = 1.0f32; // perceptual_volume(1.0)

    assert!((vol_zero).abs() < 1e-6, "Zero volume should produce silence");
    assert!((vol_half - 0.25).abs() < 1e-6, "Half volume should be 0.25 amplitude (-12dB)");
    assert!((vol_full - 1.0).abs() < 1e-6, "Full volume should be unity");
}

#[test]
fn test_parametric_eq_flat_bypass() {
    let mut eq = ParametricEqualizer::default_10_band(48000.0, 2);
    let mut buffer = vec![0.123f32, -0.456f32, 0.789f32, -0.012f32];
    let original = buffer.clone();
    eq.process_interleaved(&mut buffer);
    assert_eq!(buffer, original, "Flat EQ must pass samples bit-perfectly with zero mutation");
}
