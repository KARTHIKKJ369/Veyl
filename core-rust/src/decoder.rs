use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{Decoder, DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use symphonia::core::units::{Time, TimeBase};
use lofty::probe::Probe;
use lofty::file::TaggedFileExt;
use thiserror::Error;

use crate::dsd::decimator::DsdDecimator;
use crate::dsd::dff::DffReader;
use crate::dsd::dop::DopEncoder;
use crate::dsd::dsf::DsfReader;
use crate::dsd::{DsdError, DsdPlaybackMode, DsdReader};
use crate::source::{AudioSource, SourceError};

#[derive(Error, Debug)]
pub enum DecodeError {
    #[error("Source error: {0}")]
    Source(#[from] SourceError),
    #[error("Symphonia error: {0}")]
    Symphonia(#[from] SymphoniaError),
    #[error("DSD error: {0}")]
    Dsd(#[from] DsdError),
    #[error("No supported audio tracks found")]
    NoAudioTrack,
    #[error("Unsupported audio format or codec")]
    UnsupportedFormat,
    #[error("End of stream reached")]
    EndOfStream,
}

#[derive(Debug, Clone)]
pub struct AudioStreamInfo {
    pub sample_rate: u32,
    pub channels: u32,
    pub bit_depth: Option<u32>,
    pub duration_seconds: f64,
    pub total_frames: Option<u64>,
    pub codec_name: String,
    pub container_name: String,
    /// ReplayGain track gain in dB (e.g. -6.5 means reduce by 6.5dB)
    /// None if no ReplayGain tags were found
    pub replaygain_db: Option<f32>,
}

enum DecoderBackend {
    Symphonia {
        reader: Box<dyn FormatReader>,
        decoder: Box<dyn Decoder>,
        track_id: u32,
        time_base: TimeBase,
    },
    Dsd {
        reader: Box<dyn DsdReader>,
        decimator: DsdDecimator,
        dop_encoder: DopEncoder,
        mode: DsdPlaybackMode,
        is_lsb_first: bool,
    },
}

pub struct AudioDecoder {
    backend: DecoderBackend,
    info: AudioStreamInfo,
    current_frame: u64,
}

impl AudioDecoder {
    pub fn open(source: &dyn AudioSource) -> Result<Self, DecodeError> {
        Self::open_with_dsd_mode(source, DsdPlaybackMode::PcmDecimation)
    }

    pub fn open_with_dsd_mode(source: &dyn AudioSource, dsd_mode: DsdPlaybackMode) -> Result<Self, DecodeError> {
        let uri = source.uri();
        let ext = uri.rsplit('.').next().unwrap_or("").to_lowercase();

        if ext == "dsf" {
            return Self::open_dsf(source, dsd_mode);
        } else if ext == "dff" || ext == "diff" {
            return Self::open_dff(source, dsd_mode);
        }

        Self::open_symphonia(source)
    }

    fn open_dsf(source: &dyn AudioSource, dsd_mode: DsdPlaybackMode) -> Result<Self, DecodeError> {
        let uri = source.uri();
        let reader = DsfReader::open(&uri)?;
        let dsd_info = reader.info().clone();

        let (pcm_sample_rate, decimation_factor) = match (dsd_mode, dsd_info.sample_rate) {
            (DsdPlaybackMode::DoP, 2822400) => (176400, 16),
            (DsdPlaybackMode::DoP, 5644800) => (352800, 16),
            (DsdPlaybackMode::DoP, 11289600) => (705600, 16),
            (DsdPlaybackMode::DoP, r) => (r / 16, 16),
            (DsdPlaybackMode::PcmDecimation, 2822400) => (88200, 32),
            (DsdPlaybackMode::PcmDecimation, 5644800) => (176400, 32),
            (DsdPlaybackMode::PcmDecimation, 11289600) => (176400, 64),
            (DsdPlaybackMode::PcmDecimation, r) => (r / 32, 32),
        };

        let codec_name = match dsd_mode {
            DsdPlaybackMode::DoP => format!("{} (DoP)", dsd_info.dsd_rate.name()),
            DsdPlaybackMode::PcmDecimation => format!("{} (DSF)", dsd_info.dsd_rate.name()),
        };

        let decimator = DsdDecimator::new(decimation_factor, 64, dsd_info.channels as usize, true);
        let dop_encoder = DopEncoder::new();

        let total_frames = Some((dsd_info.total_samples_per_channel / (decimation_factor as u64 / 8)) as u64);

        let info = AudioStreamInfo {
            sample_rate: pcm_sample_rate,
            channels: dsd_info.channels,
            bit_depth: Some(1), // 1-bit native DSD
            duration_seconds: dsd_info.duration_seconds,
            total_frames,
            codec_name,
            container_name: "DSF".to_string(),
            replaygain_db: None,
        };

        Ok(Self {
            backend: DecoderBackend::Dsd {
                reader: Box::new(reader),
                decimator,
                dop_encoder,
                mode: dsd_mode,
                is_lsb_first: true, // DSF is LSB first
            },
            info,
            current_frame: 0,
        })
    }

    fn open_dff(source: &dyn AudioSource, dsd_mode: DsdPlaybackMode) -> Result<Self, DecodeError> {
        let uri = source.uri();
        let reader = DffReader::open(&uri)?;
        let dsd_info = reader.info().clone();

        let (pcm_sample_rate, decimation_factor) = match (dsd_mode, dsd_info.sample_rate) {
            (DsdPlaybackMode::DoP, 2822400) => (176400, 16),
            (DsdPlaybackMode::DoP, 5644800) => (352800, 16),
            (DsdPlaybackMode::DoP, 11289600) => (705600, 16),
            (DsdPlaybackMode::DoP, r) => (r / 16, 16),
            (DsdPlaybackMode::PcmDecimation, 2822400) => (88200, 32),
            (DsdPlaybackMode::PcmDecimation, 5644800) => (176400, 32),
            (DsdPlaybackMode::PcmDecimation, 11289600) => (176400, 64),
            (DsdPlaybackMode::PcmDecimation, r) => (r / 32, 32),
        };

        let codec_name = match dsd_mode {
            DsdPlaybackMode::DoP => format!("{} (DoP)", dsd_info.dsd_rate.name()),
            DsdPlaybackMode::PcmDecimation => format!("{} (DSDIFF)", dsd_info.dsd_rate.name()),
        };

        let decimator = DsdDecimator::new(decimation_factor, 64, dsd_info.channels as usize, true);
        let dop_encoder = DopEncoder::new();

        let total_frames = Some((dsd_info.total_samples_per_channel / (decimation_factor as u64 / 8)) as u64);

        let info = AudioStreamInfo {
            sample_rate: pcm_sample_rate,
            channels: dsd_info.channels,
            bit_depth: Some(1),
            duration_seconds: dsd_info.duration_seconds,
            total_frames,
            codec_name,
            container_name: "DSDIFF".to_string(),
            replaygain_db: None,
        };

        Ok(Self {
            backend: DecoderBackend::Dsd {
                reader: Box::new(reader),
                decimator,
                dop_encoder,
                mode: dsd_mode,
                is_lsb_first: false, // DSDIFF is MSB first
            },
            info,
            current_frame: 0,
        })
    }

    fn open_symphonia(source: &dyn AudioSource) -> Result<Self, DecodeError> {
        let mss = source.open_stream()?;

        let mut hint = Hint::new();
        if let Some(ext) = source.uri().rsplit('.').next() {
            hint.with_extension(ext);
        }

        let format_opts = FormatOptions {
            enable_gapless: true,
            ..Default::default()
        };
        let metadata_opts = MetadataOptions::default();

        let probed = symphonia::default::get_probe()
            .format(&hint, mss, &format_opts, &metadata_opts)
            .map_err(DecodeError::Symphonia)?;

        let reader = probed.format;

        // Find the first audio track
        let track = reader
            .tracks()
            .iter()
            .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
            .ok_or(DecodeError::NoAudioTrack)?
            .clone();

        let track_id = track.id;
        let time_base = track.codec_params.time_base.unwrap_or(TimeBase::new(1, track.codec_params.sample_rate.unwrap_or(44100)));
        let sample_rate = track.codec_params.sample_rate.unwrap_or(44100);
        let channels = track.codec_params.channels.map(|c| c.count() as u32).unwrap_or(2);
        let bit_depth = track.codec_params.bits_per_sample;
        let total_frames = track.codec_params.n_frames;

        let duration_seconds = if let Some(n_frames) = total_frames {
            n_frames as f64 / sample_rate as f64
        } else {
            0.0
        };

        let codec_name = format!("{:?}", track.codec_params.codec);
        let container_name = "Container".to_string();

        let decoder_opts = DecoderOptions {
            verify: false,
        };

        let decoder = symphonia::default::get_codecs()
            .make(&track.codec_params, &decoder_opts)
            .map_err(DecodeError::Symphonia)?;

        let info = AudioStreamInfo {
            sample_rate,
            channels,
            bit_depth,
            duration_seconds,
            total_frames,
            codec_name,
            container_name,
            replaygain_db: Self::extract_replaygain(&source.uri()),
        };

        Ok(Self {
            backend: DecoderBackend::Symphonia {
                reader,
                decoder,
                track_id,
                time_base,
            },
            info,
            current_frame: 0,
        })
    }

    /// Extract ReplayGain track gain from file tags using lofty.
    fn extract_replaygain(uri: &str) -> Option<f32> {
        let path = std::path::Path::new(uri);
        let tagged_file = Probe::open(path).ok()?.read().ok()?;

        for tag in tagged_file.tags() {
            for item in tag.items() {
                let key_str = format!("{:?}", item.key());
                if key_str.to_uppercase().contains("REPLAYGAIN_TRACK_GAIN") {
                    if let lofty::tag::ItemValue::Text(val) = item.value() {
                        let cleaned = val.trim().to_lowercase().replace("db", "").trim().to_string();
                        if let Ok(gain) = cleaned.trim().parse::<f32>() {
                            log::info!("ReplayGain track gain found: {:.2} dB for {}", gain, uri);
                            return Some(gain);
                        }
                    }
                }
            }
        }

        None
    }

    pub fn info(&self) -> &AudioStreamInfo {
        &self.info
    }

    pub fn current_frame(&self) -> u64 {
        self.current_frame
    }

    pub fn current_time_seconds(&self) -> f64 {
        match &self.backend {
            DecoderBackend::Symphonia { .. } => {
                if self.info.sample_rate > 0 {
                    self.current_frame as f64 / self.info.sample_rate as f64
                } else {
                    0.0
                }
            }
            DecoderBackend::Dsd { reader, .. } => reader.current_time_seconds(),
        }
    }

    /// Decode the next packet and return interleaved 32-bit floating point samples.
    /// Interleaved order: [L, R, L, R, ...] for stereo.
    pub fn decode_next(&mut self) -> Result<Vec<f32>, DecodeError> {
        match &mut self.backend {
            DecoderBackend::Dsd {
                reader,
                decimator,
                dop_encoder,
                mode,
                is_lsb_first,
            } => {
                let channel_blocks = reader.read_dsd_chunk(32768)?;
                if channel_blocks.is_empty() || channel_blocks[0].is_empty() {
                    return Err(DecodeError::EndOfStream);
                }

                let channel_slices: Vec<&[u8]> = channel_blocks.iter().map(|b| b.as_slice()).collect();

                let samples = match mode {
                    DsdPlaybackMode::PcmDecimation => {
                        decimator.decimate_interleaved(&channel_slices, *is_lsb_first)
                    }
                    DsdPlaybackMode::DoP => {
                        dop_encoder.encode_interleaved(&channel_slices, *is_lsb_first)
                    }
                };

                let frames = samples.len() / self.info.channels.max(1) as usize;
                self.current_frame += frames as u64;
                Ok(samples)
            }
            DecoderBackend::Symphonia {
                reader,
                decoder,
                track_id,
                ..
            } => {
                loop {
                    let packet = match reader.next_packet() {
                        Ok(packet) => packet,
                        Err(SymphoniaError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                            return Err(DecodeError::EndOfStream);
                        }
                        Err(e) => return Err(DecodeError::Symphonia(e)),
                    };

                    if packet.track_id() != *track_id {
                        continue;
                    }

                    match decoder.decode(&packet) {
                        Ok(decoded) => {
                            let num_frames = decoded.frames();
                            let spec = *decoded.spec();
                            let num_channels = spec.channels.count();

                            let mut sample_buf = SampleBuffer::<f32>::new(decoded.capacity() as u64, spec);
                            sample_buf.copy_interleaved_ref(decoded);
                            let raw_samples = sample_buf.samples();

                            let mut interleaved = Vec::with_capacity(num_frames * 2);

                            if num_channels == 1 {
                                // Mono -> Duplicate to Stereo (L, R)
                                for &s in raw_samples {
                                    interleaved.push(s);
                                    interleaved.push(s);
                                }
                            } else if num_channels == 2 {
                                // Stereo (L, R)
                                interleaved.extend_from_slice(raw_samples);
                            } else {
                                // Multi-channel (downmix to stereo: L=ch0, R=ch1)
                                for f in 0..num_frames {
                                    interleaved.push(raw_samples[f * num_channels]);
                                    interleaved.push(raw_samples[f * num_channels + 1]);
                                }
                            }

                            self.current_frame += num_frames as u64;
                            return Ok(interleaved);
                        }
                        Err(SymphoniaError::DecodeError(_)) => {
                            // Recoverable decode error, proceed to next packet
                            continue;
                        }
                        Err(e) => return Err(DecodeError::Symphonia(e)),
                    }
                }
            }
        }
    }

    /// Seek to a specific timestamp in seconds
    pub fn seek(&mut self, time_seconds: f64) -> Result<f64, DecodeError> {
        match &mut self.backend {
            DecoderBackend::Dsd {
                reader,
                decimator,
                dop_encoder,
                ..
            } => {
                let actual = reader.seek_seconds(time_seconds)?;
                decimator.reset();
                dop_encoder.reset();
                self.current_frame = (actual * self.info.sample_rate as f64) as u64;
                Ok(actual)
            }
            DecoderBackend::Symphonia {
                reader,
                decoder,
                track_id,
                time_base,
            } => {
                let time = Time::from(time_seconds);
                let seek_to = SeekTo::Time {
                    time,
                    track_id: Some(*track_id),
                };

                let seeked = reader.seek(SeekMode::Accurate, seek_to).map_err(DecodeError::Symphonia)?;
                decoder.reset();

                let actual_time = time_base.calc_time(seeked.actual_ts);
                let actual_seconds = actual_time.seconds as f64 + actual_time.frac;
                self.current_frame = (actual_seconds * self.info.sample_rate as f64) as u64;

                Ok(actual_seconds)
            }
        }
    }
}
