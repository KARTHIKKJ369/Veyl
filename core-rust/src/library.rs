use lofty::file::{AudioFile, TaggedFileExt};
use lofty::probe::Probe;
use lofty::tag::Accessor;
use std::fs;
use std::path::Path;
use thiserror::Error;

use crate::dsd::dff::DffReader;
use crate::dsd::dsf::DsfReader;
use crate::dsd::DsdReader;

#[derive(Error, Debug)]
pub enum LibraryError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Lofty tag error: {0}")]
    Lofty(#[from] lofty::error::LoftyError),
    #[error("DSD error: {0}")]
    Dsd(#[from] crate::dsd::DsdError),
}

#[derive(Debug, Clone)]
pub struct TrackMetadata {
    pub uri: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: Option<String>,
    pub genre: Option<String>,
    pub year: Option<u32>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    pub duration_seconds: f64,
    pub sample_rate: u32,
    pub bit_depth: Option<u8>,
    pub bitrate: Option<u32>,
    pub channels: u8,
    pub file_size_bytes: u64,
    pub format_name: String,
    pub has_artwork: bool,
}

#[derive(Debug, Clone)]
pub struct AlbumArtwork {
    pub mime_type: String,
    pub data: Vec<u8>,
}

pub struct MetadataExtractor;

impl MetadataExtractor {
    pub fn extract_metadata<P: AsRef<Path>>(path: P) -> Result<TrackMetadata, LibraryError> {
        let path = path.as_ref();
        let file_size_bytes = fs::metadata(path).map(|m| m.len()).unwrap_or(0);
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("").to_lowercase();

        // Special handling for DSD DSF files
        if ext == "dsf" {
            if let Ok(dsf) = DsfReader::open(path) {
                let info = dsf.info().clone();
                let title = info.title.unwrap_or_else(|| {
                    path.file_stem()
                        .map(|s| s.to_string_lossy().to_string())
                        .unwrap_or_else(|| "Unknown Title".to_string())
                });
                let artist = info.artist.unwrap_or_else(|| "Unknown Artist".to_string());
                let album = info.album.unwrap_or_else(|| "Unknown Album".to_string());
                let has_artwork = Self::extract_artwork(path).map(|a| a.is_some()).unwrap_or(false);

                return Ok(TrackMetadata {
                    uri: path.to_string_lossy().to_string(),
                    title,
                    artist,
                    album,
                    album_artist: None,
                    genre: None,
                    year: info.year,
                    track_number: info.track_number,
                    disc_number: None,
                    duration_seconds: info.duration_seconds,
                    sample_rate: info.sample_rate,
                    bit_depth: Some(1),
                    bitrate: Some(info.sample_rate * info.channels),
                    channels: info.channels as u8,
                    file_size_bytes,
                    format_name: format!("{} (DSF)", info.dsd_rate.name()),
                    has_artwork,
                });
            }
        }

        // Special handling for DSD DSDIFF / DFF files
        if ext == "dff" || ext == "diff" {
            if let Ok(dff) = DffReader::open(path) {
                let info = dff.info().clone();
                let title = path.file_stem()
                    .map(|s| s.to_string_lossy().to_string())
                    .unwrap_or_else(|| "Unknown Title".to_string());

                return Ok(TrackMetadata {
                    uri: path.to_string_lossy().to_string(),
                    title,
                    artist: "Unknown Artist".to_string(),
                    album: "Unknown Album".to_string(),
                    album_artist: None,
                    genre: None,
                    year: None,
                    track_number: None,
                    disc_number: None,
                    duration_seconds: info.duration_seconds,
                    sample_rate: info.sample_rate,
                    bit_depth: Some(1),
                    bitrate: Some(info.sample_rate * info.channels),
                    channels: info.channels as u8,
                    file_size_bytes,
                    format_name: format!("{} (DSDIFF)", info.dsd_rate.name()),
                    has_artwork: false,
                });
            }
        }

        // Standard metadata probe with lofty (FLAC, WAV, ALAC, MP3, etc.)
        let tagged_file = Probe::open(path)?.read()?;

        let properties = tagged_file.properties();
        let duration_seconds = properties.duration().as_secs_f64();
        let sample_rate = properties.sample_rate().unwrap_or(44100);
        let bit_depth = properties.bit_depth();
        let bitrate = properties.audio_bitrate();
        let channels = properties.channels().unwrap_or(2);
        let format_name = format!("{:?}", tagged_file.file_type());

        let tag = tagged_file.primary_tag().or_else(|| tagged_file.first_tag());

        let (title, artist, album, album_artist, genre, year, track_number, disc_number, has_artwork) =
            if let Some(tag) = tag {
                let title = tag
                    .title()
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| {
                        path.file_stem()
                            .map(|s| s.to_string_lossy().to_string())
                            .unwrap_or_else(|| "Unknown Title".to_string())
                    });
                let artist = tag
                    .artist()
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| "Unknown Artist".to_string());
                let album = tag
                    .album()
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| "Unknown Album".to_string());
                let album_artist = tag.get_string(&lofty::tag::ItemKey::AlbumArtist).map(|s| s.to_string());
                let genre = tag.genre().map(|s| s.to_string());
                let year = tag.year();
                let track_number = tag.track();
                let disc_number = tag.disk();
                let has_artwork = !tag.pictures().is_empty();

                (title, artist, album, album_artist, genre, year, track_number, disc_number, has_artwork)
            } else {
                let title = path
                    .file_stem()
                    .map(|s| s.to_string_lossy().to_string())
                    .unwrap_or_else(|| "Unknown Title".to_string());
                (
                    title,
                    "Unknown Artist".to_string(),
                    "Unknown Album".to_string(),
                    None,
                    None,
                    None,
                    None,
                    None,
                    false,
                )
            };

        Ok(TrackMetadata {
            uri: path.to_string_lossy().to_string(),
            title,
            artist,
            album,
            album_artist,
            genre,
            year,
            track_number,
            disc_number,
            duration_seconds,
            sample_rate,
            bit_depth,
            bitrate,
            channels,
            file_size_bytes,
            format_name,
            has_artwork,
        })
    }

    pub fn extract_artwork<P: AsRef<Path>>(path: P) -> Result<Option<AlbumArtwork>, LibraryError> {
        let path = path.as_ref();
        let tagged_file = match Probe::open(path).and_then(|p| p.read()) {
            Ok(tf) => tf,
            Err(_) => return Ok(None),
        };

        let tag = tagged_file.primary_tag().or_else(|| tagged_file.first_tag());

        if let Some(tag) = tag {
            if let Some(picture) = tag.pictures().first() {
                let mime_type = match picture.mime_type() {
                    Some(m) => m.as_str().to_string(),
                    None => "image/jpeg".to_string(),
                };
                return Ok(Some(AlbumArtwork {
                    mime_type,
                    data: picture.data().to_vec(),
                }));
            }
        }

        Ok(None)
    }

    /// Recursively scans a directory for supported audio formats (flac, wav, alac, mp3, aac, ogg, opus, m4a, dsf, dff)
    pub fn scan_directory<P: AsRef<Path>>(dir_path: P) -> Vec<TrackMetadata> {
        let mut tracks = Vec::new();
        let supported_extensions = ["flac", "wav", "aiff", "aif", "mp3", "m4a", "aac", "ogg", "opus", "dsf", "dff", "diff"];

        fn scan_recursive(dir: &Path, extensions: &[&str], tracks: &mut Vec<TrackMetadata>) {
            if let Ok(entries) = fs::read_dir(dir) {
                for entry in entries.flatten() {
                    let path = entry.path();
                    if path.is_dir() {
                        scan_recursive(&path, extensions, tracks);
                    } else if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
                        if extensions.contains(&ext.to_lowercase().as_str()) {
                            if let Ok(meta) = MetadataExtractor::extract_metadata(&path) {
                                tracks.push(meta);
                            }
                        }
                    }
                }
            }
        }

        scan_recursive(dir_path.as_ref(), &supported_extensions, &mut tracks);
        tracks
    }
}
