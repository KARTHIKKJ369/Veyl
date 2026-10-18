use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use base64::Engine as _;
use cipher::{BlockCipherDecrypt, KeyInit};
use des::Des;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::Write;
use std::path::PathBuf;
use std::sync::LazyLock;
use std::time::Duration;

const DES_KEY: &[u8; 8] = b"38346591";

static HTTP_CLIENT: LazyLock<Client> = LazyLock::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(12))
        .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .build()
        .unwrap_or_default()
});

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CloudTrack {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_seconds: f64,
    pub duration_text: String,
    pub thumbnail_url: String,
    pub stream_url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CloudSection {
    pub id: String,
    pub title: String,
    pub subtitle: Option<String>,
    pub language: Option<String>,
    pub items: Vec<CloudTrack>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LyricLine {
    pub sec: f64,
    pub text: String,
}

pub fn unescape_html(input: &str) -> String {
    let mut out = input
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ");

    // Handle decimal character entities &#123;
    while let Some(start) = out.find("&#") {
        if let Some(end) = out[start..].find(';') {
            let entity = &out[start + 2..start + end];
            if let Ok(code) = entity.parse::<u32>() {
                if let Some(ch) = char::from_u32(code) {
                    out.replace_range(start..=start + end, &ch.to_string());
                    continue;
                }
            }
        }
        break;
    }
    out.trim().to_string()
}

pub fn format_duration(seconds: f64) -> String {
    let total = seconds as u64;
    let m = total / 60;
    let s = total % 60;
    format!("{}:{:02}", m, s)
}

pub fn decrypt_media_url(encrypted_url: &str) -> Option<String> {
    let trimmed = encrypted_url.trim();
    if trimmed.is_empty() {
        return None;
    }

    let decoded_bytes = BASE64_STANDARD.decode(trimmed).ok()?;
    if decoded_bytes.is_empty() || decoded_bytes.len() % 8 != 0 {
        return None;
    }

    let cipher = Des::new_from_slice(DES_KEY).ok()?;
    let mut decrypted = decoded_bytes;

    for chunk in decrypted.chunks_exact_mut(8) {
        if let Ok(fixed_arr) = <&mut [u8; 8]>::try_from(chunk) {
            let block: &mut cipher::Block<Des> = fixed_arr.into();
            cipher.decrypt_block(block);
        }
    }

    // PKCS5/PKCS7 unpadding
    if let Some(&pad_len) = decrypted.last() {
        let pad_usize = pad_len as usize;
        if pad_usize > 0 && pad_usize <= 8 && pad_usize <= decrypted.len() {
            let pad_valid = decrypted[decrypted.len() - pad_usize..].iter().all(|&b| b == pad_len);
            if pad_valid {
                decrypted.truncate(decrypted.len() - pad_usize);
            }
        }
    }

    let raw_url = String::from_utf8(decrypted).ok()?.trim().to_string();
    if raw_url.is_empty() {
        return None;
    }

    // Upgrade to studio 320 kbps stream
    let upgraded_url = raw_url.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4");
    Some(upgraded_url)
}

fn parse_song_item(val: &serde_json::Value) -> Option<CloudTrack> {
    let id = val.get("id")?.as_str()?.to_string();

    let raw_title = val
        .get("title")
        .and_then(|v| v.as_str())
        .or_else(|| val.get("song").and_then(|v| v.as_str()))
        .unwrap_or("Unknown Title");
    let title = unescape_html(raw_title);

    let more_info = val.get("more_info").unwrap_or(val);

    let raw_album = more_info
        .get("album")
        .and_then(|v| v.as_str())
        .or_else(|| val.get("album").and_then(|v| v.as_str()))
        .unwrap_or("");
    let album = unescape_html(raw_album);

    // Primary artists extraction
    let mut artist = String::new();
    if let Some(artist_map) = more_info.get("artistMap") {
        if let Some(primary) = artist_map.get("primary_artists").and_then(|v| v.as_array()) {
            let names: Vec<String> = primary
                .iter()
                .filter_map(|a| a.get("name").and_then(|v| v.as_str()).map(unescape_html))
                .collect();
            if !names.is_empty() {
                artist = names.join(", ");
            }
        }
    }
    if artist.is_empty() {
        if let Some(p) = more_info.get("primary_artists").and_then(|v| v.as_str()) {
            artist = unescape_html(p);
        }
    }
    if artist.is_empty() {
        if let Some(s) = val.get("singers").and_then(|v| v.as_str()) {
            artist = unescape_html(s);
        }
    }
    if artist.is_empty() {
        if let Some(sub) = val.get("subtitle").and_then(|v| v.as_str()) {
            artist = unescape_html(sub);
        }
    }
    if artist.is_empty() {
        artist = "Unknown Artist".to_string();
    }

    let duration_seconds = more_info
        .get("duration")
        .and_then(|v| v.as_str().and_then(|s| s.parse::<f64>().ok()).or_else(|| v.as_f64()))
        .or_else(|| {
            val.get("duration")
                .and_then(|v| v.as_str().and_then(|s| s.parse::<f64>().ok()).or_else(|| v.as_f64()))
        })
        .unwrap_or(0.0);
    let duration_text = format_duration(duration_seconds);

    // High res thumbnail (500x500)
    let raw_image = val.get("image").and_then(|v| v.as_str()).unwrap_or("");
    let thumbnail_url = raw_image.replace("150x150", "500x500").replace("50x50", "500x500");

    // Decrypt media URL
    let enc_url = more_info
        .get("encrypted_media_url")
        .and_then(|v| v.as_str())
        .or_else(|| val.get("encrypted_media_url").and_then(|v| v.as_str()))
        .unwrap_or("");
    let stream_url = decrypt_media_url(enc_url)?;

    Some(CloudTrack {
        id,
        title,
        artist,
        album,
        duration_seconds,
        duration_text,
        thumbnail_url,
        stream_url,
    })
}

pub async fn search_songs(query: &str) -> Result<Vec<CloudTrack>, String> {
    let trimmed = query.trim();
    if trimmed.len() < 2 {
        return Ok(Vec::new());
    }

    let endpoint = format!(
        "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=android&n=25&q={}",
        urlencoding::encode(trimmed)
    );

    let res = HTTP_CLIENT
        .get(&endpoint)
        .send()
        .await
        .map_err(|e| format!("HTTP request failed: {}", e))?;

    if !res.status().is_success() {
        return Err(format!("Search returned HTTP {}", res.status()));
    }

    let json: serde_json::Value = res
        .json()
        .await
        .map_err(|e| format!("JSON parsing failed: {}", e))?;

    let mut results = Vec::new();
    if let Some(arr) = json.get("results").and_then(|v| v.as_array()) {
        for item in arr {
            if let Some(track) = parse_song_item(item) {
                results.push(track);
            }
        }
    }

    Ok(results)
}

pub async fn get_playlist_songs(list_id: &str) -> Result<Vec<CloudTrack>, String> {
    let endpoint = format!(
        "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&_marker=0&api_version=4&listid={}",
        list_id
    );

    let res = HTTP_CLIENT
        .get(&endpoint)
        .send()
        .await
        .map_err(|e| format!("HTTP request failed: {}", e))?;

    if !res.status().is_success() {
        return Err(format!("Playlist returned HTTP {}", res.status()));
    }

    let json: serde_json::Value = res
        .json()
        .await
        .map_err(|e| format!("JSON parse failed: {}", e))?;

    let mut results = Vec::new();
    let arr = json
        .get("list")
        .and_then(|v| v.as_array())
        .or_else(|| json.get("songs").and_then(|v| v.as_array()));

    if let Some(arr) = arr {
        for item in arr {
            if let Some(track) = parse_song_item(item) {
                results.push(track);
            }
        }
    }

    Ok(results)
}

pub async fn get_recommendations(
    selected_languages: Vec<String>,
    selected_artists: Vec<String>,
) -> Result<Vec<CloudSection>, String> {
    let effective_langs = if selected_languages.is_empty() {
        vec![
            "Malayalam".to_string(),
            "Tamil".to_string(),
            "Hindi".to_string(),
            "English".to_string(),
        ]
    } else {
        selected_languages
    };

    let effective_artists = if selected_artists.is_empty() {
        vec![
            "A.R. Rahman".to_string(),
            "Sushin Shyam".to_string(),
            "Anirudh Ravichander".to_string(),
            "Arijit Singh".to_string(),
        ]
    } else {
        selected_artists
    };

    let chart_ids = [
        ("Malayalam", "1134705865"),
        ("Tamil", "1134651042"),
        ("Hindi", "1134543272"),
        ("English", "1134595537"),
        ("Telugu", "1134643225"),
        ("Punjabi", "1134543511"),
        ("Kannada", "1134676505"),
    ];

    let mut sections = Vec::new();

    // 1. Language top charts
    for lang in effective_langs.iter().take(4) {
        let chart = chart_ids.iter().find(|(l, _)| l.eq_ignore_ascii_case(lang));
        let tracks = if let Some((_, id)) = chart {
            get_playlist_songs(id).await.unwrap_or_default()
        } else {
            search_songs(lang).await.unwrap_or_default()
        };

        if !tracks.is_empty() {
            sections.push(CloudSection {
                id: format!("chart_{}", lang.to_lowercase()),
                title: format!("Trending in {}", lang),
                subtitle: Some("Top 50 Superhits Chart".to_string()),
                language: Some(lang.clone()),
                items: tracks.into_iter().take(15).collect(),
            });
        }
    }

    // 2. Artist spotlights
    for artist in effective_artists.iter().take(3) {
        if let Ok(tracks) = search_songs(artist).await {
            if !tracks.is_empty() {
                sections.push(CloudSection {
                    id: format!("artist_{}", artist.to_lowercase().replace(' ', "_")),
                    title: format!("Best of {}", artist),
                    subtitle: Some("Essential hits & fan favorites".to_string()),
                    language: None,
                    items: tracks.into_iter().take(15).collect(),
                });
            }
        }
    }

    // 3. Daily Vibe Mix
    if let Ok(tracks) = search_songs("Top 50 Global").await {
        if !tracks.is_empty() {
            sections.push(CloudSection {
                id: "daily_vibe".to_string(),
                title: "Daily Vibe & Chill".to_string(),
                subtitle: Some("Handpicked for uninterrupted listening".to_string()),
                language: None,
                items: tracks.into_iter().take(12).collect(),
            });
        }
    }

    Ok(sections)
}

pub async fn stream_to_cache(stream_url: &str) -> Result<PathBuf, String> {
    let cache_dir = std::env::temp_dir().join("veyl_desktop_cache");
    let _ = std::fs::create_dir_all(&cache_dir);

    // Clean old files
    if let Ok(entries) = std::fs::read_dir(&cache_dir) {
        for entry in entries.flatten() {
            if let Ok(metadata) = entry.metadata() {
                if let Ok(modified) = metadata.modified() {
                    if let Ok(age) = modified.elapsed() {
                        if age.as_secs() > 86400 {
                            let _ = std::fs::remove_file(entry.path());
                        }
                    }
                }
            }
        }
    }

    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    let file_path = cache_dir.join(format!("veyl_stream_{}.m4a", timestamp));

    let res = HTTP_CLIENT
        .get(stream_url)
        .send()
        .await
        .map_err(|e| format!("Failed to download stream: {}", e))?;

    if !res.status().is_success() {
        return Err(format!("Stream download HTTP {}", res.status()));
    }

    let bytes = res
        .bytes()
        .await
        .map_err(|e| format!("Failed reading stream bytes: {}", e))?;

    let mut file = File::create(&file_path).map_err(|e| format!("Failed creating temp file: {}", e))?;
    file.write_all(&bytes)
        .map_err(|e| format!("Failed writing stream to disk: {}", e))?;
    file.flush()
        .map_err(|e| format!("Failed flushing stream file: {}", e))?;

    Ok(file_path)
}

pub async fn download_to_music_folder(
    stream_url: &str,
    title: &str,
    artist: &str,
    thumbnail_url: &str,
) -> Result<String, String> {
    let music_dir = dirs_or_fallback_music_dir();
    let veyl_dir = music_dir.join("Veyl");
    std::fs::create_dir_all(&veyl_dir)
        .map_err(|e| format!("Failed to create Veyl music directory: {}", e))?;

    let sanitized_title: String = title
        .chars()
        .map(|c| if c.is_alphanumeric() || c == ' ' || c == '-' { c } else { '_' })
        .collect();
    let sanitized_artist: String = artist
        .chars()
        .map(|c| if c.is_alphanumeric() || c == ' ' || c == '-' { c } else { '_' })
        .collect();

    let file_name = format!("{} - {}.m4a", sanitized_title.trim(), sanitized_artist.trim());
    let target_file = veyl_dir.join(&file_name);

    if !target_file.exists() {
        let res = HTTP_CLIENT
            .get(stream_url)
            .send()
            .await
            .map_err(|e| format!("Download stream failed: {}", e))?;

        if !res.status().is_success() {
            return Err(format!("Download failed with HTTP {}", res.status()));
        }

        let bytes = res.bytes().await.map_err(|e| format!("Download read error: {}", e))?;
        let mut file = File::create(&target_file).map_err(|e| format!("Could not create file: {}", e))?;
        file.write_all(&bytes).map_err(|e| format!("Could not write file: {}", e))?;
    }

    // Download companion cover art if thumbnail available
    if !thumbnail_url.is_empty() {
        let cover_file = veyl_dir.join(format!("{}.jpg", target_file.file_stem().unwrap().to_str().unwrap()));
        if !cover_file.exists() {
            if let Ok(res) = HTTP_CLIENT.get(thumbnail_url).send().await {
                if let Ok(bytes) = res.bytes().await {
                    let _ = std::fs::write(cover_file, bytes);
                }
            }
        }
    }

    Ok(target_file.to_string_lossy().to_string())
}

fn dirs_or_fallback_music_dir() -> PathBuf {
    if let Some(home) = std::env::var_os("HOME") {
        let path = PathBuf::from(home).join("Music");
        if path.exists() {
            return path;
        }
    }
    std::env::temp_dir()
}

pub async fn fetch_synced_lyrics(
    track_uri: &str,
    title: &str,
    artist: &str,
    album: &str,
    duration_seconds: f64,
) -> Result<Vec<LyricLine>, String> {
    // 1. Check local sidecar .lrc files if it's a local file
    if !track_uri.starts_with("http") {
        let p = std::path::Path::new(track_uri);
        let lrc_path = p.with_extension("lrc");
        if lrc_path.exists() {
            if let Ok(content) = std::fs::read_to_string(&lrc_path) {
                let parsed = parse_lrc(&content);
                if !parsed.is_empty() {
                    return Ok(parsed);
                }
            }
        }
    }

    // 2. Fetch from LRCLIB direct API
    let clean_title = title.split('(').next().unwrap_or(title).trim();
    let clean_artist = artist.split(',').next().unwrap_or(artist).trim();

    let endpoint = format!(
        "https://lrclib.net/api/get?track_name={}&artist_name={}&album_name={}&duration={}",
        urlencoding::encode(clean_title),
        urlencoding::encode(clean_artist),
        urlencoding::encode(album.trim()),
        duration_seconds as u64
    );

    if let Ok(res) = HTTP_CLIENT.get(&endpoint).send().await {
        if res.status().is_success() {
            if let Ok(json) = res.json::<serde_json::Value>().await {
                if let Some(synced) = json.get("syncedLyrics").and_then(|v| v.as_str()) {
                    let parsed = parse_lrc(synced);
                    if !parsed.is_empty() {
                        return Ok(parsed);
                    }
                }
                if let Some(plain) = json.get("plainLyrics").and_then(|v| v.as_str()) {
                    let parsed = parse_plain_lyrics(plain, duration_seconds);
                    if !parsed.is_empty() {
                        return Ok(parsed);
                    }
                }
            }
        }
    }

    // 3. Fallback search on LRCLIB
    let search_q = format!("{} {}", clean_title, clean_artist);
    let search_endpoint = format!(
        "https://lrclib.net/api/search?q={}",
        urlencoding::encode(&search_q)
    );

    if let Ok(res) = HTTP_CLIENT.get(&search_endpoint).send().await {
        if res.status().is_success() {
            if let Ok(json) = res.json::<serde_json::Value>().await {
                if let Some(arr) = json.as_array() {
                    for item in arr {
                        if let Some(synced) = item.get("syncedLyrics").and_then(|v| v.as_str()) {
                            let parsed = parse_lrc(synced);
                            if !parsed.is_empty() {
                                return Ok(parsed);
                            }
                        }
                    }
                }
            }
        }
    }

    Ok(Vec::new())
}

pub fn parse_lrc(content: &str) -> Vec<LyricLine> {
    let mut lines = Vec::new();
    for raw in content.lines() {
        let line = raw.trim();
        if line.starts_with('[') {
            if let Some(bracket_end) = line.find(']') {
                let tag = &line[1..bracket_end];
                let text = line[bracket_end + 1..].trim();
                let parts: Vec<&str> = tag.split(':').collect();
                if parts.len() == 2 {
                    if let (Ok(min), Ok(sec)) = (parts[0].parse::<f64>(), parts[1].parse::<f64>()) {
                        let total_sec = min * 60.0 + sec;
                        if !text.is_empty() {
                            lines.push(LyricLine {
                                sec: total_sec,
                                text: text.to_string(),
                            });
                        }
                    }
                }
            }
        }
    }
    lines.sort_by(|a, b| a.sec.partial_cmp(&b.sec).unwrap_or(std::cmp::Ordering::Equal));
    lines
}

fn parse_plain_lyrics(plain: &str, duration: f64) -> Vec<LyricLine> {
    let raw_lines: Vec<&str> = plain.lines().map(|l| l.trim()).filter(|l| !l.is_empty()).collect();
    if raw_lines.is_empty() {
        return Vec::new();
    }
    let step = if duration > 0.0 { duration / (raw_lines.len() as f64) } else { 3.5 };
    raw_lines
        .into_iter()
        .enumerate()
        .map(|(i, text)| LyricLine {
            sec: i as f64 * step,
            text: text.to_string(),
        })
        .collect()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SpotifyTrackDto {
    pub title: String,
    pub artist: String,
    pub duration_seconds: f64,
    pub cover_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SpotifyPlaylistDto {
    pub title: String,
    pub cover_url: Option<String>,
    pub tracks: Vec<SpotifyTrackDto>,
}

pub async fn fetch_spotify_playlist(url: &str) -> Result<SpotifyPlaylistDto, String> {
    let trimmed = url.trim();
    let (id, item_type) = if let Some(pos) = trimmed.find("/playlist/") {
        let after = &trimmed[pos + 10..];
        let id_clean = after.split('?').next().unwrap_or(after).trim();
        (id_clean.to_string(), "playlist")
    } else if let Some(pos) = trimmed.find("/album/") {
        let after = &trimmed[pos + 7..];
        let id_clean = after.split('?').next().unwrap_or(after).trim();
        (id_clean.to_string(), "album")
    } else if let Some(pos) = trimmed.find("spotify:playlist:") {
        let after = &trimmed[pos + 17..];
        (after.trim().to_string(), "playlist")
    } else if let Some(pos) = trimmed.find("spotify:album:") {
        let after = &trimmed[pos + 14..];
        (after.trim().to_string(), "album")
    } else if trimmed.len() >= 20 && trimmed.len() <= 24 && trimmed.chars().all(|c| c.is_alphanumeric()) {
        (trimmed.to_string(), "playlist")
    } else {
        return Err("Invalid Spotify URL. Please paste a link to a Spotify playlist or album (e.g. https://open.spotify.com/playlist/...)".to_string());
    };

    let embed_url = format!("https://open.spotify.com/embed/{}/{}", item_type, id);
    let resp = HTTP_CLIENT
        .get(&embed_url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.9")
        .send()
        .await
        .map_err(|e| format!("Failed to reach Spotify embed: {}", e))?;

    if !resp.status().is_success() {
        return Err(format!("Spotify returned HTTP {}. Please ensure the playlist is public.", resp.status()));
    }

    let html = resp.text().await.map_err(|e| format!("Failed to read Spotify response: {}", e))?;

    // Look for <script id="__NEXT_DATA__" or <script id="initial-state"
    let json_str = if let Some(start_tag) = html.find(r#"<script id="__NEXT_DATA__""#) {
        let tag_close = html[start_tag..].find('>').ok_or("Malformed script tag in Spotify embed")?;
        let content_start = start_tag + tag_close + 1;
        let end_tag = html[content_start..].find("</script>").ok_or("Unclosed script tag in Spotify embed")?;
        &html[content_start..content_start + end_tag]
    } else if let Some(start_tag) = html.find(r#"<script id="initial-state""#) {
        let tag_close = html[start_tag..].find('>').ok_or("Malformed initial-state tag")?;
        let content_start = start_tag + tag_close + 1;
        let end_tag = html[content_start..].find("</script>").ok_or("Unclosed initial-state tag")?;
        &html[content_start..content_start + end_tag]
    } else {
        return Err("Could not extract playlist data from Spotify embed. Please check that the playlist is public.".to_string());
    };

    let root: serde_json::Value = serde_json::from_str(json_str).map_err(|e| format!("Failed to parse Spotify JSON: {}", e))?;

    let entity = root.get("props")
        .and_then(|p| p.get("pageProps"))
        .and_then(|pp| {
            pp.get("state")
                .and_then(|s| s.get("data"))
                .and_then(|d| d.get("entity"))
                .or_else(|| pp.get("entity"))
        })
        .or_else(|| root.get("entity"))
        .ok_or("Could not locate playlist entity in Spotify response")?;

    let raw_title = entity.get("name")
        .and_then(|v| v.as_str())
        .or_else(|| entity.get("title").and_then(|v| v.as_str()))
        .unwrap_or(if item_type == "album" { "Imported Spotify Album" } else { "Imported Spotify Playlist" });
    let playlist_title = unescape_html(raw_title);

    let mut cover_url: Option<String> = None;
    if let Some(visual_images) = entity.get("visualIdentity").and_then(|v| v.get("image")).and_then(|img| img.as_array()) {
        if let Some(first_img) = visual_images.first() {
            if let Some(u) = first_img.get("url").and_then(|v| v.as_str()) {
                cover_url = Some(u.to_string());
            }
        }
    }
    if cover_url.is_none() {
        if let Some(sources) = entity.get("coverArt").and_then(|c| c.get("sources")).and_then(|s| s.as_array()) {
            if let Some(first) = sources.first() {
                if let Some(u) = first.get("url").and_then(|v| v.as_str()) {
                    cover_url = Some(u.to_string());
                }
            }
        }
    }
    if cover_url.is_none() {
        if let Some(images) = entity.get("images").and_then(|i| i.as_array()) {
            if let Some(first) = images.first() {
                if let Some(u) = first.get("url").and_then(|v| v.as_str()) {
                    cover_url = Some(u.to_string());
                }
            }
        }
    }

    let mut tracks = Vec::new();
    if let Some(track_list) = entity.get("trackList").and_then(|t| t.as_array()) {
        for item in track_list {
            let title = item.get("title").and_then(|v| v.as_str()).unwrap_or("").trim();
            if title.is_empty() {
                continue;
            }
            let artist = item.get("subtitle").and_then(|v| v.as_str()).unwrap_or("Unknown Artist").trim();
            let duration_ms = item.get("duration").and_then(|v| v.as_f64()).unwrap_or(0.0);
            let duration_seconds = if duration_ms > 0.0 { duration_ms / 1000.0 } else { 0.0 };

            let is_album = item_type == "album";
            let track_cover = item.get("coverUrl").and_then(|v| v.as_str())
                .or_else(|| item.get("artworkUrl").and_then(|v| v.as_str()))
                .map(|s| s.to_string())
                .or_else(|| if is_album { cover_url.clone() } else { None });

            tracks.push(SpotifyTrackDto {
                title: unescape_html(title),
                artist: unescape_html(artist),
                duration_seconds,
                cover_url: track_cover,
            });
        }
    }

    if tracks.is_empty() {
        return Err("No tracks found in this Spotify playlist. Ensure it contains playable songs.".to_string());
    }

    Ok(SpotifyPlaylistDto {
        title: playlist_title,
        cover_url,
        tracks,
    })
}

pub async fn resolve_track_artwork(title: &str, artist: &str) -> Result<Option<String>, String> {
    let clean_title = title.split('(').next().unwrap_or(title).trim();
    let query = format!("{} {}", clean_title, artist.trim());
    let encoded = urlencoding::encode(&query);
    let itunes_url = format!("https://itunes.apple.com/search?term={}&entity=song&limit=1", encoded);

    if let Ok(resp) = HTTP_CLIENT.get(&itunes_url).send().await {
        if resp.status().is_success() {
            if let Ok(val) = resp.json::<serde_json::Value>().await {
                if let Some(results) = val.get("results").and_then(|r| r.as_array()) {
                    if let Some(first) = results.first() {
                        if let Some(art) = first.get("artworkUrl100").and_then(|a| a.as_str()) {
                            let high_res = art.replace("100x100bb.jpg", "600x600bb.jpg");
                            return Ok(Some(high_res));
                        }
                    }
                }
            }
        }
    }

    if let Ok(cloud_tracks) = search_songs(&query).await {
        if let Some(first) = cloud_tracks.first() {
            if !first.thumbnail_url.is_empty() {
                return Ok(Some(first.thumbnail_url.clone()));
            }
        }
    }

    Ok(None)
}

pub fn write_text_file(path: &str, content: &str) -> Result<(), String> {
    let mut file = File::create(path).map_err(|e| format!("Failed to create file: {}", e))?;
    file.write_all(content.as_bytes()).map_err(|e| format!("Failed to write file: {}", e))?;
    Ok(())
}

pub fn read_text_file(path: &str) -> Result<String, String> {
    std::fs::read_to_string(path).map_err(|e| format!("Failed to read file: {}", e))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cloud_search_and_decrypt() {
        tauri::async_runtime::block_on(async {
            let results = search_songs("Arijit Singh").await.expect("search should succeed");
            assert!(!results.is_empty(), "results should not be empty");
            let first = &results[0];
            println!("Found track: {} by {}", first.title, first.artist);
            println!("Stream URL: {}", first.stream_url);
            assert!(first.stream_url.contains("http"), "stream url should be valid http url");
            assert!(first.stream_url.contains("_320.mp4"), "stream url should be upgraded to 320 kbps");
        });
    }

    #[test]
    fn test_resolve_track_artwork() {
        tauri::async_runtime::block_on(async {
            let art = resolve_track_artwork("Maya Nadhi", "Santhosh Narayanan").await.expect("resolve should succeed");
            println!("Resolved art for Maya Nadhi: {:?}", art);
            assert!(art.is_some(), "should resolve artwork for Maya Nadhi");

            let art2 = resolve_track_artwork("expectations (feat. Chappell Roan)", "Olivia Rodrigo, Chappell Roan").await.expect("resolve should succeed");
            println!("Resolved art for expectations: {:?}", art2);
            assert!(art2.is_some(), "should resolve artwork for Olivia Rodrigo");

            let art3 = resolve_track_artwork("Manickyachirakulla", "Job Kurien").await.expect("resolve should succeed");
            println!("Resolved art for Manickyachirakulla: {:?}", art3);
            assert!(art3.is_some(), "should resolve artwork for Manickyachirakulla");
        });
    }
}
