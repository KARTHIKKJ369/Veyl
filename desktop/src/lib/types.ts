export type PlaybackState = 'Stopped' | 'Playing' | 'Paused' | 'Buffering' | 'Seeking' | 'Ended' | 'Error';

export interface TrackInfo {
  uri: string;
  title: string;
  artist: string;
  album: string;
  album_artist?: string | null;
  genre?: string | null;
  year?: number | null;
  track_number?: number | null;
  disc_number?: number | null;
  duration_seconds: number;
  sample_rate: number;
  bit_depth?: number | null;
  bitrate?: number | null;
  channels: number;
  format_name: string;
  has_artwork: boolean;
}

export interface AudioMetrics {
  rms_left: number;
  rms_right: number;
  peak_left: number;
  peak_right: number;
  spectrum_16: number[];
}

export interface EngineStatus {
  state: PlaybackState;
  current_track?: TrackInfo | null;
  current_track_index: number;
  position_seconds: number;
  duration_seconds: number;
  sample_rate: number;
  bit_depth?: number | null;
  channels: number;
  codec_name: string;
  volume: number;
  is_bit_perfect: boolean;
  is_exclusive_mmap: boolean;
  metrics: AudioMetrics;
}

export type FilterType =
  | 'Peaking'
  | 'LowShelf'
  | 'HighShelf'
  | 'LowPass'
  | 'HighPass'
  | 'Notch'
  | 'BandPass';

export interface EqBandInfo {
  index: number;
  enabled: boolean;
  filter_type: FilterType;
  frequency: number;
  gain_db: number;
  q: number;
}

export interface FrequencyPoint {
  frequency: number;
  gain_db: number;
}

export type RepeatMode = 'off' | 'all' | 'one';

export type SortField = 'title' | 'artist' | 'album' | 'duration' | 'sample_rate' | 'bit_depth';

export type AppearanceMode = 'dark' | 'light';

export interface CloudTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration_seconds: number;
  duration_text: string;
  thumbnail_url: string;
  stream_url: string;
}

export interface CloudSection {
  id: string;
  title: string;
  subtitle?: string | null;
  language?: string | null;
  items: CloudTrack[];
}

export interface LyricLine {
  sec: number;
  text: string;
}

export interface Playlist {
  id: string;
  name: string;
  description?: string;
  coverUrl?: string | null;
  trackUris: string[];
  created_at: number;
}

export interface SpotifyTrackDto {
  title: string;
  artist: string;
  duration_seconds: number;
  cover_url?: string | null;
}

export interface SpotifyPlaylistDto {
  title: string;
  cover_url?: string | null;
  tracks: SpotifyTrackDto[];
}

