import { invoke } from '@tauri-apps/api/core';
import { open } from '@tauri-apps/plugin-dialog';
import type { EngineStatus, EqBandInfo, FrequencyPoint, TrackInfo } from './types';

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
};
