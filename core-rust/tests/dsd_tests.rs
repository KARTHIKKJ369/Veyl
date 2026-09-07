use audiophile_core::dsd::decimator::DsdDecimator;
use audiophile_core::dsd::dop::DopEncoder;
use audiophile_core::dsd::dsf::DsfReader;
use audiophile_core::dsd::dff::DffReader;
use audiophile_core::dsd::{DsdReader, DsdRate};
use std::io::Write;
use std::fs::File;

#[test]
fn test_dsd_decimator_silence() {
    let mut decimator = DsdDecimator::default_stereo();
    // 0xAA (10101010) in DSD is zero DC / silence
    let silence_l = vec![0xAAu8; 128];
    let silence_r = vec![0xAAu8; 128];

    let pcm = decimator.decimate_interleaved(&[&silence_l, &silence_r], true);
    assert!(!pcm.is_empty());
    // In DSD silence, the output after FIR filtering should be near 0.0 (within ±0.05)
    for sample in pcm {
        assert!(sample.abs() < 0.1, "Silence sample should be close to 0.0, got {}", sample);
    }
}

#[test]
fn test_dsd_decimator_dc_positive() {
    let mut decimator = DsdDecimator::new(32, 64, 1, false); // unity gain
    // 0xFF (all 1s) in DSD is maximum positive DC (+1.0)
    let dc_pos = vec![0xFFu8; 256];

    let pcm = decimator.decimate_interleaved(&[&dc_pos], true);
    assert!(!pcm.is_empty());
    let last_sample = *pcm.last().unwrap();
    // Filter output should converge to +1.0
    assert!((last_sample - 1.0).abs() < 0.05, "DC +1.0 output should be ~1.0, got {}", last_sample);
}

#[test]
fn test_dop_encoder_alternating_markers() {
    let mut encoder = DopEncoder::new();
    let dsd_l = vec![0x12u8, 0x34, 0x56, 0x78];
    let dsd_r = vec![0xABu8, 0xCD, 0xEF, 0x01];

    let pcm = encoder.encode_interleaved(&[&dsd_l, &dsd_r], false);
    // 4 bytes / 2 bytes per sample = 2 stereo frames = 4 float samples [L0, R0, L1, R1]
    assert_eq!(pcm.len(), 4);

    // Reconstruct 24-bit integer from float
    let i24_l0 = (pcm[0] as f64 * 8388608.0).round() as i32;
    let i24_r0 = (pcm[1] as f64 * 8388608.0).round() as i32;
    let i24_l1 = (pcm[2] as f64 * 8388608.0).round() as i32;
    let i24_r1 = (pcm[3] as f64 * 8388608.0).round() as i32;

    let marker_0 = ((i24_l0 >> 16) & 0xFF) as u8;
    let marker_0_r = ((i24_r0 >> 16) & 0xFF) as u8;
    let marker_1 = ((i24_l1 >> 16) & 0xFF) as u8;
    let marker_1_r = ((i24_r1 >> 16) & 0xFF) as u8;

    // Frame 0 marker must be 0x05
    assert_eq!(marker_0, 0x05);
    assert_eq!(marker_0_r, 0x05);

    // Frame 1 marker must be 0xFA
    assert_eq!(marker_1, 0xFA);
    assert_eq!(marker_1_r, 0xFA);
}

#[test]
fn test_dsf_container_creation_and_parsing() {
    let temp_dir = std::env::temp_dir();
    let dsf_path = temp_dir.join("test_synthetic.dsf");

    // Create valid synthetic DSF file
    {
        let mut file = File::create(&dsf_path).unwrap();

        // 1. 'DSD ' Header (28 bytes)
        file.write_all(b"DSD ").unwrap();
        file.write_all(&28u64.to_le_bytes()).unwrap(); // chunk size
        let file_size = 28 + 52 + 12 + (4096 * 2);
        file.write_all(&(file_size as u64).to_le_bytes()).unwrap(); // total file size
        file.write_all(&0u64.to_le_bytes()).unwrap(); // metadata offset

        // 2. 'fmt ' Chunk (52 bytes)
        file.write_all(b"fmt ").unwrap();
        file.write_all(&52u64.to_le_bytes()).unwrap(); // chunk size
        file.write_all(&1u32.to_le_bytes()).unwrap(); // format version
        file.write_all(&0u32.to_le_bytes()).unwrap(); // format id
        file.write_all(&2u32.to_le_bytes()).unwrap(); // channel type (stereo)
        file.write_all(&2u32.to_le_bytes()).unwrap(); // channel num (2)
        file.write_all(&2822400u32.to_le_bytes()).unwrap(); // sampling freq (DSD64)
        file.write_all(&1u32.to_le_bytes()).unwrap(); // bits per sample (1)
        file.write_all(&(4096u64 * 8).to_le_bytes()).unwrap(); // sample count per channel
        file.write_all(&4096u32.to_le_bytes()).unwrap(); // block size per channel
        file.write_all(&0u32.to_le_bytes()).unwrap(); // reserved

        // 3. 'data' Chunk (12 header + 8192 audio bytes)
        file.write_all(b"data").unwrap();
        file.write_all(&(12u64 + 4096 * 2).to_le_bytes()).unwrap(); // chunk size
        // Left channel block
        file.write_all(&vec![0xAAu8; 4096]).unwrap();
        // Right channel block
        file.write_all(&vec![0xAAu8; 4096]).unwrap();
    }

    let mut reader = DsfReader::open(&dsf_path).unwrap();
    assert_eq!(reader.info().sample_rate, 2822400);
    assert_eq!(reader.info().channels, 2);
    assert_eq!(reader.info().dsd_rate, DsdRate::Dsd64);

    let chunk = reader.read_dsd_chunk(32768).unwrap();
    assert_eq!(chunk.len(), 2);
    assert_eq!(chunk[0].len(), 4096);
    assert_eq!(chunk[1].len(), 4096);

    let _ = std::fs::remove_file(dsf_path);
}

#[test]
fn test_dff_container_creation_and_parsing() {
    let temp_dir = std::env::temp_dir();
    let dff_path = temp_dir.join("test_synthetic.dff");

    // Create valid synthetic DSDIFF / DFF file
    {
        let mut file = File::create(&dff_path).unwrap();

        // 1. 'FRM8' Header
        file.write_all(b"FRM8").unwrap();
        let total_size = 4 + (12 + 4 + 16 + 10) + (12 + 2048);
        file.write_all(&(total_size as u64).to_be_bytes()).unwrap();
        file.write_all(b"DSD ").unwrap();

        // 2. 'PROP' Chunk with 'SND '
        file.write_all(b"PROP").unwrap();
        let prop_size = 4 + 16 + 10;
        file.write_all(&(prop_size as u64).to_be_bytes()).unwrap();
        file.write_all(b"SND ").unwrap();

        // Subchunk: 'FS  ' (4 bytes sample rate)
        file.write_all(b"FS  ").unwrap();
        file.write_all(&4u64.to_be_bytes()).unwrap();
        file.write_all(&2822400u32.to_be_bytes()).unwrap();

        // Subchunk: 'CHNL' (2 bytes channel count)
        file.write_all(b"CHNL").unwrap();
        file.write_all(&2u64.to_be_bytes()).unwrap();
        file.write_all(&2u16.to_be_bytes()).unwrap();

        // 3. 'DSD ' Audio Data Chunk
        file.write_all(b"DSD ").unwrap();
        file.write_all(&2048u64.to_be_bytes()).unwrap();
        file.write_all(&vec![0xAAu8; 2048]).unwrap();
    }

    let mut reader = DffReader::open(&dff_path).unwrap();
    assert_eq!(reader.info().sample_rate, 2822400);
    assert_eq!(reader.info().channels, 2);
    assert_eq!(reader.info().dsd_rate, DsdRate::Dsd64);

    let chunk = reader.read_dsd_chunk(4096).unwrap();
    assert_eq!(chunk.len(), 2);
    assert!(!chunk[0].is_empty());

    let _ = std::fs::remove_file(dff_path);
}
