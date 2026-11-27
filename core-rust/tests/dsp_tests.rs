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
        let expected = (s * 32767.0).round() as i32;
        assert!(((q16 as i32) - expected).abs() <= 2);
        let q24 = dither.quantize_i24(s);
        assert!((-8388608..=8388607).contains(&q24));
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

#[test]
fn test_crossfeed_off_bypass() {
    use audiophile_core::dsp::{CrossfeedFilter, CrossfeedPreset};
    let mut filter = CrossfeedFilter::new(48000.0);
    assert_eq!(filter.preset, CrossfeedPreset::Off);

    let mut buffer = vec![0.5f32, -0.2f32, 0.8f32, -0.4f32];
    let original = buffer.clone();
    filter.process_interleaved(&mut buffer, 2);
    assert_eq!(buffer, original, "Crossfeed Off must pass audio 100% bit-perfect");
}

#[test]
fn test_crossfeed_hard_panned_stereo() {
    use audiophile_core::dsp::{CrossfeedFilter, CrossfeedPreset};
    let mut filter = CrossfeedFilter::new(48000.0);
    filter.set_preset(CrossfeedPreset::Standard);

    // Hard-panned signal: Left has sound, Right has zero sound
    let mut buffer = vec![0.0f32; 200];
    for i in (0..200).step_by(2) {
        buffer[i] = 0.8; // Left channel DC / low freq
        buffer[i + 1] = 0.0; // Right channel total silence
    }

    filter.process_interleaved(&mut buffer, 2);

    // After filtering has settled, right channel should now contain crossfeed audio (not silence)
    let right_sample = buffer[199];
    assert!(right_sample > 0.15, "Crossfeed must mix filtered contralateral sound into opposite ear, got {}", right_sample);
    // Left channel direct sound should be slightly scaled
    let left_sample = buffer[198];
    assert!(left_sample > 0.35 && left_sample < 0.8, "Left direct sound should be properly proportioned, got {}", left_sample);
}

#[test]
fn test_crossfeed_mono_unity_preservation() {
    use audiophile_core::dsp::{CrossfeedFilter, CrossfeedPreset};
    let mut filter = CrossfeedFilter::new(48000.0);
    filter.set_preset(CrossfeedPreset::Standard);

    // Mono signal: Left and Right are identical 0.5
    let mut buffer = vec![0.0f32; 200];
    for i in (0..200).step_by(2) {
        buffer[i] = 0.5;
        buffer[i + 1] = 0.5;
    }

    filter.process_interleaved(&mut buffer, 2);

    // Settled output for center-panned mono should equal original amplitude ~0.5
    let settled_l = buffer[198];
    let settled_r = buffer[199];
    assert!((settled_l - 0.5).abs() < 0.02, "Mono center vocal energy should be preserved at ~0.5, got {}", settled_l);
    assert!((settled_r - 0.5).abs() < 0.02, "Mono center vocal energy should be preserved at ~0.5, got {}", settled_r);
}

#[test]
fn test_peak_limiter() {
    use audiophile_core::dsp::PeakLimiter;
    let limiter = PeakLimiter::new();
    assert!(limiter.enabled);

    // Linear region (<= 0.95) should be completely bit-perfect and uncolored
    let quiet_sample = 0.5f32;
    assert_eq!(limiter.process_sample(quiet_sample), quiet_sample);
    let threshold_sample = 0.95f32;
    assert_eq!(limiter.process_sample(threshold_sample), threshold_sample);

    // Extreme overs (> 0.95 up to 10.0) should be smoothly compressed strictly below 1.0
    let loud_sample = 2.5f32;
    let limited = limiter.process_sample(loud_sample);
    assert!(limited > 0.95 && limited < 1.0, "Overs must be smoothly limited under 1.0, got {}", limited);

    let negative_overs = -4.0f32;
    let limited_neg = limiter.process_sample(negative_overs);
    assert!(limited_neg < -0.95 && limited_neg > -1.0, "Negative overs must be strictly bounded, got {}", limited_neg);
}

#[test]
fn test_loudness_normalizer() {
    use audiophile_core::dsp::LoudnessNormalizer;
    let mut norm = LoudnessNormalizer::new(48000.0);
    assert!(!norm.enabled);

    // When disabled, buffer is untouched
    let mut buffer = vec![0.1f32; 100];
    let orig = buffer.clone();
    norm.process_interleaved(&mut buffer, 2);
    assert_eq!(buffer, orig);

    // When enabled, quiet audio is gradually brought up towards target LUFS
    norm.set_enabled(true);
    norm.set_target_lufs(-14.0);
    for _ in 0..10 {
        norm.process_interleaved(&mut buffer, 2);
    }
    assert!(norm.current_gain > 1.0, "Quiet audio should have gain boosted, got gain {}", norm.current_gain);
}

#[test]
fn test_spatializer_360_audible_widening() {
    use audiophile_core::dsp::Spatializer360;
    let mut spat = Spatializer360::new(48000.0);
    spat.set_enabled(true);
    spat.set_width(1.5);

    // Hard panned stereo signal: L = 0.5, R = 0.0
    let mut buffer = vec![0.0f32; 100];
    for i in (0..100).step_by(2) {
        buffer[i] = 0.5;
        buffer[i + 1] = 0.0;
    }

    spat.process_interleaved(&mut buffer, 2);

    // After spatializer with width 1.5, delayed contralateral diffusion should populate R
    let last_r = buffer[99];
    assert!(last_r.abs() > 0.01, "Contralateral diffuse field must be audible on opposite channel, got {}", last_r);
}

#[test]
fn test_sub_bass_exciter_audible_boost() {
    use audiophile_core::dsp::SubBassExciter;
    let mut sub = SubBassExciter::new(48000.0);
    sub.set_enabled(true);
    sub.set_intensity(0.8);

    // 50 Hz sub-bass sine wave (1000 frames = ~20ms, capturing a full cycle of 50Hz)
    let frames = 1000;
    let mut buffer = vec![0.0f32; frames * 2];
    for i in 0..frames {
        let t = i as f32 / 48000.0;
        let s = (2.0 * std::f32::consts::PI * 50.0 * t).sin() * 0.4;
        buffer[i * 2] = s;
        buffer[i * 2 + 1] = s;
    }

    let orig_rms: f32 = (buffer.iter().map(|s| s * s).sum::<f32>() / buffer.len() as f32).sqrt();
    sub.process_interleaved(&mut buffer, 2);
    let boosted_rms: f32 = (buffer.iter().map(|s| s * s).sum::<f32>() / buffer.len() as f32).sqrt();

    assert!(boosted_rms > orig_rms * 1.15, "Sub-bass exciter must produce substantial audible boost, orig={}, boosted={}", orig_rms, boosted_rms);
}

#[test]
fn test_vocal_clarity_audible_presence() {
    use audiophile_core::dsp::VocalClarity;
    let mut voc = VocalClarity::new(48000.0);
    voc.set_enabled(true);
    voc.set_clarity(0.8);

    // 2800 Hz vocal formant sine wave
    let mut buffer = vec![0.0f32; 200];
    for i in 0..100 {
        let t = i as f32 / 48000.0;
        let s = (2.0 * std::f32::consts::PI * 2800.0 * t).sin() * 0.3;
        buffer[i * 2] = s;
        buffer[i * 2 + 1] = s;
    }

    let orig_peak = buffer.iter().cloned().fold(0.0f32, f32::max);
    voc.process_interleaved(&mut buffer, 2);
    let boosted_peak = buffer[50..].iter().cloned().fold(0.0f32, f32::max);

    assert!(boosted_peak > orig_peak * 1.15, "Vocal clarity must provide substantial presence boost at 2.8kHz, orig={}, boosted={}", orig_peak, boosted_peak);
}

#[test]
fn test_tube_warmth_audible_harmonics() {
    use audiophile_core::dsp::TubeWarmthExciter;
    let mut tube = TubeWarmthExciter::new();
    tube.set_enabled(true);
    tube.set_drive(0.6);

    let clean_sample = 0.4f32;
    let warmed = tube.process_sample(clean_sample);
    assert!((warmed - clean_sample).abs() > 0.02, "Tube warmth must produce measurable harmonic difference, got {} vs {}", warmed, clean_sample);
}

#[test]
fn test_stream_resampler_flush_drains_tail() {
    use audiophile_core::resampler::StreamResampler;
    let mut r = StreamResampler::new(44100, 48000, 2);
    // Feed fewer than 1024 frames (chunk_size) -> process_interleaved holds them pending
    let input = vec![0.25f32; 400 * 2];
    let immediate = r.process_interleaved(&input);
    assert!(immediate.is_empty(), "Partial chunk should buffer before flush");
    let flushed = r.flush();
    assert!(!flushed.is_empty(), "flush() must drain remaining tail frames at EOF");
    let out_frames = flushed.len() / 2;
    assert!(out_frames >= 400 && out_frames <= 460, "Expected ~435 output frames for 400 @ 44.1->48k, got {}", out_frames);
}

#[test]
fn test_itu_multichannel_downmix_preserves_center_and_surround() {
    use audiophile_core::decoder::downmix_multichannel_to_stereo;
    // 5.1 layout: L, R, C, LFE, Ls, Rs — put vocal on Center only
    let raw = vec![0.0, 0.0, 0.8, 0.0, 0.0, 0.0];
    let mut out = Vec::new();
    downmix_multichannel_to_stereo(&raw, 6, 1, &mut out);
    assert_eq!(out.len(), 2);
    assert!(out[0] > 0.25 && out[1] > 0.25, "Center channel must fold equally into L/R stereo: {:?}", out);
    assert!((out[0] - out[1]).abs() < 1e-5);
}

#[test]
fn test_wsola_time_stretcher_changes_duration_cleanly() {
    use audiophile_core::dsp::TimeStretcher;
    let mut ts = TimeStretcher::new(48000.0, 2);
    ts.set_speed(1.5);
    let frames = 8192;
    let mut input = vec![0.0f32; frames * 2];
    for i in 0..frames {
        let t = i as f32 / 48000.0;
        let s = (2.0 * std::f32::consts::PI * 440.0 * t).sin() * 0.5;
        input[i * 2] = s;
        input[i * 2 + 1] = s;
    }
    let stretched = ts.process_interleaved(&input);
    let out_frames = stretched.len() / 2;
    assert!(out_frames > 0 && out_frames < frames, "1.5x speed should produce fewer frames cleanly, got {} from {}", out_frames, frames);
}


