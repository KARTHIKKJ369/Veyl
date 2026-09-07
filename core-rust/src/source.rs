use std::fs::File;
use std::io::Cursor;
use std::path::{Path, PathBuf};
use symphonia::core::io::MediaSourceStream;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SourceError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Source not found: {0}")]
    NotFound(String),
    #[error("Unsupported source URI: {0}")]
    Unsupported(String),
}

/// Abstract audio source providing random-access read streams.
pub trait AudioSource: Send + Sync {
    /// Open the stream for reading and seeking.
    fn open_stream(&self) -> Result<MediaSourceStream, SourceError>;
    /// Get the identifier/URI of this source.
    fn uri(&self) -> String;
    /// Human-readable title or file name.
    fn display_name(&self) -> String;
}

/// Local file audio source
pub struct LocalFileSource {
    path: PathBuf,
}

impl LocalFileSource {
    pub fn new<P: AsRef<Path>>(path: P) -> Self {
        Self {
            path: path.as_ref().to_path_buf(),
        }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }
}

impl AudioSource for LocalFileSource {
    fn open_stream(&self) -> Result<MediaSourceStream, SourceError> {
        let file = File::open(&self.path).map_err(|e| {
            SourceError::Io(std::io::Error::new(
                e.kind(),
                format!("Failed to open file {:?}: {}", self.path, e),
            ))
        })?;
        Ok(MediaSourceStream::new(
            Box::new(file),
            Default::default(),
        ))
    }

    fn uri(&self) -> String {
        self.path.to_string_lossy().to_string()
    }

    fn display_name(&self) -> String {
        self.path
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| self.uri())
    }
}

/// In-memory audio source (useful for testing and network buffer chunks)
pub struct MemorySource {
    data: Vec<u8>,
    name: String,
}

impl MemorySource {
    pub fn new(data: Vec<u8>, name: String) -> Self {
        Self { data, name }
    }
}

impl AudioSource for MemorySource {
    fn open_stream(&self) -> Result<MediaSourceStream, SourceError> {
        let cursor = Cursor::new(self.data.clone());
        Ok(MediaSourceStream::new(
            Box::new(cursor),
            Default::default(),
        ))
    }

    fn uri(&self) -> String {
        format!("memory://{}", self.name)
    }

    fn display_name(&self) -> String {
        self.name.clone()
    }
}
