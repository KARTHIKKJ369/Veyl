import { invoke } from '@tauri-apps/api/core';
import { open, save } from '@tauri-apps/plugin-dialog';
import type {
  CloudSection,
  CloudTrack,
  EngineStatus,
  EqBandInfo,
  FrequencyPoint,
  LyricLine,
  SpotifyPlaylistDto,
  TrackInfo,
} from './types';

export const api = {
  async playTrack(track: TrackInfo): Promise<boolean> {
    return await invoke('play_track', {
      uri: track.uri,
      title: track.title,
      artist: track.artist,
      album: track.album,
    });
  },

  async play(): Promise<void> {
    await invoke('play');
  },

  async pause(): Promise<void> {
    await invoke('pause');
  },

  async togglePlayPause(): Promise<boolean> {
    return await invoke('toggle_play_pause');
  },

  async stop(): Promise<void> {
    await invoke('stop');
  },

  async seek(seconds: number): Promise<void> {
    await invoke('seek', { seconds });
  },

  async setVolume(volume: number): Promise<void> {
    await invoke('set_volume', { volume });
  },

  async getEngineStatus(): Promise<EngineStatus> {
    return await invoke('get_engine_status');
  },

  async scanMusicDirectory(path: string): Promise<TrackInfo[]> {
    return await invoke('scan_music_directory', { path });
  },

  async getTrackArtwork(path: string): Promise<string | null> {
    return await invoke('get_track_artwork', { path });
  },

  async setEqualizerEnabled(enabled: boolean): Promise<void> {
    await invoke('set_equalizer_enabled', { enabled });
  },

  async setEqualizerPreamp(gainDb: number): Promise<void> {
    await invoke('set_equalizer_preamp', { gainDb });
  },

  async setEqualizerBand(index: number, gainDb: number, freq: number, q: number): Promise<void> {
    await invoke('set_equalizer_band', { index, gainDb, freq, q });
  },

  async getEqualizerBands(): Promise<EqBandInfo[]> {
    return await invoke('get_equalizer_bands');
  },

  async getEqualizerCurve(numPoints: number = 100): Promise<FrequencyPoint[]> {
    return await invoke('get_equalizer_curve', { numPoints });
  },

  async setDsdMode(mode: 'pcm' | 'dop'): Promise<void> {
    await invoke('set_dsd_mode', { mode });
  },

  async reconfigureOutput(sampleRate: number, channels: number = 2, exclusive: boolean = false): Promise<boolean> {
    return await invoke('reconfigure_output', { sampleRate, channels, exclusive });
  },

  async preloadNextTrack(uri: string): Promise<boolean> {
    return await invoke('preload_next_track', { uri });
  },

  async pickMusicFolder(): Promise<string | null> {
    const selected = await open({
      directory: true,
      multiple: false,
      title: 'Select Music Library Folder',
    });
    return (selected as string) ?? null;
  },

  async pickAudioFiles(): Promise<string[]> {
    const selected = await open({
      directory: false,
      multiple: true,
      title: 'Add Audio Files to Queue',
      filters: [
        {
          name: 'Audiophile Audio Files',
          extensions: ['flac', 'dsf', 'dff', 'wav', 'alac', 'm4a', 'mp3', 'aac', 'ogg', 'opus'],
        },
      ],
    });
    if (!selected) return [];
    return Array.isArray(selected) ? selected : [selected];
  },

  async searchCloudTracks(query: string): Promise<CloudTrack[]> {
    return await invoke('search_cloud_tracks', { query });
  },

  async getCloudRecommendations(languages: string[] = [], artists: string[] = []): Promise<CloudSection[]> {
    return await invoke('get_cloud_recommendations', { languages, artists });
  },

  async getCloudPlaylist(listId: string): Promise<CloudTrack[]> {
    return await invoke('get_cloud_playlist', { listId });
  },

  async playCloudTrack(track: CloudTrack): Promise<boolean> {
    return await invoke('play_cloud_track', { track });
  },

  async downloadCloudTrack(track: CloudTrack): Promise<string> {
    return await invoke('download_cloud_track', { track });
  },

  async getSyncedLyrics(
    trackUri: string,
    title: string,
    artist: string,
    album: string = '',
    durationSeconds: number = 0,
  ): Promise<LyricLine[]> {
    return await invoke('get_synced_lyrics', {
      trackUri,
      title,
      artist,
      album,
      durationSeconds,
    });
  },

  async fetchSpotifyPlaylist(url: string): Promise<SpotifyPlaylistDto> {
    return await invoke('fetch_spotify_playlist', { url });
  },

  async resolveTrackArtwork(title: string, artist: string): Promise<string | null> {
    return await invoke('resolve_track_artwork', { title, artist });
  },

  async writeTextFile(path: string, content: string): Promise<void> {
    await invoke('write_text_file', { path, content });
  },

  async readTextFile(path: string): Promise<string> {
    return await invoke('read_text_file', { path });
  },

  async pickM3uFile(): Promise<string | null> {
    const selected = await open({
      directory: false,
      multiple: false,
      title: 'Import M3U Playlist',
      filters: [
        {
          name: 'M3U Playlists',
          extensions: ['m3u', 'm3u8'],
        },
      ],
    });
    return (selected as string) ?? null;
  },

  async saveM3uFile(defaultName: string = 'playlist.m3u8'): Promise<string | null> {
    const selected = await save({
      title: 'Export Playlist as M3U8',
      defaultPath: defaultName,
      filters: [
        {
          name: 'M3U8 Playlist',
          extensions: ['m3u8', 'm3u'],
        },
      ],
    });
    return selected ?? null;
  },
};

