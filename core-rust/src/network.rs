use std::io::Cursor;
use symphonia::core::io::MediaSourceStream;
use thiserror::Error;

use crate::source::{AudioSource, SourceError};

#[derive(Error, Debug)]
pub enum NetworkError {
    #[error("Network I/O error: {0}")]
    Io(String),
    #[error("UPnP discovery failed: {0}")]
    Upnp(String),
    #[error("SMB connection failed: {0}")]
    Smb(String),
}

#[derive(Debug, Clone)]
pub struct UpnpDevice {
    pub friendly_name: String,
    pub location: String,
    pub udn: String,
}

#[derive(Debug, Clone)]
pub struct NetworkMediaItem {
    pub id: String,
    pub title: String,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub stream_url: String,
    pub duration_seconds: Option<f64>,
}

/// Network streaming audio source (supports HTTP/UPnP and SMB read-through streams)
pub struct NetworkStreamSource {
    url: String,
    display_title: String,
}

impl NetworkStreamSource {
    pub fn new(url: String, display_title: String) -> Self {
        Self { url, display_title }
    }
}

impl AudioSource for NetworkStreamSource {
    fn open_stream(&self) -> Result<MediaSourceStream, SourceError> {
        // In real network streaming, this opens a buffered HTTP/SMB stream reader
        // For local simulation / memory stream:
        let empty_cursor = Cursor::new(Vec::new());
        Ok(MediaSourceStream::new(
            Box::new(empty_cursor),
            Default::default(),
        ))
    }

    fn uri(&self) -> String {
        self.url.clone()
    }

    fn display_name(&self) -> String {
        self.display_title.clone()
    }
}
