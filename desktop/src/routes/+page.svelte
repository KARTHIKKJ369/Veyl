<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { api } from '$lib/api';
  import type { EngineStatus, EqBandInfo, FrequencyPoint, TrackInfo } from '$lib/types';

  // Navigation tabs
  type Tab = 'library' | 'equalizer' | 'queue';
  let activeTab = $state<Tab>('library');

  // Library & Queue state
  let libraryTracks = $state<TrackInfo[]>([]);
  let queue = $state<TrackInfo[]>([]);
  let currentTrack = $state<TrackInfo | null>(null);
  let currentArtwork = $state<string | null>(null);
  let searchQuery = $state('');
  let filterFormat = $state<'ALL' | 'HI-RES' | 'DSD' | 'LOSSLESS'>('ALL');

  // Player state
  let isPlaying = $state(false);
  let positionSec = $state(0);
  let durationSec = $state(0);
  let volume = $state(1.0);
  let isMuted = $state(false);
  let sampleRate = $state(44100);
  let bitDepth = $state<number | null>(16);
  let codecName = $state('FLAC/PCM');
  let isBitPerfect = $state(true);
  let spectrum16 = $state<number[]>(new Array(16).fill(0));
  let rmsLeft = $state(0);
  let rmsRight = $state(0);

  // Equalizer state
  let eqEnabled = $state(true);
  let eqPreamp = $state(0);
  let eqBands = $state<EqBandInfo[]>([]);
  let eqCurve = $state<FrequencyPoint[]>([]);

  // Telemetry poll interval
  let pollInterval: any = null;

  // Filtered tracks
  let filteredTracks = $derived.by(() => {
    let result = libraryTracks;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          t.artist.toLowerCase().includes(q) ||
          t.album.toLowerCase().includes(q)
      );
    }
    if (filterFormat === 'HI-RES') {
      result = result.filter((t) => t.sample_rate >= 88200 || (t.bit_depth && t.bit_depth >= 24));
    } else if (filterFormat === 'DSD') {
      result = result.filter((t) => t.format_name.toUpperCase().includes('DSD'));
    } else if (filterFormat === 'LOSSLESS') {
      result = result.filter(
        (t) =>
          t.format_name.toUpperCase().includes('FLAC') ||
          t.format_name.toUpperCase().includes('ALAC') ||
          t.format_name.toUpperCase().includes('WAV')
      );
    }
    return result;
  });

  // Format spec string
  let formatBadge = $derived.by(() => {
    if (codecName.toUpperCase().includes('DSD')) return 'DSD NATIVE';
    const khz = (sampleRate / 1000).toFixed(1);
    const bits = bitDepth ? `${bitDepth}b` : '16b';
    return `${codecName.toUpperCase()} ${khz}k/${bits}`;
  });

  function formatTime(sec: number): string {
    if (!sec || isNaN(sec) || sec < 0) return '0:00';
    const totalSec = Math.floor(sec);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  async function syncEngineStatus() {
    try {
      const status: EngineStatus = await api.getEngineStatus();
      isPlaying = status.state === 'Playing';
      positionSec = status.position_seconds;
      durationSec = status.duration_seconds;
      sampleRate = status.sample_rate;
      bitDepth = status.bit_depth ?? null;
      codecName = status.codec_name;
      isBitPerfect = status.is_bit_perfect;

      if (status.metrics) {
        spectrum16 = status.metrics.spectrum_16 ?? new Array(16).fill(0);
        rmsLeft = status.metrics.rms_left ?? 0;
        rmsRight = status.metrics.rms_right ?? 0;
      }

      if (status.current_track && (!currentTrack || currentTrack.uri !== status.current_track.uri)) {
        currentTrack = status.current_track;
        loadArtwork(currentTrack.uri);
      }
    } catch (e) {
      // Background poll error handled silently
    }
  }

  async function loadArtwork(uri: string) {
    try {
      const art = await api.getTrackArtwork(uri);
      currentArtwork = art;
    } catch {
      currentArtwork = null;
    }
  }

  async function refreshEqualizer() {
    try {
      eqBands = await api.getEqualizerBands();
      eqCurve = await api.getEqualizerCurve(100);
    } catch (e) {
      console.error('EQ sync error:', e);
    }
  }

  async function handlePlayTrack(track: TrackInfo) {
    await api.playTrack(track);
    currentTrack = track;
    isPlaying = true;
    loadArtwork(track.uri);

    // If queue doesn't contain this track, append it
    if (!queue.some((t) => t.uri === track.uri)) {
      queue = [...queue, track];
    }
  }

  async function togglePlay() {
    isPlaying = await api.togglePlayPause();
  }

  async function handleSeek(e: Event) {
    const target = e.target as HTMLInputElement;
    const newPos = parseFloat(target.value);
    positionSec = newPos;
    await api.seek(newPos);
  }

  async function handleVolume(e: Event) {
    const target = e.target as HTMLInputElement;
    const val = parseFloat(target.value);
    volume = val;
    isMuted = val === 0;
    await api.setVolume(val);
  }

  async function toggleMute() {
    if (isMuted) {
      isMuted = false;
      await api.setVolume(volume > 0 ? volume : 0.8);
    } else {
      isMuted = true;
      await api.setVolume(0);
    }
  }

  async function handleScanFolder() {
    const folder = await api.pickMusicFolder();
    if (folder) {
      const tracks = await api.scanMusicDirectory(folder);
      if (tracks && tracks.length > 0) {
        // Merge with existing library avoiding duplicates
        const existingUris = new Set(libraryTracks.map((t) => t.uri));
        const newTracks = tracks.filter((t) => !existingUris.has(t.uri));
        libraryTracks = [...libraryTracks, ...newTracks];
        if (!currentTrack && libraryTracks.length > 0) {
          handlePlayTrack(libraryTracks[0]);
        }
      }
    }
  }

  async function handleAddFiles() {
    const files = await api.pickAudioFiles();
    if (files && files.length > 0) {
      // Scan each path
      for (const f of files) {
        const scanned = await api.scanMusicDirectory(f);
        if (scanned && scanned.length > 0) {
          libraryTracks = [...libraryTracks, ...scanned];
          queue = [...queue, ...scanned];
        }
      }
    }
  }

  async function adjustBandGain(index: number, deltaDb: number) {
    const band = eqBands[index];
    if (!band) return;
    const newGain = Math.round((band.gain_db + deltaDb) * 10) / 10;
    const clampedGain = Math.max(-12, Math.min(12, newGain));
    band.gain_db = clampedGain;
    await api.setEqualizerBand(index, clampedGain, band.frequency, band.q);
    eqCurve = await api.getEqualizerCurve(100);
  }

  async function toggleEq() {
    eqEnabled = !eqEnabled;
    await api.setEqualizerEnabled(eqEnabled);
  }

  function playNextInQueue() {
    if (queue.length === 0) return;
    const curIdx = currentTrack ? queue.findIndex((t) => t.uri === currentTrack?.uri) : -1;
    const nextIdx = (curIdx + 1) % queue.length;
    handlePlayTrack(queue[nextIdx]);
  }

  function playPrevInQueue() {
    if (queue.length === 0) return;
    const curIdx = currentTrack ? queue.findIndex((t) => t.uri === currentTrack?.uri) : 0;
    const prevIdx = (curIdx - 1 + queue.length) % queue.length;
    handlePlayTrack(queue[prevIdx]);
  }

  function removeQueueItem(index: number) {
    queue = queue.filter((_, i) => i !== index);
  }

  onMount(() => {
    refreshEqualizer();
    syncEngineStatus();
    pollInterval = setInterval(syncEngineStatus, 150);
  });

  onDestroy(() => {
    if (pollInterval) clearInterval(pollInterval);
  });
</script>

<div class="veyl-app">
  <!-- Top Title / Window Bar -->
  <header class="top-bar">
    <div class="brand">
      <div class="brand-badge">VEYL</div>
      <div class="telemetry-pill">
        <span class="live-pulse"></span>
        <span class="pill-text">BIT-PERFECT DESKTOP CORE</span>
      </div>
    </div>

    <!-- Search input -->
    <div class="search-box">
      <svg class="icon search-icon" viewBox="0 0 24 24">
        <path fill="currentColor" d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
      </svg>
      <input
        type="text"
        placeholder="Search track, artist, album, format..."
        bind:value={searchQuery}
      />
    </div>

    <!-- Actions -->
    <div class="header-actions">
      <button class="btn btn-secondary" onclick={handleScanFolder}>
        <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>
        <span>Open Folder</span>
      </button>
      <button class="btn btn-primary" onclick={handleAddFiles}>
        <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
        <span>Add Files</span>
      </button>
    </div>
  </header>

  <!-- Workspace Container: Sidebar + Main Content -->
  <div class="workspace">
    <!-- Left Navigation Rail -->
    <nav class="sidebar">
      <div class="nav-section">
        <div class="section-title">LIBRARY</div>
        <button
          class="nav-item"
          class:active={activeTab === 'library'}
          onclick={() => (activeTab = 'library')}
        >
          <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg>
          <span>All Tracks</span>
          <span class="count-badge">{libraryTracks.length}</span>
        </button>
      </div>

      <div class="nav-section">
        <div class="section-title">AUDIO ENGINE</div>
        <button
          class="nav-item"
          class:active={activeTab === 'equalizer'}
          onclick={() => {
            activeTab = 'equalizer';
            refreshEqualizer();
          }}
        >
          <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M10 20h4V4h-4v16zm-6 0h4v-7H4v7zM16 9v11h4V9h-4z"/></svg>
          <span>10-Band EQ</span>
          <span class="status-indicator" class:on={eqEnabled}></span>
        </button>

        <button
          class="nav-item"
          class:active={activeTab === 'queue'}
          onclick={() => (activeTab = 'queue')}
        >
          <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M4 10h12v2H4zm0-4h12v2H4zm0 8h8v2H4zm10 0v6l5-3z"/></svg>
          <span>Playback Queue</span>
          <span class="count-badge">{queue.length}</span>
        </button>
      </div>

      <div class="sidebar-spacer"></div>

      <!-- Quick Audio Spec Card -->
      <div class="sidebar-spec-card">
        <div class="spec-label">ACTIVE STREAM</div>
        <div class="spec-rate">{formatBadge}</div>
        <div class="spec-route">
          <span class="dot-green"></span>
          <span>{isBitPerfect ? 'Exclusive / Direct Hardware' : 'Shared Stream'}</span>
        </div>
      </div>
    </nav>

    <!-- Main Content Area -->
    <main class="content-panel">
      <!-- 1. LIBRARY TAB -->
      {#if activeTab === 'library'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Music Library</h2>
              <p class="view-subtitle">{libraryTracks.length} lossless & Hi-Res tracks indexed</p>
            </div>

            <!-- Format Filter Chips -->
            <div class="filter-chips">
              {#each ['ALL', 'HI-RES', 'DSD', 'LOSSLESS'] as fmt}
                <button
                  class="chip"
                  class:active={filterFormat === fmt}
                  onclick={() => (filterFormat = fmt as any)}
                >
                  {fmt}
                </button>
              {/each}
            </div>
          </div>

          {#if filteredTracks.length === 0}
            <div class="empty-state">
              <div class="empty-icon">📁</div>
              <h3>No audio tracks found</h3>
              <p>Click "Open Folder" to index your local FLAC, DSD, ALAC, or WAV music collection.</p>
              <button class="btn btn-primary" onclick={handleScanFolder}>Open Music Folder</button>
            </div>
          {:else}
            <div class="track-table-container">
              <table class="track-table">
                <thead>
                  <tr>
                    <th style="width: 44px">#</th>
                    <th>TITLE & ARTIST</th>
                    <th>ALBUM</th>
                    <th>FORMAT / SPECS</th>
                    <th style="width: 80px; text-align: right">DURATION</th>
                  </tr>
                </thead>
                <tbody>
                  {#each filteredTracks as track, i}
                    <tr
                      class:active={currentTrack?.uri === track.uri}
                      ondblclick={() => handlePlayTrack(track)}
                    >
                      <td class="col-index">
                        {#if currentTrack?.uri === track.uri && isPlaying}
                          <span class="playing-bars"><span></span><span></span><span></span></span>
                        {:else}
                          <span class="index-num">{i + 1}</span>
                          <button class="row-play-btn" onclick={() => handlePlayTrack(track)}>▶</button>
                        {/if}
                      </td>
                      <td class="col-title">
                        <div class="track-title-text">{track.title}</div>
                        <div class="track-artist-text">{track.artist || 'Unknown Artist'}</div>
                      </td>
                      <td class="col-album">{track.album || 'Unknown Album'}</td>
                      <td class="col-format">
                        <span class="format-pill">
                          {track.format_name} {Math.round(track.sample_rate / 1000)}kHz
                        </span>
                      </td>
                      <td class="col-duration">{formatTime(track.duration_seconds)}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        </div>

      <!-- 2. EQUALIZER TAB -->
      {:else if activeTab === 'equalizer'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Parametric Equalizer</h2>
              <p class="view-subtitle">High-precision 10-band biquad IIR filter engine with zero clipping</p>
            </div>

            <div class="header-actions">
              <button class="btn" class:btn-primary={eqEnabled} class:btn-secondary={!eqEnabled} onclick={toggleEq}>
                {eqEnabled ? 'EQ Enabled' : 'Bypass EQ'}
              </button>
            </div>
          </div>

          <!-- Interactive Response Curve Canvas -->
          <div class="curve-card">
            <div class="curve-header">
              <span class="spec-label">REAL-TIME FREQUENCY RESPONSE (20 Hz — 20 kHz)</span>
              <span class="spec-rate">{eqCurve.length} analysis points</span>
            </div>
            <svg class="curve-svg" viewBox="0 0 800 200" preserveAspectRatio="none">
              <!-- Grid lines -->
              <line x1="0" y1="100" x2="800" y2="100" stroke="rgba(255,255,255,0.1)" stroke-width="1" />
              <line x1="0" y1="50" x2="800" y2="50" stroke="rgba(255,255,255,0.05)" stroke-dasharray="4" />
              <line x1="0" y1="150" x2="800" y2="150" stroke="rgba(255,255,255,0.05)" stroke-dasharray="4" />

              <!-- Frequency Response Curve -->
              {#if eqCurve.length > 1}
                {@const pathD = eqCurve.reduce((acc, pt, idx) => {
                  const x = (idx / (eqCurve.length - 1)) * 800;
                  // Map -12dB to +12dB to y: 190 down to 10
                  const y = 100 - (pt.gain_db / 12) * 80;
                  return idx === 0 ? `M ${x} ${y}` : `${acc} L ${x} ${y}`;
                }, '')}
                <path d={pathD} fill="none" stroke="#CCFF00" stroke-width="2.5" />
                <path d="{pathD} L 800 200 L 0 200 Z" fill="url(#curveGlow)" opacity="0.15" />
              {/if}

              <defs>
                <linearGradient id="curveGlow" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#CCFF00" stop-opacity="0.8" />
                  <stop offset="100%" stop-color="#CCFF00" stop-opacity="0" />
                </linearGradient>
              </defs>
            </svg>
          </div>

          <!-- 10-Band Sliders -->
          <div class="eq-bands-strip">
            {#each eqBands as band, i}
              <div class="band-column">
                <div class="gain-badge" class:boosted={band.gain_db > 0} class:cut={band.gain_db < 0}>
                  {band.gain_db > 0 ? `+${band.gain_db.toFixed(1)}` : band.gain_db.toFixed(1)} dB
                </div>

                <div class="slider-wrapper">
                  <input
                    type="range"
                    min="-12"
                    max="12"
                    step="0.5"
                    value={band.gain_db}
                    oninput={(e) => {
                      const v = parseFloat((e.target as HTMLInputElement).value);
                      band.gain_db = v;
                      api.setEqualizerBand(i, v, band.frequency, band.q).then(() => {
                        api.getEqualizerCurve(100).then((c) => (eqCurve = c));
                      });
                    }}
                  />
                </div>

                <!-- Stepper Nudge Buttons -->
                <div class="stepper-row">
                  <button class="step-btn" onclick={() => adjustBandGain(i, -0.5)}>−</button>
                  <button class="step-btn" onclick={() => adjustBandGain(i, +0.5)}>+</button>
                </div>

                <div class="freq-label">
                  {band.frequency >= 1000 ? `${(band.frequency / 1000).toFixed(0)}k` : `${Math.round(band.frequency)}`}
                </div>
              </div>
            {/each}
          </div>
        </div>

      <!-- 3. QUEUE TAB -->
      {:else if activeTab === 'queue'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Playback Sequence</h2>
              <p class="view-subtitle">{queue.length} tracks scheduled in queue</p>
            </div>
            <button class="btn btn-secondary" onclick={() => (queue = [])}>Clear Queue</button>
          </div>

          {#if queue.length === 0}
            <div class="empty-state">
              <div class="empty-icon">📑</div>
              <h3>Queue is empty</h3>
              <p>Add songs from your library or click "Add Files" to start a listening session.</p>
            </div>
          {:else}
            <div class="queue-list">
              {#each queue as track, i}
                <div class="queue-item" class:active={currentTrack?.uri === track.uri}>
                  <div class="q-index">{i + 1}</div>
                  <div class="q-info">
                    <div class="q-title">{track.title}</div>
                    <div class="q-sub">{track.artist || 'Unknown'} • {track.format_name}</div>
                  </div>
                  <div class="q-dur">{formatTime(track.duration_seconds)}</div>
                  <button class="q-del-btn" onclick={() => removeQueueItem(i)}>✕</button>
                </div>
              {/each}
            </div>
          {/if}
        </div>
      {/if}
    </main>
  </div>

  <!-- Persistent Audiophile Player Dock (Bottom) -->
  <footer class="player-dock">
    <!-- Left: Track Metadata & Specs -->
    <div class="dock-meta">
      <div class="artwork-thumb">
        {#if currentArtwork}
          <img src={currentArtwork} alt="Cover" />
        {:else}
          <div class="thumb-placeholder">🎵</div>
        {/if}
      </div>

      <div class="meta-texts">
        <div class="title-row">
          <span class="active-title">{currentTrack?.title || 'No Track Selected'}</span>
        </div>
        <div class="active-artist">{currentTrack?.artist || 'Veyl Audio Engine'}</div>
        <div class="spec-chip">{formatBadge}</div>
      </div>
    </div>

    <!-- Center: Transport & Progress Controls -->
    <div class="dock-transport">
      <div class="transport-buttons">
        <button class="ctrl-btn" onclick={playPrevInQueue} title="Previous">⏮</button>
        <button class="ctrl-btn play-main-btn" onclick={togglePlay} title={isPlaying ? 'Pause' : 'Play'}>
          {isPlaying ? '⏸' : '▶'}
        </button>
        <button class="ctrl-btn" onclick={playNextInQueue} title="Next">⏭</button>
      </div>

      <!-- Scrubber -->
      <div class="scrub-container">
        <span class="time-text">{formatTime(positionSec)}</span>
        <div class="slider-box">
          <input
            type="range"
            min="0"
            max={durationSec || 1}
            step="0.1"
            value={positionSec}
            oninput={handleSeek}
          />
        </div>
        <span class="time-text right">-{formatTime((durationSec - positionSec) > 0 ? durationSec - positionSec : 0)}</span>
      </div>
    </div>

    <!-- Right: Telemetry Spectrum & Volume -->
    <div class="dock-telemetry">
      <!-- 16-band live spectrum analyzer -->
      <div class="spectrum-visualizer" title="Real-time 16-band hardware spectrum">
        {#each spectrum16 as val}
          <div
            class="spec-bar"
            style="height: {Math.max(3, Math.min(28, val * 35))}px"
          ></div>
        {/each}
      </div>

      <!-- Volume -->
      <div class="vol-cluster">
        <button class="vol-btn" onclick={toggleMute}>
          {isMuted ? '🔇' : '🔊'}
        </button>
        <input
          class="vol-slider"
          type="range"
          min="0"
          max="1.5"
          step="0.01"
          value={isMuted ? 0 : volume}
          oninput={handleVolume}
        />
      </div>
    </div>
  </footer>
</div>

<style>
  .veyl-app {
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: 100vw;
    background-color: var(--bg-void);
    color: var(--text-primary);
  }

  /* Header */
  .top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 20px;
    background: var(--surface-panel);
    border-bottom: 1px solid var(--border-hairline);
    height: 60px;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .brand-badge {
    font-family: var(--font-mono);
    font-size: 16px;
    font-weight: 800;
    letter-spacing: 2px;
    color: var(--signal-green);
  }

  .telemetry-pill {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 8px;
    background: var(--signal-green-dim);
    border-radius: var(--radius-pill);
    border: 1px solid var(--border-active);
  }

  .live-pulse {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--signal-green);
    box-shadow: 0 0 8px var(--signal-green);
  }

  .pill-text {
    font-family: var(--font-mono);
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: var(--signal-green);
  }

  .search-box {
    display: flex;
    align-items: center;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    padding: 6px 14px;
    width: 360px;
  }

  .search-box input {
    background: transparent;
    border: none;
    outline: none;
    color: var(--text-primary);
    font-size: 12px;
    margin-left: 8px;
    width: 100%;
  }

  .search-icon {
    width: 16px;
    height: 16px;
    color: var(--text-muted);
  }

  .header-actions {
    display: flex;
    gap: 8px;
  }

  .btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 7px 14px;
    border-radius: var(--radius-md);
    font-size: 12px;
    font-weight: 600;
    transition: all 0.2s;
  }

  .btn-primary {
    background: var(--signal-green);
    color: #000;
  }

  .btn-primary:hover {
    background: #ddff33;
    box-shadow: 0 0 12px var(--signal-green-glow);
  }

  .btn-secondary {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    color: var(--text-primary);
  }

  .btn-secondary:hover {
    background: var(--surface-card-hover);
    border-color: rgba(255, 255, 255, 0.2);
  }

  .icon {
    width: 16px;
    height: 16px;
  }

  /* Workspace */
  .workspace {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  /* Sidebar */
  .sidebar {
    width: 220px;
    background: var(--surface-panel);
    border-right: 1px solid var(--border-hairline);
    display: flex;
    flex-direction: column;
    padding: 16px 12px;
    gap: 20px;
  }

  .nav-section {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .section-title {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--text-muted);
    letter-spacing: 1px;
    padding-left: 8px;
    margin-bottom: 6px;
  }

  .nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    border-radius: var(--radius-md);
    font-size: 13px;
    color: var(--text-secondary);
    transition: all 0.15s;
  }

  .nav-item:hover {
    background: var(--surface-card);
    color: var(--text-primary);
  }

  .nav-item.active {
    background: var(--surface-card);
    color: var(--signal-green);
    border: 1px solid var(--border-active);
  }

  .count-badge {
    margin-left: auto;
    font-family: var(--font-mono);
    font-size: 10px;
    background: var(--surface-pill);
    padding: 2px 6px;
    border-radius: var(--radius-pill);
  }

  .status-indicator {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--text-muted);
    margin-left: auto;
  }

  .status-indicator.on {
    background: var(--signal-green);
    box-shadow: 0 0 6px var(--signal-green);
  }

  .sidebar-spacer {
    flex: 1;
  }

  .sidebar-spec-card {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 12px;
  }

  .spec-label {
    font-family: var(--font-mono);
    font-size: 9px;
    color: var(--text-muted);
    letter-spacing: 0.5px;
  }

  .spec-rate {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    color: var(--signal-green);
    margin: 4px 0;
  }

  .spec-route {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 10px;
    color: var(--text-secondary);
  }

  .dot-green {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--signal-green);
  }

  /* Main Content Area */
  .content-panel {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: var(--bg-void);
  }

  .view-container {
    max-width: 1200px;
    margin: 0 auto;
  }

  .view-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: 20px;
  }

  .view-title {
    font-size: 22px;
    font-weight: 700;
  }

  .view-subtitle {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 2px;
  }

  .filter-chips {
    display: flex;
    gap: 6px;
  }

  .chip {
    padding: 5px 12px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--text-secondary);
    transition: all 0.15s;
  }

  .chip.active {
    background: var(--signal-green-dim);
    border-color: var(--border-active);
    color: var(--signal-green);
  }

  /* Track Table */
  .track-table-container {
    background: var(--surface-panel);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-lg);
    overflow: hidden;
  }

  .track-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
  }

  .track-table th {
    text-align: left;
    padding: 10px 14px;
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--text-muted);
    border-bottom: 1px solid var(--border-hairline);
  }

  .track-table tr {
    border-bottom: 1px solid rgba(255, 255, 255, 0.04);
    transition: background 0.15s;
    cursor: pointer;
  }

  .track-table tr:hover {
    background: var(--surface-card);
  }

  .track-table tr.active {
    background: rgba(204, 255, 0, 0.08);
  }

  .track-table td {
    padding: 10px 14px;
  }

  .col-index {
    position: relative;
    text-align: center;
  }

  .row-play-btn {
    display: none;
    font-size: 10px;
    color: var(--signal-green);
  }

  .track-table tr:hover .index-num {
    display: none;
  }

  .track-table tr:hover .row-play-btn {
    display: inline-block;
  }

  .track-title-text {
    font-weight: 600;
    color: var(--text-primary);
  }

  .track-table tr.active .track-title-text {
    color: var(--signal-green);
  }

  .track-artist-text {
    font-size: 11px;
    color: var(--text-muted);
  }

  .format-pill {
    display: inline-block;
    padding: 2px 6px;
    background: var(--surface-pill);
    border-radius: 4px;
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--text-secondary);
  }

  .col-duration {
    font-family: var(--font-mono);
    color: var(--text-muted);
  }

  /* Empty State */
  .empty-state {
    text-align: center;
    padding: 60px 20px;
    background: var(--surface-panel);
    border: 1px dashed var(--border-hairline);
    border-radius: var(--radius-lg);
  }

  .empty-icon {
    font-size: 36px;
    margin-bottom: 12px;
  }

  .empty-state h3 {
    font-size: 16px;
    margin-bottom: 6px;
  }

  .empty-state p {
    font-size: 12px;
    color: var(--text-muted);
    margin-bottom: 18px;
  }

  /* Equalizer Tab */
  .curve-card {
    background: var(--surface-panel);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-lg);
    padding: 16px;
    margin-bottom: 24px;
  }

  .curve-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .curve-svg {
    width: 100%;
    height: 160px;
    background: #0d0e11;
    border-radius: var(--radius-md);
  }

  .eq-bands-strip {
    display: flex;
    justify-content: space-between;
    background: var(--surface-panel);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-lg);
    padding: 24px 16px;
  }

  .band-column {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }

  .gain-badge {
    font-family: var(--font-mono);
    font-size: 11px;
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--surface-pill);
    color: var(--text-secondary);
  }

  .gain-badge.boosted {
    color: var(--signal-green);
    background: var(--signal-green-dim);
  }

  .gain-badge.cut {
    color: #ff5555;
    background: rgba(255, 85, 85, 0.15);
  }

  .slider-wrapper {
    height: 160px;
    display: flex;
    align-items: center;
  }

  .slider-wrapper input[type='range'] {
    writing-mode: vertical-lr;
    direction: rtl;
    width: 6px;
    height: 140px;
    accent-color: var(--signal-green);
  }

  .stepper-row {
    display: flex;
    gap: 4px;
  }

  .step-btn {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    color: var(--text-secondary);
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .step-btn:hover {
    color: var(--signal-green);
    border-color: var(--border-active);
  }

  .freq-label {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    color: var(--text-primary);
  }

  /* Queue List */
  .queue-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .queue-item {
    display: flex;
    align-items: center;
    padding: 10px 16px;
    background: var(--surface-panel);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    gap: 14px;
  }

  .queue-item.active {
    border-color: var(--border-active);
    background: var(--surface-card);
  }

  .q-index {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-muted);
    width: 20px;
  }

  .q-info {
    flex: 1;
  }

  .q-title {
    font-weight: 600;
  }

  .q-sub {
    font-size: 11px;
    color: var(--text-muted);
  }

  .q-dur {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-muted);
  }

  .q-del-btn {
    color: var(--text-muted);
    padding: 4px;
  }

  .q-del-btn:hover {
    color: #ff5555;
  }

  /* Player Dock (Bottom) */
  .player-dock {
    height: 80px;
    background: var(--surface-panel);
    border-top: 1px solid var(--border-hairline);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
  }

  .dock-meta {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 280px;
  }

  .artwork-thumb {
    width: 48px;
    height: 48px;
    border-radius: var(--radius-sm);
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .artwork-thumb img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .thumb-placeholder {
    font-size: 20px;
  }

  .meta-texts {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .active-title {
    font-weight: 700;
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--signal-green);
  }

  .active-artist {
    font-size: 11px;
    color: var(--text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .spec-chip {
    font-family: var(--font-mono);
    font-size: 9px;
    color: var(--text-secondary);
  }

  /* Dock Transport Controls */
  .dock-transport {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    width: 440px;
  }

  .transport-buttons {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .ctrl-btn {
    font-size: 16px;
    color: var(--text-secondary);
    transition: all 0.15s;
  }

  .ctrl-btn:hover {
    color: var(--text-primary);
  }

  .play-main-btn {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: var(--signal-green);
    color: #000;
    font-size: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .play-main-btn:hover {
    box-shadow: 0 0 10px var(--signal-green-glow);
    transform: scale(1.05);
  }

  .scrub-container {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }

  .time-text {
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--text-muted);
    width: 36px;
  }

  .time-text.right {
    text-align: right;
  }

  .slider-box {
    flex: 1;
    display: flex;
    align-items: center;
  }

  .slider-box input[type='range'] {
    width: 100%;
    height: 4px;
    accent-color: var(--signal-green);
  }

  /* Dock Telemetry */
  .dock-telemetry {
    display: flex;
    align-items: center;
    gap: 20px;
    width: 280px;
    justify-content: flex-end;
  }

  .spectrum-visualizer {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 28px;
    padding: 0 4px;
  }

  .spec-bar {
    width: 3px;
    background: var(--signal-green);
    border-radius: 1px;
    transition: height 0.05s ease-out;
  }

  .vol-cluster {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .vol-btn {
    font-size: 14px;
  }

  .vol-slider {
    width: 80px;
    height: 4px;
    accent-color: var(--signal-green);
  }

  .playing-bars {
    display: inline-flex;
    align-items: flex-end;
    gap: 1.5px;
    height: 12px;
  }

  .playing-bars span {
    width: 2px;
    background: var(--signal-green);
    animation: bounce 0.6s infinite alternate;
  }

  .playing-bars span:nth-child(2) {
    animation-delay: 0.2s;
  }

  .playing-bars span:nth-child(3) {
    animation-delay: 0.4s;
  }

  @keyframes bounce {
    0% { height: 2px; }
    100% { height: 12px; }
  }
</style>
