use std::sync::Arc;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use base64::Engine as _;
use audiophile_core::{
    AudiophileEngineHandle, EngineStatus, EqBandInfo, FrequencyPoint, LibraryEngine, PlaybackStateEnum, TrackInfo,
};

mod cloud;

pub struct AppState {
    pub engine: Arc<AudiophileEngineHandle>,
    pub library: Arc<LibraryEngine>,
}

#[tauri::command]
fn play_track(
    state: tauri::State<'_, AppState>,
    uri: String,
    title: String,
    artist: String,
    album: String,
) -> Result<bool, String> {
    Ok(state.engine.play_track(uri, title, artist, album))
}

#[tauri::command]
fn play(state: tauri::State<'_, AppState>) -> Result<(), String> {
    state.engine.play();
    Ok(())
}

#[tauri::command]
fn pause(state: tauri::State<'_, AppState>) -> Result<(), String> {
    state.engine.pause();
    Ok(())
}

#[tauri::command]
fn toggle_play_pause(state: tauri::State<'_, AppState>) -> Result<bool, String> {
    let status = state.engine.get_status();
    if status.state == PlaybackStateEnum::Playing {
        state.engine.pause();
        Ok(false)
    } else {
        state.engine.play();
        Ok(true)
    }
}

#[tauri::command]
fn stop(state: tauri::State<'_, AppState>) -> Result<(), String> {
    state.engine.stop();
    Ok(())
}

#[tauri::command]
fn seek(state: tauri::State<'_, AppState>, seconds: f64) -> Result<(), String> {
    state.engine.seek(seconds);
    Ok(())
}

#[tauri::command]
fn set_volume(state: tauri::State<'_, AppState>, volume: f32) -> Result<(), String> {
    state.engine.set_volume(volume.clamp(0.0, 1.5));
    Ok(())
}

#[tauri::command]
fn get_engine_status(state: tauri::State<'_, AppState>) -> Result<EngineStatus, String> {
    Ok(state.engine.get_status())
}

#[tauri::command]
fn scan_music_directory(state: tauri::State<'_, AppState>, path: String) -> Result<Vec<TrackInfo>, String> {
    Ok(state.library.scan_directory(path))
}

#[tauri::command]
fn get_track_artwork(state: tauri::State<'_, AppState>, path: String) -> Result<Option<String>, String> {
    if let Some(art) = state.library.extract_artwork(path) {
        let b64 = BASE64_STANDARD.encode(&art.data);
        Ok(Some(format!("data:{};base64,{}", art.mime_type, b64)))
    } else {
        Ok(None)
    }
}

#[tauri::command]
fn set_equalizer_enabled(state: tauri::State<'_, AppState>, enabled: bool) -> Result<(), String> {
    state.engine.set_eq_enabled(enabled);
    Ok(())
}

#[tauri::command]
fn set_equalizer_preamp(state: tauri::State<'_, AppState>, gain_db: f32) -> Result<(), String> {
    state.engine.set_eq_preamp(gain_db);
    Ok(())
}

#[tauri::command]
fn set_equalizer_band(
    state: tauri::State<'_, AppState>,
    index: u32,
    gain_db: f32,
    freq: f32,
    q: f32,
) -> Result<(), String> {
    state.engine.set_eq_band(index, gain_db, freq, q);
    Ok(())
}

#[tauri::command]
fn get_equalizer_bands(state: tauri::State<'_, AppState>) -> Result<Vec<EqBandInfo>, String> {
    Ok(state.engine.get_eq_bands())
}

#[tauri::command]
fn get_equalizer_curve(state: tauri::State<'_, AppState>, num_points: u32) -> Result<Vec<FrequencyPoint>, String> {
    Ok(state.engine.get_eq_curve(num_points))
}

#[tauri::command]
fn set_dsd_mode(state: tauri::State<'_, AppState>, mode: String) -> Result<(), String> {
    let dsd_enum = match mode.to_lowercase().as_str() {
        "dop" => audiophile_core::DsdModeEnum::DoP,
        _ => audiophile_core::DsdModeEnum::PcmDecimation,
    };
    state.engine.set_dsd_mode(dsd_enum);
    Ok(())
}

#[tauri::command]
fn reconfigure_output(
    state: tauri::State<'_, AppState>,
    sample_rate: u32,
    channels: u32,
    exclusive: bool,
) -> Result<bool, String> {
    Ok(state.engine.reconfigure_output(sample_rate, channels, None, exclusive))
}

#[tauri::command]
fn preload_next_track(state: tauri::State<'_, AppState>, uri: String) -> Result<bool, String> {
    Ok(state.engine.preload_next_track(uri))
}

#[tauri::command]
async fn search_cloud_tracks(query: String) -> Result<Vec<cloud::CloudTrack>, String> {
    cloud::search_songs(&query).await
}

#[tauri::command]
async fn get_cloud_recommendations(
    languages: Vec<String>,
    artists: Vec<String>,
) -> Result<Vec<cloud::CloudSection>, String> {
    cloud::get_recommendations(languages, artists).await
}

#[tauri::command]
async fn get_cloud_playlist(list_id: String) -> Result<Vec<cloud::CloudTrack>, String> {
    cloud::get_playlist_songs(&list_id).await
}

#[tauri::command]
async fn play_cloud_track(
    state: tauri::State<'_, AppState>,
    track: cloud::CloudTrack,
) -> Result<bool, String> {
    let cache_file = cloud::stream_to_cache(&track.stream_url).await?;
    let path_str = cache_file.to_string_lossy().to_string();
    Ok(state.engine.play_track(path_str, track.title, track.artist, track.album))
}

#[tauri::command]
async fn download_cloud_track(track: cloud::CloudTrack) -> Result<String, String> {
    cloud::download_to_music_folder(&track.stream_url, &track.title, &track.artist, &track.thumbnail_url).await
}

#[tauri::command]
async fn get_synced_lyrics(
    track_uri: String,
    title: String,
    artist: String,
    album: String,
    duration_seconds: f64,
) -> Result<Vec<cloud::LyricLine>, String> {
    cloud::fetch_synced_lyrics(&track_uri, &title, &artist, &album, duration_seconds).await
}

#[tauri::command]
async fn fetch_spotify_playlist(url: String) -> Result<cloud::SpotifyPlaylistDto, String> {
    cloud::fetch_spotify_playlist(&url).await
}

#[tauri::command]
async fn resolve_track_artwork(title: String, artist: String) -> Result<Option<String>, String> {
    cloud::resolve_track_artwork(&title, &artist).await
}

#[tauri::command]
fn write_text_file(path: String, content: String) -> Result<(), String> {
    cloud::write_text_file(&path, &content)
}

#[tauri::command]
fn read_text_file(path: String) -> Result<String, String> {
    cloud::read_text_file(&path)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let engine = Arc::new(AudiophileEngineHandle::new(44100.0, 2));
    let library = Arc::new(LibraryEngine::new());

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState { engine, library })
        .invoke_handler(tauri::generate_handler![
            play_track,
            play,
            pause,
            toggle_play_pause,
            stop,
            seek,
            set_volume,
            get_engine_status,
            scan_music_directory,
            get_track_artwork,
            set_equalizer_enabled,
            set_equalizer_preamp,
            set_equalizer_band,
            get_equalizer_bands,
            get_equalizer_curve,
            set_dsd_mode,
            reconfigure_output,
            preload_next_track,
            search_cloud_tracks,
            get_cloud_recommendations,
            get_cloud_playlist,
            play_cloud_track,
            download_cloud_track,
            get_synced_lyrics,
            fetch_spotify_playlist,
            resolve_track_artwork,
            write_text_file,
            read_text_file,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

