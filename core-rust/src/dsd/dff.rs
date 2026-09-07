use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;

use super::{DsdError, DsdFormat, DsdRate, DsdReader, DsdStreamInfo};

pub struct DffReader {
    file: File,
    info: DsdStreamInfo,
    data_start_offset: u64,
    #[allow(dead_code)]
    data_size: u64,
    current_sample: u64,
    channel_count: usize,
}

impl DffReader {
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, DsdError> {
        let mut file = File::open(path)?;

        // 1. Read 'FRM8' Header (12 bytes: 4 byte ID, 8 byte size, 4 byte form type)
        let mut frm8_hdr = [0u8; 16];
        file.read_exact(&mut frm8_hdr)?;

        if &frm8_hdr[0..4] != b"FRM8" {
            return Err(DsdError::InvalidHeader("Missing 'FRM8' header in DSDIFF".to_string()));
        }

        let _frm8_size = u64::from_be_bytes(frm8_hdr[4..12].try_into().unwrap());
        let form_type = &frm8_hdr[12..16];

        if form_type != b"DSD " {
            return Err(DsdError::InvalidHeader("Invalid DSDIFF form type (expected 'DSD ')".to_string()));
        }

        let mut sample_rate = 2822400u32;
        let mut channel_count = 2u32;
        let mut data_start_offset = 0u64;
        let mut data_size = 0u64;

        // Parse chunks sequentially
        let mut chunk_hdr = [0u8; 12];
        while file.read_exact(&mut chunk_hdr).is_ok() {
            let chunk_id = &chunk_hdr[0..4];
            let chunk_size = u64::from_be_bytes(chunk_hdr[4..12].try_into().unwrap());

            match chunk_id {
                b"PROP" => {
                    // Read property type (4 bytes, e.g. "SND ")
                    let mut prop_type = [0u8; 4];
                    file.read_exact(&mut prop_type)?;

                    let mut prop_remaining = chunk_size.saturating_sub(4);
                    while prop_remaining >= 12 {
                        let mut sub_hdr = [0u8; 12];
                        if file.read_exact(&mut sub_hdr).is_err() {
                            break;
                        }
                        prop_remaining -= 12;

                        let sub_id = &sub_hdr[0..4];
                        let sub_size = u64::from_be_bytes(sub_hdr[4..12].try_into().unwrap());

                        match sub_id {
                            b"FS  " => {
                                let mut fs_buf = [0u8; 4];
                                file.read_exact(&mut fs_buf)?;
                                sample_rate = u32::from_be_bytes(fs_buf);
                                prop_remaining = prop_remaining.saturating_sub(4);
                                if sub_size > 4 {
                                    file.seek(SeekFrom::Current((sub_size - 4) as i64))?;
                                    prop_remaining = prop_remaining.saturating_sub(sub_size - 4);
                                }
                            }
                            b"CHNL" => {
                                let mut ch_buf = [0u8; 2];
                                file.read_exact(&mut ch_buf)?;
                                channel_count = u16::from_be_bytes(ch_buf) as u32;
                                prop_remaining = prop_remaining.saturating_sub(2);
                                if sub_size > 2 {
                                    file.seek(SeekFrom::Current((sub_size - 2) as i64))?;
                                    prop_remaining = prop_remaining.saturating_sub(sub_size - 2);
                                }
                            }
                            _ => {
                                file.seek(SeekFrom::Current(sub_size as i64))?;
                                prop_remaining = prop_remaining.saturating_sub(sub_size);
                            }
                        }
                    }
                }
                b"DSD " => {
                    data_start_offset = file.stream_position()?;
                    data_size = chunk_size;
                    // Seek past data or break
                    break;
                }
                _ => {
                    // Skip unknown chunk
                    file.seek(SeekFrom::Current(chunk_size as i64))?;
                }
            }
        }

        if data_start_offset == 0 {
            return Err(DsdError::InvalidHeader("Missing 'DSD ' audio data chunk in DSDIFF".to_string()));
        }

        if channel_count == 0 || channel_count > 8 {
            return Err(DsdError::UnsupportedChannels(channel_count));
        }

        let bytes_per_channel = data_size / channel_count as u64;
        let total_samples = bytes_per_channel * 8;
        let duration_seconds = if sample_rate > 0 {
            total_samples as f64 / sample_rate as f64
        } else {
            0.0
        };

        let dsd_rate = DsdRate::from_sample_rate(sample_rate);

        // Reset file pointer to start of audio data
        file.seek(SeekFrom::Start(data_start_offset))?;

        let info = DsdStreamInfo {
            format: DsdFormat::Dsdiff,
            dsd_rate,
            sample_rate,
            channels: channel_count,
            total_samples_per_channel: total_samples,
            duration_seconds,
            block_size_per_channel: 4096,
            title: None,
            artist: None,
            album: None,
            track_number: None,
            year: None,
        };

        Ok(Self {
            file,
            info,
            data_start_offset,
            data_size,
            current_sample: 0,
            channel_count: channel_count as usize,
        })
    }
}

impl DsdReader for DffReader {
    fn info(&self) -> &DsdStreamInfo {
        &self.info
    }

    /// Read next chunk of DSD samples from DSDIFF file.
    /// DSDIFF is byte-interleaved: [L0, R0, L1, R1, L2, R2, ...].
    /// De-interleaves into separate channel buffers for DSP decimation or DoP encoding.
    fn read_dsd_chunk(&mut self, samples_per_channel: usize) -> Result<Vec<Vec<u8>>, DsdError> {
        if self.current_sample >= self.info.total_samples_per_channel {
            return Ok(Vec::new());
        }

        // 4096 bytes per channel chunk
        let bytes_per_ch = 4096.min(samples_per_channel / 8).max(512);
        let total_bytes_to_read = bytes_per_ch * self.channel_count;

        let mut interleaved_buf = vec![0u8; total_bytes_to_read];
        let bytes_read = match self.file.read(&mut interleaved_buf) {
            Ok(0) => return Ok(Vec::new()),
            Ok(n) => n,
            Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => return Ok(Vec::new()),
            Err(e) => return Err(DsdError::Io(e)),
        };

        let actual_bytes_per_ch = bytes_read / self.channel_count;
        if actual_bytes_per_ch == 0 {
            return Ok(Vec::new());
        }

        let mut channel_data = vec![vec![0u8; actual_bytes_per_ch]; self.channel_count];

        // De-interleave bytes
        for i in 0..actual_bytes_per_ch {
            for ch in 0..self.channel_count {
                channel_data[ch][i] = interleaved_buf[i * self.channel_count + ch];
            }
        }

        let samples_read = (actual_bytes_per_ch * 8) as u64;
        self.current_sample = (self.current_sample + samples_read).min(self.info.total_samples_per_channel);

        Ok(channel_data)
    }

    fn seek_seconds(&mut self, seconds: f64) -> Result<f64, DsdError> {
        let target_sample = (seconds * self.info.sample_rate as f64) as u64;
        let target_sample = target_sample.min(self.info.total_samples_per_channel);

        let byte_offset_per_ch = target_sample / 8;
        let interleaved_offset = byte_offset_per_ch * self.channel_count as u64;

        let file_offset = self.data_start_offset + interleaved_offset;
        self.file.seek(SeekFrom::Start(file_offset))?;

        let actual_sample = byte_offset_per_ch * 8;
        self.current_sample = actual_sample;

        let actual_seconds = actual_sample as f64 / self.info.sample_rate as f64;
        Ok(actual_seconds)
    }

    fn current_time_seconds(&self) -> f64 {
        if self.info.sample_rate > 0 {
            self.current_sample as f64 / self.info.sample_rate as f64
        } else {
            0.0
        }
    }
}
