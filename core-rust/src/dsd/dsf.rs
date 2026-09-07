use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;

use super::{DsdError, DsdFormat, DsdRate, DsdReader, DsdStreamInfo};

pub struct DsfReader {
    file: File,
    info: DsdStreamInfo,
    data_start_offset: u64,
    #[allow(dead_code)]
    data_size: u64,
    current_sample: u64,
    block_size: usize,
    channel_count: usize,
}

impl DsfReader {
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, DsdError> {
        let mut file = File::open(path)?;

        // 1. Parse 'DSD ' Header Chunk (28 bytes)
        let mut dsd_hdr = [0u8; 28];
        file.read_exact(&mut dsd_hdr)?;

        if &dsd_hdr[0..4] != b"DSD " {
            return Err(DsdError::InvalidHeader("Missing 'DSD ' header marker".to_string()));
        }

        let chunk_size = u64::from_le_bytes(dsd_hdr[4..12].try_into().unwrap());
        let file_size = u64::from_le_bytes(dsd_hdr[12..20].try_into().unwrap());
        let metadata_offset = u64::from_le_bytes(dsd_hdr[20..28].try_into().unwrap());

        if chunk_size != 28 {
            file.seek(SeekFrom::Start(chunk_size))?;
        }

        // 2. Parse 'fmt ' Chunk (52 bytes)
        let mut fmt_hdr = [0u8; 52];
        file.read_exact(&mut fmt_hdr)?;

        if &fmt_hdr[0..4] != b"fmt " {
            return Err(DsdError::InvalidHeader("Missing 'fmt ' chunk in DSF".to_string()));
        }

        let fmt_chunk_size = u64::from_le_bytes(fmt_hdr[4..12].try_into().unwrap());
        let _format_version = u32::from_le_bytes(fmt_hdr[12..16].try_into().unwrap());
        let _format_id = u32::from_le_bytes(fmt_hdr[16..20].try_into().unwrap());
        let _channel_type = u32::from_le_bytes(fmt_hdr[20..24].try_into().unwrap());
        let channel_count = u32::from_le_bytes(fmt_hdr[24..28].try_into().unwrap());
        let sample_rate = u32::from_le_bytes(fmt_hdr[28..32].try_into().unwrap());
        let bits_per_sample = u32::from_le_bytes(fmt_hdr[32..36].try_into().unwrap());
        let sample_count = u64::from_le_bytes(fmt_hdr[36..44].try_into().unwrap());
        let block_size = u32::from_le_bytes(fmt_hdr[44..48].try_into().unwrap());

        if channel_count == 0 || channel_count > 8 {
            return Err(DsdError::UnsupportedChannels(channel_count));
        }

        if bits_per_sample != 1 {
            return Err(DsdError::InvalidHeader(format!("DSF bits per sample must be 1, got {}", bits_per_sample)));
        }

        if fmt_chunk_size > 52 {
            let extra = fmt_chunk_size - 52;
            file.seek(SeekFrom::Current(extra as i64))?;
        }

        // 3. Parse 'data' Chunk Header (12 bytes)
        let mut data_hdr = [0u8; 12];
        file.read_exact(&mut data_hdr)?;

        if &data_hdr[0..4] != b"data" {
            return Err(DsdError::InvalidHeader("Missing 'data' chunk in DSF".to_string()));
        }

        let data_chunk_size = u64::from_le_bytes(data_hdr[4..12].try_into().unwrap());
        let data_start_offset = file.stream_position()?;
        let data_size = data_chunk_size.saturating_sub(12);

        let duration_seconds = if sample_rate > 0 {
            sample_count as f64 / sample_rate as f64
        } else {
            0.0
        };

        let dsd_rate = DsdRate::from_sample_rate(sample_rate);

        // Optional: Parse ID3v2 metadata if present
        let (title, artist, album, track_number, year) = if metadata_offset > 0 && metadata_offset < file_size {
            Self::read_id3_tags(&mut file, metadata_offset)
        } else {
            (None, None, None, None, None)
        };

        // Reset file pointer to start of audio data
        file.seek(SeekFrom::Start(data_start_offset))?;

        let info = DsdStreamInfo {
            format: DsdFormat::Dsf,
            dsd_rate,
            sample_rate,
            channels: channel_count,
            total_samples_per_channel: sample_count,
            duration_seconds,
            block_size_per_channel: block_size,
            title,
            artist,
            album,
            track_number,
            year,
        };

        Ok(Self {
            file,
            info,
            data_start_offset,
            data_size,
            current_sample: 0,
            block_size: block_size as usize,
            channel_count: channel_count as usize,
        })
    }

    fn read_id3_tags(file: &mut File, offset: u64) -> (Option<String>, Option<String>, Option<String>, Option<u32>, Option<u32>) {
        if file.seek(SeekFrom::Start(offset)).is_err() {
            return (None, None, None, None, None);
        }

        let mut id3_hdr = [0u8; 10];
        if file.read_exact(&mut id3_hdr).is_err() || &id3_hdr[0..3] != b"ID3" {
            return (None, None, None, None, None);
        }

        // Tag length is encoded as 4 syncsafe integers (7 bits each)
        let tag_size = ((id3_hdr[6] as usize & 0x7F) << 21)
            | ((id3_hdr[7] as usize & 0x7F) << 14)
            | ((id3_hdr[8] as usize & 0x7F) << 7)
            | (id3_hdr[9] as usize & 0x7F);

        let mut tag_buf = vec![0u8; tag_size.min(1024 * 1024)]; // Cap at 1MB
        if file.read_exact(&mut tag_buf).is_err() {
            return (None, None, None, None, None);
        }

        let mut title = None;
        let mut artist = None;
        let mut album = None;
        let mut track_number = None;
        let mut year = None;

        let mut pos = 0;
        while pos + 10 <= tag_buf.len() {
            let frame_id = &tag_buf[pos..pos + 4];
            let frame_size = ((tag_buf[pos + 4] as usize) << 24)
                | ((tag_buf[pos + 5] as usize) << 16)
                | ((tag_buf[pos + 6] as usize) << 8)
                | (tag_buf[pos + 7] as usize);

            pos += 10;
            if frame_size == 0 || pos + frame_size > tag_buf.len() {
                break;
            }

            let frame_data = &tag_buf[pos..pos + frame_size];
            pos += frame_size;

            // Simple text frame parsing (skip 1 byte encoding header)
            if frame_data.len() > 1 {
                let text_bytes = &frame_data[1..];
                let text = String::from_utf8_lossy(text_bytes).trim_matches('\0').trim().to_string();

                match frame_id {
                    b"TIT2" => title = Some(text),
                    b"TPE1" | b"TPE2" => artist = Some(text),
                    b"TALB" => album = Some(text),
                    b"TRCK" => {
                        let track_str = text.split('/').next().unwrap_or("0");
                        track_number = track_str.parse().ok();
                    }
                    b"TYER" | b"TDRC" => {
                        let year_str = text.chars().take(4).collect::<String>();
                        year = year_str.parse().ok();
                    }
                    _ => {}
                }
            }
        }

        (title, artist, album, track_number, year)
    }
}

impl DsdReader for DsfReader {
    fn info(&self) -> &DsdStreamInfo {
        &self.info
    }

    /// Read next chunk of DSD samples for each channel.
    /// Reads one full DSF block group across all channels.
    /// Returns `Vec<Vec<u8>>` where each inner Vec has `block_size` bytes for that channel.
    fn read_dsd_chunk(&mut self, _target_samples: usize) -> Result<Vec<Vec<u8>>, DsdError> {
        if self.current_sample >= self.info.total_samples_per_channel {
            return Ok(Vec::new());
        }

        let mut channels_data = vec![vec![0u8; self.block_size]; self.channel_count];

        // DSF block layout: Channel 0 block, Channel 1 block, Channel 2 block...
        for ch in 0..self.channel_count {
            match self.file.read_exact(&mut channels_data[ch]) {
                Ok(_) => {}
                Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                    return Ok(Vec::new());
                }
                Err(e) => return Err(DsdError::Io(e)),
            }
        }

        let samples_in_block = (self.block_size * 8) as u64;
        self.current_sample = (self.current_sample + samples_in_block).min(self.info.total_samples_per_channel);

        Ok(channels_data)
    }

    fn seek_seconds(&mut self, seconds: f64) -> Result<f64, DsdError> {
        let target_sample = (seconds * self.info.sample_rate as f64) as u64;
        let target_sample = target_sample.min(self.info.total_samples_per_channel);

        let samples_per_block = (self.block_size * 8) as u64;
        let block_idx = target_sample / samples_per_block;
        let total_block_bytes = (self.block_size * self.channel_count) as u64;

        let file_offset = self.data_start_offset + (block_idx * total_block_bytes);
        self.file.seek(SeekFrom::Start(file_offset))?;

        let actual_sample = block_idx * samples_per_block;
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
