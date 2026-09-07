pub mod decimator;
pub mod dff;
pub mod dop;
pub mod dsf;

use thiserror::Error;

#[derive(Error, Debug)]
pub enum DsdError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Invalid DSD header or format: {0}")]
    InvalidHeader(String),
    #[error("Unsupported DSD channel count: {0}")]
    UnsupportedChannels(u32),
    #[error("Unsupported DSD sample rate: {0} Hz")]
    UnsupportedSampleRate(u32),
    #[error("Unsupported DSD block size: {0}")]
    UnsupportedBlockSize(u32),
    #[error("End of DSD stream")]
    EndOfStream,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DsdFormat {
    Dsf,
    Dsdiff,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DsdRate {
    Dsd64,  // 2,822,400 Hz (64 x 44.1 kHz) or 3,072,000 Hz (64 x 48 kHz)
    Dsd128, // 5,644,800 Hz (128 x 44.1 kHz)
    Dsd256, // 11,289,600 Hz (256 x 44.1 kHz)
    Dsd512, // 22,579,200 Hz (512 x 44.1 kHz)
    Other(u32),
}

impl DsdRate {
    pub fn from_sample_rate(rate: u32) -> Self {
        match rate {
            2822400 | 3072000 => DsdRate::Dsd64,
            5644800 | 6144000 => DsdRate::Dsd128,
            11289600 | 12288000 => DsdRate::Dsd256,
            22579200 | 24576000 => DsdRate::Dsd512,
            other => DsdRate::Other(other),
        }
    }

    pub fn name(&self) -> String {
        match self {
            DsdRate::Dsd64 => "DSD64".to_string(),
            DsdRate::Dsd128 => "DSD128".to_string(),
            DsdRate::Dsd256 => "DSD256".to_string(),
            DsdRate::Dsd512 => "DSD512".to_string(),
            DsdRate::Other(r) => format!("DSD ({} Hz)", r),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DsdPlaybackMode {
    /// High-precision multi-stage FIR decimation to PCM (88.2 kHz, 176.4 kHz, etc.)
    PcmDecimation,
    /// DoP (DSD over PCM v1.1) for native hardware playback on compatible USB DACs
    DoP,
}

#[derive(Debug, Clone)]
pub struct DsdStreamInfo {
    pub format: DsdFormat,
    pub dsd_rate: DsdRate,
    pub sample_rate: u32,
    pub channels: u32,
    pub total_samples_per_channel: u64,
    pub duration_seconds: f64,
    pub block_size_per_channel: u32,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub track_number: Option<u32>,
    pub year: Option<u32>,
}

pub trait DsdReader: Send {
    fn info(&self) -> &DsdStreamInfo;
    /// Read next block of 1-bit DSD samples.
    /// Returns raw DSD bytes for each channel (channel -> byte vector).
    /// Returns empty on EOF.
    fn read_dsd_chunk(&mut self, samples_per_channel: usize) -> Result<Vec<Vec<u8>>, DsdError>;
    /// Seek to target time in seconds
    fn seek_seconds(&mut self, seconds: f64) -> Result<f64, DsdError>;
    /// Current playback position in seconds
    fn current_time_seconds(&self) -> f64;
}
