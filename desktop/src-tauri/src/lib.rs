use std::sync::Arc;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use base64::Engine as _;
use audiophile_core::{
    AudiophileEngineHandle, EngineStatus, EqBandInfo, FrequencyPoint, LibraryEngine, PlaybackStateEnum, TrackInfo,
};

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
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
