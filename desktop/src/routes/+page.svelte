<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { SvelteMap } from 'svelte/reactivity';
  import { api } from '$lib/api';
  import { ICONS } from '$lib/icons';
  import { applyAppearance } from '$lib/theme';
  import type {
    CloudSection,
    CloudTrack,
    EngineStatus,
    EqBandInfo,
    FrequencyPoint,
    LyricLine,
    Playlist,
    RepeatMode,
    SortField,
    SpotifyPlaylistDto,
    SpotifyTrackDto,
    TrackInfo,
  } from '$lib/types';

  // Navigation tabs matching mobile structure
  type Tab = 'cloud' | 'songs' | 'albums' | 'artists' | 'favorites' | 'playlists' | 'now_playing' | 'equalizer' | 'queue' | 'settings';
  let activeTab = $state<Tab>('songs');

  // Cloud streaming state
  let cloudSections = $state<CloudSection[]>([]);
  let isCloudLoading = $state(false);
  let isCloudBuffering = $state(false);
  let bufferingSongTitle = $state('');
  let selectedCloudLanguage = $state('All');
  let cloudLanguages = ['All', 'Malayalam', 'Tamil', 'Hindi', 'English', 'Telugu', 'Punjabi', 'Kannada'];
  let cloudDownloadingIds = $state<Set<string>>(new Set());
  let downloadedCloudIds = $state<Set<string>>(new Set());

  // Unified Global Search state
  let globalCloudResults = $state<CloudTrack[]>([]);
  let isGlobalCloudSearching = $state(false);
  let globalSearchDebounceTimer: any = null;
  let searchFilterTab = $state<'all' | 'local' | 'cloud'>('all');

  // Playlists state (matching mobile VeylPlaylistsScreen)
  let playlists = $state<Playlist[]>([]);
  let selectedPlaylistId = $state<string | null>(null);
  let onlineTrackMetaMap = $state<Map<string, TrackInfo>>(new Map());

  // Playlist Modals
  let showCreatePlaylistModal = $state(false);
  let newPlaylistName = $state('');
  let newPlaylistDesc = $state('');

  let showSpotifyImportModal = $state(false);
  let spotifyUrlInput = $state('');
  let isSpotifyImporting = $state(false);
  let spotifyImportError = $state<string | null>(null);

  let trackToAddToPlaylist = $state<TrackInfo | CloudTrack | null>(null);
  let showAddToPlaylistModal = $state(false);

  // Toast feedback
  let toastMessage = $state<string | null>(null);
  let toastTimer: any = null;
  function showToast(msg: string) {
    toastMessage = msg;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastMessage = null;
    }, 3500);
  }

  // Greeting matching mobile VeylCloudHomeScreen
  let cloudGreeting = $derived.by(() => {
    const hour = new Date().getHours();
    if (hour >= 4 && hour < 12) return 'Good morning';
    if (hour >= 12 && hour < 17) return 'Good afternoon';
    if (hour >= 17 && hour < 22) return 'Good evening';
    return 'Late night vibes';
  });

  // Selected Album / Artist for drilled-down view
  let selectedAlbum = $state<string | null>(null);
  let selectedArtist = $state<string | null>(null);

  // Library & Queue state
  let libraryTracks = $state<TrackInfo[]>([]);
  let queue = $state<TrackInfo[]>([]);
  let historyTracks = $state<TrackInfo[]>([]);
  let favorites = $state<Set<string>>(new Set());

  // Queue drag & reorder state
  let draggedQueueIndex = $state<number | null>(null);
  let dragOverIndex = $state<number | null>(null);

  // Spotify-inspired Library Sidebar & Home State
  let librarySidebarFilter = $state<'all' | 'playlists' | 'artists' | 'albums'>('all');
  let librarySearchQuery = $state('');
  let isLibrarySearchOpen = $state(false);
  let isSidebarCollapsed = $state(false);
  let showLibraryAddMenu = $state(false);
  let librarySortOption = $state<'recents' | 'alphabetical' | 'added'>('recents');
  let selectedHomeCategory = $state<'all' | 'music'>('all');
  let homeSortOption = $state<'recent' | 'title' | 'artist' | 'album' | 'duration' | 'hires'>('recent');
  let showHomeSortMenu = $state(false);
  let isSearchSubmitted = $state(false);
  let showSearchSuggestions = $state(false);
  let isEqMode = $state(false);

  let currentTrack = $state<TrackInfo | null>(null);
  let currentArtwork = $state<string | null>(null);
  let searchQuery = $state('');
  let filterFormat = $state<'ALL' | 'FAVORITES' | 'HI-RES' | 'DSD' | 'LOSSLESS'>('ALL');
  let sortField = $state<SortField>('title');
  let sortAscending = $state(true);
  let songViewLayout = $state<'list' | 'grid'>('list');

  // Track artwork cache with SvelteMap for fine-grained reactivity
  let trackArtworkMap = new SvelteMap<string, string | null>();
  const artworkPending = new Set<string>();

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
  const ZERO_SPECTRUM = new Array(16).fill(0);
  let spectrum16 = $state<number[]>(ZERO_SPECTRUM);
  let rmsLeft = $state(0);
  let rmsRight = $state(0);
  let peakLeft = $state(0);
  let peakRight = $state(0);

  // Player playback modes
  let isShuffle = $state(false);
  let repeatMode = $state<RepeatMode>('off');

  // Now Playing view toggle: 'artwork' | 'lyrics'
  let isLyricsMode = $state(false);
  let syncedLyrics = $state<LyricLine[]>([]);
  let isLyricsLoading = $state(false);
  let lyricsError = $state<string | null>(null);
  let lyricsContainerEl = $state<HTMLElement | null>(null);

  // Mobile-matched Appearance: Dark vs Light
  let isDarkMode = $state(true);

  // Equalizer state
  let eqEnabled = $state(true);
  let eqBands = $state<EqBandInfo[]>([]);
  let eqCurve = $state<FrequencyPoint[]>([]);
  let activeEqPreset = $state<string>('Flat');

  // Audio Engine Settings (matching mobile)
  let dsdMode = $state<'pcm' | 'dop'>('pcm');
  let targetSampleRate = $state(44100);
  let isSampleRateAuto = $state(true);
  let gaplessEnabled = $state(true);
  let autoFetchLyrics = $state(true);

  // Telemetry poll interval
  let pollInterval: any = null;

  // EQ Presets - Exact mobile Veyl presets including Harman Curve
  const EQ_PRESETS: Record<string, number[]> = {
    'Flat': [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    'Harman Curve': [6.0, 4.5, 3.0, 1.0, 0.0, 0.5, 2.0, 4.0, 2.0, 0.0],
    'Bass Boost': [9.0, 7.5, 5.0, 2.5, 0.0, 0.0, 0.0, 1.0, 2.5, 3.0],
    'Vocal Presence': [-2.0, -1.0, 0.0, 2.5, 5.0, 6.0, 4.0, 2.0, 0.0, -1.0],
    'Treble Boost': [0.0, 0.0, 0.0, 0.0, 1.0, 2.5, 4.0, 6.5, 8.0, 9.5],
    'Electronic': [5.0, 4.0, 2.0, 0.0, -1.0, 2.0, 3.0, 5.0, 6.0, 7.0],
    'Acoustic': [4.0, 3.0, 1.0, 2.0, 3.0, 3.0, 2.0, 3.0, 4.0, 4.0],
    'Classical': [3.5, 2.5, 1.5, 1.0, -0.5, -0.5, 0.5, 2.0, 3.0, 3.5],
    'Rock Energy': [4.5, 3.0, 1.5, -0.5, -1.5, 0.5, 2.0, 3.5, 4.0, 4.5],
  };

  // Groupings: Albums
  interface AlbumGroup {
    name: string;
    artist: string;
    trackCount: number;
    sampleRateMax: number;
    hasHiRes: boolean;
    firstTrack: TrackInfo;
    tracks: TrackInfo[];
  }

  let albums = $derived.by<AlbumGroup[]>(() => {
    const map = new Map<string, AlbumGroup>();
    for (const t of libraryTracks) {
      const key = `${t.album || 'Unknown Album'}___${t.artist || 'Unknown'}`;
      const existing = map.get(key);
      const isHiRes = t.sample_rate >= 88200 || (t.bit_depth != null && t.bit_depth >= 24);
      if (!existing) {
        map.set(key, {
          name: t.album || 'Unknown Album',
          artist: t.artist || 'Unknown Artist',
          trackCount: 1,
          sampleRateMax: t.sample_rate,
          hasHiRes: isHiRes,
          firstTrack: t,
          tracks: [t],
        });
      } else {
        existing.trackCount++;
        existing.tracks.push(t);
        if (t.sample_rate > existing.sampleRateMax) existing.sampleRateMax = t.sample_rate;
        if (isHiRes) existing.hasHiRes = true;
      }
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  });

  function getAlbumCover(album: AlbumGroup): string | null {
    if (!album) return null;
    if (album.firstTrack && trackArtworkMap.get(album.firstTrack.uri)) {
      return trackArtworkMap.get(album.firstTrack.uri) || null;
    }
    const maxCheck = Math.min(album.tracks.length, 3);
    for (let i = 0; i < maxCheck; i++) {
      const art = trackArtworkMap.get(album.tracks[i].uri);
      if (art) return art;
    }
    return null;
  }

  // Groupings: Artists
  interface ArtistGroup {
    name: string;
    trackCount: number;
    albumCount: number;
    firstTrack: TrackInfo;
    tracks: TrackInfo[];
  }

  let artists = $derived.by<ArtistGroup[]>(() => {
    const map = new Map<string, { trackCount: number; albums: Set<string>; firstTrack: TrackInfo; tracks: TrackInfo[] }>();
    for (const t of libraryTracks) {
      const art = t.artist || 'Unknown Artist';
      const existing = map.get(art);
      if (!existing) {
        map.set(art, {
          trackCount: 1,
          albums: new Set([t.album || 'Unknown Album']),
          firstTrack: t,
          tracks: [t],
        });
      } else {
        existing.trackCount++;
        existing.albums.add(t.album || 'Unknown Album');
        existing.tracks.push(t);
      }
    }
    return Array.from(map.entries())
      .map(([name, data]) => ({
        name,
        trackCount: data.trackCount,
        albumCount: data.albums.size,
        firstTrack: data.firstTrack,
        tracks: data.tracks,
      }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  function getArtistAvatar(art: ArtistGroup): string | null {
    if (!art) return null;
    if (art.firstTrack && trackArtworkMap.get(art.firstTrack.uri)) {
      return trackArtworkMap.get(art.firstTrack.uri) || null;
    }
    const maxCheck = Math.min(art.tracks.length, 3);
    for (let i = 0; i < maxCheck; i++) {
      const a = trackArtworkMap.get(art.tracks[i].uri);
      if (a) return a;
    }
    return null;
  }

  // Spotify "Your Library" filtered and sorted item list
  let filteredLibraryItems = $derived.by(() => {
    const q = librarySearchQuery.trim().toLowerCase();
    const filter = librarySidebarFilter;
    const items: Array<{
      type: 'liked' | 'playlist' | 'artist' | 'album';
      id: string;
      title: string;
      subtitle: string;
      artwork?: string | null;
      isCircular?: boolean;
      data?: any;
    }> = [];

    // 1. Liked Songs (shown if filter is 'all' or 'playlists')
    if ((filter === 'all' || filter === 'playlists') && (!q || 'liked songs'.includes(q) || 'favorites'.includes(q))) {
      items.push({
        type: 'liked',
        id: 'liked_songs',
        title: 'Liked Songs',
        subtitle: `Playlist • ${favorites.size} songs`,
      });
    }

    // 2. Playlists
    if (filter === 'all' || filter === 'playlists') {
      for (const pl of playlists) {
        if (!q || pl.name.toLowerCase().includes(q) || (pl.description && pl.description.toLowerCase().includes(q))) {
          const art = pl.coverUrl || (pl.trackUris.length > 0 ? trackArtworkMap.get(pl.trackUris[0]) : null);
          items.push({
            type: 'playlist',
            id: pl.id,
            title: pl.name,
            subtitle: `Playlist • ${pl.trackUris.length} tracks`,
            artwork: art,
            data: pl,
          });
        }
      }
    }

    // 3. Artists
    if (filter === 'all' || filter === 'artists') {
      for (const art of artists) {
        if (!q || art.name.toLowerCase().includes(q)) {
          items.push({
            type: 'artist',
            id: 'art_' + art.name,
            title: art.name,
            subtitle: `Artist • ${art.trackCount} tracks`,
            artwork: getArtistAvatar(art),
            isCircular: true,
            data: art,
          });
        }
      }
    }

    // 4. Albums
    if (filter === 'all' || filter === 'albums') {
      for (const al of albums) {
        if (!q || al.name.toLowerCase().includes(q) || al.artist.toLowerCase().includes(q)) {
          items.push({
            type: 'album',
            id: 'alb_' + al.name,
            title: al.name,
            subtitle: `Album • ${al.artist}`,
            artwork: getAlbumCover(al),
            data: al,
          });
        }
      }
    }

    // Sorting
    if (librarySortOption === 'alphabetical') {
      items.sort((a, b) => {
        if (a.type === 'liked') return -1;
        if (b.type === 'liked') return 1;
        return a.title.localeCompare(b.title);
      });
    }

    return items;
  });

  // Spotify-style Popular Radio artist cards
  const popularRadioSeeds = [
    { name: 'Arijit Singh', subtitle: 'With Shashwat Sachdev, Pritam, Irshad Kamil and more', color: 'linear-gradient(135deg, #e07a5f 0%, #3d405b 100%)' },
    { name: 'Sidhu Moose Wala', subtitle: 'With Shubh, Sharry Mann, Jxggi and more', color: 'linear-gradient(135deg, #3d5a80 0%, #293241 100%)' },
    { name: 'Kishore Kumar', subtitle: 'With Mukesh, Hemant Kumar, Kalyanji-Anandji and more', color: 'linear-gradient(135deg, #e63946 0%, #1d3557 100%)' },
    { name: 'Diljit Dosanjh', subtitle: 'With Karan Aujla, AP Dhillon, Harrdy Sandhu and more', color: 'linear-gradient(135deg, #2a9d8f 0%, #264653 100%)' },
    { name: 'A.R. Rahman', subtitle: 'With Hariharan, Anirudh Ravichander, Shankar Mahadevan and more', color: 'linear-gradient(135deg, #e9c46a 0%, #f4a261 100%)' },
    { name: 'Sushin Shyam', subtitle: 'With Dabzee, Nazriya Nazim, Vedan and more', color: 'linear-gradient(135deg, #6b705c 0%, #3f4238 100%)' },
  ];

  function handlePlayAlbum(albumName: string, shuffle: boolean = false) {
    const tracks = libraryTracks.filter((t) => (t.album || 'Unknown Album') === albumName);
    if (tracks.length === 0) return;
    queue = shuffle ? [...tracks].sort(() => Math.random() - 0.5) : [...tracks];
    handlePlayTrack(queue[0]);
  }

  function handlePlayArtist(artistName: string, shuffle: boolean = false) {
    const tracks = libraryTracks.filter((t) => (t.artist || 'Unknown Artist') === artistName);
    if (tracks.length === 0) return;
    queue = shuffle ? [...tracks].sort(() => Math.random() - 0.5) : [...tracks];
    handlePlayTrack(queue[0]);
  }
  let filteredTracks = $derived.by(() => {
    let result = libraryTracks;

    // Drilled-down filter if selected
    if (selectedAlbum) {
      result = result.filter((t) => (t.album || 'Unknown Album') === selectedAlbum);
    } else if (selectedArtist) {
      result = result.filter((t) => (t.artist || 'Unknown Artist') === selectedArtist);
    }

    // Favorites tab
    if (activeTab === 'favorites') {
      result = result.filter((t) => favorites.has(t.uri));
    }

    // Search query filter
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          t.artist.toLowerCase().includes(q) ||
          t.album.toLowerCase().includes(q) ||
          t.format_name.toLowerCase().includes(q)
      );
    }

    // Format filter matching mobile
    if (filterFormat === 'FAVORITES') {
      result = result.filter((t) => favorites.has(t.uri));
    } else if (filterFormat === 'HI-RES') {
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

    // Sorting
    return [...result].sort((a, b) => {
      let cmp = 0;
      if (sortField === 'title') cmp = a.title.localeCompare(b.title);
      else if (sortField === 'artist') cmp = a.artist.localeCompare(b.artist);
      else if (sortField === 'album') cmp = a.album.localeCompare(b.album);
      else if (sortField === 'duration') cmp = a.duration_seconds - b.duration_seconds;
      else if (sortField === 'sample_rate') cmp = a.sample_rate - b.sample_rate;
      else if (sortField === 'bit_depth') cmp = (a.bit_depth || 16) - (b.bit_depth || 16);
      return sortAscending ? cmp : -cmp;
    });
  });

  // Global search filtered local tracks (all library tracks matching query)
  let globalLocalResults = $derived.by(() => {
    if (!searchQuery.trim()) return [];
    const q = searchQuery.toLowerCase();
    return libraryTracks.filter(
      (t) =>
        t.title.toLowerCase().includes(q) ||
        (t.artist && t.artist.toLowerCase().includes(q)) ||
        (t.album && t.album.toLowerCase().includes(q)) ||
        (t.format_name && t.format_name.toLowerCase().includes(q))
    );
  });

  // High-performance concurrency-controlled artwork loading pipeline
  const artworkQueue: string[] = [];
  let activeArtworkWorkers = 0;
  const MAX_CONCURRENT_ARTWORK = 3;

  function enqueueTrackArtwork(uri: string) {
    if (!uri || trackArtworkMap.has(uri) || artworkPending.has(uri) || uri.startsWith('http')) return;
    artworkPending.add(uri);
    artworkQueue.push(uri);
    processArtworkQueue();
  }

  function processArtworkQueue() {
    while (activeArtworkWorkers < MAX_CONCURRENT_ARTWORK && artworkQueue.length > 0) {
      const uri = artworkQueue.shift();
      if (!uri) break;
      activeArtworkWorkers++;
      api.getTrackArtwork(uri)
        .then((art) => {
          if (art) {
            trackArtworkMap.set(uri, art);
          } else {
            trackArtworkMap.set(uri, null);
          }
        })
        .catch(() => {
          trackArtworkMap.set(uri, null);
        })
        .finally(() => {
          artworkPending.delete(uri);
          activeArtworkWorkers--;
          setTimeout(processArtworkQueue, 24);
        });
    }
  }

  // Load artwork for all library tracks in smooth background batches
  $effect(() => {
    if (libraryTracks.length > 0) {
      for (const t of libraryTracks) {
        enqueueTrackArtwork(t.uri);
      }
    }
  });

  // Prioritize artwork for visible/filtered tracks
  $effect(() => {
    if (filteredTracks.length > 0) {
      for (const t of filteredTracks) {
        enqueueTrackArtwork(t.uri);
      }
    }
  });

  // Ensure album covers are loaded when on Albums tab
  $effect(() => {
    if (activeTab === 'albums' && albums.length > 0) {
      for (const al of albums) {
        if (!getAlbumCover(al)) {
          for (const t of al.tracks) {
            enqueueTrackArtwork(t.uri);
          }
        }
      }
    }
  });

  // Ensure artist avatars are loaded when on Artists tab
  $effect(() => {
    if (activeTab === 'artists' && artists.length > 0) {
      for (const art of artists) {
        if (!getArtistAvatar(art)) {
          for (const t of art.tracks) {
            enqueueTrackArtwork(t.uri);
          }
        }
      }
    }
  });

  // Online track artwork resolver (iTunes 600x600 & JioSaavn fallback)
  function requestOnlineTrackArtwork(uri: string, title: string, artist: string) {
    if (!uri || trackArtworkMap.has(uri) || artworkPending.has(uri)) return;
    artworkPending.add(uri);
    api.resolveTrackArtwork(title, artist)
      .then((art) => {
        if (art) trackArtworkMap.set(uri, art);
        else trackArtworkMap.set(uri, null);
      })
      .catch(() => {
        trackArtworkMap.set(uri, null);
      })
      .finally(() => {
        artworkPending.delete(uri);
      });
  }

  // Debounced Unified Online & Local Search (prevents lag and fires online search)
  let searchDebounceTimeout: any = null;

  $effect(() => {
    const q = searchQuery.trim();
    if (searchDebounceTimeout) clearTimeout(searchDebounceTimeout);

    if (!q) {
      globalCloudResults = [];
      isGlobalCloudSearching = false;
      return;
    }

    isGlobalCloudSearching = true;
    searchDebounceTimeout = setTimeout(async () => {
      try {
        const results = await api.searchCloudTracks(q);
        if (searchQuery.trim() === q) {
          globalCloudResults = results || [];
        }
      } catch (err) {
        console.error('Online search failed:', err);
        if (searchQuery.trim() === q) {
          globalCloudResults = [];
        }
      } finally {
        if (searchQuery.trim() === q) {
          isGlobalCloudSearching = false;
        }
      }
    }, 280);
  });

  // Queue Drag & Drop and manual reordering
  function handleQueueDragStart(e: DragEvent, index: number) {
    draggedQueueIndex = index;
    if (e.dataTransfer) {
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', index.toString());
    }
  }

  function handleQueueDragOver(e: DragEvent, index: number) {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'move';
    }
    dragOverIndex = index;
  }

  function handleQueueDrop(e: DragEvent, dropIndex: number) {
    e.preventDefault();
    e.stopPropagation();
    let fromIndex = draggedQueueIndex;
    if (fromIndex === null && e.dataTransfer) {
      const text = e.dataTransfer.getData('text/plain');
      if (text) {
        const parsed = parseInt(text, 10);
        if (!isNaN(parsed)) fromIndex = parsed;
      }
    }
    if (fromIndex !== null && fromIndex !== dropIndex && fromIndex >= 0 && fromIndex < queue.length && dropIndex >= 0 && dropIndex < queue.length) {
      const newQueue = [...queue];
      const [removed] = newQueue.splice(fromIndex, 1);
      newQueue.splice(dropIndex, 0, removed);
      queue = newQueue;
    }
    draggedQueueIndex = null;
    dragOverIndex = null;
  }

  function handleQueueDragEnd() {
    draggedQueueIndex = null;
    dragOverIndex = null;
  }

  function moveQueueItem(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= queue.length) return;
    const newQueue = [...queue];
    const [item] = newQueue.splice(index, 1);
    newQueue.splice(targetIndex, 0, item);
    queue = newQueue;
  }

  function cleanCodecName(name: string): string {
    if (!name) return 'FLAC';
    const upper = name.toUpperCase();
    if (upper.includes('8192') || upper.includes('FLAC')) return 'FLAC';
    if (upper.includes('4096') || upper.includes('ALAC')) return 'ALAC';
    if (upper.includes('MP3') || upper.includes('MPEG')) return 'MP3';
    if (upper.includes('AAC')) return 'AAC';
    if (upper.includes('OPUS')) return 'OPUS';
    if (upper.includes('VORBIS') || upper.includes('OGG')) return 'OGG';
    if (upper.includes('WAV') || upper.includes('PCM')) return 'PCM';
    if (upper.includes('DSD')) return 'DSD';
    const match = upper.match(/CODECTYPE\((.*?)\)/);
    if (match) return 'FLAC';
    return upper.replace(/[^A-Z0-9]/g, '') || 'FLAC';
  }

  // Format spec string matching mobile (Clean audiophile label)
  let formatBadge = $derived.by(() => {
    const clean = cleanCodecName(codecName);
    if (clean.includes('DSD')) return 'DSD NATIVE';
    const khz = (sampleRate / 1000).toFixed(1);
    const bits = bitDepth ? `${bitDepth}b` : '16b';
    return `${clean} ${khz}kHz / ${bits}`;
  });

  // Instant Search Suggestions (for top popover dropdown)
  let searchSuggestions = $derived.by(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return { local: [], cloud: [] };
    const local = libraryTracks
      .filter((t) => t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q) || (t.album && t.album.toLowerCase().includes(q)))
      .slice(0, 4);
    const cloud = globalCloudResults.slice(0, 4);
    return { local, cloud };
  });

  // Fisher-Yates Shuffle from current position across current category
  function handleShuffleCurrentView() {
    let pool: TrackInfo[] = [];
    if (selectedHomeCategory === 'music') {
      pool = [...libraryTracks];
    } else {
      pool = [...libraryTracks];
      for (const sec of cloudSections) {
        for (const item of sec.items) {
          if (!pool.some((p) => p.uri === item.stream_url || p.title === item.title)) {
            pool.push({
              uri: item.stream_url,
              title: item.title,
              artist: item.artist,
              album: 'Online Discovery',
              duration_seconds: item.duration_seconds || 210,
              format_name: '320k',
              sample_rate: 44100,
              bit_depth: 16,
              channels: 2,
              bitrate: 320,
              file_size_bytes: 0,
            });
          }
        }
      }
    }

    if (pool.length === 0) {
      toastMessage = 'No tracks available to shuffle';
      setTimeout(() => (toastMessage = null), 2500);
      return;
    }

    // High quality Fisher-Yates shuffle
    for (let i = pool.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [pool[i], pool[j]] = [pool[j], pool[i]];
    }

    // Preserve currently playing track at top if it is in the pool
    if (currentTrack) {
      const idx = pool.findIndex((t) => t.uri === currentTrack?.uri);
      if (idx > -1) {
        const [curr] = pool.splice(idx, 1);
        pool.unshift(curr);
      }
    }

    isShuffle = true;
    queue = pool;
    handlePlayTrack(queue[0]);
    toastMessage = `Shuffled ${pool.length} tracks`;
    setTimeout(() => (toastMessage = null), 2500);
  }

  // Sorted Albums for Home Screen
  let sortedHomeAlbums = $derived.by(() => {
    let list = [...albums];
    if (homeSortOption === 'title') {
      list.sort((a, b) => a.name.localeCompare(b.name));
    } else if (homeSortOption === 'artist') {
      list.sort((a, b) => (a.artist || '').localeCompare(b.artist || ''));
    } else if (homeSortOption === 'hires') {
      list.sort((a, b) => {
        const aHires = a.tracks.some((t) => t.sample_rate >= 88200 || (t.bit_depth && t.bit_depth >= 24));
        const bHires = b.tracks.some((t) => t.sample_rate >= 88200 || (t.bit_depth && t.bit_depth >= 24));
        return (bHires ? 1 : 0) - (aHires ? 1 : 0);
      });
    } else if (homeSortOption === 'duration') {
      list.sort((a, b) => {
        const aDur = a.tracks.reduce((sum, t) => sum + t.duration_seconds, 0);
        const bDur = b.tracks.reduce((sum, t) => sum + t.duration_seconds, 0);
        return bDur - aDur;
      });
    }
    return list;
  });

  // Fetch real synchronized lyrics via local .lrc or LRCLIB
  async function loadLyricsForTrack(track: TrackInfo) {
    if (!autoFetchLyrics) return;
    isLyricsLoading = true;
    lyricsError = null;
    syncedLyrics = [];
    try {
      const lines = await api.getSyncedLyrics(
        track.uri,
        track.title,
        track.artist,
        track.album,
        track.duration_seconds
      );
      syncedLyrics = lines || [];
      if (!lines || lines.length === 0) {
        lyricsError = 'No synchronized lyrics found for this track';
      }
    } catch (e: any) {
      syncedLyrics = [];
      lyricsError = 'Could not load synchronized lyrics';
    } finally {
      isLyricsLoading = false;
    }
  }

  let activeLyricIndex = $derived.by(() => {
    if (syncedLyrics.length === 0) return -1;
    let idx = -1;
    for (let i = 0; i < syncedLyrics.length; i++) {
      if (positionSec >= syncedLyrics[i].sec) {
        idx = i;
      } else {
        break;
      }
    }
    return idx === -1 ? 0 : idx;
  });

  // Auto-scroll lyrics smoothly to keep active line vertically centered
  $effect(() => {
    if (isLyricsMode && activeLyricIndex >= 0 && lyricsContainerEl) {
      const activeEl = lyricsContainerEl.querySelector(`[data-lyric-index="${activeLyricIndex}"]`);
      if (activeEl) {
        activeEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  });

  function formatTime(sec: number): string {
    if (!sec || isNaN(sec) || sec < 0) return '0:00';
    const totalSec = Math.floor(sec);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  let isSyncingEngine = false;
  let pollTimeout: any = null;
  let isComponentAlive = true;

  async function syncEngineStatus() {
    if (isSyncingEngine || !isComponentAlive) return;
    isSyncingEngine = true;
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
        if (status.state === 'Playing' && status.metrics.spectrum_16) {
          spectrum16 = status.metrics.spectrum_16;
        } else if (spectrum16 !== ZERO_SPECTRUM && spectrum16.some((v) => v !== 0)) {
          spectrum16 = ZERO_SPECTRUM;
        }
        rmsLeft = status.metrics.rms_left ?? 0;
        rmsRight = status.metrics.rms_right ?? 0;
        peakLeft = status.metrics.peak_left ?? 0;
        peakRight = status.metrics.peak_right ?? 0;
      }

      if (status.current_track && (!currentTrack || currentTrack.uri !== status.current_track.uri)) {
        currentTrack = status.current_track;
        loadArtwork(currentTrack.uri);
        loadLyricsForTrack(currentTrack);
        if (isSampleRateAuto && currentTrack.sample_rate && currentTrack.sample_rate !== targetSampleRate) {
          targetSampleRate = currentTrack.sample_rate;
          api.reconfigureOutput(currentTrack.sample_rate, 2, isBitPerfect).catch(() => {});
        }
        recordHistory(currentTrack);
      }

      if (status.state === 'Ended') {
        handleTrackEnded();
      }
    } catch {
      // background poll silently handled
    } finally {
      isSyncingEngine = false;
    }
  }

  function scheduleEnginePoll() {
    if (!isComponentAlive) return;
    if (pollTimeout) clearTimeout(pollTimeout);
    const intervalMs = isPlaying ? 220 : 600;
    pollTimeout = setTimeout(async () => {
      await syncEngineStatus();
      scheduleEnginePoll();
    }, intervalMs);
  }

  function handleTrackEnded() {
    if (repeatMode === 'one' && currentTrack) {
      handlePlayTrack(currentTrack);
      return;
    }
    playNextInQueue();
  }

  async function loadCloudFeed() {
    isCloudLoading = true;
    try {
      const langs = selectedCloudLanguage === 'All'
        ? ['Malayalam', 'Tamil', 'Hindi', 'English']
        : [selectedCloudLanguage];
      cloudSections = await api.getCloudRecommendations(langs, ['A.R. Rahman', 'Sushin Shyam', 'Anirudh Ravichander', 'Arijit Singh']);
    } catch (e) {
      console.error('Failed to load cloud feed:', e);
    } finally {
      isCloudLoading = false;
    }
  }

  // Title & Artist Normalization matching mobile AudioEngineController
  function normalizeTitle(raw: string): string {
    return raw
      .toLowerCase()
      .replace(/[\u2018\u2019]/g, "'")
      .replace(/[\u201C\u201D]/g, '"')
      .replace(/[\u2013\u2014]/g, '-')
      .replace(/^\d{1,3}[\s.\-_]+/, '')
      .replace(/\s*[\(\[](?:feat\.?|ft\.?|with)\s+[^)\]]+[\)\]]/gi, '')
      .replace(/\s+-\s+(?:feat\.?|ft\.?|with)\s+.*$/gi, '')
      .replace(/\s*[\(\[](?:remastered|remaster|radio edit|original mix|acoustic|live|bonus track|deluxe|version|edit|mono|stereo|anniversary)[^)\]]*[\)\]]/gi, '')
      .replace(/\s+-\s+(?:remastered|remaster|radio edit|original mix|acoustic|live|bonus track|deluxe|anniversary).*$/gi, '')
      .replace(/\s*[\(\[]from\s+[^)\]]+[\)\]]/gi, '')
      .replace(/\s+-\s+from\s+.*$/gi, '')
      .replace(/\.(mp3|flac|m4a|wav|aac|ogg|opus|dsf|dff)$/gi, '')
      .replace(/[^\p{L}\p{Nd}\s]/gu, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function normalizeArtist(raw: string): string {
    return raw
      .toLowerCase()
      .replace(/[\u2018\u2019]/g, "'")
      .replace(/\s*(?:feat\.?|ft\.?|with|&|,|\/|x)\s*/gi, ' ')
      .replace(/[^\p{L}\p{Nd}\s]/gu, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  // Playlists helper & resolution
  function savePlaylists() {
    try {
      localStorage.setItem('veyl_playlists', JSON.stringify(playlists));
    } catch {}
  }

  function saveOnlineMeta() {
    try {
      const arr = Array.from(onlineTrackMetaMap.entries());
      localStorage.setItem('veyl_online_meta', JSON.stringify(arr));
    } catch {}
  }

  function resolveTrack(uri: string): TrackInfo {
    const local = libraryTracks.find((t) => t.uri === uri);
    if (local) return local;

    const online = onlineTrackMetaMap.get(uri);
    if (online) return online;

    const fileName = uri.split(/[/\\]/).pop() || 'Unknown Track';
    return {
      uri,
      title: fileName.replace(/\.[^/.]+$/, ''),
      artist: 'Unknown Artist',
      album: 'Playlist',
      duration_seconds: 0,
      sample_rate: 44100,
      bit_depth: 16,
      channels: 2,
      format_name: uri.startsWith('http') || uri.startsWith('online://') ? 'Cloud' : 'Audio',
      has_artwork: false,
    };
  }

  let currentSelectedPlaylist = $derived.by(() => {
    if (!selectedPlaylistId) return null;
    return playlists.find((p) => p.id === selectedPlaylistId) || null;
  });

  let selectedPlaylistTracks = $derived.by<TrackInfo[]>(() => {
    if (!currentSelectedPlaylist) return [];
    return currentSelectedPlaylist.trackUris.map((uri) => resolveTrack(uri));
  });

  let selectedPlaylistDuration = $derived.by(() => {
    return selectedPlaylistTracks.reduce((acc, t) => acc + (t.duration_seconds || 0), 0);
  });

  $effect(() => {
    if (selectedPlaylistTracks.length > 0) {
      const plCover = currentSelectedPlaylist?.coverUrl;
      for (const t of selectedPlaylistTracks) {
        const currentArt = trackArtworkMap.get(t.uri);
        if (t.uri.startsWith('online://')) {
          if (!currentArt || (plCover && currentArt === plCover)) {
            requestOnlineTrackArtwork(t.uri, t.title, t.artist);
          }
        } else if (!t.uri.startsWith('http')) {
          enqueueTrackArtwork(t.uri);
        }
      }
    }
  });

  function handleCreatePlaylistSubmit() {
    const name = newPlaylistName.trim();
    if (!name) return;
    const newPl: Playlist = {
      id: 'pl_' + Date.now(),
      name,
      description: newPlaylistDesc.trim() || undefined,
      trackUris: [],
      created_at: Date.now(),
    };
    playlists = [newPl, ...playlists];
    savePlaylists();
    showCreatePlaylistModal = false;
    newPlaylistName = '';
    newPlaylistDesc = '';
    selectedPlaylistId = newPl.id;
    activeTab = 'playlists';
    showToast(`Created playlist "${newPl.name}"`);
  }

  function handleDeletePlaylist(id: string) {
    const pl = playlists.find((p) => p.id === id);
    playlists = playlists.filter((p) => p.id !== id);
    savePlaylists();
    if (selectedPlaylistId === id) {
      selectedPlaylistId = null;
    }
    showToast(`Deleted playlist "${pl?.name || ''}"`);
  }

  function handleRemoveTrackFromPlaylist(index: number) {
    if (!currentSelectedPlaylist) return;
    currentSelectedPlaylist.trackUris = currentSelectedPlaylist.trackUris.filter((_, i) => i !== index);
    playlists = [...playlists];
    savePlaylists();
    showToast('Removed track from playlist');
  }

  function handlePlayPlaylist(playlist: Playlist, shuffle = false) {
    const tracks = playlist.trackUris.map((u) => resolveTrack(u));
    if (tracks.length === 0) {
      showToast('Playlist is empty');
      return;
    }
    let order = [...tracks];
    if (shuffle) {
      order.sort(() => Math.random() - 0.5);
    }
    queue = order;
    handlePlayTrack(order[0]);
    showToast(`${shuffle ? 'Shuffling' : 'Playing'} "${playlist.name}"`);
  }

  function openAddToPlaylistModal(track: TrackInfo | CloudTrack) {
    trackToAddToPlaylist = track;
    showAddToPlaylistModal = true;
  }

  function addTrackToSpecificPlaylist(playlistId: string) {
    if (!trackToAddToPlaylist) return;
    const pl = playlists.find((p) => p.id === playlistId);
    if (!pl) return;

    let uri = '';
    if ('stream_url' in trackToAddToPlaylist) {
      const ct = trackToAddToPlaylist as CloudTrack;
      uri = ct.stream_url;
      const converted: TrackInfo = {
        uri: ct.stream_url,
        title: ct.title,
        artist: ct.artist,
        album: ct.album || 'Cloud Stream',
        duration_seconds: ct.duration_seconds,
        sample_rate: 44100,
        bit_depth: 16,
        channels: 2,
        format_name: 'AAC 320k (Cloud)',
        has_artwork: !!ct.thumbnail_url,
      };
      onlineTrackMetaMap.set(ct.stream_url, converted);
      if (ct.thumbnail_url) {
        trackArtworkMap.set(ct.stream_url, ct.thumbnail_url);
      }
    } else {
      uri = (trackToAddToPlaylist as TrackInfo).uri;
    }

    if (!pl.trackUris.includes(uri)) {
      pl.trackUris = [...pl.trackUris, uri];
      playlists = [...playlists];
      savePlaylists();
      saveOnlineMeta();
      showToast(`Added to "${pl.name}"`);
    } else {
      showToast(`Already in "${pl.name}"`);
    }
    showAddToPlaylistModal = false;
    trackToAddToPlaylist = null;
  }

  async function handleSpotifyImport() {
    const url = spotifyUrlInput.trim();
    if (!url) return;
    isSpotifyImporting = true;
    spotifyImportError = null;
    try {
      const data = await api.fetchSpotifyPlaylist(url);
      if (!data.tracks || data.tracks.length === 0) {
        throw new Error('No tracks found in Spotify playlist or album URL.');
      }

      const importedUris: string[] = [];
      let localMatches = 0;
      let cloudMatches = 0;

      for (const spTrack of data.tracks) {
        const spNormTitle = normalizeTitle(spTrack.title);
        const spNormArtist = normalizeArtist(spTrack.artist);
        const spArtistWords = spNormArtist.split(' ').filter((w) => w.length > 2);

        // 4-tier matching against local library
        const matched =
          libraryTracks.find(
            (lib) =>
              lib.title.toLowerCase() === spTrack.title.toLowerCase() &&
              (lib.artist.toLowerCase().includes(spTrack.artist.toLowerCase()) ||
                spTrack.artist.toLowerCase().includes(lib.artist.toLowerCase()))
          ) ||
          libraryTracks.find((lib) => {
            const libNorm = normalizeTitle(lib.title);
            if (libNorm && libNorm === spNormTitle) {
              const libArt = normalizeArtist(lib.artist || '');
              return (
                spArtistWords.length === 0 ||
                spArtistWords.some((w) => libArt.includes(w)) ||
                !libArt ||
                libArt === 'unknown artist'
              );
            }
            return false;
          }) ||
          libraryTracks.find((lib) => {
            const fName = lib.uri.split(/[/\\]/).pop()?.replace(/\.[^/.]+$/, '') || '';
            const normFileName = normalizeTitle(fName);
            return (
              normFileName === spNormTitle ||
              (normFileName.includes(spNormTitle) && spArtistWords.some((w) => normFileName.includes(w)))
            );
          }) ||
          libraryTracks.find((lib) => {
            const libNorm = normalizeTitle(lib.title);
            return !!libNorm && libNorm === spNormTitle;
          });

        if (matched) {
          importedUris.push(matched.uri);
          localMatches++;
        } else {
          // Add online cloud streaming track (matches mobile online://spotify_hash architecture)
          const hash = Math.abs(
            `${spTrack.title}_${spTrack.artist}`
              .split('')
              .reduce((acc, c) => (acc << 5) - acc + c.charCodeAt(0), 0)
          ).toString(16);
          const onlineUri = `online://spotify_${hash}`;
          const onlineTrack: TrackInfo = {
            uri: onlineUri,
            title: spTrack.title,
            artist: spTrack.artist,
            album: data.title,
            duration_seconds: spTrack.duration_seconds,
            sample_rate: 44100,
            bit_depth: 16,
            channels: 2,
            format_name: 'AAC 320k (Cloud)',
            has_artwork: !!spTrack.cover_url,
          };
          onlineTrackMetaMap.set(onlineUri, onlineTrack);
          if (spTrack.cover_url) {
            trackArtworkMap.set(onlineUri, spTrack.cover_url);
          } else {
            requestOnlineTrackArtwork(onlineUri, spTrack.title, spTrack.artist);
          }
          importedUris.push(onlineUri);
          cloudMatches++;
        }
      }

      const newPlaylist: Playlist = {
        id: 'pl_' + Date.now(),
        name: data.title || 'Spotify Playlist',
        description: `Imported from Spotify (${localMatches} local, ${cloudMatches} cloud)`,
        coverUrl: data.cover_url || null,
        trackUris: importedUris,
        created_at: Date.now(),
      };

      playlists = [newPlaylist, ...playlists];
      savePlaylists();
      saveOnlineMeta();

      showSpotifyImportModal = false;
      spotifyUrlInput = '';
      selectedPlaylistId = newPlaylist.id;
      activeTab = 'playlists';
      showToast(`Imported "${newPlaylist.name}": ${localMatches} local, ${cloudMatches} cloud`);
    } catch (err: any) {
      console.error('Spotify import error:', err);
      spotifyImportError = err.message || String(err);
    } finally {
      isSpotifyImporting = false;
    }
  }

  async function handleExportM3u(playlist: Playlist) {
    try {
      const safeName = playlist.name.replace(/[^a-zA-Z0-9_\- ]/g, '_').trim() || 'playlist';
      const filePath = await api.saveM3uFile(`${safeName}.m3u8`);
      if (!filePath) return;

      let content = '#EXTM3U\n';
      content += `#PLAYLIST:${playlist.name}\n`;
      for (const uri of playlist.trackUris) {
        const tr = resolveTrack(uri);
        const dur = Math.round(tr.duration_seconds || 0);
        const artist = (tr.artist || 'Unknown Artist').replace(/[\r\n]/g, ' ');
        const title = (tr.title || 'Unknown Title').replace(/[\r\n]/g, ' ');
        content += `#EXTINF:${dur},${artist} - ${title}\n`;
        if (tr.album) {
          content += `#EXTALB:${tr.album.replace(/[\r\n]/g, ' ')}\n`;
        }
        content += `${uri}\n`;
      }
      await api.writeTextFile(filePath, content);
      showToast(`Exported "${playlist.name}" to M3U8`);
    } catch (err: any) {
      console.error('Export error:', err);
      showToast(`Failed to export M3U: ${err.message || err}`);
    }
  }

  async function handleImportM3u() {
    try {
      const filePath = await api.pickM3uFile();
      if (!filePath) return;

      const raw = await api.readTextFile(filePath);
      const lines = raw.replace(/^\uFEFF/, '').split(/\r?\n/);
      let playlistName = filePath.split(/[/\\]/).pop()?.replace(/\.[^/.]+$/, '') || 'Imported Playlist';
      const importedUris: string[] = [];

      let pendingTitle: string | null = null;
      let pendingArtist: string | null = null;
      let pendingAlbum: string | null = null;
      let pendingDuration = 0;

      for (const rawLine of lines) {
        const line = rawLine.trim();
        if (!line) continue;

        if (line.startsWith('#')) {
          if (line.toUpperCase().startsWith('#PLAYLIST:')) {
            playlistName = line.substring(10).trim() || playlistName;
          } else if (line.toUpperCase().startsWith('#EXTINF:')) {
            const info = line.substring(8).trim();
            const commaIdx = info.indexOf(',');
            if (commaIdx !== -1) {
              pendingDuration = parseFloat(info.substring(0, commaIdx).trim()) || 0;
              const rest = info.substring(commaIdx + 1).trim();
              const dashIdx = rest.indexOf(' - ');
              if (dashIdx !== -1) {
                pendingArtist = rest.substring(0, dashIdx).trim();
                pendingTitle = rest.substring(dashIdx + 3).trim();
              } else {
                pendingTitle = rest;
              }
            }
          } else if (line.toUpperCase().startsWith('#EXTALB:')) {
            pendingAlbum = line.substring(8).trim();
          }
          continue;
        }

        const trackUri = line;
        const fName = trackUri.split(/[/\\]/).pop() || '';

        // Match against local library
        const matched = libraryTracks.find(
          (lib) =>
            lib.uri.toLowerCase() === trackUri.toLowerCase() ||
            (fName && lib.uri.toLowerCase().endsWith(fName.toLowerCase())) ||
            (pendingTitle &&
              lib.title.toLowerCase() === pendingTitle.toLowerCase() &&
              (!pendingArtist || lib.artist.toLowerCase().includes(pendingArtist.toLowerCase())))
        );

        if (matched) {
          importedUris.push(matched.uri);
        } else {
          if (pendingTitle) {
            const onlineUri = trackUri.startsWith('http')
              ? trackUri
              : `online://m3u_${Math.abs(pendingTitle.split('').reduce((a, b) => (a << 5) - a + b.charCodeAt(0), 0))}`;
            const fallbackTrack: TrackInfo = {
              uri: onlineUri,
              title: pendingTitle,
              artist: pendingArtist || 'Unknown Artist',
              album: pendingAlbum || '',
              duration_seconds: pendingDuration,
              sample_rate: 44100,
              bit_depth: 16,
              channels: 2,
              format_name: 'Audio',
              has_artwork: false,
            };
            onlineTrackMetaMap.set(onlineUri, fallbackTrack);
            importedUris.push(onlineUri);
          } else {
            importedUris.push(trackUri);
          }
        }

        pendingTitle = null;
        pendingArtist = null;
        pendingAlbum = null;
        pendingDuration = 0;
      }

      if (importedUris.length === 0) {
        showToast('No tracks found in playlist file');
        return;
      }

      const newPl: Playlist = {
        id: 'pl_' + Date.now(),
        name: playlistName,
        description: `Imported from M3U (${importedUris.length} tracks)`,
        trackUris: importedUris,
        created_at: Date.now(),
      };

      playlists = [newPl, ...playlists];
      savePlaylists();
      saveOnlineMeta();
      selectedPlaylistId = newPl.id;
      activeTab = 'playlists';
      showToast(`Imported "${playlistName}" (${importedUris.length} tracks)`);
    } catch (err: any) {
      console.error('M3U import error:', err);
      showToast(`Failed to import M3U: ${err.message || err}`);
    }
  }

  async function handlePlayCloudTrack(track: CloudTrack, contextList?: CloudTrack[]) {
    isCloudBuffering = true;
    bufferingSongTitle = track.title;
    try {
      await api.playCloudTrack(track);
      const converted: TrackInfo = {
        uri: track.stream_url,
        title: track.title,
        artist: track.artist,
        album: track.album || 'Cloud Stream',
        duration_seconds: track.duration_seconds,
        sample_rate: 44100,
        bit_depth: 16,
        channels: 2,
        format_name: 'AAC 320k (Studio Master)',
        has_artwork: !!track.thumbnail_url,
      };
      currentTrack = converted;
      currentArtwork = track.thumbnail_url || null;
      isPlaying = true;
      loadLyricsForTrack(converted);
      recordHistory(converted);

      if (contextList && contextList.length > 0) {
        queue = contextList.map((t) => ({
          uri: t.stream_url,
          title: t.title,
          artist: t.artist,
          album: t.album || 'Cloud Stream',
          duration_seconds: t.duration_seconds,
          sample_rate: 44100,
          bit_depth: 16,
          channels: 2,
          format_name: 'AAC 320k (Studio Master)',
          has_artwork: !!t.thumbnail_url,
        }));
      } else if (!queue.some((t) => t.uri === converted.uri)) {
        queue = [...queue, converted];
      }
    } catch (e) {
      console.error('Failed to play cloud stream:', e);
    } finally {
      isCloudBuffering = false;
      bufferingSongTitle = '';
    }
  }

  async function handleDownloadCloudTrack(track: CloudTrack, e?: Event) {
    if (e) e.stopPropagation();
    cloudDownloadingIds = new Set([...cloudDownloadingIds, track.id]);
    try {
      const localPath = await api.downloadCloudTrack(track);
      downloadedCloudIds = new Set([...downloadedCloudIds, track.id]);
      try {
        const scanned = await api.scanMusicDirectory(localPath);
        if (scanned.length > 0) {
          libraryTracks = [...libraryTracks, ...scanned.filter((st) => !libraryTracks.some((lt) => lt.uri === st.uri))];
        }
      } catch {}
    } catch (err) {
      console.error('Failed downloading track:', err);
    } finally {
      const s = new Set(cloudDownloadingIds);
      s.delete(track.id);
      cloudDownloadingIds = s;
    }
  }

  function handleShuffleSection(section: CloudSection) {
    if (!section.items || section.items.length === 0) return;
    const shuffled = [...section.items].sort(() => Math.random() - 0.5);
    handlePlayCloudTrack(shuffled[0], shuffled);
  }

  async function loadArtwork(uri: string) {
    if (uri.startsWith('http')) {
      return;
    }
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
    if (track.uri.startsWith('online://')) {
      isCloudBuffering = true;
      bufferingSongTitle = track.title;
      try {
        const query = `${track.title} ${track.artist}`.trim();
        let results = await api.searchCloudTracks(query);
        if (!results || results.length === 0) {
          results = await api.searchCloudTracks(track.title);
        }
        if (results && results.length > 0) {
          await handlePlayCloudTrack(results[0]);
          return;
        }
        showToast(`Could not find online stream for "${track.title}"`);
      } catch (e) {
        console.error('Failed to stream online track:', e);
        showToast(`Error streaming "${track.title}"`);
      } finally {
        isCloudBuffering = false;
        bufferingSongTitle = '';
      }
      return;
    }

    if (track.uri.startsWith('http')) {
      const cloudTrack: CloudTrack = {
        id: track.uri,
        title: track.title,
        artist: track.artist,
        album: track.album,
        duration_seconds: track.duration_seconds,
        duration_text: formatTime(track.duration_seconds),
        thumbnail_url: currentArtwork || '',
        stream_url: track.uri,
      };
      await handlePlayCloudTrack(cloudTrack);
      return;
    }

    await api.playTrack(track);
    currentTrack = track;
    isPlaying = true;
    loadArtwork(track.uri);
    loadLyricsForTrack(track);
    if (isSampleRateAuto && track.sample_rate && track.sample_rate !== targetSampleRate) {
      targetSampleRate = track.sample_rate;
      api.reconfigureOutput(track.sample_rate, 2, isBitPerfect).catch(() => {});
    }
    recordHistory(track);

    if (!queue.some((t) => t.uri === track.uri)) {
      queue = [...queue, track];
    }

    const curIdx = queue.findIndex((t) => t.uri === track.uri);
    if (curIdx !== -1 && curIdx + 1 < queue.length) {
      if (!queue[curIdx + 1].uri.startsWith('http')) {
        api.preloadNextTrack(queue[curIdx + 1].uri).catch(() => {});
      }
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

  function toggleShuffle() {
    isShuffle = !isShuffle;
    localStorage.setItem('veyl_shuffle', String(isShuffle));
  }

  function cycleRepeatMode() {
    if (repeatMode === 'off') repeatMode = 'all';
    else if (repeatMode === 'all') repeatMode = 'one';
    else repeatMode = 'off';
    localStorage.setItem('veyl_repeat', repeatMode);
  }

  function toggleFavorite(uri: string) {
    const updated = new Set(favorites);
    if (updated.has(uri)) updated.delete(uri);
    else updated.add(uri);
    favorites = updated;
    localStorage.setItem('veyl_favorites', JSON.stringify(Array.from(updated)));
  }

  function recordHistory(track: TrackInfo) {
    const filtered = historyTracks.filter((t) => t.uri !== track.uri);
    historyTracks = [track, ...filtered].slice(0, 50);
    try {
      localStorage.setItem('veyl_history', JSON.stringify(historyTracks));
    } catch {}
  }

  async function handleScanFolder() {
    const folder = await api.pickMusicFolder();
    if (folder) {
      const tracks = await api.scanMusicDirectory(folder);
      if (tracks && tracks.length > 0) {
        const existingUris = new Set(libraryTracks.map((t) => t.uri));
        const newTracks = tracks.filter((t) => !existingUris.has(t.uri));
        libraryTracks = [...libraryTracks, ...newTracks];
        saveLibraryTracks();
      }
    }
  }

  async function handleAddFiles() {
    const files = await api.pickAudioFiles();
    if (files && files.length > 0) {
      for (const f of files) {
        const scanned = await api.scanMusicDirectory(f);
        if (scanned && scanned.length > 0) {
          libraryTracks = [...libraryTracks, ...scanned];
          queue = [...queue, ...scanned];
        }
      }
      saveLibraryTracks();
    }
  }

  function saveLibraryTracks() {
    try {
      localStorage.setItem('veyl_library_cache', JSON.stringify(libraryTracks));
    } catch {}
  }

  function playNextInQueue() {
    if (queue.length === 0) return;
    const curIdx = currentTrack ? queue.findIndex((t) => t.uri === currentTrack?.uri) : -1;

    if (isShuffle) {
      const remaining = queue.filter((t) => t.uri !== currentTrack?.uri);
      if (remaining.length > 0) {
        const rand = remaining[Math.floor(Math.random() * remaining.length)];
        handlePlayTrack(rand);
        return;
      }
    }

    if (curIdx === queue.length - 1 && repeatMode === 'off') {
      isPlaying = false;
      return;
    }

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

  function shuffleQueue() {
    const cur = currentTrack;
    const others = queue.filter((t) => t.uri !== cur?.uri);
    for (let i = others.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [others[i], others[j]] = [others[j], others[i]];
    }
    queue = cur ? [cur, ...others] : others;
  }

  async function applyEqPreset(name: string) {
    const gains = EQ_PRESETS[name];
    if (!gains) return;
    activeEqPreset = name;
    for (let i = 0; i < gains.length && i < eqBands.length; i++) {
      eqBands[i].gain_db = gains[i];
      await api.setEqualizerBand(i, gains[i], eqBands[i].frequency, eqBands[i].q);
    }
    eqCurve = await api.getEqualizerCurve(100);
  }

  async function adjustBandGain(index: number, deltaDb: number) {
    const band = eqBands[index];
    if (!band) return;
    const newGain = Math.round((band.gain_db + deltaDb) * 10) / 10;
    const clampedGain = Math.max(-12, Math.min(12, newGain));
    band.gain_db = clampedGain;
    activeEqPreset = 'Custom';
    await api.setEqualizerBand(index, clampedGain, band.frequency, band.q);
    eqCurve = await api.getEqualizerCurve(100);
  }

  async function toggleEq() {
    eqEnabled = !eqEnabled;
    await api.setEqualizerEnabled(eqEnabled);
  }

  function toggleAppearance() {
    isDarkMode = !isDarkMode;
    applyAppearance(isDarkMode);
    localStorage.setItem('veyl_dark_mode', String(isDarkMode));
  }

  async function handleDsdModeChange(mode: 'pcm' | 'dop') {
    dsdMode = mode;
    await api.setDsdMode(mode);
  }

  function handleShuffleAll() {
    const pool = filteredTracks.length > 0 ? filteredTracks : libraryTracks;
    if (pool.length === 0) return;
    const shuffled = [...pool].sort(() => Math.random() - 0.5);
    queue = shuffled;
    isShuffle = true;
    handlePlayTrack(shuffled[0]);
  }

  async function handleToggleBitPerfect() {
    isBitPerfect = !isBitPerfect;
    try {
      await api.reconfigureOutput(targetSampleRate, 2, isBitPerfect);
    } catch (e) {
      console.warn('Bit-perfect reconfigure:', e);
    }
  }

  async function handleSetAutoSampleRate() {
    isSampleRateAuto = true;
    if (currentTrack?.sample_rate) {
      targetSampleRate = currentTrack.sample_rate;
      await api.reconfigureOutput(currentTrack.sample_rate, 2, isBitPerfect).catch(() => {});
    }
  }

  async function handleSampleRateChange(rate: number) {
    isSampleRateAuto = false;
    targetSampleRate = rate;
    await api.reconfigureOutput(rate, 2, isBitPerfect).catch(() => {});
  }

  function handleKeydown(e: KeyboardEvent) {
    const target = e.target as HTMLElement | null;
    const tagName = target?.tagName?.toLowerCase();
    if (tagName === 'input' || tagName === 'textarea' || target?.isContentEditable) {
      if (e.key === 'Escape') {
        (target as HTMLInputElement).blur();
        searchQuery = '';
      }
      return;
    }

    switch (e.code) {
      case 'Space':
        e.preventDefault();
        togglePlay();
        break;
      case 'ArrowLeft':
        e.preventDefault();
        if (e.shiftKey) {
          playPrevInQueue();
        } else {
          const newPos = Math.max(0, positionSec - 5);
          positionSec = newPos;
          api.seek(newPos).catch(() => {});
        }
        break;
      case 'ArrowRight':
        e.preventDefault();
        if (e.shiftKey) {
          playNextInQueue();
        } else {
          const newPos = Math.min(durationSec, positionSec + 5);
          positionSec = newPos;
          api.seek(newPos).catch(() => {});
        }
        break;
      case 'ArrowUp':
        e.preventDefault();
        const vUp = Math.min(1.5, volume + 0.05);
        volume = vUp;
        isMuted = false;
        api.setVolume(vUp).catch(() => {});
        break;
      case 'ArrowDown':
        e.preventDefault();
        const vDown = Math.max(0.0, volume - 0.05);
        volume = vDown;
        if (vDown === 0) isMuted = true;
        api.setVolume(vDown).catch(() => {});
        break;
      case 'KeyM':
        e.preventDefault();
        toggleMute();
        break;
      case 'KeyS':
        e.preventDefault();
        toggleShuffle();
        break;
      case 'KeyR':
        e.preventDefault();
        cycleRepeatMode();
        break;
      case 'KeyF':
        if (currentTrack) {
          e.preventDefault();
          toggleFavorite(currentTrack.uri);
        }
        break;
      case 'KeyL':
        e.preventDefault();
        isLyricsMode = !isLyricsMode;
        if (activeTab !== 'now_playing') activeTab = 'now_playing';
        break;
      case 'Slash':
        e.preventDefault();
        const searchInput = document.querySelector<HTMLInputElement>('.search-box input');
        searchInput?.focus();
        break;
    }
  }

  function handleSortChange(field: SortField) {
    if (sortField === field) {
      sortAscending = !sortAscending;
    } else {
      sortField = field;
      sortAscending = true;
    }
  }

  function handleDragOver(e: DragEvent) {
    e.preventDefault();
  }

  async function handleDrop(e: DragEvent) {
    e.preventDefault();
    if (e.dataTransfer && e.dataTransfer.files.length > 0) {
      const files = Array.from(e.dataTransfer.files);
      for (const f of files) {
        const path = (f as any).path;
        if (path) {
          const scanned = await api.scanMusicDirectory(path);
          if (scanned && scanned.length > 0) {
            libraryTracks = [...libraryTracks, ...scanned];
            queue = [...queue, ...scanned];
          }
        }
      }
      saveLibraryTracks();
    }
  }

  function handleGoHome() {
    activeTab = 'cloud';
    selectedAlbum = null;
    selectedArtist = null;
    selectedPlaylistId = null;
    selectedHomeCategory = 'all';
    searchQuery = '';
    isSearchSubmitted = false;
    showSearchSuggestions = false;
    showHomeSortMenu = false;
    showLibraryAddMenu = false;
    if (typeof document !== 'undefined') {
      const panel = document.querySelector('.content-panel');
      if (panel) {
        panel.scrollTo({ top: 0, behavior: 'smooth' });
      }
    }
  }

  function handleGlobalClick(e: MouseEvent) {
    const target = e.target as HTMLElement | null;
    if (!target) return;
    if (showSearchSuggestions && !target.closest('.search-pill-container')) {
      showSearchSuggestions = false;
    }
    if (showLibraryAddMenu && !target.closest('.relative-wrap')) {
      showLibraryAddMenu = false;
    }
    if (showHomeSortMenu && !target.closest('.home-sort-dropdown-wrap')) {
      showHomeSortMenu = false;
    }
  }

  onMount(() => {
    // Restore Appearance (Dark / Light)
    const savedDark = localStorage.getItem('veyl_dark_mode');
    isDarkMode = savedDark !== 'false';
    applyAppearance(isDarkMode);

    // Register global desktop keyboard & click controls
    window.addEventListener('keydown', handleKeydown);
    window.addEventListener('click', handleGlobalClick);

    // Restore favorites
    try {
      const favs = localStorage.getItem('veyl_favorites');
      if (favs) favorites = new Set(JSON.parse(favs));
    } catch {}

    // Restore history
    try {
      const hist = localStorage.getItem('veyl_history');
      if (hist) historyTracks = JSON.parse(hist);
    } catch {}

    // Restore library cache
    try {
      const lib = localStorage.getItem('veyl_library_cache');
      if (lib) libraryTracks = JSON.parse(lib);
    } catch {}

    // Restore repeat & shuffle
    const rep = localStorage.getItem('veyl_repeat') as RepeatMode;
    if (rep) repeatMode = rep;
    isShuffle = localStorage.getItem('veyl_shuffle') === 'true';

    // Restore playlists
    try {
      const plData = localStorage.getItem('veyl_playlists');
      if (plData) playlists = JSON.parse(plData);
    } catch {}

    // Restore online track metadata
    try {
      const metaData = localStorage.getItem('veyl_online_meta');
      if (metaData) {
        onlineTrackMetaMap = new Map(JSON.parse(metaData));
      }
    } catch {}

    // Ensure any online track whose artwork was previously set to a playlist cover is re-resolved
    try {
      const plCovers = new Set(playlists.map((p) => p.coverUrl).filter(Boolean));
      for (const [uri, track] of onlineTrackMetaMap.entries()) {
        const art = trackArtworkMap.get(uri);
        if (art && plCovers.has(art)) {
          trackArtworkMap.delete(uri);
          requestOnlineTrackArtwork(uri, track.title, track.artist);
        }
      }
    } catch {}

    refreshEqualizer();
    isComponentAlive = true;
    syncEngineStatus().then(() => scheduleEnginePoll());
    loadCloudFeed();
  });

  onDestroy(() => {
    isComponentAlive = false;
    if (pollTimeout) clearTimeout(pollTimeout);
    if (pollInterval) clearInterval(pollInterval);
    window.removeEventListener('keydown', handleKeydown);
    window.removeEventListener('click', handleGlobalClick);
  });
</script>

<div
  class="veyl-app"
  role="region"
  aria-label="Veyl Desktop Workstation"
  ondragover={handleDragOver}
  ondrop={handleDrop}
>
  <!-- Top Navigation & System Status Header -->
  <!-- Top Navigation & Clean Workspace Header -->
  <!-- Top Navigation Bar: Spotify Centered Pill Search & Home -->
  <header class="top-bar">
    <!-- Left: App Brand as Home Button & Sidebar Toggle Pill -->
    <div class="top-bar-left">
      <button
        class="brand-badge-btn"
        class:active={activeTab === 'cloud' && !selectedAlbum && !selectedArtist && !selectedPlaylistId && !searchQuery}
        onclick={handleGoHome}
        title="Home"
        aria-label="Home"
      >
        <img src="/logo.png" alt="Veyl Logo" class="brand-icon" />
      </button>

      <!-- Sidebar Toggle Pill in Top Bar -->
      <button
        class="home-circle-btn sidebar-toggle-btn"
        class:active={!isSidebarCollapsed}
        onclick={() => (isSidebarCollapsed = !isSidebarCollapsed)}
        title={isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
        aria-label={isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
      >
        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.sidebar}/></svg>
      </button>
    </div>

    <!-- Center: Spotify Rounded Search Pill with Instant Suggestions -->
    <div class="search-pill-container">
      <div class="search-box-pill">
        <svg class="icon search-icon" viewBox="0 0 24 24">
          <path fill="currentColor" d={ICONS.search}/>
        </svg>
        <input
          type="text"
          placeholder="What do you want to play?"
          bind:value={searchQuery}
          onfocus={() => { if (searchQuery.trim()) showSearchSuggestions = true; }}
          oninput={() => {
            showSearchSuggestions = true;
            isSearchSubmitted = false;
          }}
          onkeydown={(e) => {
            if (e.key === 'Enter' && searchQuery.trim()) {
              isSearchSubmitted = true;
              showSearchSuggestions = false;
            }
          }}
        />
        {#if searchQuery}
          <button
            class="clear-btn"
            onclick={() => {
              searchQuery = '';
              isSearchSubmitted = false;
              showSearchSuggestions = false;
            }}
            title="Clear search"
            aria-label="Clear search"
          >
            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
          </button>
        {/if}
        <div class="search-pill-divider"></div>
        <button
          class="browse-inline-btn"
          onclick={handleScanFolder}
          title="Browse local music folders"
          aria-label="Browse music folders"
        >
          <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.browse}/></svg>
        </button>
      </div>

      <!-- Instant Live Search Suggestions Dropdown -->
      {#if showSearchSuggestions && searchQuery.trim() && !isSearchSubmitted}
        <div class="search-suggestions-popover">
          {#if searchSuggestions.local.length > 0}
            <div class="suggestion-group-title">In Your Library</div>
            {#each searchSuggestions.local as track}
              <button
                type="button"
                class="suggestion-row"
                onclick={() => {
                  showSearchSuggestions = false;
                  handlePlayTrack(track);
                }}
              >
                <div class="suggestion-thumb">
                  {#if trackArtworkMap.has(track.uri) && trackArtworkMap.get(track.uri)}
                    <img src={trackArtworkMap.get(track.uri)} alt="" class="suggestion-img" />
                  {:else}
                    <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                  {/if}
                </div>
                <div class="suggestion-meta">
                  <span class="suggestion-title">{track.title}</span>
                  <span class="suggestion-artist">{track.artist || 'Local Track'}</span>
                </div>
                <span class="suggestion-badge">{track.format_name.toUpperCase()}</span>
              </button>
            {/each}
          {/if}

          {#if searchSuggestions.cloud.length > 0}
            <div class="suggestion-group-title" style="margin-top: 6px;">Online Music</div>
            {#each searchSuggestions.cloud as track}
              <button
                type="button"
                class="suggestion-row"
                onclick={() => {
                  showSearchSuggestions = false;
                  handlePlayCloudTrack(track, searchSuggestions.cloud);
                }}
              >
                <div class="suggestion-thumb">
                  {#if track.thumbnail_url}
                    <img src={track.thumbnail_url} alt="" class="suggestion-img" />
                  {:else}
                    <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                  {/if}
                </div>
                <div class="suggestion-meta">
                  <span class="suggestion-title">{track.title}</span>
                  <span class="suggestion-artist">{track.artist}</span>
                </div>
                <span class="suggestion-badge online">320k</span>
              </button>
            {/each}
          {/if}

          <!-- Enter to view all results button -->
          <button
            type="button"
            class="suggestion-footer-btn"
            onclick={() => {
              isSearchSubmitted = true;
              showSearchSuggestions = false;
            }}
          >
            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.search}/></svg>
            <span>Press <strong>Enter</strong> to see all results for "{searchQuery}"</span>
          </button>
        </div>
      {/if}
    </div>

    <!-- Right: Import Button, Parametric Equalizer & Settings -->
    <div class="top-bar-right">
      <button
        class="btn-import-pill-header"
        onclick={() => {
          spotifyUrlInput = '';
          spotifyImportError = null;
          showSpotifyImportModal = true;
        }}
        title="Import Spotify playlist"
      >
        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.spotify}/></svg>
        <span>Import library</span>
      </button>

      <!-- Equalizer Quick Launcher -->
      <button
        class="icon-btn-header"
        class:active={activeTab === 'equalizer'}
        onclick={() => (activeTab = activeTab === 'equalizer' ? 'cloud' : 'equalizer')}
        title="Parametric Equalizer (10-Band + Harman)"
        aria-label="Equalizer"
      >
        <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.equalizer}/></svg>
      </button>

      <button
        class="icon-btn-header"
        onclick={() => (activeTab = 'settings')}
        title="Settings & Audio Engine"
        aria-label="Settings"
      >
        <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.settings}/></svg>
      </button>
    </div>
  </header>

  <!-- Workspace Container: Sidebar Rail + Main View Panel (Floating 8px cards on #000000) -->
  <div class="workspace">
    <!-- Left Navigation: Spotify "Your Library" Container -->
    <nav class="sidebar" class:collapsed={isSidebarCollapsed}>
      {#if !isSidebarCollapsed}
        <!-- Your Library Header Row -->
        <div class="library-header">
          <button
            class="library-title-btn"
            onclick={() => (isSidebarCollapsed = !isSidebarCollapsed)}
            title="Collapse Your Library"
          >
            <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.library}/></svg>
            <span class="library-title">Your Library</span>
          </button>

          <div class="library-header-actions">
            <div class="relative-wrap">
              <button
                class="icon-btn-ghost"
                onclick={() => (showLibraryAddMenu = !showLibraryAddMenu)}
                title="Create playlist or add music"
                aria-label="Add"
              >
                <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.plus}/></svg>
              </button>
              {#if showLibraryAddMenu}
                <div class="dropdown-popover">
                  <button
                    class="dropdown-item"
                    onclick={() => {
                      showLibraryAddMenu = false;
                      newPlaylistName = '';
                      newPlaylistDesc = '';
                      showCreatePlaylistModal = true;
                    }}
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.playlist}/></svg>
                    <span>Create a new playlist</span>
                  </button>
                  <button
                    class="dropdown-item"
                    onclick={() => {
                      showLibraryAddMenu = false;
                      spotifyUrlInput = '';
                      spotifyImportError = null;
                      showSpotifyImportModal = true;
                    }}
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.spotify}/></svg>
                    <span>Import from Spotify</span>
                  </button>
                  <button
                    class="dropdown-item"
                    onclick={() => {
                      showLibraryAddMenu = false;
                      handleImportM3u();
                    }}
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.upload}/></svg>
                    <span>Import M3U playlist</span>
                  </button>
                  <button
                    class="dropdown-item"
                    onclick={() => {
                      showLibraryAddMenu = false;
                      handleScanFolder();
                    }}
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.folder}/></svg>
                    <span>Add local folder</span>
                  </button>
                </div>
              {/if}
            </div>

            <!-- Collapse Sidebar Button (Toggle) -->
            <button
              class="icon-btn-ghost collapse-toggle-btn"
              onclick={() => (isSidebarCollapsed = !isSidebarCollapsed)}
              title="Collapse Your Library"
              aria-label="Collapse Your Library"
            >
              <svg class="icon" viewBox="0 0 24 24">
                <path fill="currentColor" d={ICONS.chevronLeft}/>
              </svg>
            </button>
          </div>
        </div>

        <!-- Filter Chips Row: Playlists, Artists, Albums -->
        <div class="sidebar-chips">
          <button
            class="sidebar-chip"
            class:active={librarySidebarFilter === 'playlists'}
            onclick={() => (librarySidebarFilter = librarySidebarFilter === 'playlists' ? 'all' : 'playlists')}
          >
            Playlists
          </button>
          <button
            class="sidebar-chip"
            class:active={librarySidebarFilter === 'artists'}
            onclick={() => (librarySidebarFilter = librarySidebarFilter === 'artists' ? 'all' : 'artists')}
          >
            Artists
          </button>
          <button
            class="sidebar-chip"
            class:active={librarySidebarFilter === 'albums'}
            onclick={() => (librarySidebarFilter = librarySidebarFilter === 'albums' ? 'all' : 'albums')}
          >
            Albums
          </button>
        </div>

        <!-- Import Promo Card (Hidden in collapsed mode) -->
        <div class="sidebar-promo-card">
          <div class="promo-text">
            <div class="promo-title">Import your music from other apps</div>
            <div class="promo-desc">Bring your playlists, songs and artists.</div>
          </div>
          <button
            class="btn-import-pill"
            onclick={() => {
              spotifyUrlInput = '';
              spotifyImportError = null;
              showSpotifyImportModal = true;
            }}
          >
            Import library
          </button>
        </div>

        <!-- Search & Sort Row (Hidden in collapsed mode) -->
        <div class="sidebar-search-sort-bar">
          {#if isLibrarySearchOpen}
            <div class="inline-search-input-box">
              <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.search}/></svg>
              <input
                type="text"
                placeholder="Search in Your Library"
                bind:value={librarySearchQuery}
              />
              <button
                class="icon-btn-xs"
                title="Clear search"
                aria-label="Clear search"
                onclick={() => {
                  librarySearchQuery = '';
                  isLibrarySearchOpen = false;
                }}
              >
                <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
              </button>
            </div>
          {:else}
            <button
              class="icon-btn-ghost btn-search-trigger"
              onclick={() => (isLibrarySearchOpen = true)}
              title="Search in Your Library"
            >
              <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.search}/></svg>
            </button>
          {/if}

          <button
            class="sort-dropdown-btn"
            onclick={() => {
              librarySortOption = librarySortOption === 'recents' ? 'alphabetical' : 'recents';
            }}
            title="Change sorting"
          >
            <span>{librarySortOption === 'alphabetical' ? 'Alphabetical' : 'Recents'}</span>
            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.sort}/></svg>
          </button>
        </div>

      <!-- Scrollable List of Library Items -->
      <div class="library-items-list" role="list">
        {#each filteredLibraryItems as item (item.id)}
          <button
            class="library-row-item"
            class:active={
              (item.type === 'liked' && activeTab === 'favorites') ||
              (item.type === 'playlist' && selectedPlaylistId === item.id) ||
              (item.type === 'album' && selectedAlbum === item.title) ||
              (item.type === 'artist' && selectedArtist === item.title)
            }
            title={item.title}
            onclick={() => {
              if (item.type === 'liked') {
                activeTab = 'favorites';
                selectedAlbum = null;
                selectedArtist = null;
                selectedPlaylistId = null;
              } else if (item.type === 'playlist') {
                selectedPlaylistId = item.id;
                selectedAlbum = null;
                selectedArtist = null;
                activeTab = 'playlists';
              } else if (item.type === 'album') {
                selectedAlbum = item.title;
                selectedArtist = null;
                selectedPlaylistId = null;
                activeTab = 'songs';
              } else if (item.type === 'artist') {
                selectedArtist = item.title;
                selectedAlbum = null;
                selectedPlaylistId = null;
                activeTab = 'songs';
              }
            }}
          >
            <!-- Thumbnail / Artwork -->
            {#if item.type === 'liked'}
              <div class="liked-songs-tile" title="Liked Songs">
                <svg class="icon-sm" viewBox="0 0 24 24"><path fill="#ffffff" d={ICONS.heartFilled}/></svg>
              </div>
            {:else if item.artwork}
              <img
                src={item.artwork}
                alt={item.title}
                class="library-row-art"
                class:avatar-circle={item.isCircular}
              />
            {:else}
              <div class="library-row-placeholder" class:avatar-circle={item.isCircular}>
                {#if item.type === 'artist'}
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.artist}/></svg>
                {:else if item.type === 'album'}
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                {:else}
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.playlist}/></svg>
                {/if}
              </div>
            {/if}

            <div class="library-row-texts">
              <div class="library-row-title">{item.title}</div>
              <div class="library-row-sub">{item.subtitle}</div>
            </div>
          </button>
        {/each}
      </div>

      <!-- Sticky Hardware Spec Tag at bottom of sidebar -->
      <div class="sidebar-dac-pill">
        <span class="dot-live"></span>
        <span class="dac-text">{formatBadge}</span>
      </div>
    {:else}
      <!-- Collapsed Sidebar Mini-Rail: Always-Visible Library Toggle & Mini Library Items -->
      <div class="sidebar-mini-rail">
        <!-- Always-Visible Expand Toggle Button -->
        <button
          class="sidebar-mini-toggle-btn"
          onclick={() => (isSidebarCollapsed = false)}
          title="Expand Your Library"
          aria-label="Expand Your Library"
        >
          <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.library}/></svg>
        </button>

        <div class="sidebar-mini-divider"></div>

        <div class="sidebar-mini-list">
          <!-- Liked Songs Tile -->
          <button
            class="sidebar-mini-item"
            class:active={activeTab === 'favorites'}
            onclick={() => {
              activeTab = 'favorites';
              selectedAlbum = null;
              selectedArtist = null;
              selectedPlaylistId = null;
            }}
            title="Liked Songs ({favorites.size})"
            aria-label="Liked Songs"
          >
            <div class="mini-liked-tile">
              <svg class="icon-sm" viewBox="0 0 24 24"><path fill="#ffffff" d={ICONS.heartFilled}/></svg>
            </div>
          </button>

          <!-- Quick library items (Playlists, Albums, Artists) -->
          {#each filteredLibraryItems.filter((item) => item.type !== 'liked').slice(0, 10) as item (item.id)}
            <button
              class="sidebar-mini-item"
              class:active={
                (item.type === 'playlist' && selectedPlaylistId === item.id) ||
                (item.type === 'album' && selectedAlbum === item.title) ||
                (item.type === 'artist' && selectedArtist === item.title)
              }
              title="{item.title} ({item.subtitle})"
              onclick={() => {
                if (item.type === 'playlist') {
                  selectedPlaylistId = item.id;
                  selectedAlbum = null;
                  selectedArtist = null;
                  activeTab = 'playlists';
                } else if (item.type === 'album') {
                  selectedAlbum = item.title;
                  selectedArtist = null;
                  selectedPlaylistId = null;
                  activeTab = 'songs';
                } else if (item.type === 'artist') {
                  selectedArtist = item.title;
                  selectedAlbum = null;
                  selectedPlaylistId = null;
                  activeTab = 'songs';
                }
              }}
            >
              {#if item.artwork}
                <img src={item.artwork} alt={item.title} class="sidebar-mini-art" class:avatar-circle={item.isCircular} />
              {:else}
                <div class="sidebar-mini-placeholder" class:avatar-circle={item.isCircular}>
                  {#if item.type === 'artist'}
                    <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.artist}/></svg>
                  {:else if item.type === 'album'}
                    <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                  {:else}
                    <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.playlist}/></svg>
                  {/if}
                </div>
              {/if}
            </button>
          {/each}
        </div>
      </div>
    {/if}
  </nav>

    <!-- Main View Panel Area -->
    <main class="content-panel">
      <!-- 0. GLOBAL UNIFIED SEARCH VIEW (Active ONLY when search is submitted by pressing Enter) -->
      {#if isSearchSubmitted && searchQuery.trim().length > 0}
        <div class="view-container">
          <div class="view-header">
            <div>
              <div class="header-title-row">
                <h2 class="view-title">Search Results</h2>
              </div>
              <p class="view-subtitle">
                Matching "{searchQuery}" across your library & online music
              </p>
            </div>

            <div class="header-right-cluster">
              <div class="filter-chips">
                <button
                  class="chip"
                  class:active={searchFilterTab === 'all'}
                  onclick={() => (searchFilterTab = 'all')}
                >
                  ALL ({globalLocalResults.length + globalCloudResults.length})
                </button>
                <button
                  class="chip"
                  class:active={searchFilterTab === 'local'}
                  onclick={() => (searchFilterTab = 'local')}
                >
                  IN LIBRARY ({globalLocalResults.length})
                </button>
                <button
                  class="chip"
                  class:active={searchFilterTab === 'cloud'}
                  onclick={() => (searchFilterTab = 'cloud')}
                >
                  ONLINE MUSIC ({globalCloudResults.length})
                </button>
              </div>

              <button class="btn btn-secondary" onclick={() => (searchQuery = '')}>
                Clear Search
              </button>
            </div>
          </div>

          <!-- Section 1: Local Library Results -->
          {#if searchFilterTab === 'all' || searchFilterTab === 'local'}
            <div class="search-category-section">
              <div class="section-title-bar">
                <h3 class="panel-section-title">IN LIBRARY ({globalLocalResults.length})</h3>
              </div>

              {#if globalLocalResults.length === 0}
                <div class="empty-substate">
                  <p>No tracks matching "{searchQuery}" in your local library.</p>
                </div>
              {:else}
                <div class="song-list-container">
                  <div class="song-list">
                    {#each globalLocalResults as track, i}
                      <!-- svelte-ignore a11y_click_events_have_key_events -->
                      <div
                        class="song-list-row"
                        role="button"
                        tabindex="0"
                        class:active={currentTrack?.uri === track.uri}
                        onclick={() => handlePlayTrack(track)}
                      >
                        <div class="sl-prefix">
                          <span class="sl-index">{i + 1}</span>
                          <button
                            type="button"
                            class="sl-cover-box"
                            onclick={() => handlePlayTrack(track)}
                            aria-label="Play {track.title}"
                          >
                            {#if trackArtworkMap.has(track.uri) && trackArtworkMap.get(track.uri)}
                              <img src={trackArtworkMap.get(track.uri)} alt="" class="sl-cover-img" />
                            {:else}
                              <div class="sl-cover-placeholder">
                                <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                              </div>
                            {/if}
                            {#if currentTrack?.uri === track.uri && isPlaying}
                              <div class="sl-cover-overlay active">
                                <span class="playing-bars"><span></span><span></span><span></span></span>
                              </div>
                            {:else}
                              <div class="sl-cover-overlay hover-play">
                                <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                              </div>
                            {/if}
                          </button>
                        </div>

                        <button
                          type="button"
                          class="sl-main"
                          onclick={() => handlePlayTrack(track)}
                        >
                          <div class="sl-title-line">
                            <span class="sl-title" class:active-title={currentTrack?.uri === track.uri}>
                              {track.title}
                            </span>
                            {#if track.sample_rate >= 88200 || (track.bit_depth && track.bit_depth >= 24)}
                              <span class="sl-spec-pill hires">
                                {track.format_name.toUpperCase()} {Math.round(track.sample_rate / 1000)}k{track.bit_depth ? `/${track.bit_depth}b` : ''}
                              </span>
                            {:else if track.format_name}
                              <span class="sl-spec-pill">{track.format_name.toUpperCase()}</span>
                            {/if}
                          </div>
                          <div class="sl-subtitle">
                            <span class="sl-artist">{track.artist || 'Unknown Artist'}</span>
                            {#if track.album}
                              <span class="sl-dot">•</span>
                              <span class="sl-album">{track.album}</span>
                            {/if}
                          </div>
                        </button>

                        <div class="sl-trailing">
                          <span class="sl-duration">{formatTime(track.duration_seconds)}</span>
                          <button
                            class="sl-action-btn"
                            class:favorited={favorites.has(track.uri)}
                            onclick={(e) => { e.stopPropagation(); toggleFavorite(track.uri); }}
                            title="Favorite"
                            aria-label="Favorite"
                          >
                            <svg class="icon-sm" viewBox="0 0 24 24">
                              <path fill="currentColor" d={favorites.has(track.uri) ? ICONS.heartFilled : ICONS.heart}/>
                            </svg>
                          </button>
                          <button
                            class="sl-action-btn"
                            onclick={(e) => { e.stopPropagation(); openAddToPlaylistModal(track); }}
                            title="Add to Playlist"
                            aria-label="Add to Playlist"
                          >
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.plus}/></svg>
                          </button>
                        </div>
                      </div>
                    {/each}
                  </div>
                </div>
              {/if}
            </div>
          {/if}

          <!-- Section 2: Online Music Results -->
          {#if searchFilterTab === 'all' || searchFilterTab === 'cloud'}
            <div class="search-category-section" style="margin-top: 28px;">
              <div class="section-title-bar">
                <div class="title-with-spinner">
                  <h3 class="panel-section-title">ONLINE MUSIC ({globalCloudResults.length})</h3>
                  {#if isGlobalCloudSearching}
                    <span class="inline-spinner" style="margin-left: 8px;"></span>
                  {/if}
                </div>
              </div>

              {#if isGlobalCloudSearching && globalCloudResults.length === 0}
                <div class="empty-substate">
                  <span class="inline-spinner"></span>
                  <p>Searching online music catalog...</p>
                </div>
              {:else if globalCloudResults.length === 0}
                <div class="empty-substate">
                  <p>No online tracks found for "{searchQuery}".</p>
                </div>
              {:else}
                <div class="cloud-tracks-grid">
                  {#each globalCloudResults as track (track.id)}
                    <div class="cloud-track-card" class:active={currentTrack?.title === track.title}>
                      <div class="track-card-art-box">
                        {#if track.thumbnail_url}
                          <img src={track.thumbnail_url} alt={track.title} class="track-card-img" loading="lazy" />
                        {:else}
                          <div class="track-card-placeholder">
                            <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                          </div>
                        {/if}
                        <button
                          class="card-play-action"
                          onclick={() => handlePlayCloudTrack(track, globalCloudResults)}
                          title="Stream Online"
                        >
                          <svg class="icon" viewBox="0 0 24 24">
                            <path fill="currentColor" d={currentTrack?.title === track.title && isPlaying ? ICONS.pause : ICONS.play}/>
                          </svg>
                        </button>
                      </div>

                      <div class="track-card-body">
                        <div class="track-card-title" title={track.title}>{track.title}</div>
                        <div class="track-card-artist" title={track.artist}>{track.artist}</div>
                        <div class="track-card-bottom">
                          <span class="badge badge-accent">320k</span>
                          <span class="track-duration-text">{track.duration_text}</span>
                          <div class="card-btn-cluster">
                            <button
                              class="download-action-btn"
                              onclick={(e) => handleDownloadCloudTrack(track, e)}
                              title="Download to library"
                              disabled={cloudDownloadingIds.has(track.id) || downloadedCloudIds.has(track.id)}
                            >
                              <svg class="icon-sm" viewBox="0 0 24 24">
                                <path fill="currentColor" d={downloadedCloudIds.has(track.id) ? ICONS.heartFilled : ICONS.download}/>
                              </svg>
                            </button>
                            <button
                              class="download-action-btn"
                              onclick={() => openAddToPlaylistModal(track)}
                              title="Add to Playlist"
                            >
                              <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.plus}/></svg>
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  {/each}
                </div>
              {/if}
            </div>
          {/if}
        </div>

      <!-- 1. HOME / DISCOVER TAB (Spotify-Grade Clean Architecture) -->
      {:else if activeTab === 'cloud'}
        <div class="view-container home-view">
          <!-- Top Category Chips & Actions: All (local+online), Music (local), Shuffle, and Expandable Sort -->
          <div class="home-category-chips">
            <div class="home-filter-pills">
              <button
                class="home-category-chip"
                class:active={selectedHomeCategory === 'all'}
                onclick={() => (selectedHomeCategory = 'all')}
              >
                All
              </button>
              <button
                class="home-category-chip"
                class:active={selectedHomeCategory === 'music'}
                onclick={() => (selectedHomeCategory = 'music')}
              >
                Music
              </button>
            </div>

            <div class="home-actions-rail">
              <!-- Shuffle Button -->
              <button
                class="home-action-btn home-shuffle-btn"
                onclick={handleShuffleCurrentView}
                title="Shuffle Current View"
                aria-label="Shuffle Current View"
              >
                <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.shuffle}/></svg>
                <span>Shuffle</span>
              </button>

              <!-- Expandable Sort Dropdown Button -->
              <div class="home-sort-dropdown-wrap">
                <button
                  class="home-action-btn home-sort-btn"
                  onclick={() => (showHomeSortMenu = !showHomeSortMenu)}
                  title="Sort options"
                  aria-label="Sort options"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.sort}/></svg>
                  <span>{homeSortOption === 'recent' ? 'Recently Added' : homeSortOption === 'title' ? 'Title (A-Z)' : homeSortOption === 'artist' ? 'Artist (A-Z)' : homeSortOption === 'album' ? 'Album (A-Z)' : homeSortOption === 'duration' ? 'Duration' : 'Hi-Res First'}</span>
                  <svg class="icon-xs" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.chevronDown}/></svg>
                </button>

                {#if showHomeSortMenu}
                  <!-- svelte-ignore a11y_click_events_have_key_events -->
                  <!-- svelte-ignore a11y_no_static_element_interactions -->
                  <div class="sort-popover-menu" role="menu" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.stopPropagation()}>
                    <button
                      class="sort-option-item"
                      class:active={homeSortOption === 'recent'}
                      onclick={() => { homeSortOption = 'recent'; showHomeSortMenu = false; }}
                    >
                      Recently Added
                    </button>
                    <button
                      class="sort-option-item"
                      class:active={homeSortOption === 'title'}
                      onclick={() => { homeSortOption = 'title'; showHomeSortMenu = false; }}
                    >
                      Title (A-Z)
                    </button>
                    <button
                      class="sort-option-item"
                      class:active={homeSortOption === 'artist'}
                      onclick={() => { homeSortOption = 'artist'; showHomeSortMenu = false; }}
                    >
                      Artist (A-Z)
                    </button>
                    <button
                      class="sort-option-item"
                      class:active={homeSortOption === 'album'}
                      onclick={() => { homeSortOption = 'album'; showHomeSortMenu = false; }}
                    >
                      Album (A-Z)
                    </button>
                    <button
                      class="sort-option-item"
                      class:active={homeSortOption === 'duration'}
                      onclick={() => { homeSortOption = 'duration'; showHomeSortMenu = false; }}
                    >
                      Duration
                    </button>
                    <button
                      class="sort-option-item"
                      class:active={homeSortOption === 'hires'}
                      onclick={() => { homeSortOption = 'hires'; showHomeSortMenu = false; }}
                    >
                      Hi-Res First
                    </button>
                  </div>
                {/if}
              </div>
            </div>
          </div>

          {#if selectedHomeCategory === 'all'}
            <!-- Section 1: Popular albums and singles -->
            <section class="shelf-section">
              <div class="shelf-header">
                <h2 class="shelf-title">Popular albums and singles</h2>
              <button
                class="shelf-show-all-btn"
                onclick={() => {
                  activeTab = 'albums';
                  selectedAlbum = null;
                  selectedArtist = null;
                }}
              >
                Show all
              </button>
            </div>

            <div class="shelf-grid">
              {#if sortedHomeAlbums.length > 0}
                {#each sortedHomeAlbums.slice(0, 12) as al (al.name)}
                  <div
                    class="shelf-card"
                    role="button"
                    tabindex="0"
                    onclick={() => {
                      selectedAlbum = al.name;
                      selectedArtist = null;
                      activeTab = 'songs';
                    }}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') {
                        selectedAlbum = al.name;
                        activeTab = 'songs';
                      }
                    }}
                  >
                    <div class="shelf-card-art-box">
                      {#if getAlbumCover(al)}
                        <img src={getAlbumCover(al)} alt={al.name} class="shelf-card-img" loading="lazy" />
                      {:else}
                        <div class="shelf-card-placeholder">
                          <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                        </div>
                      {/if}
                      <button
                        type="button"
                        class="card-hover-play-btn"
                        onclick={(e) => {
                          e.stopPropagation();
                          handlePlayAlbum(al.name);
                        }}
                        title="Play {al.name}"
                        aria-label="Play {al.name}"
                      >
                        <svg class="icon-play-white" viewBox="0 0 24 24">
                          <path fill="currentColor" d={ICONS.play}/>
                        </svg>
                      </button>
                    </div>

                    <div class="shelf-card-info">
                      <div class="shelf-card-title" title={al.name}>{al.name}</div>
                      <div class="shelf-card-sub" title={al.artist}>{al.artist || 'Various Artists'}</div>
                    </div>
                  </div>
                {/each}
              {:else if cloudSections.length > 0 && cloudSections[0].items.length > 0}
                <!-- Fallback to featured discovery tracks if local library empty -->
                {#each cloudSections[0].items.slice(0, 8) as track (track.id)}
                  <div
                    class="shelf-card"
                    role="button"
                    tabindex="0"
                    onclick={() => handlePlayCloudTrack(track, cloudSections[0].items)}
                    onkeydown={(e) => { if (e.key === 'Enter') handlePlayCloudTrack(track, cloudSections[0].items); }}
                  >
                    <div class="shelf-card-art-box">
                      {#if track.thumbnail_url}
                        <img src={track.thumbnail_url} alt={track.title} class="shelf-card-img" loading="lazy" />
                      {:else}
                        <div class="shelf-card-placeholder">
                          <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                        </div>
                      {/if}
                      <button
                        type="button"
                        class="card-hover-play-btn"
                        onclick={(e) => {
                          e.stopPropagation();
                          handlePlayCloudTrack(track, cloudSections[0].items);
                        }}
                        title="Play {track.title}"
                        aria-label="Play {track.title}"
                      >
                        <svg class="icon-play-white" viewBox="0 0 24 24">
                          <path fill="currentColor" d={currentTrack?.title === track.title && isPlaying ? ICONS.pause : ICONS.play}/>
                        </svg>
                      </button>
                    </div>

                    <div class="shelf-card-info">
                      <div class="shelf-card-title" title={track.title}>{track.title}</div>
                      <div class="shelf-card-sub" title={track.artist}>{track.artist || 'Online Hit'}</div>
                    </div>
                  </div>
                {/each}
              {:else}
                <div class="shelf-empty-state">
                  <p>No albums scanned yet. Click "Import library" or browse folders above!</p>
                </div>
              {/if}
            </div>
          </section>

          <!-- Section 2: Online Trending & Recommended (shown in 'all' view) -->
          {#if selectedHomeCategory === 'all' && cloudSections.length > 0 && cloudSections[0].items.length > 0}
            <section class="shelf-section">
              <div class="shelf-header">
                <h2 class="shelf-title">Online Trending & Recommended</h2>
              </div>

              <div class="shelf-grid">
                {#each cloudSections[0].items.slice(0, 8) as track (track.id)}
                  <div
                    class="shelf-card"
                    role="button"
                    tabindex="0"
                    onclick={() => handlePlayCloudTrack(track, cloudSections[0].items)}
                    onkeydown={(e) => { if (e.key === 'Enter') handlePlayCloudTrack(track, cloudSections[0].items); }}
                  >
                    <div class="shelf-card-art-box">
                      {#if track.thumbnail_url}
                        <img src={track.thumbnail_url} alt={track.title} class="shelf-card-img" loading="lazy" />
                      {:else}
                        <div class="shelf-card-placeholder">
                          <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                        </div>
                      {/if}
                      <button
                        type="button"
                        class="card-hover-play-btn"
                        onclick={(e) => {
                          e.stopPropagation();
                          handlePlayCloudTrack(track, cloudSections[0].items);
                        }}
                        title="Play {track.title}"
                        aria-label="Play {track.title}"
                      >
                        <svg class="icon-play-white" viewBox="0 0 24 24">
                          <path fill="currentColor" d={currentTrack?.title === track.title && isPlaying ? ICONS.pause : ICONS.play}/>
                        </svg>
                      </button>
                    </div>

                    <div class="shelf-card-info">
                      <div class="shelf-card-title" title={track.title}>{track.title}</div>
                      <div class="shelf-card-sub" title={track.artist}>{track.artist || 'Online Hit'}</div>
                    </div>
                  </div>
                {/each}
              </div>
            </section>
          {/if}

          <!-- Section 3: Editor's Picks: No-Skip Playlists -->
          <section class="shelf-section">
              <div class="shelf-header">
                <h2 class="shelf-title">Editor's Picks: No-Skip Playlists</h2>
                <button
                  class="shelf-show-all-btn"
                  onclick={() => {
                    activeTab = 'playlists';
                    selectedPlaylistId = null;
                  }}
                >
                  Show all
                </button>
              </div>

              <div class="shelf-grid">
                <!-- User Liked Songs Playlist Tile -->
                <div
                  class="shelf-card"
                  role="button"
                  tabindex="0"
                  onclick={() => {
                    activeTab = 'favorites';
                    selectedAlbum = null;
                    selectedArtist = null;
                    selectedPlaylistId = null;
                  }}
                  onkeydown={(e) => { if (e.key === 'Enter') activeTab = 'favorites'; }}
                >
                  <div class="shelf-card-art-box liked-gradient-box">
                    <svg class="icon-xl" viewBox="0 0 24 24"><path fill="#ffffff" d={ICONS.heartFilled}/></svg>
                    <button
                      type="button"
                      class="card-hover-play-btn"
                      onclick={(e) => {
                        e.stopPropagation();
                        if (favorites.size > 0) {
                          const favTracks = libraryTracks.filter(t => favorites.has(t.uri));
                          if (favTracks.length > 0) {
                            queue = [...favTracks];
                            handlePlayTrack(queue[0]);
                          }
                        }
                      }}
                      title="Play Liked Songs"
                      aria-label="Play Liked Songs"
                    >
                      <svg class="icon-play-white" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                    </button>
                  </div>
                  <div class="shelf-card-info">
                    <div class="shelf-card-title">Liked Songs</div>
                    <div class="shelf-card-sub">Playlist • {favorites.size} songs</div>
                  </div>
                </div>

                <!-- User Playlists -->
                {#each playlists.slice(0, 7) as pl (pl.id)}
                  <div
                    class="shelf-card"
                    role="button"
                    tabindex="0"
                    onclick={() => {
                      selectedPlaylistId = pl.id;
                      activeTab = 'playlists';
                    }}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') {
                        selectedPlaylistId = pl.id;
                        activeTab = 'playlists';
                      }
                    }}
                  >
                    <div class="shelf-card-art-box">
                      <div class="shelf-card-placeholder playlist-bg">
                        <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.playlist}/></svg>
                      </div>
                      <button
                        type="button"
                        class="card-hover-play-btn"
                        onclick={(e) => {
                          e.stopPropagation();
                          if (pl.tracks.length > 0) {
                            queue = [...pl.tracks];
                            handlePlayTrack(queue[0]);
                          }
                        }}
                        title="Play {pl.name}"
                        aria-label="Play {pl.name}"
                      >
                        <svg class="icon-play-white" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                      </button>
                    </div>

                    <div class="shelf-card-info">
                      <div class="shelf-card-title" title={pl.name}>{pl.name}</div>
                      <div class="shelf-card-sub">Playlist • {pl.tracks.length} songs</div>
                    </div>
                  </div>
                {/each}
              </div>
            </section>

          <!-- Dynamic Cloud Discovery Rows (Trending, Top Charts, Regional) -->
          {#if isCloudLoading && cloudSections.length === 0}
            <div class="cloud-loading-container">
              <span class="spinner-large"></span>
              <p class="loading-label">Curating your recommendations...</p>
            </div>
          {:else}
            {#each cloudSections as section (section.id)}
              <section class="shelf-section">
                <div class="shelf-header">
                  <div>
                    <h2 class="shelf-title">{section.title}</h2>
                    {#if section.subtitle}
                      <span class="shelf-sub-label">{section.subtitle}</span>
                    {/if}
                  </div>

                  <button
                    class="btn btn-secondary btn-sm shuffle-btn"
                    onclick={() => handleShuffleSection(section)}
                    title="Shuffle play section"
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.shuffle}/></svg>
                    <span>Shuffle</span>
                  </button>
                </div>

                <div class="shelf-grid">
                  {#each section.items.slice(0, 8) as track (track.id)}
                    <div
                      class="shelf-card"
                      role="button"
                      tabindex="0"
                      onclick={() => handlePlayCloudTrack(track, section.items)}
                      onkeydown={(e) => { if (e.key === 'Enter') handlePlayCloudTrack(track, section.items); }}
                    >
                      <div class="shelf-card-art-box">
                        {#if track.thumbnail_url}
                          <img src={track.thumbnail_url} alt={track.title} class="shelf-card-img" loading="lazy" />
                        {:else}
                          <div class="shelf-card-placeholder">
                            <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                          </div>
                        {/if}
                        <button
                          type="button"
                          class="card-hover-play-btn"
                          onclick={(e) => {
                            e.stopPropagation();
                            handlePlayCloudTrack(track, section.items);
                          }}
                          title="Play {track.title}"
                          aria-label="Play {track.title}"
                        >
                          <svg class="icon-play-white" viewBox="0 0 24 24">
                            <path fill="currentColor" d={currentTrack?.title === track.title && isPlaying ? ICONS.pause : ICONS.play}/>
                          </svg>
                        </button>
                      </div>

                      <div class="shelf-card-info">
                        <div class="shelf-card-title" title={track.title}>{track.title}</div>
                        <div class="shelf-card-sub" title={track.artist}>{track.artist}</div>
                      </div>
                    </div>
                  {/each}
                </div>
              </section>
            {/each}
          {/if}
        {:else if selectedHomeCategory === 'music'}
          <!-- MUSIC VIEW: Dedicated Local Music Workstation -->
          <!-- Shelf 1: Albums in Library -->
          <section class="shelf-section">
            <div class="shelf-header">
              <div>
                <h2 class="shelf-title">Albums in Library</h2>
                <span class="shelf-sub-label">{sortedHomeAlbums.length} albums</span>
              </div>
              <button
                class="shelf-show-all-btn"
                onclick={() => {
                  activeTab = 'albums';
                  selectedAlbum = null;
                  selectedArtist = null;
                }}
              >
                Show all
              </button>
            </div>

            <div class="shelf-grid">
              {#if sortedHomeAlbums.length > 0}
                {#each sortedHomeAlbums as al (al.name)}
                  <div
                    class="shelf-card"
                    role="button"
                    tabindex="0"
                    onclick={() => {
                      selectedAlbum = al.name;
                      selectedArtist = null;
                      activeTab = 'songs';
                    }}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') {
                        selectedAlbum = al.name;
                        activeTab = 'songs';
                      }
                    }}
                  >
                    <div class="shelf-card-art-box">
                      {#if getAlbumCover(al)}
                        <img src={getAlbumCover(al)} alt={al.name} class="shelf-card-img" loading="lazy" />
                      {:else}
                        <div class="shelf-card-placeholder">
                          <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                        </div>
                      {/if}
                      <button
                        type="button"
                        class="card-hover-play-btn"
                        onclick={(e) => {
                          e.stopPropagation();
                          handlePlayAlbum(al.name);
                        }}
                        title="Play {al.name}"
                        aria-label="Play {al.name}"
                      >
                        <svg class="icon-play-white" viewBox="0 0 24 24">
                          <path fill="currentColor" d={ICONS.play}/>
                        </svg>
                      </button>
                    </div>

                    <div class="shelf-card-info">
                      <div class="shelf-card-title" title={al.name}>{al.name}</div>
                      <div class="shelf-card-sub" title={al.artist}>{al.artist || 'Various Artists'}</div>
                    </div>
                  </div>
                {/each}
              {:else}
                <div class="shelf-empty-state">
                  <p>No albums scanned yet. Click "Import library" or browse folders above!</p>
                </div>
              {/if}
            </div>
          </section>

          <!-- Shelf 2: Artists in Library -->
          {#if artists.length > 0}
            <section class="shelf-section">
              <div class="shelf-header">
                <div>
                  <h2 class="shelf-title">Artists in Library</h2>
                  <span class="shelf-sub-label">{artists.length} artists</span>
                </div>
                <button
                  class="shelf-show-all-btn"
                  onclick={() => {
                    activeTab = 'artists';
                    selectedAlbum = null;
                    selectedArtist = null;
                  }}
                >
                  Show all
                </button>
              </div>

              <div class="shelf-grid">
                {#each artists.slice(0, 12) as art (art.name)}
                  <div
                    class="shelf-card"
                    role="button"
                    tabindex="0"
                    onclick={() => {
                      selectedArtist = art.name;
                      selectedAlbum = null;
                      activeTab = 'songs';
                    }}
                    onkeydown={(e) => {
                      if (e.key === 'Enter') {
                        selectedArtist = art.name;
                        activeTab = 'songs';
                      }
                    }}
                  >
                    <div class="shelf-card-art-box artist-avatar-box">
                      {#if getArtistAvatar(art)}
                        <img src={getArtistAvatar(art)} alt={art.name} class="shelf-card-img artist-circle-img" loading="lazy" />
                      {:else}
                        <div class="shelf-card-placeholder artist-circle-img">
                          <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.artist}/></svg>
                        </div>
                      {/if}
                    </div>

                    <div class="shelf-card-info">
                      <div class="shelf-card-title" title={art.name}>{art.name}</div>
                      <div class="shelf-card-sub">{art.albumCount} albums • {art.trackCount} tracks</div>
                    </div>
                  </div>
                {/each}
              </div>
            </section>
          {/if}

          <!-- Shelf 3: Songs in Library -->
          {#if libraryTracks.length > 0}
            <section class="shelf-section">
              <div class="shelf-header">
                <div>
                  <h2 class="shelf-title">Songs in Library</h2>
                  <span class="shelf-sub-label">{libraryTracks.length} tracks</span>
                </div>
                <button
                  class="shelf-show-all-btn"
                  onclick={() => {
                    activeTab = 'songs';
                    selectedAlbum = null;
                    selectedArtist = null;
                  }}
                >
                  Show all
                </button>
              </div>

              <div class="song-list">
                {#each libraryTracks.slice(0, 15) as track, i (track.uri)}
                  <div
                    class="song-list-row"
                    class:active={currentTrack?.uri === track.uri}
                  >
                    <div class="sl-prefix">
                      <span class="sl-index">{i + 1}</span>
                      <button
                        type="button"
                        class="sl-cover-box"
                        onclick={() => handlePlayTrack(track)}
                        aria-label="Play {track.title}"
                      >
                        {#if trackArtworkMap.has(track.uri) && trackArtworkMap.get(track.uri)}
                          <img src={trackArtworkMap.get(track.uri)} alt="" class="sl-cover-img" />
                        {:else}
                          <div class="sl-cover-placeholder">
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                          </div>
                        {/if}
                        {#if currentTrack?.uri === track.uri && isPlaying}
                          <div class="sl-cover-overlay active">
                            <span class="playing-bars"><span></span><span></span><span></span></span>
                          </div>
                        {:else}
                          <div class="sl-cover-overlay hover-play">
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                          </div>
                        {/if}
                      </button>
                    </div>

                    <button
                      type="button"
                      class="sl-main"
                      onclick={() => handlePlayTrack(track)}
                    >
                      <div class="sl-title-line">
                        <span class="sl-title" class:active-title={currentTrack?.uri === track.uri}>
                          {track.title}
                        </span>
                        {#if track.sample_rate >= 88200 || (track.bit_depth && track.bit_depth >= 24)}
                          <span class="sl-spec-pill hires">
                            {track.format_name.toUpperCase()} {Math.round(track.sample_rate / 1000)}k{track.bit_depth ? `/${track.bit_depth}b` : ''}
                          </span>
                        {:else if track.format_name}
                          <span class="sl-spec-pill">
                            {track.format_name.toUpperCase()}
                          </span>
                        {/if}
                      </div>
                      <div class="sl-subtitle">
                        <span class="sl-artist">{track.artist || 'Unknown Artist'}</span>
                        {#if track.album}
                          <span class="sl-dot">•</span>
                          <span class="sl-album">{track.album}</span>
                        {/if}
                      </div>
                    </button>

                    <div class="sl-actions">
                      <span class="sl-dur">{formatTime(track.duration_seconds)}</span>
                    </div>
                  </div>
                {/each}
              </div>
            </section>
          {/if}
        {/if}
        </div>

      <!-- 1. SONGS TAB / DRILL-DOWN -->
      {:else if activeTab === 'songs' || activeTab === 'favorites'}
        <div class="view-container">
          <div class="view-header">
            <div>
              {#if selectedAlbum}
                <div class="header-with-back">
                  <button
                    type="button"
                    class="btn-back-nav"
                    onclick={() => (selectedAlbum = null)}
                    title="Back to All Albums"
                    aria-label="Back to All Albums"
                  >
                    <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.arrowLeft}/></svg>
                  </button>
                  <div>
                    <h2 class="view-title">{selectedAlbum}</h2>
                    <p class="view-subtitle">{filteredTracks.length} tracks in album</p>
                  </div>
                </div>
              {:else if selectedArtist}
                <div class="header-with-back">
                  <button
                    type="button"
                    class="btn-back-nav"
                    onclick={() => (selectedArtist = null)}
                    title="Back to All Artists"
                    aria-label="Back to All Artists"
                  >
                    <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.arrowLeft}/></svg>
                  </button>
                  <div>
                    <h2 class="view-title">{selectedArtist}</h2>
                    <p class="view-subtitle">{filteredTracks.length} tracks by artist</p>
                  </div>
                </div>
              {:else}
                <div>
                  <h2 class="view-title">
                    {activeTab === 'favorites' ? 'Favorites' : 'Songs'}
                  </h2>
                  <p class="view-subtitle">
                    {filteredTracks.length} tracks available
                  </p>
                </div>
              {/if}
            </div>

            <!-- Header Action Controls & Mobile-matched Filter Chips -->
            <div class="header-right-cluster">
              <button
                class="btn btn-primary shuffle-all-btn"
                onclick={handleShuffleAll}
                title="Shuffle all tracks in view"
              >
                <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.shuffle}/></svg>
                <span>Shuffle All</span>
              </button>

              <!-- View Layout Switcher (List vs Grid, matching mobile) -->
              <div class="view-layout-group">
                <button
                  class="layout-btn"
                  class:active={songViewLayout === 'list'}
                  onclick={() => (songViewLayout = 'list')}
                  title="List View"
                  aria-label="List View"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.listView}/></svg>
                </button>
                <button
                  class="layout-btn"
                  class:active={songViewLayout === 'grid'}
                  onclick={() => (songViewLayout = 'grid')}
                  title="Grid View"
                  aria-label="Grid View"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.gridView}/></svg>
                </button>
              </div>

              <div class="filter-chips">
                {#each ['ALL', 'FAVORITES', 'HI-RES', 'LOSSLESS', 'DSD'] as fmt}
                  <button
                    class="chip"
                    class:active={filterFormat === fmt || (fmt === 'FAVORITES' && activeTab === 'favorites')}
                    onclick={() => {
                      filterFormat = fmt as any;
                      if (fmt === 'FAVORITES') activeTab = 'favorites';
                      else if (activeTab === 'favorites') activeTab = 'songs';
                    }}
                  >
                    {fmt}
                  </button>
                {/each}
              </div>
            </div>
          </div>

          <!-- Mobile-matched Sort Bar -->
          <div class="song-sort-bar">
            <span class="sort-label">Sort</span>
            {#each [
              { field: 'title', label: 'Title' },
              { field: 'artist', label: 'Artist' },
              { field: 'album', label: 'Album' },
              { field: 'duration', label: 'Time' },
              { field: 'sample_rate', label: 'Specs' }
            ] as s}
              <button
                class="sort-pill"
                class:active={sortField === s.field}
                onclick={() => handleSortChange(s.field as any)}
              >
                <span>{s.label}</span>
                {#if sortField === s.field}
                  <span class="sort-arrow">{sortAscending ? '↑' : '↓'}</span>
                {/if}
              </button>
            {/each}
          </div>

          {#if filteredTracks.length === 0}
            <div class="empty-state">
              <h3>No songs available</h3>
              <p>Click Open Folder to index your music files from local storage.</p>
              <button class="btn btn-primary" onclick={handleScanFolder}>Open Folder</button>
            </div>
          {:else if songViewLayout === 'grid'}
            <!-- Grid Layout (Matching mobile SongViewLayout.GRID) -->
            <div class="song-cards-grid">
              {#each filteredTracks as track}
                <button
                  type="button"
                  class="song-grid-card"
                  class:active={currentTrack?.uri === track.uri}
                  onclick={() => handlePlayTrack(track)}
                >
                  <div class="sg-cover-box">
                    {#if trackArtworkMap.has(track.uri) && trackArtworkMap.get(track.uri)}
                      <img src={trackArtworkMap.get(track.uri)} alt="" class="sg-cover-img" />
                    {:else}
                      <div class="sg-cover-placeholder">
                        <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                      </div>
                    {/if}
                    {#if track.sample_rate >= 88200 || (track.bit_depth && track.bit_depth >= 24)}
                      <span class="hires-badge">HI-RES</span>
                    {/if}
                    {#if currentTrack?.uri === track.uri && isPlaying}
                      <div class="sg-play-badge active">
                        <span class="playing-bars"><span></span><span></span><span></span></span>
                      </div>
                    {:else}
                      <div class="sg-play-badge hover-play">
                        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                      </div>
                    {/if}
                  </div>
                  <div class="sg-info">
                    <div class="sg-title" class:active-title={currentTrack?.uri === track.uri}>{track.title}</div>
                    <div class="sg-artist">{track.artist || 'Unknown Artist'}</div>
                    <div class="sg-meta">
                      <span>{formatTime(track.duration_seconds)}</span>
                      <span>•</span>
                      <span>{track.format_name.toUpperCase()}</span>
                    </div>
                  </div>
                </button>
              {/each}
            </div>
          {:else}
            <!-- Proper List Layout (Matching mobile SongViewLayout.LIST) -->
            <div class="song-list-container">
              <div class="song-list">
                {#each filteredTracks as track, i}
                  <!-- svelte-ignore a11y_click_events_have_key_events -->
                  <div
                    class="song-list-row"
                    role="button"
                    tabindex="0"
                    class:active={currentTrack?.uri === track.uri}
                    onclick={() => handlePlayTrack(track)}
                  >
                    <div class="sl-prefix">
                      <span class="sl-index">{i + 1}</span>
                      <button
                        type="button"
                        class="sl-cover-box"
                        onclick={() => handlePlayTrack(track)}
                        aria-label="Play {track.title}"
                      >
                        {#if trackArtworkMap.has(track.uri) && trackArtworkMap.get(track.uri)}
                          <img src={trackArtworkMap.get(track.uri)} alt="" class="sl-cover-img" />
                        {:else}
                          <div class="sl-cover-placeholder">
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                          </div>
                        {/if}
                        {#if currentTrack?.uri === track.uri && isPlaying}
                          <div class="sl-cover-overlay active">
                            <span class="playing-bars"><span></span><span></span><span></span></span>
                          </div>
                        {:else}
                          <div class="sl-cover-overlay hover-play">
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                          </div>
                        {/if}
                      </button>
                    </div>

                    <button
                      type="button"
                      class="sl-main"
                      onclick={() => handlePlayTrack(track)}
                    >
                      <div class="sl-title-line">
                        <span class="sl-title" class:active-title={currentTrack?.uri === track.uri}>
                          {track.title}
                        </span>
                        {#if track.sample_rate >= 88200 || (track.bit_depth && track.bit_depth >= 24)}
                          <span class="sl-spec-pill hires">
                            {track.format_name.toUpperCase()} {Math.round(track.sample_rate / 1000)}k{track.bit_depth ? `/${track.bit_depth}b` : ''}
                          </span>
                        {:else if track.format_name && track.format_name.toUpperCase().includes('DSD')}
                          <span class="sl-spec-pill dsd">
                            {track.format_name.toUpperCase()}
                          </span>
                        {:else if track.format_name}
                          <span class="sl-spec-pill">
                            {track.format_name.toUpperCase()}
                          </span>
                        {/if}
                      </div>
                      <div class="sl-subtitle">
                        <span class="sl-artist">{track.artist || 'Unknown Artist'}</span>
                        {#if track.album}
                          <span class="sl-dot">•</span>
                          <span class="sl-album">{track.album}</span>
                        {/if}
                      </div>
                    </button>

                    <div class="sl-trailing">
                      <span class="sl-duration">{formatTime(track.duration_seconds)}</span>
                      <button
                        class="sl-action-btn"
                        class:favorited={favorites.has(track.uri)}
                        onclick={(e) => { e.stopPropagation(); toggleFavorite(track.uri); }}
                        title={favorites.has(track.uri) ? 'Remove Favorite' : 'Add to Favorites'}
                        aria-label="Favorite"
                      >
                        <svg class="icon-sm" viewBox="0 0 24 24">
                          <path fill="currentColor" d={favorites.has(track.uri) ? ICONS.heartFilled : ICONS.heart}/>
                        </svg>
                      </button>
                      <button
                        class="sl-action-btn"
                        onclick={(e) => { e.stopPropagation(); openAddToPlaylistModal(track); }}
                        title="Add to Playlist"
                        aria-label="Add to Playlist"
                      >
                        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.plus}/></svg>
                      </button>
                    </div>
                  </div>
                {/each}
              </div>
            </div>
          {/if}
        </div>

      <!-- 2. ALBUMS TAB -->
      {:else if activeTab === 'albums'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Albums</h2>
              <p class="view-subtitle">{albums.length} albums in your collection</p>
            </div>
          </div>

          <div class="albums-grid">
            {#each albums as album}
              <button
                class="album-card"
                onclick={() => {
                  selectedAlbum = album.name;
                  activeTab = 'songs';
                }}
              >
                <div class="album-art-wrap">
                  {#if getAlbumCover(album)}
                    <img src={getAlbumCover(album)} alt={album.name} class="album-cover-img" />
                  {:else}
                    <div class="art-canvas-placeholder">
                      <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                    </div>
                  {/if}
                  {#if album.hasHiRes}
                    <span class="hires-badge">HI-RES</span>
                  {/if}
                </div>
                <div class="album-details">
                  <div class="album-name" title={album.name}>{album.name}</div>
                  <div class="album-artist" title={album.artist}>{album.artist}</div>
                  <div class="album-meta">{album.trackCount} tracks • {Math.round(album.sampleRateMax / 1000)}kHz</div>
                </div>
              </button>
            {/each}
          </div>
        </div>

      <!-- 3. ARTISTS TAB -->
      {:else if activeTab === 'artists'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Artists</h2>
              <p class="view-subtitle">{artists.length} artists found</p>
            </div>
          </div>

          <div class="artists-grid">
            {#each artists as art}
              <button
                class="artist-card"
                onclick={() => {
                  selectedArtist = art.name;
                  activeTab = 'songs';
                }}
              >
                <div class="artist-avatar">
                  {#if getArtistAvatar(art)}
                    <img src={getArtistAvatar(art)} alt={art.name} class="artist-avatar-img" />
                  {:else}
                    <span>{art.name.slice(0, 2).toUpperCase()}</span>
                  {/if}
                </div>
                <div class="artist-info">
                  <div class="artist-name">{art.name}</div>
                  <div class="artist-meta">{art.albumCount} albums • {art.trackCount} tracks</div>
                </div>
              </button>
            {/each}
          </div>
        </div>

      <!-- 4. PLAYLISTS TAB (Matches mobile VeylPlaylistsScreen) -->
      {:else if activeTab === 'playlists'}
        <div class="view-container">
          {#if selectedPlaylistId && currentSelectedPlaylist}
            <!-- PLAYLIST DETAIL VIEW -->
            <div class="view-header">
              <div class="header-with-back">
                <button
                  type="button"
                  class="btn-back-nav"
                  onclick={() => (selectedPlaylistId = null)}
                  title="Back to All Playlists"
                  aria-label="Back to All Playlists"
                >
                  <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.arrowLeft}/></svg>
                </button>
                <div class="header-titles">
                  <h2 class="view-title">{currentSelectedPlaylist.name}</h2>
                  <p class="view-subtitle">
                    {selectedPlaylistTracks.length} tracks • {formatTime(selectedPlaylistDuration)}
                    {#if currentSelectedPlaylist.description}
                      • {currentSelectedPlaylist.description}
                    {/if}
                  </p>
                </div>
              </div>

              <div class="header-right-cluster">
                <button
                  class="btn btn-primary"
                  onclick={() => handlePlayPlaylist(currentSelectedPlaylist, false)}
                  disabled={selectedPlaylistTracks.length === 0}
                  title="Play all tracks"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                  <span>Play All</span>
                </button>

                <button
                  class="btn btn-secondary"
                  onclick={() => handlePlayPlaylist(currentSelectedPlaylist, true)}
                  disabled={selectedPlaylistTracks.length === 0}
                  title="Shuffle all tracks"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.shuffle}/></svg>
                  <span>Shuffle</span>
                </button>

                <button
                  class="btn btn-secondary"
                  onclick={() => handleExportM3u(currentSelectedPlaylist)}
                  disabled={selectedPlaylistTracks.length === 0}
                  title="Export playlist as M3U8"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.upload}/></svg>
                  <span>Export M3U</span>
                </button>

                <button
                  class="btn btn-danger-subtle"
                  onclick={() => handleDeletePlaylist(currentSelectedPlaylist.id)}
                  title="Delete playlist"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.trash}/></svg>
                </button>
              </div>
            </div>

            {#if selectedPlaylistTracks.length === 0}
              <div class="empty-state">
                <h3>This playlist is empty</h3>
                <p>Use the "+" button on songs or search results to add tracks to this playlist.</p>
                <button class="btn btn-primary" onclick={() => (activeTab = 'songs')}>Browse Songs</button>
              </div>
            {:else}
              <div class="song-list-container">
                <div class="song-list">
                  {#each selectedPlaylistTracks as track, i}
                    <!-- svelte-ignore a11y_click_events_have_key_events -->
                    <div
                      class="song-list-row"
                      role="button"
                      tabindex="0"
                      class:active={currentTrack?.uri === track.uri}
                      onclick={() => handlePlayTrack(track)}
                    >
                      <div class="sl-prefix">
                        <span class="sl-index">{i + 1}</span>
                        <button
                          type="button"
                          class="sl-cover-box"
                          onclick={() => handlePlayTrack(track)}
                          aria-label="Play {track.title}"
                        >
                          {#if trackArtworkMap.has(track.uri) && trackArtworkMap.get(track.uri)}
                            <img src={trackArtworkMap.get(track.uri)} alt="" class="sl-cover-img" />
                          {:else}
                            <div class="sl-cover-placeholder">
                              <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                            </div>
                          {/if}
                          {#if currentTrack?.uri === track.uri && isPlaying}
                            <div class="sl-cover-overlay active">
                              <span class="playing-bars"><span></span><span></span><span></span></span>
                            </div>
                          {:else}
                            <div class="sl-cover-overlay hover-play">
                              <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                            </div>
                          {/if}
                        </button>
                      </div>

                      <button
                        type="button"
                        class="sl-main"
                        onclick={() => handlePlayTrack(track)}
                      >
                        <div class="sl-title-line">
                          <span class="sl-title" class:active-title={currentTrack?.uri === track.uri}>
                            {track.title}
                          </span>
                          {#if track.sample_rate >= 88200 || (track.bit_depth && track.bit_depth >= 24)}
                            <span class="sl-spec-pill hires">
                              {track.format_name.toUpperCase()} {Math.round(track.sample_rate / 1000)}k{track.bit_depth ? `/${track.bit_depth}b` : ''}
                            </span>
                          {:else if track.format_name}
                            <span class="sl-spec-pill">{track.format_name.toUpperCase()}</span>
                          {/if}
                        </div>
                        <div class="sl-subtitle">
                          <span class="sl-artist">{track.artist || 'Unknown Artist'}</span>
                          {#if track.album}
                            <span class="sl-dot">•</span>
                            <span class="sl-album">{track.album}</span>
                          {/if}
                        </div>
                      </button>

                      <div class="sl-trailing">
                        <span class="sl-duration">{formatTime(track.duration_seconds)}</span>
                        <button
                          class="sl-action-btn"
                          class:favorited={favorites.has(track.uri)}
                          onclick={(e) => { e.stopPropagation(); toggleFavorite(track.uri); }}
                          title="Favorite"
                          aria-label="Favorite"
                        >
                          <svg class="icon-sm" viewBox="0 0 24 24">
                            <path fill="currentColor" d={favorites.has(track.uri) ? ICONS.heartFilled : ICONS.heart}/>
                          </svg>
                        </button>
                        <button
                          class="sl-action-btn remove-btn"
                          onclick={(e) => { e.stopPropagation(); handleRemoveTrackFromPlaylist(i); }}
                          title="Remove from playlist"
                          aria-label="Remove from playlist"
                        >
                          <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
                        </button>
                      </div>
                    </div>
                  {/each}
                </div>
              </div>
            {/if}
          {:else}
            <!-- PLAYLISTS OVERVIEW LIST / GRID -->
            <div class="view-header">
              <div>
                <h2 class="view-title">Playlists</h2>
                <p class="view-subtitle">{playlists.length} playlists • {favorites.size} favorites</p>
              </div>

              <div class="header-right-cluster">
                <button
                  class="btn btn-primary"
                  onclick={() => {
                    newPlaylistName = '';
                    newPlaylistDesc = '';
                    showCreatePlaylistModal = true;
                  }}
                  title="Create new playlist"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.plus}/></svg>
                  <span>New Playlist</span>
                </button>

                <button
                  class="btn btn-secondary"
                  onclick={handleImportM3u}
                  title="Import M3U or M3U8 file"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.upload}/></svg>
                  <span>Import M3U</span>
                </button>

                <button
                  class="btn btn-spotify"
                  onclick={() => {
                    spotifyUrlInput = '';
                    spotifyImportError = null;
                    showSpotifyImportModal = true;
                  }}
                  title="Import from Spotify"
                >
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.spotify}/></svg>
                  <span>Spotify</span>
                </button>
              </div>
            </div>

            <!-- Smart Hero Cards: Favorites & History (matching mobile) -->
            <div class="smart-playlists-row">
              <div
                class="smart-card favorites-smart-card"
                role="button"
                tabindex="0"
                onclick={() => (activeTab = 'favorites')}
                onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (activeTab = 'favorites')}
              >
                <div class="smart-card-art heart-bg">
                  <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.heartFilled}/></svg>
                </div>
                <div class="smart-card-info">
                  <div class="smart-card-title">Favorites</div>
                  <div class="smart-card-sub">{favorites.size} loved tracks</div>
                </div>
                <button
                  class="card-play-action"
                  onclick={(e) => {
                    e.stopPropagation();
                    const favList = libraryTracks.filter((t) => favorites.has(t.uri));
                    if (favList.length > 0) {
                      queue = favList;
                      handlePlayTrack(favList[0]);
                    } else {
                      showToast('No favorites yet');
                    }
                  }}
                  title="Play Favorites"
                >
                  <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                </button>
              </div>

              <div
                class="smart-card history-smart-card"
                role="button"
                tabindex="0"
                onclick={() => (activeTab = 'queue')}
                onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (activeTab = 'queue')}
              >
                <div class="smart-card-art history-bg">
                  <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.queueList}/></svg>
                </div>
                <div class="smart-card-info">
                  <div class="smart-card-title">History & Queue</div>
                  <div class="smart-card-sub">{historyTracks.length} recent • {queue.length} in queue</div>
                </div>
                <button
                  class="card-play-action"
                  onclick={(e) => {
                    e.stopPropagation();
                    if (historyTracks.length > 0) {
                      queue = historyTracks;
                      handlePlayTrack(historyTracks[0]);
                    } else if (queue.length > 0) {
                      handlePlayTrack(queue[0]);
                    } else {
                      showToast('No recent history');
                    }
                  }}
                  title="Play History"
                >
                  <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                </button>
              </div>
            </div>

            <!-- User Playlists Grid -->
            {#if playlists.length === 0}
              <div class="empty-state" style="margin-top: 36px;">
                <h3>No playlists created yet</h3>
                <p>Create a custom playlist, import an M3U file, or import any public playlist from Spotify.</p>
                <div class="empty-actions-row">
                  <button
                    class="btn btn-primary"
                    onclick={() => {
                      newPlaylistName = '';
                      newPlaylistDesc = '';
                      showCreatePlaylistModal = true;
                    }}
                  >
                    + New Playlist
                  </button>
                  <button
                    class="btn btn-spotify"
                    onclick={() => {
                      spotifyUrlInput = '';
                      spotifyImportError = null;
                      showSpotifyImportModal = true;
                    }}
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.spotify}/></svg>
                    Import Spotify
                  </button>
                </div>
              </div>
            {:else}
              <div class="playlists-grid">
                {#each playlists as pl}
                  <div
                    class="playlist-card"
                    role="button"
                    tabindex="0"
                    onclick={() => (selectedPlaylistId = pl.id)}
                    onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (selectedPlaylistId = pl.id)}
                  >
                    <div class="playlist-card-art">
                      {#if pl.coverUrl}
                        <img src={pl.coverUrl} alt={pl.name} class="playlist-card-img" />
                      {:else if pl.trackUris.length > 0 && trackArtworkMap.has(pl.trackUris[0]) && trackArtworkMap.get(pl.trackUris[0])}
                        <img src={trackArtworkMap.get(pl.trackUris[0])} alt={pl.name} class="playlist-card-img" />
                      {:else}
                        <div class="playlist-card-placeholder">
                          <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.playlist}/></svg>
                        </div>
                      {/if}
                      <button
                        class="card-play-action"
                        onclick={(e) => {
                          e.stopPropagation();
                          handlePlayPlaylist(pl, false);
                        }}
                        title="Play Playlist"
                      >
                        <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                      </button>
                    </div>

                    <div class="playlist-card-body">
                      <div class="playlist-card-title" title={pl.name}>{pl.name}</div>
                      <div class="playlist-card-meta">
                        <span>{pl.trackUris.length} tracks</span>
                        <div class="card-btn-cluster">
                          <button
                            class="download-action-btn"
                            onclick={(e) => {
                              e.stopPropagation();
                              handleExportM3u(pl);
                            }}
                            title="Export M3U8"
                          >
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.upload}/></svg>
                          </button>
                          <button
                            class="download-action-btn delete-btn"
                            onclick={(e) => {
                              e.stopPropagation();
                              handleDeletePlaylist(pl.id);
                            }}
                            title="Delete Playlist"
                          >
                            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.trash}/></svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                {/each}
              </div>
            {/if}
          {/if}
        </div>

      <!-- 5. NOW PLAYING COCKPIT TAB -->
      {:else if activeTab === 'now_playing'}
        <div class="cockpit-container">
          <div class="cockpit-header">
            <div class="mode-pills">
              <button
                class="mode-btn"
                class:active={!isLyricsMode && !isEqMode}
                onclick={() => { isLyricsMode = false; isEqMode = false; }}
              >
                Cover
              </button>
              <button
                class="mode-btn"
                class:active={isLyricsMode && !isEqMode}
                onclick={() => { isLyricsMode = true; isEqMode = false; }}
              >
                Synced Lyrics
              </button>
              <button
                class="mode-btn"
                class:active={isEqMode}
                onclick={() => { isEqMode = true; }}
              >
                Equalizer
              </button>
            </div>
          </div>

          <div class="cockpit-body">
            <!-- Left Hero Stage (Cover, Synced Lyrics, or 10-Band Equalizer) -->
            <div class="cockpit-stage">
              {#if !isLyricsMode && !isEqMode}
                <div class="artwork-immersive-wrap">
                  {#if currentArtwork}
                    <img src={currentArtwork} alt="Cover" class="large-cover-img" />
                  {:else}
                    <div class="large-cover-placeholder">
                      <svg class="icon-xl" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
                    </div>
                  {/if}
                </div>
              {:else if isLyricsMode && !isEqMode}
                <div class="lyrics-card-panel">
                  {#if isLyricsLoading}
                    <div class="lyrics-status-box">
                      <div class="status-pulse-dot"></div>
                      <p class="status-msg">Fetching synchronized lyrics...</p>
                      <p class="status-sub">Checking offline sidecar and studio database</p>
                    </div>
                  {:else if syncedLyrics.length === 0}
                    <div class="lyrics-status-box">
                      <svg class="icon-lg" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.lyrics}/></svg>
                      <p class="status-msg">{lyricsError || 'No synchronized lyrics available'}</p>
                      <p class="status-sub">Add a matching .lrc file in your track's directory for offline sync</p>
                    </div>
                  {:else}
                    <div class="lyrics-scroll" bind:this={lyricsContainerEl}>
                      {#each syncedLyrics as line, i}
                        <button
                          type="button"
                          class="lyric-line"
                          class:active={i === activeLyricIndex}
                          class:past={i < activeLyricIndex}
                          data-lyric-index={i}
                          onclick={() => {
                            positionSec = line.sec;
                            api.seek(line.sec);
                          }}
                        >
                          {line.text}
                        </button>
                      {/each}
                    </div>
                  {/if}
                </div>
              {:else}
                <!-- Embedded Equalizer Stage in Cockpit -->
                <div class="cockpit-eq-panel">
                  <div class="cockpit-eq-header">
                    <div>
                      <h3 class="cockpit-eq-title">PARAMETRIC EQUALIZER</h3>
                      <p class="cockpit-eq-sub">10-band IIR filter engine with zero clipping</p>
                    </div>
                    <div class="cockpit-eq-actions">
                      <button
                        class="btn btn-sm"
                        class:btn-primary={eqEnabled}
                        class:btn-secondary={!eqEnabled}
                        onclick={toggleEq}
                      >
                        {eqEnabled ? 'Active' : 'Bypass'}
                      </button>
                    </div>
                  </div>

                  <!-- Presets -->
                  <div class="cockpit-presets-row">
                    {#each Object.keys(EQ_PRESETS) as preset}
                      <button
                        class="cockpit-preset-chip"
                        class:active={activeEqPreset === preset}
                        onclick={() => applyEqPreset(preset)}
                      >
                        {preset}
                      </button>
                    {/each}
                  </div>

                  <!-- Transfer Curve -->
                  <div class="cockpit-curve-card">
                    <svg class="curve-svg" viewBox="0 0 600 110" preserveAspectRatio="none">
                      <line x1="0" y1="55" x2="600" y2="55" stroke="rgba(255,255,255,0.12)" stroke-width="1" />
                      <line x1="0" y1="27" x2="600" y2="27" stroke="rgba(255,255,255,0.06)" stroke-dasharray="4" />
                      <line x1="0" y1="83" x2="600" y2="83" stroke="rgba(255,255,255,0.06)" stroke-dasharray="4" />

                      {#if eqCurve.length > 1}
                        {@const pathD = eqCurve.reduce((acc, pt, idx) => {
                          const x = (idx / (eqCurve.length - 1)) * 600;
                          const y = 55 - (pt.gain_db / 12) * 45;
                          return idx === 0 ? `M ${x} ${y}` : `${acc} L ${x} ${y}`;
                        }, '')}
                        <path d={pathD} fill="none" stroke="var(--signal-accent)" stroke-width="2.2" />
                        <path d="{pathD} L 600 110 L 0 110 Z" fill="url(#eqGlowCockpit)" opacity="0.18" />
                      {/if}

                      <defs>
                        <linearGradient id="eqGlowCockpit" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stop-color="var(--signal-accent)" stop-opacity="0.8" />
                          <stop offset="100%" stop-color="var(--signal-accent)" stop-opacity="0" />
                        </linearGradient>
                      </defs>
                    </svg>
                  </div>

                  <!-- 10-Band Sliders -->
                  <div class="cockpit-bands-strip">
                    {#each eqBands as band, i}
                      <div class="cockpit-band-col">
                        <span class="cockpit-gain-val" class:boosted={band.gain_db > 0} class:cut={band.gain_db < 0}>
                          {band.gain_db > 0 ? `+${band.gain_db.toFixed(1)}` : band.gain_db.toFixed(1)}
                        </span>
                        <div class="cockpit-slider-wrap">
                          <input
                            type="range"
                            min="-12"
                            max="12"
                            step="0.5"
                            value={band.gain_db}
                            oninput={(e) => {
                              const v = parseFloat((e.target as HTMLInputElement).value);
                              band.gain_db = v;
                              activeEqPreset = 'Custom';
                              api.setEqualizerBand(i, v, band.frequency, band.q).then(() => {
                                api.getEqualizerCurve(100).then((c) => (eqCurve = c));
                              });
                            }}
                          />
                        </div>
                        <div class="cockpit-steppers">
                          <button class="step-btn-mini" onclick={() => adjustBandGain(i, -0.5)}>−</button>
                          <button class="step-btn-mini" onclick={() => adjustBandGain(i, +0.5)}>+</button>
                        </div>
                        <span class="cockpit-freq-label">
                          {band.frequency >= 1000 ? `${(band.frequency / 1000).toFixed(0)}k` : `${Math.round(band.frequency)}`}
                        </span>
                      </div>
                    {/each}
                  </div>
                </div>
              {/if}
            </div>

            <!-- Right Telemetry & Controls Deck -->
            <div class="cockpit-meters-deck">
              <div class="track-now-heading">
                <span class="now-badge">NOW PLAYING</span>
                <h1 class="now-title">{currentTrack?.title || 'No Track Selected'}</h1>
                <h2 class="now-artist">{currentTrack?.artist || 'Veyl Audio Engine'} • {currentTrack?.album || ''}</h2>
              </div>

              <!-- Stereo VU Analog Meters -->
              <div class="analog-vu-cluster">
                <div class="vu-title-row">
                  <span>STEREO LEVEL METERS</span>
                  <span class="vu-readout">L: {(rmsLeft * 100).toFixed(0)}% | R: {(rmsRight * 100).toFixed(0)}%</span>
                </div>

                <div class="vu-channel">
                  <span class="vu-ch-label">L</span>
                  <div class="vu-bar-track">
                    <div class="vu-bar-fill" style="width: {Math.min(100, rmsLeft * 120)}%"></div>
                    <div class="vu-peak-needle" style="left: {Math.min(99, peakLeft * 120)}%"></div>
                  </div>
                </div>

                <div class="vu-channel">
                  <span class="vu-ch-label">R</span>
                  <div class="vu-bar-track">
                    <div class="vu-bar-fill" style="width: {Math.min(100, rmsRight * 120)}%"></div>
                    <div class="vu-peak-needle" style="left: {Math.min(99, peakRight * 120)}%"></div>
                  </div>
                </div>

                <div class="vu-scale">
                  <span>-48 dB</span>
                  <span>-24 dB</span>
                  <span>-12 dB</span>
                  <span>-6 dB</span>
                  <span>0 dB</span>
                </div>
              </div>

              <!-- 16-Band Spectrum Analyzer -->
              <div class="full-spectrum-panel">
                <div class="spec-heading">
                  <span>16-BAND HARDWARE SPECTRUM</span>
                  <span class="spec-khz">31 Hz - 16 kHz</span>
                </div>
                <div class="spec-bars-container">
                  {#each spectrum16 as val, idx}
                    <div class="spec-bar-col">
                      <div class="spec-bar-fill" style="height: {Math.max(4, Math.min(100, val * 120))}px"></div>
                      <span class="freq-axis-label">
                        {idx === 0 ? '31' : idx === 3 ? '125' : idx === 7 ? '1k' : idx === 11 ? '4k' : idx === 15 ? '16k' : ''}
                      </span>
                    </div>
                  {/each}
                </div>
              </div>
            </div>
          </div>
        </div>

      <!-- 5. EQUALIZER TAB -->
      {:else if activeTab === 'equalizer'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Parametric Equalizer</h2>
              <p class="view-subtitle">10-band biquad IIR filter engine with zero clipping</p>
            </div>

            <div class="header-actions">
              <button
                class="btn"
                class:btn-primary={eqEnabled}
                class:btn-secondary={!eqEnabled}
                onclick={toggleEq}
              >
                {eqEnabled ? 'Active' : 'Bypass'}
              </button>
            </div>
          </div>

          <!-- EQ Presets Selector -->
          <div class="presets-row">
            <span class="preset-label">PRESETS:</span>
            <div class="preset-pills">
              {#each Object.keys(EQ_PRESETS) as preset}
                <button
                  class="preset-btn"
                  class:active={activeEqPreset === preset}
                  onclick={() => applyEqPreset(preset)}
                >
                  {preset}
                </button>
              {/each}
            </div>
          </div>

          <!-- Transfer Function Canvas -->
          <div class="curve-card">
            <div class="curve-header">
              <span class="spec-label">TRANSFER CURVE (20 Hz - 20 kHz)</span>
              <span class="spec-rate">{eqCurve.length} analysis points</span>
            </div>
            <svg class="curve-svg" viewBox="0 0 800 200" preserveAspectRatio="none">
              <line x1="0" y1="100" x2="800" y2="100" stroke="rgba(255,255,255,0.12)" stroke-width="1" />
              <line x1="0" y1="50" x2="800" y2="50" stroke="rgba(255,255,255,0.06)" stroke-dasharray="4" />
              <line x1="0" y1="150" x2="800" y2="150" stroke="rgba(255,255,255,0.06)" stroke-dasharray="4" />

              {#if eqCurve.length > 1}
                {@const pathD = eqCurve.reduce((acc, pt, idx) => {
                  const x = (idx / (eqCurve.length - 1)) * 800;
                  const y = 100 - (pt.gain_db / 12) * 80;
                  return idx === 0 ? `M ${x} ${y}` : `${acc} L ${x} ${y}`;
                }, '')}
                <path d={pathD} fill="none" stroke="var(--signal-accent)" stroke-width="2.5" />
                <path d="{pathD} L 800 200 L 0 200 Z" fill="url(#eqGlow)" opacity="0.18" />
              {/if}

              <defs>
                <linearGradient id="eqGlow" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="var(--signal-accent)" stop-opacity="0.8" />
                  <stop offset="100%" stop-color="var(--signal-accent)" stop-opacity="0" />
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
                      activeEqPreset = 'Custom';
                      api.setEqualizerBand(i, v, band.frequency, band.q).then(() => {
                        api.getEqualizerCurve(100).then((c) => (eqCurve = c));
                      });
                    }}
                  />
                </div>

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

      <!-- 6. QUEUE TAB -->
      {:else if activeTab === 'queue'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Playback Queue</h2>
              <p class="view-subtitle">{queue.length} tracks scheduled in current session</p>
            </div>
            <div class="header-actions">
              <button class="btn btn-secondary" onclick={shuffleQueue}>Shuffle Queue</button>
              <button class="btn btn-secondary" onclick={() => (queue = [])}>Clear Queue</button>
            </div>
          </div>

          {#if queue.length === 0}
            <div class="empty-state">
              <h3>Queue is empty</h3>
              <p>Add songs from your library to start listening.</p>
            </div>
          {:else}
            <div class="queue-list" role="list">
              {#each queue as track, i (track.uri + '_' + i)}
                <!-- svelte-ignore a11y_click_events_have_key_events -->
                <div
                  class="queue-item"
                  role="button"
                  tabindex="0"
                  class:active={currentTrack?.uri === track.uri}
                  class:dragging={draggedQueueIndex === i}
                  class:drag-over={dragOverIndex === i}
                  ondragover={(e) => handleQueueDragOver(e, i)}
                  ondragenter={() => (dragOverIndex = i)}
                  ondragleave={() => { if (dragOverIndex === i) dragOverIndex = null; }}
                  ondrop={(e) => handleQueueDrop(e, i)}
                  onclick={() => handlePlayTrack(track)}
                >
                  <!-- svelte-ignore a11y_no_static_element_interactions -->
                  <div
                    class="q-drag-handle"
                    title="Drag to reorder"
                    aria-label="Drag to reorder"
                    draggable="true"
                    ondragstart={(e) => {
                      e.stopPropagation();
                      handleQueueDragStart(e, i);
                    }}
                    ondragend={handleQueueDragEnd}
                    onclick={(e) => e.stopPropagation()}
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.dragHandle}/></svg>
                  </div>
                  <div class="q-index">{i + 1}</div>
                  <div class="q-info">
                    <div class="q-title">{track.title}</div>
                    <div class="q-sub">{track.artist || 'Unknown'} • {track.format_name} {Math.round(track.sample_rate / 1000)}kHz</div>
                  </div>
                  <!-- svelte-ignore a11y_no_static_element_interactions -->
                  <div class="q-reorder-actions" onclick={(e) => e.stopPropagation()}>
                    <button
                      class="q-mini-btn"
                      disabled={i === 0}
                      onclick={(e) => { e.stopPropagation(); moveQueueItem(i, -1); }}
                      title="Move up"
                      aria-label="Move up"
                    >
                      ▲
                    </button>
                    <button
                      class="q-mini-btn"
                      disabled={i === queue.length - 1}
                      onclick={(e) => { e.stopPropagation(); moveQueueItem(i, 1); }}
                      title="Move down"
                      aria-label="Move down"
                    >
                      ▼
                    </button>
                  </div>
                  <div class="q-dur">{formatTime(track.duration_seconds)}</div>
                  <button
                    class="q-play-btn"
                    onclick={(e) => { e.stopPropagation(); handlePlayTrack(track); }}
                    title="Play track"
                    aria-label="Play track"
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24">
                      <path fill="currentColor" d={currentTrack?.uri === track.uri && isPlaying ? ICONS.pause : ICONS.play}/>
                    </svg>
                  </button>
                  <button
                    class="q-del-btn"
                    onclick={(e) => { e.stopPropagation(); removeQueueItem(i); }}
                    title="Remove from queue"
                    aria-label="Remove from queue"
                  >
                    <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
                  </button>
                </div>
              {/each}
            </div>
          {/if}

          <!-- History Section -->
          {#if historyTracks.length > 0}
            <div class="history-section">
              <h3 class="panel-section-title">RECENTLY PLAYED</h3>
              <div class="history-list">
                {#each historyTracks.slice(0, 10) as hist}
                  <button
                    type="button"
                    class="hist-item"
                    onclick={() => handlePlayTrack(hist)}
                  >
                    <span class="hist-title">{hist.title}</span>
                    <span class="hist-artist">{hist.artist}</span>
                    <span class="row-play-btn inline-btn">
                      <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.play}/></svg>
                    </span>
                  </button>
                {/each}
              </div>
            </div>
          {/if}
        </div>

      <!-- 7. SETTINGS TAB (Exact match to mobile VeylSettingsRedesigned) -->
      {:else if activeTab === 'settings'}
        <div class="view-container">
          <div class="view-header">
            <div>
              <h2 class="view-title">Settings</h2>
              <p class="view-subtitle">Make Veyl feel right for you.</p>
            </div>
          </div>

          <!-- Section 1: Appearance (Dark / Light) -->
          <div class="settings-card">
            <h3 class="panel-section-title">APPEARANCE</h3>
            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Dark appearance</div>
                <div class="setting-desc">Use Veyl's dark listening environment</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={isDarkMode}
                class="switch-toggle"
                class:checked={isDarkMode}
                onclick={toggleAppearance}
                title="Toggle Dark Appearance"
                aria-label="Toggle Dark Appearance"
              >
                <span class="switch-thumb"></span>
              </button>
            </div>
          </div>

          <!-- Section 2: Library -->
          <div class="settings-card">
            <h3 class="panel-section-title">LIBRARY</h3>
            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Music folder</div>
                <div class="setting-desc">Choose where your local lossless music lives</div>
              </div>
              <button class="btn btn-secondary" onclick={handleScanFolder}>
                <span>Change Folder</span>
              </button>
            </div>

            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Rescan library</div>
                <div class="setting-desc">{libraryTracks.length} tracks available in local library</div>
              </div>
              <button class="btn btn-secondary" onclick={handleScanFolder}>
                <span>Rescan</span>
              </button>
            </div>
          </div>

          <!-- Section 3: Listening -->
          <div class="settings-card">
            <h3 class="panel-section-title">LISTENING</h3>
            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Gapless playback</div>
                <div class="setting-desc">Move between tracks without a pause</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={gaplessEnabled}
                class="switch-toggle"
                class:checked={gaplessEnabled}
                onclick={() => (gaplessEnabled = !gaplessEnabled)}
                title="Toggle Gapless Playback"
                aria-label="Toggle Gapless Playback"
              >
                <span class="switch-thumb"></span>
              </button>
            </div>

            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Find synced lyrics</div>
                <div class="setting-desc">Fetch lyrics when available for active playback</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={autoFetchLyrics}
                class="switch-toggle"
                class:checked={autoFetchLyrics}
                onclick={() => {
                  autoFetchLyrics = !autoFetchLyrics;
                  if (autoFetchLyrics && currentTrack) loadLyricsForTrack(currentTrack);
                }}
                title="Toggle Synced Lyrics"
                aria-label="Toggle Synced Lyrics"
              >
                <span class="switch-thumb"></span>
              </button>
            </div>
          </div>

          <!-- Section 4: Advanced Audio -->
          <div class="settings-card">
            <h3 class="panel-section-title">ADVANCED AUDIO</h3>

            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Bit-perfect output</div>
                <div class="setting-desc">Use the source sample rate with a compatible DAC</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={isBitPerfect}
                class="switch-toggle"
                class:checked={isBitPerfect}
                onclick={handleToggleBitPerfect}
                title="Toggle Bit-Perfect Output"
                aria-label="Toggle Bit-Perfect Output"
              >
                <span class="switch-thumb"></span>
              </button>
            </div>

            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">DSD playback</div>
                <div class="setting-desc">{dsdMode === 'dop' ? 'DoP (Bit-Perfect)' : 'PCM conversion (88.2 kHz)'}</div>
              </div>
              <div class="setting-choice">
                <button
                  class="chip"
                  class:active={dsdMode === 'pcm'}
                  onclick={() => handleDsdModeChange('pcm')}
                >
                  PCM conversion
                </button>
                <button
                  class="chip"
                  class:active={dsdMode === 'dop'}
                  onclick={() => handleDsdModeChange('dop')}
                >
                  DoP
                </button>
              </div>
            </div>

            <div class="setting-row">
              <div class="setting-text">
                <div class="setting-name">Hardware sample rate</div>
                <div class="setting-desc">
                  {isSampleRateAuto
                    ? `Auto (Native track rate matching — ${(sampleRate / 1000).toFixed(1)} kHz active)`
                    : `Fixed hardware clock: ${(targetSampleRate / 1000).toFixed(1)} kHz`}
                </div>
              </div>
              <div class="setting-choice">
                <button
                  class="chip"
                  class:active={isSampleRateAuto}
                  onclick={handleSetAutoSampleRate}
                  title="Auto native track rate switching"
                >
                  Auto
                </button>
                {#each [44100, 48000, 88200, 96000, 176400, 192000] as rate}
                  <button
                    class="chip"
                    class:active={!isSampleRateAuto && targetSampleRate === rate}
                    onclick={() => handleSampleRateChange(rate)}
                  >
                    {(rate / 1000).toFixed(1)}k
                  </button>
                {/each}
              </div>
            </div>
          </div>
        </div>
      {/if}
    </main>
  </div>

  <!-- Persistent Audiophile Player Dock (Bottom - Spotify Reference Architecture) -->
  <footer class="player-dock">
    <!-- Left: Track Metadata, Cover & Actions -->
    <div class="dock-meta">
      <button
        type="button"
        class="artwork-thumb"
        onclick={() => (activeTab = 'now_playing')}
        title="Open Now Playing"
      >
        {#if currentArtwork}
          <img src={currentArtwork} alt="Cover" />
        {:else}
          <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.disc}/></svg>
        {/if}
      </button>

      <div class="meta-texts">
        <div class="title-row">
          <span class="active-title">{currentTrack?.title || 'No Track Selected'}</span>
        </div>
        <div class="active-artist">{currentTrack?.artist || 'Veyl'}</div>
      </div>

      {#if currentTrack}
        <button
          class="dock-fav-btn"
          class:favorited={favorites.has(currentTrack.uri)}
          onclick={() => currentTrack && toggleFavorite(currentTrack.uri)}
          title="Save to Liked Songs"
          aria-label="Save to Liked Songs"
        >
          <svg class="icon-sm" viewBox="0 0 24 24">
            <path fill="currentColor" d={favorites.has(currentTrack.uri) ? ICONS.heartFilled : ICONS.heart}/>
          </svg>
        </button>

        <button
          class="dock-fav-btn"
          onclick={() => currentTrack && openAddToPlaylistModal(currentTrack)}
          title="Add to Playlist"
          aria-label="Add to Playlist"
        >
          <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.plus}/></svg>
        </button>
      {/if}
    </div>

    <!-- Center: Transport & Progress Controls -->
    <div class="dock-transport">
      <div class="transport-buttons">
        <button
          class="ctrl-btn sub-btn"
          class:active={isShuffle}
          onclick={toggleShuffle}
          title="Enable shuffle"
          aria-label="Enable shuffle"
        >
          <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.shuffle}/></svg>
        </button>

        <button class="ctrl-btn" onclick={playPrevInQueue} title="Previous" aria-label="Previous">
          <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.skipPrevious}/></svg>
        </button>

        <button
          class="ctrl-btn play-main-btn"
          onclick={togglePlay}
          title={isPlaying ? 'Pause' : 'Play'}
          aria-label={isPlaying ? 'Pause' : 'Play'}
        >
          <svg class="icon-play-white" viewBox="0 0 24 24">
            <path fill="currentColor" d={isPlaying ? ICONS.pause : ICONS.play}/>
          </svg>
        </button>

        <button class="ctrl-btn" onclick={playNextInQueue} title="Next" aria-label="Next">
          <svg class="icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.skipNext}/></svg>
        </button>

        <button
          class="ctrl-btn sub-btn"
          class:active={repeatMode !== 'off'}
          onclick={cycleRepeatMode}
          title={`Repeat: ${repeatMode}`}
          aria-label={`Repeat: ${repeatMode}`}
        >
          <svg class="icon-sm" viewBox="0 0 24 24">
            <path fill="currentColor" d={repeatMode === 'one' ? ICONS.repeatOne : ICONS.repeat}/>
          </svg>
        </button>
      </div>

      <!-- Scrubber Track & Durations -->
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
            aria-label="Seek track position"
          />
        </div>
        <span class="time-text right">{formatTime(durationSec)}</span>
      </div>
    </div>

    <!-- Right: Telemetry, Lyrics, Queue, Volume & Fullscreen -->
    <div class="dock-telemetry">
      <!-- Synced Lyrics Toggle -->
      <button
        class="dock-icon-action"
        class:active={activeTab === 'lyrics'}
        onclick={() => (activeTab = activeTab === 'lyrics' ? 'songs' : 'lyrics')}
        title="Synced Lyrics"
        aria-label="Synced Lyrics"
      >
        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.lyrics}/></svg>
      </button>

      <!-- Queue Drawer Toggle -->
      <button
        class="dock-icon-action"
        class:active={activeTab === 'queue'}
        onclick={() => (activeTab = activeTab === 'queue' ? 'songs' : 'queue')}
        title="Queue ({queue.length})"
        aria-label="Queue ({queue.length})"
      >
        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.queueList}/></svg>
      </button>

      <!-- Audiophile DAC Spec Badge (Informational Display) -->
      <div
        class="dock-spec-pill"
        title="Hardware DAC Spec: {formatBadge}"
      >
        <span class="dot-live-sm"></span>
        <span>{formatBadge}</span>
      </div>

      <!-- 16-band live spectrum visualizer -->
      <button
        type="button"
        class="spectrum-visualizer"
        onclick={() => (activeTab = 'now_playing')}
        title="Hardware Spectrum (Click for Now Playing)"
      >
        {#each spectrum16 as val}
          <div
            class="spec-bar"
            style="height: {Math.max(3, Math.min(22, val * 28))}px"
          ></div>
        {/each}
      </button>

      <!-- Volume Controls -->
      <div class="vol-cluster">
        <button class="vol-btn" onclick={toggleMute} title={isMuted ? 'Unmute' : 'Mute'} aria-label={isMuted ? 'Unmute' : 'Mute'}>
          <svg class="icon-sm" viewBox="0 0 24 24">
            <path fill="currentColor" d={isMuted ? ICONS.volumeMute : ICONS.volumeUp}/>
          </svg>
        </button>
        <input
          class="vol-slider"
          type="range"
          min="0"
          max="1.5"
          step="0.01"
          value={isMuted ? 0 : volume}
          oninput={handleVolume}
          aria-label="Volume slider"
        />
      </div>

      <!-- Fullscreen / Cockpit Expand Button -->
      <button
        class="dock-icon-action"
        class:active={activeTab === 'now_playing'}
        onclick={() => (activeTab = activeTab === 'now_playing' ? 'songs' : 'now_playing')}
        title="Full Screen / Cockpit"
        aria-label="Full Screen"
      >
        <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.expand}/></svg>
      </button>
    </div>
  </footer>

  <!-- Toast Feedback Notification -->
  {#if toastMessage}
    <div class="veyl-toast" role="status">
      <span>{toastMessage}</span>
    </div>
  {/if}

  <!-- Create Playlist Modal -->
  {#if showCreatePlaylistModal}
    <div class="modal-backdrop" role="presentation" onclick={() => (showCreatePlaylistModal = false)}>
      <div class="modal-card" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.stopPropagation()}>
        <div class="modal-header">
          <h3 class="modal-title">Create New Playlist</h3>
          <button class="modal-close-btn" aria-label="Close" onclick={() => (showCreatePlaylistModal = false)}>
            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
          </button>
        </div>
        <div class="modal-body">
          <label class="modal-label" for="new-playlist-name-input">Playlist Name</label>
          <input
            id="new-playlist-name-input"
            type="text"
            class="modal-input"
            placeholder="My Audiophile Mix"
            bind:value={newPlaylistName}
            onkeydown={(e) => e.key === 'Enter' && handleCreatePlaylistSubmit()}
          />
          <label class="modal-label" for="new-playlist-desc-input" style="margin-top: 14px;">Description (Optional)</label>
          <input
            id="new-playlist-desc-input"
            type="text"
            class="modal-input"
            placeholder="High-resolution FLAC and studio tracks"
            bind:value={newPlaylistDesc}
            onkeydown={(e) => e.key === 'Enter' && handleCreatePlaylistSubmit()}
          />
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" onclick={() => (showCreatePlaylistModal = false)}>Cancel</button>
          <button class="btn btn-primary" onclick={handleCreatePlaylistSubmit} disabled={!newPlaylistName.trim()}>
            Create Playlist
          </button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Spotify Import Modal -->
  {#if showSpotifyImportModal}
    <div class="modal-backdrop" role="presentation" onclick={() => (!isSpotifyImporting && (showSpotifyImportModal = false))}>
      <div class="modal-card" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.stopPropagation()}>
        <div class="modal-header">
          <div class="modal-header-with-icon">
            <svg class="icon spotify-brand-icon" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.spotify}/></svg>
            <h3 class="modal-title">Import Spotify Playlist</h3>
          </div>
          {#if !isSpotifyImporting}
            <button class="modal-close-btn" aria-label="Close" onclick={() => (showSpotifyImportModal = false)}>
              <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
            </button>
          {/if}
        </div>
        <div class="modal-body">
          <p class="modal-desc">
            Paste any Spotify playlist or album link. No login required.
            Tracks already in your local library will match automatically. Any tracks not found locally will be added as high-fidelity online cloud streams with streaming and offline download capability.
          </p>
          <label class="modal-label" for="spotify-url-input">Spotify URL</label>
          <input
            id="spotify-url-input"
            type="text"
            class="modal-input"
            placeholder="https://open.spotify.com/playlist/..."
            bind:value={spotifyUrlInput}
            disabled={isSpotifyImporting}
            onkeydown={(e) => e.key === 'Enter' && handleSpotifyImport()}
          />
          {#if spotifyImportError}
            <div class="modal-error-text">{spotifyImportError}</div>
          {/if}
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" onclick={() => (showSpotifyImportModal = false)} disabled={isSpotifyImporting}>
            Cancel
          </button>
          <button class="btn btn-spotify" onclick={handleSpotifyImport} disabled={isSpotifyImporting || !spotifyUrlInput.trim()}>
            {#if isSpotifyImporting}
              <span class="inline-spinner"></span>
              <span>Importing Tracks...</span>
            {:else}
              <span>Import Playlist</span>
            {/if}
          </button>
        </div>
      </div>
    </div>
  {/if}

  <!-- Add to Playlist Modal -->
  {#if showAddToPlaylistModal && trackToAddToPlaylist}
    <div class="modal-backdrop" role="presentation" onclick={() => (showAddToPlaylistModal = false)}>
      <div class="modal-card" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.stopPropagation()}>
        <div class="modal-header">
          <h3 class="modal-title">Add to Playlist</h3>
          <button class="modal-close-btn" aria-label="Close" onclick={() => (showAddToPlaylistModal = false)}>
            <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.close}/></svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="adding-track-preview">
            <span class="preview-title">{trackToAddToPlaylist.title}</span>
            <span class="preview-artist">{trackToAddToPlaylist.artist || 'Unknown Artist'}</span>
          </div>

          <div class="modal-playlist-list">
            {#if playlists.length === 0}
              <div class="empty-substate">
                <p>No playlists yet.</p>
              </div>
            {:else}
              {#each playlists as pl}
                <button class="modal-playlist-item" onclick={() => addTrackToSpecificPlaylist(pl.id)}>
                  <svg class="icon-sm" viewBox="0 0 24 24"><path fill="currentColor" d={ICONS.playlist}/></svg>
                  <div class="pl-item-info">
                    <span class="pl-item-name">{pl.name}</span>
                    <span class="pl-item-count">{pl.trackUris.length} tracks</span>
                  </div>
                </button>
              {/each}
            {/if}
          </div>
        </div>
        <div class="modal-footer">
          <button
            class="btn btn-secondary"
            onclick={() => {
              showAddToPlaylistModal = false;
              newPlaylistName = '';
              newPlaylistDesc = '';
              showCreatePlaylistModal = true;
            }}
          >
            + Create New Playlist
          </button>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .veyl-app {
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: 100vw;
    background-color: var(--bg-void);
    color: var(--text-primary);
    overflow: hidden;
  }

  /* Icons */
  .icon {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
  }

  .icon-sm {
    width: 15px;
    height: 15px;
    flex-shrink: 0;
  }

  .icon-lg {
    width: 36px;
    height: 36px;
    opacity: 0.5;
  }

  .icon-xl {
    width: 64px;
    height: 64px;
    opacity: 0.5;
  }

  /* Header: Spotify Void Top Bar (#000000) */
  .top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px;
    background: #000000;
    height: 64px;
    flex-shrink: 0;
    gap: 16px;
  }

  .top-bar-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .brand-badge-btn {
    background: transparent;
    border: 1px solid transparent;
    padding: 4px;
    margin: 0;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    transition: transform 0.15s ease, background 0.15s ease, border-color 0.15s ease;
  }
  .brand-badge-btn:hover {
    transform: scale(1.06);
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.15);
  }
  .brand-badge-btn:active {
    transform: scale(0.96);
  }
  .brand-badge-btn.active {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.18);
  }

  .brand-icon {
    width: 36px;
    height: 36px;
    object-fit: contain;
    border-radius: 7px;
    display: block;
  }

  .home-circle-btn {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: #1f1f1f;
    color: #b3b3b3;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .home-circle-btn:hover {
    background: #2a2a2a;
    color: #ffffff;
    transform: scale(1.04);
  }
  .home-circle-btn.active {
    background: #1f1f1f;
    color: #ffffff;
  }

  /* Center Search Pill (Exact match with Spotify "What do you want to play?") */
  .search-pill-container {
    flex: 1;
    max-width: 480px;
    display: flex;
    justify-content: center;
    position: relative;
  }

  .search-box-pill {
    width: 100%;
    height: 48px;
    background: #1f1f1f;
    border-radius: 500px;
    padding: 0 16px;
    display: flex;
    align-items: center;
    gap: 10px;
    border: 1px solid transparent;
    transition: all 0.15s ease;
  }
  .search-box-pill:hover {
    background: #2a2a2a;
    border-color: rgba(255, 255, 255, 0.12);
  }
  .search-box-pill:focus-within {
    background: #1f1f1f;
    border-color: #ffffff;
    box-shadow: 0 0 0 1px #ffffff;
  }

  .search-box-pill input {
    background: transparent;
    border: none;
    outline: none;
    color: #ffffff;
    font-size: 14px;
    font-weight: 500;
    width: 100%;
  }
  .search-box-pill input::placeholder {
    color: #7c7c7c;
  }

  .search-pill-divider {
    width: 1px;
    height: 24px;
    background: #3e3e3e;
    flex-shrink: 0;
  }

  .browse-inline-btn {
    background: transparent;
    border: none;
    color: #b3b3b3;
    padding: 6px;
    cursor: pointer;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: color 0.15s ease;
  }
  .browse-inline-btn:hover {
    color: #ffffff;
  }

  /* Search Live Suggestions Popover */
  .search-suggestions-popover {
    position: absolute;
    top: calc(100% + 8px);
    left: 0;
    right: 0;
    background: #282828;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 12px;
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.7);
    padding: 8px;
    z-index: 200;
    display: flex;
    flex-direction: column;
    max-height: 420px;
    overflow-y: auto;
  }

  .suggestion-group-title {
    font-size: 11px;
    font-weight: 700;
    color: #a7a7a7;
    letter-spacing: 0.5px;
    text-transform: uppercase;
    padding: 6px 8px 4px;
  }

  .suggestion-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 8px;
    border-radius: 6px;
    background: transparent;
    border: none;
    cursor: pointer;
    text-align: left;
    width: 100%;
    transition: background 0.12s ease;
  }
  .suggestion-row:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .suggestion-thumb {
    width: 38px;
    height: 38px;
    border-radius: 4px;
    background: #181818;
    overflow: hidden;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #727272;
  }

  .suggestion-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .suggestion-meta {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .suggestion-title {
    font-size: 13px;
    font-weight: 600;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .suggestion-artist {
    font-size: 11px;
    color: #b3b3b3;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .suggestion-badge {
    font-size: 10px;
    font-family: var(--font-mono);
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 4px;
    flex-shrink: 0;
    text-transform: uppercase;
    background: rgba(30, 215, 96, 0.15);
    color: #1ed760;
  }
  .suggestion-badge.online {
    background: rgba(224, 90, 43, 0.2);
    color: var(--signal-accent);
  }

  .suggestion-footer-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 6px;
    padding: 10px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.05);
    border: none;
    color: #ffffff;
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .suggestion-footer-btn:hover {
    background: rgba(255, 255, 255, 0.12);
  }

  /* Right Action Cluster */
  .top-bar-right {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 200px;
    justify-content: flex-end;
  }

  .btn-import-pill-header {
    background: #ffffff;
    color: #000000;
    font-weight: 700;
    font-size: 13px;
    border-radius: 500px;
    padding: 8px 16px;
    border: none;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    transition: transform 0.15s ease, background-color 0.15s ease;
  }
  .btn-import-pill-header:hover {
    transform: scale(1.04);
    background: #f2f2f2;
  }

  .icon-btn-header {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.06);
    border: none;
    color: #b3b3b3;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .icon-btn-header:hover {
    background: rgba(255, 255, 255, 0.12);
    color: #ffffff;
    transform: scale(1.04);
  }

  /* Workspace: Two Floating Panels on Pitch Black (#000000) */
  .workspace {
    display: flex;
    gap: 8px;
    padding: 0 8px 8px 8px;
    flex: 1;
    overflow: hidden;
    background: #000000;
  }

  /* Left Floating Panel: "Your Library" */
  .sidebar {
    width: 340px;
    min-width: 340px;
    background: #121212;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding: 12px 8px;
    gap: 6px;
    flex-shrink: 0;
    transition: width 0.25s cubic-bezier(0.2, 0, 0, 1), min-width 0.25s cubic-bezier(0.2, 0, 0, 1), padding 0.25s ease;
  }
  .sidebar.collapsed {
    width: 68px;
    min-width: 68px;
    padding: 10px 6px;
    align-items: center;
    border: none;
    overflow-y: auto;
    overflow-x: hidden;
    opacity: 1;
    pointer-events: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .sidebar-mini-rail {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    gap: 8px;
  }

  .sidebar-mini-toggle-btn {
    width: 44px;
    height: 44px;
    border-radius: 8px;
    background: transparent;
    border: none;
    color: #b3b3b3;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .sidebar-mini-toggle-btn:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.08);
    transform: scale(1.05);
  }

  .sidebar-mini-divider {
    width: 32px;
    height: 1px;
    background: rgba(255, 255, 255, 0.1);
    margin: 2px 0;
  }

  .sidebar-mini-list {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    gap: 8px;
  }

  .sidebar-mini-item {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    background: transparent;
    border: 1px solid transparent;
    padding: 2px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .sidebar-mini-item:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.15);
    transform: scale(1.06);
  }
  .sidebar-mini-item.active {
    border-color: #1ed760;
    background: rgba(30, 215, 96, 0.12);
  }

  .mini-liked-tile {
    width: 100%;
    height: 100%;
    border-radius: 6px;
    background: linear-gradient(135deg, #450af5, #c4efd9);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .sidebar-mini-art {
    width: 100%;
    height: 100%;
    border-radius: 6px;
    object-fit: cover;
  }
  .sidebar-mini-art.avatar-circle {
    border-radius: 50%;
  }

  .sidebar-mini-placeholder {
    width: 100%;
    height: 100%;
    border-radius: 6px;
    background: #282828;
    color: #b3b3b3;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .sidebar-mini-placeholder.avatar-circle {
    border-radius: 50%;
  }

  .library-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 8px 8px;
  }

  .library-title-btn {
    display: flex;
    align-items: center;
    gap: 12px;
    background: transparent;
    border: none;
    color: #b3b3b3;
    font-weight: 700;
    font-size: 15px;
    cursor: pointer;
    transition: color 0.15s ease;
  }
  .library-title-btn:hover {
    color: #ffffff;
  }

  .library-header-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .icon-btn-ghost {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: transparent;
    border: none;
    color: #b3b3b3;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .icon-btn-ghost:hover {
    background: #1f1f1f;
    color: #ffffff;
  }

  .relative-wrap {
    position: relative;
  }

  .dropdown-popover {
    position: absolute;
    right: 0;
    top: 38px;
    width: 220px;
    background: #282828;
    border-radius: 8px;
    padding: 4px;
    box-shadow: 0 16px 24px rgba(0, 0, 0, 0.5);
    z-index: 100;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .dropdown-item {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    font-size: 13px;
    color: #e0e0e0;
    border-radius: 4px;
    text-align: left;
    background: transparent;
    border: none;
    cursor: pointer;
    transition: background-color 0.12s ease;
  }
  .dropdown-item:hover {
    background: rgba(255, 255, 255, 0.1);
    color: #ffffff;
  }

  /* Filter Chips: Playlists, Artists, Albums */
  .sidebar-chips {
    display: flex;
    gap: 8px;
    padding: 4px 8px 8px;
    overflow-x: auto;
  }

  .sidebar-chip {
    background: #232323;
    color: #ffffff;
    font-size: 13px;
    font-weight: 500;
    padding: 6px 14px;
    border-radius: 20px;
    border: none;
    cursor: pointer;
    transition: all 0.15s ease;
    white-space: nowrap;
  }
  .sidebar-chip:hover {
    background: #2a2a2a;
  }
  .sidebar-chip.active {
    background: #ffffff;
    color: #000000;
    font-weight: 700;
  }

  /* Import Promo Card (Exact match with Spotify reference) */
  .sidebar-promo-card {
    background: linear-gradient(135deg, #242424 0%, #1a1a1a 100%);
    border-radius: 8px;
    padding: 16px;
    margin: 6px 8px 10px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }

  .promo-title {
    font-size: 14px;
    font-weight: 700;
    color: #ffffff;
    line-height: 1.3;
  }

  .promo-desc {
    font-size: 12px;
    color: #b3b3b3;
    line-height: 1.4;
  }

  .btn-import-pill {
    background: #ffffff;
    color: #000000;
    font-size: 13px;
    font-weight: 700;
    padding: 6px 16px;
    border-radius: 500px;
    width: fit-content;
    margin-top: 8px;
    cursor: pointer;
    border: none;
    transition: transform 0.15s ease, background-color 0.15s ease;
  }
  .btn-import-pill:hover {
    transform: scale(1.04);
    background: #f2f2f2;
  }

  /* Search & Sort Bar */
  .sidebar-search-sort-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 2px 8px 8px;
  }

  .btn-search-trigger {
    width: 28px;
    height: 28px;
  }

  .inline-search-input-box {
    display: flex;
    align-items: center;
    gap: 6px;
    background: #242424;
    border-radius: 4px;
    padding: 4px 8px;
    flex: 1;
    margin-right: 8px;
  }
  .inline-search-input-box input {
    background: transparent;
    border: none;
    outline: none;
    color: #ffffff;
    font-size: 12px;
    width: 100%;
  }

  .icon-btn-xs {
    background: transparent;
    border: none;
    color: #b3b3b3;
    cursor: pointer;
    padding: 2px;
  }

  .sort-dropdown-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    background: transparent;
    border: none;
    color: #b3b3b3;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    margin-left: auto;
    transition: color 0.15s ease;
  }
  .sort-dropdown-btn:hover {
    color: #ffffff;
  }

  /* Scrollable Items List */
  .library-items-list {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 0 4px;
  }

  .library-row-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px;
    border-radius: 6px;
    background: transparent;
    border: none;
    width: 100%;
    text-align: left;
    cursor: pointer;
    transition: background-color 0.15s ease;
  }
  .library-row-item:hover {
    background: #1a1a1a;
  }
  .library-row-item.active {
    background: #242424;
  }

  .liked-songs-tile {
    width: 48px;
    height: 48px;
    border-radius: 4px;
    background: linear-gradient(135deg, #450af5 0%, #8e8ee5 50%, #c4efd9 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .library-row-art {
    width: 48px;
    height: 48px;
    border-radius: 4px;
    object-fit: cover;
    flex-shrink: 0;
  }
  .avatar-circle {
    border-radius: 50% !important;
  }

  .library-row-placeholder {
    width: 48px;
    height: 48px;
    border-radius: 4px;
    background: #282828;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #b3b3b3;
    flex-shrink: 0;
  }

  .library-row-texts {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    min-width: 0;
  }

  .library-row-title {
    font-size: 14px;
    font-weight: 600;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .library-row-sub {
    font-size: 12px;
    color: #b3b3b3;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-top: 2px;
  }

  .sidebar-dac-pill {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    margin: 4px 8px 0;
    background: #181818;
    border-radius: 4px;
    font-family: var(--font-mono);
    font-size: 10px;
    color: #a0a0a0;
  }
  .dot-live {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #1ed760;
  }
  .dac-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* Content Panel */
  .content-panel {
    flex: 1;
    overflow-y: auto;
    background: var(--bg-void);
    display: flex;
    flex-direction: column;
  }

  .view-container {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .view-header {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
  }

  .header-title-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .header-with-back {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .btn-back-nav {
    width: 38px;
    height: 38px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    color: var(--text-primary);
    cursor: pointer;
    transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    flex-shrink: 0;
  }
  .btn-back-nav:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-active);
    transform: scale(1.06);
    color: var(--signal-accent);
  }
  .btn-back-nav:active {
    transform: scale(0.96);
  }

  .view-title {
    font-size: 22px;
    font-weight: 700;
    color: var(--text-primary);
  }

  .view-subtitle {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 2px;
  }

  .header-right-cluster {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .shuffle-all-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    background: var(--signal-accent);
    color: #111110;
    font-weight: 700;
    font-size: 12px;
    border-radius: var(--radius-pill);
    box-shadow: 0 2px 8px var(--signal-accent-glow);
    transition: all 0.15s ease;
    cursor: pointer;
    flex-shrink: 0;
  }
  .shuffle-all-btn:hover {
    background: var(--signal-accent-bright);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px var(--signal-accent-glow);
  }

  .filter-chips {
    display: flex;
    gap: 6px;
    align-items: center;
  }

  .chip {
    padding: 5px 12px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    font-size: 11px;
    font-weight: 600;
    color: var(--text-secondary);
    transition: all 0.15s ease;
    cursor: pointer;
  }
  .chip:hover {
    background: var(--surface-card-hover);
    color: var(--text-primary);
  }
  .chip.active {
    background: var(--signal-accent);
    border-color: var(--signal-accent);
    color: #111110;
    font-weight: 700;
  }

  /* ── Proper Audiophile Song List Styles ── */
  .song-list-container {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .song-sort-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 4px 6px 4px;
    border-bottom: 1px solid var(--border-hairline);
    margin-bottom: 6px;
    overflow-x: auto;
  }

  .sort-label {
    font-size: 11px;
    font-weight: 700;
    font-family: var(--font-mono);
    color: var(--text-muted);
    letter-spacing: 0.5px;
    text-transform: uppercase;
    margin-right: 4px;
  }

  .sort-pill {
    padding: 3px 10px;
    border-radius: var(--radius-pill);
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    font-size: 11.5px;
    font-weight: 500;
    color: var(--text-secondary);
    display: inline-flex;
    align-items: center;
    gap: 4px;
    cursor: pointer;
    transition: all 0.12s ease;
  }
  .sort-pill:hover {
    color: var(--text-primary);
    border-color: var(--border-active);
  }
  .sort-pill.active {
    background: var(--surface-pill);
    color: #FFFFFF;
    border-color: rgba(255, 255, 255, 0.35);
    font-weight: 700;
  }
  :global(:root.light) .sort-pill.active {
    background: #E4E4E7;
    color: #18181B;
    border-color: rgba(0, 0, 0, 0.35);
  }

  .view-layout-group {
    display: flex;
    align-items: center;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-pill);
    padding: 2px;
    gap: 2px;
  }

  .layout-btn {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
    background: transparent;
    cursor: pointer;
    transition: all 0.12s ease;
  }
  .layout-btn:hover {
    color: var(--text-primary);
  }
  .layout-btn.active {
    background: var(--surface-pill);
    color: #FFFFFF;
  }
  :global(:root.light) .layout-btn.active {
    background: #E4E4E7;
    color: #18181B;
  }

  .song-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .song-list-row {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    border-radius: var(--radius-md);
    background: transparent;
    border: 1px solid transparent;
    gap: 14px;
    cursor: pointer;
    user-select: none;
    transition: background 0.12s ease, border-color 0.12s ease;
  }
  .song-list-row:hover {
    background: var(--surface-card);
    border-color: var(--border-hairline);
  }
  .song-list-row.active {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(255, 255, 255, 0.20);
  }
  :global(:root.light) .song-list-row.active {
    background: rgba(0, 0, 0, 0.05);
    border-color: rgba(0, 0, 0, 0.18);
  }

  .sl-prefix {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
  }

  .sl-index {
    width: 22px;
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 600;
    color: var(--text-muted);
    text-align: right;
  }

  .sl-cover-box {
    width: 42px;
    height: 42px;
    border-radius: 8px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    overflow: hidden;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    padding: 0;
    cursor: pointer;
  }

  .sl-cover-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .sl-cover-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
    background: var(--surface-panel);
  }

  .sl-cover-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.65);
    opacity: 0;
    transition: opacity 0.12s ease;
    color: #FFFFFF;
  }
  .sl-cover-box:hover .sl-cover-overlay.hover-play {
    opacity: 1;
  }
  .sl-cover-overlay.active {
    opacity: 1;
    background: rgba(0, 0, 0, 0.55);
  }

  .sl-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
    background: none;
    border: none;
    padding: 0;
    text-align: left;
    cursor: pointer;
    font-family: inherit;
    color: inherit;
  }

  .sl-title-line {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .sl-title {
    font-weight: 600;
    font-size: 14.5px;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .sl-title.active-title {
    color: #FFFFFF;
    font-weight: 700;
  }
  :global(:root.light) .sl-title.active-title {
    color: #18181B;
  }

  .sl-spec-pill {
    flex-shrink: 0;
    font-family: var(--font-mono);
    font-size: 9.5px;
    font-weight: 600;
    padding: 1px 6px;
    border-radius: 4px;
    background: var(--surface-pill);
    border: 1px solid var(--border-hairline);
    color: var(--text-muted);
    letter-spacing: 0.3px;
  }
  .sl-spec-pill.hires {
    background: rgba(255, 255, 255, 0.10);
    border-color: rgba(255, 255, 255, 0.28);
    color: #FFFFFF;
    font-weight: 700;
  }
  :global(:root.light) .sl-spec-pill.hires {
    background: rgba(0, 0, 0, 0.08);
    border-color: rgba(0, 0, 0, 0.25);
    color: #18181B;
  }

  .sl-subtitle {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .sl-artist {
    color: var(--text-secondary);
  }
  .sl-dot {
    color: var(--text-muted);
    opacity: 0.6;
  }
  .sl-album {
    color: var(--text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .sl-trailing {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
  }

  .sl-duration {
    font-family: var(--font-mono);
    font-size: 11.5px;
    color: var(--text-muted);
    min-width: 44px;
    text-align: right;
  }

  .sl-action-btn {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
    background: transparent;
    cursor: pointer;
    transition: all 0.12s ease;
  }
  .sl-action-btn:hover {
    color: var(--text-primary);
    background: var(--surface-card);
  }
  .sl-action-btn.favorited {
    color: #FFFFFF;
  }
  :global(:root.light) .sl-action-btn.favorited {
    color: #18181B;
  }
  .sl-action-btn.remove-btn:hover {
    color: #EF4444;
  }

  /* ── Songs Grid Layout ── */
  .song-cards-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 14px;
  }

  .song-grid-card {
    display: flex;
    flex-direction: column;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 10px;
    gap: 10px;
    cursor: pointer;
    transition: all 0.15s ease;
    text-align: left;
    font-family: inherit;
    width: 100%;
  }
  .song-grid-card:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-active);
    transform: translateY(-2px);
  }
  .song-grid-card.active {
    border-color: rgba(255, 255, 255, 0.45);
    background: rgba(255, 255, 255, 0.05);
  }
  :global(:root.light) .song-grid-card.active {
    border-color: rgba(0, 0, 0, 0.45);
    background: rgba(0, 0, 0, 0.04);
  }

  .sg-cover-box {
    width: 100%;
    aspect-ratio: 1;
    border-radius: var(--radius-sm);
    background: var(--surface-panel);
    overflow: hidden;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .sg-cover-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .sg-cover-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
  }

  .sg-play-badge {
    position: absolute;
    bottom: 8px;
    right: 8px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: #FFFFFF;
    color: #121212;
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transform: scale(0.85);
    transition: all 0.15s ease;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
  }
  :global(:root.light) .sg-play-badge {
    background: #18181B;
    color: #FFFFFF;
  }
  .song-grid-card:hover .sg-play-badge.hover-play {
    opacity: 1;
    transform: scale(1);
  }
  .sg-play-badge.active {
    opacity: 1;
    transform: scale(1);
    background: #FFFFFF;
    color: #121212;
  }

  .sg-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .sg-title {
    font-size: 13.5px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .sg-title.active-title {
    color: #FFFFFF;
    font-weight: 700;
  }
  :global(:root.light) .sg-title.active-title {
    color: #18181B;
  }

  .sg-artist {
    font-size: 12px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .sg-meta {
    font-family: var(--font-mono);
    font-size: 10.5px;
    color: var(--text-muted);
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: 2px;
  }

  .playing-bars {
    display: inline-flex;
    align-items: flex-end;
    gap: 2px;
    height: 12px;
  }
  .playing-bars span {
    width: 3px;
    background: #FFFFFF;
    animation: barBounce 0.8s ease-in-out infinite alternate;
  }
  :global(:root.light) .playing-bars span {
    background: #18181B;
  }
  .playing-bars span:nth-child(2) {
    animation-delay: 0.2s;
  }
  .playing-bars span:nth-child(3) {
    animation-delay: 0.4s;
  }

  @keyframes barBounce {
    0% { height: 3px; }
    100% { height: 12px; }
  }

  /* Albums Grid */
  .albums-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 16px;
  }

  .album-card {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    text-align: left;
    transition: all 0.15s ease;
  }
  .album-card:hover {
    background: var(--surface-card-hover);
    border-color: var(--border-active);
  }

  .album-art-wrap {
    position: relative;
    aspect-ratio: 1;
    width: 100%;
    border-radius: var(--radius-sm);
    overflow: hidden;
    background: var(--surface-panel);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .album-cover-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: var(--radius-sm);
  }

  .art-canvas-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
  }

  .hires-badge {
    position: absolute;
    top: 8px;
    right: 8px;
    background: rgba(255, 255, 255, 0.15);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    border: 1px solid rgba(255, 255, 255, 0.30);
    color: #FFFFFF;
    font-family: var(--font-mono);
    font-size: 8.5px;
    font-weight: 800;
    padding: 2px 6px;
    border-radius: 4px;
    letter-spacing: 0.5px;
  }
  :global(:root.light) .hires-badge {
    background: rgba(0, 0, 0, 0.08);
    border-color: rgba(0, 0, 0, 0.20);
    color: #18181B;
  }

  .album-name {
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .album-artist {
    font-size: 12px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .album-meta {
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--text-muted);
  }

  /* Artists Grid */
  .artists-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 14px;
  }

  .artist-card {
    display: flex;
    align-items: center;
    gap: 14px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 14px;
    text-align: left;
    transition: all 0.15s ease;
  }
  .artist-card:hover {
    background: var(--surface-card-hover);
    border-color: rgba(255, 255, 255, 0.40);
  }
  :global(:root.light) .artist-card:hover {
    border-color: rgba(0, 0, 0, 0.40);
  }

  .artist-avatar {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    background: var(--surface-pill);
    color: #FFFFFF;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: var(--font-mono);
    font-weight: 700;
    font-size: 13px;
    overflow: hidden;
    flex-shrink: 0;
  }
  :global(:root.light) .artist-avatar {
    color: #18181B;
  }

  .artist-avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 50%;
  }

  .artist-name {
    font-weight: 600;
    color: var(--text-primary);
  }

  .artist-meta {
    font-size: 11px;
    color: var(--text-muted);
  }

  /* Cockpit / Immersive View */
  .cockpit-container {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 20px;
    height: 100%;
  }

  .cockpit-header {
    display: flex;
    justify-content: center;
  }

  .mode-pills {
    display: inline-flex;
    background: var(--surface-card);
    padding: 4px;
    border-radius: var(--radius-pill);
    border: 1px solid var(--border-hairline);
    gap: 4px;
  }

  .mode-btn {
    padding: 6px 14px;
    border-radius: var(--radius-pill);
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    transition: all 0.15s ease;
  }
  .mode-btn.active {
    background: var(--signal-accent);
    color: var(--bg-void);
  }

  .cockpit-body {
    display: grid;
    grid-template-columns: 1fr 1.1fr;
    gap: 30px;
    flex: 1;
    align-items: center;
  }

  .cockpit-stage {
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .artwork-immersive-wrap {
    width: 320px;
    height: 320px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .large-cover-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 20px;
    border: 1px solid var(--border-hairline);
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.4);
  }

  .large-cover-placeholder {
    width: 100%;
    height: 100%;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
  }

  .lyrics-card-panel {
    width: 100%;
    height: 380px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: 20px;
    padding: 32px 24px;
    overflow-y: auto;
    scroll-behavior: smooth;
    display: flex;
    flex-direction: column;
  }

  /* Embedded Equalizer Panel inside Cockpit Stage */
  .cockpit-eq-panel {
    width: 100%;
    min-height: 380px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: 20px;
    padding: 20px 24px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .cockpit-eq-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .cockpit-eq-title {
    font-size: 13px;
    font-weight: 700;
    color: #ffffff;
    letter-spacing: 0.5px;
    margin: 0;
  }

  .cockpit-eq-sub {
    font-size: 11px;
    color: var(--text-muted);
    margin: 2px 0 0;
  }

  .cockpit-presets-row {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  .cockpit-preset-chip {
    padding: 4px 10px;
    font-size: 11px;
    font-weight: 600;
    border-radius: 500px;
    background: #242424;
    color: #b3b3b3;
    border: 1px solid rgba(255, 255, 255, 0.06);
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .cockpit-preset-chip:hover {
    background: #2f2f2f;
    color: #ffffff;
  }
  .cockpit-preset-chip.active {
    background: #ffffff;
    color: #000000;
    border-color: #ffffff;
    font-weight: 700;
  }

  .cockpit-curve-card {
    height: 80px;
    background: #111111;
    border-radius: 8px;
    border: 1px solid rgba(255, 255, 255, 0.06);
    overflow: hidden;
    position: relative;
    padding: 2px 4px;
  }

  .cockpit-bands-strip {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    gap: 4px;
    flex: 1;
    min-height: 150px;
    padding-top: 6px;
  }

  .cockpit-band-col {
    display: flex;
    flex-direction: column;
    align-items: center;
    flex: 1;
    gap: 3px;
    min-width: 0;
  }

  .cockpit-gain-val {
    font-size: 10px;
    font-family: var(--font-mono);
    color: #888888;
    font-weight: 600;
  }
  .cockpit-gain-val.boosted {
    color: var(--signal-accent);
  }
  .cockpit-gain-val.cut {
    color: #60a5fa;
  }

  .cockpit-slider-wrap {
    height: 90px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .cockpit-slider-wrap input[type='range'] {
    writing-mode: vertical-lr;
    direction: rtl;
    width: 4px;
    height: 84px;
    appearance: slider-vertical;
    -webkit-appearance: slider-vertical;
    accent-color: var(--signal-accent);
    cursor: pointer;
  }

  .cockpit-steppers {
    display: flex;
    gap: 2px;
  }

  .step-btn-mini {
    width: 16px;
    height: 16px;
    font-size: 11px;
    line-height: 1;
    border-radius: 3px;
    background: #242424;
    color: #b3b3b3;
    border: none;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
  }
  .step-btn-mini:hover {
    background: #383838;
    color: #ffffff;
  }

  .cockpit-freq-label {
    font-size: 9px;
    font-family: var(--font-mono);
    color: #666666;
    font-weight: 600;
    margin-top: 2px;
  }

  .lyrics-scroll {
    display: flex;
    flex-direction: column;
    gap: 20px;
    margin: auto 0;
    padding: 80px 0;
  }

  .lyrics-status-box {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    margin: auto;
    text-align: center;
    gap: 12px;
    padding: 30px;
  }

  .status-pulse-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--signal-accent);
    animation: statusPulse 1.2s ease-in-out infinite alternate;
  }

  @keyframes statusPulse {
    0% { transform: scale(0.8); opacity: 0.5; }
    100% { transform: scale(1.3); opacity: 1; box-shadow: 0 0 16px var(--signal-accent); }
  }

  .status-msg {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .status-sub {
    font-size: 12px;
    color: var(--text-muted);
    max-width: 280px;
  }

  .lyric-line {
    font-size: 16px;
    color: var(--text-muted);
    cursor: pointer;
    text-align: left;
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    line-height: 1.5;
    padding: 4px 8px;
    border-radius: 8px;
    opacity: 0.5;
  }
  .lyric-line:hover {
    opacity: 0.9;
    color: var(--text-primary);
    background: var(--surface-card-hover);
  }
  .lyric-line.active {
    color: var(--signal-accent);
    font-size: 20px;
    font-weight: 700;
    opacity: 1;
    transform: scale(1.02);
    text-shadow: 0 0 20px var(--signal-accent-glow);
  }
  .lyric-line.past {
    color: var(--text-secondary);
    opacity: 0.7;
  }

  .cockpit-meters-deck {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .now-badge {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--signal-accent);
    letter-spacing: 1px;
  }

  .now-title {
    font-size: 24px;
    font-weight: 700;
    color: var(--text-primary);
    line-height: 1.2;
    margin: 4px 0;
  }

  .now-artist {
    font-size: 14px;
    font-weight: 500;
    color: var(--text-secondary);
  }

  /* Dual Analog VU Meters */
  .analog-vu-cluster {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .vu-title-row {
    display: flex;
    justify-content: space-between;
    font-family: var(--font-mono);
    font-size: 9px;
    font-weight: 700;
    color: var(--text-muted);
  }

  .vu-channel {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .vu-ch-label {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--signal-accent);
    width: 12px;
  }

  .vu-bar-track {
    flex: 1;
    height: 10px;
    background: var(--surface-pill);
    border-radius: 2px;
    position: relative;
    overflow: hidden;
  }

  .vu-bar-fill {
    height: 100%;
    background: linear-gradient(90deg, var(--signal-accent) 0%, var(--signal-amber) 75%, var(--danger-red) 100%);
    transition: width 0.08s ease;
  }

  .vu-peak-needle {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 2px;
    background: var(--text-primary);
    transition: left 0.1s ease;
  }

  .vu-scale {
    display: flex;
    justify-content: space-between;
    font-family: var(--font-mono);
    font-size: 8px;
    color: var(--text-muted);
  }

  /* Spectrum Visualizer Full */
  .full-spectrum-panel {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 14px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .spec-heading {
    display: flex;
    justify-content: space-between;
    font-family: var(--font-mono);
    font-size: 9px;
    font-weight: 700;
    color: var(--text-muted);
  }

  .spec-bars-container {
    display: flex;
    align-items: flex-end;
    gap: 6px;
    height: 100px;
  }

  .spec-bar-col {
    flex: 1;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    align-items: center;
    gap: 4px;
  }

  .spec-bar-fill {
    width: 100%;
    background: var(--signal-accent);
    border-radius: 2px 2px 0 0;
    transition: height 0.08s ease;
  }

  .freq-axis-label {
    font-family: var(--font-mono);
    font-size: 9px;
    color: var(--text-muted);
  }

  /* Equalizer View */
  .presets-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .preset-label {
    font-family: var(--font-mono);
    font-size: 11px;
    font-weight: 700;
    color: var(--text-muted);
  }

  .preset-pills {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .preset-btn {
    padding: 4px 10px;
    border-radius: var(--radius-pill);
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    font-size: 11px;
    color: var(--text-secondary);
  }
  .preset-btn:hover {
    color: var(--text-primary);
  }
  .preset-btn.active {
    background: var(--signal-accent);
    color: var(--bg-void);
    font-weight: 700;
  }

  .curve-card {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 16px;
  }

  .curve-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .curve-svg {
    width: 100%;
    height: 160px;
    display: block;
  }

  .eq-bands-strip {
    display: grid;
    grid-template-columns: repeat(10, 1fr);
    gap: 8px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 18px 10px;
  }

  .band-column {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
  }

  .gain-badge {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--text-secondary);
    min-height: 16px;
  }
  .gain-badge.boosted {
    color: var(--signal-accent);
  }
  .gain-badge.cut {
    color: var(--signal-amber);
  }

  .slider-wrapper {
    height: 130px;
    display: flex;
    align-items: center;
  }

  .slider-wrapper input[type='range'] {
    writing-mode: vertical-lr;
    direction: rtl;
    width: 8px;
    height: 120px;
    accent-color: var(--signal-accent);
    cursor: pointer;
  }

  .stepper-row {
    display: flex;
    gap: 4px;
  }

  .step-btn {
    width: 20px;
    height: 20px;
    background: var(--surface-pill);
    border-radius: 4px;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-secondary);
  }
  .step-btn:hover {
    color: var(--signal-accent);
    background: var(--surface-card-hover);
  }

  .freq-label {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: var(--text-muted);
  }

  /* Queue */
  .queue-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .queue-item {
    display: flex;
    align-items: center;
    gap: 12px;
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-sm);
    padding: 10px 14px;
    transition: all 0.15s ease;
    user-select: none;
    cursor: pointer;
  }
  .queue-item:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: var(--border-default);
  }
  .queue-item.active {
    border-color: var(--border-active);
    background: var(--signal-accent-glow);
  }
  .queue-item.dragging {
    opacity: 0.35;
    border-style: dashed;
    border-color: var(--border-active);
  }
  .queue-item.drag-over {
    border-color: #1ed760;
    box-shadow: 0 -2px 0 0 #1ed760;
    background: rgba(30, 215, 96, 0.14);
    transform: translateY(-2px);
  }
  :global(:root.light) .queue-item.drag-over {
    border-color: #1ed760;
    box-shadow: 0 -2px 0 0 #1ed760;
    background: rgba(30, 215, 96, 0.14);
  }

  .q-drag-handle {
    cursor: grab;
    color: var(--text-muted);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    border-radius: 4px;
    transition: color 0.15s ease;
    flex-shrink: 0;
  }
  .q-drag-handle:active {
    cursor: grabbing;
  }
  .q-drag-handle:hover {
    color: var(--text-primary);
    background: var(--surface-card-hover);
  }

  .q-reorder-actions {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex-shrink: 0;
  }
  .q-mini-btn {
    background: none;
    border: none;
    color: var(--text-muted);
    font-size: 8px;
    padding: 2px 4px;
    cursor: pointer;
    line-height: 1;
    border-radius: 2px;
    transition: all 0.1s ease;
  }
  .q-mini-btn:hover:not(:disabled) {
    color: var(--text-primary);
    background: var(--surface-card-hover);
  }
  .q-mini-btn:disabled {
    opacity: 0.15;
    cursor: default;
  }

  .q-index {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-muted);
    width: 24px;
  }

  .q-info {
    flex: 1;
  }
  .q-title {
    font-weight: 600;
    color: var(--text-primary);
  }
  .q-sub {
    font-size: 12px;
    color: var(--text-secondary);
  }

  .q-dur {
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-muted);
  }

  .q-play-btn, .q-del-btn {
    color: var(--text-muted);
    padding: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .q-play-btn:hover {
    color: var(--signal-accent);
  }
  .q-del-btn:hover {
    color: var(--danger-red);
  }

  .history-section {
    margin-top: 16px;
  }

  .history-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 8px;
  }

  .hist-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: var(--surface-card);
    border-radius: var(--radius-sm);
    font-size: 12px;
    text-align: left;
    width: 100%;
  }
  .hist-item:hover {
    background: var(--surface-card-hover);
  }
  .hist-title {
    font-weight: 600;
    color: var(--text-primary);
  }
  .hist-artist {
    color: var(--text-muted);
    flex: 1;
  }
  .inline-btn {
    color: var(--signal-accent);
  }

  /* Settings Screen */
  .settings-card {
    background: var(--surface-card);
    border: 1px solid var(--border-hairline);
    border-radius: var(--radius-md);
    padding: 18px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .panel-section-title {
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 1.2px;
    color: var(--signal-accent);
  }

  .setting-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 0;
    border-bottom: 1px solid var(--border-hairline);
  }
  .setting-row:last-child {
    border-bottom: none;
  }

  .setting-name {
    font-weight: 600;
    font-size: 13px;
    color: var(--text-primary);
  }
  .setting-desc {
    font-size: 12px;
    color: var(--text-muted);
  }

  .setting-choice {
    display: flex;
    gap: 6px;
  }

  .switch-toggle {
    position: relative;
    width: 44px;
    height: 24px;
    border-radius: 12px;
    background: var(--surface-pill);
    border: 1px solid var(--border-hairline);
    cursor: pointer;
    transition: background-color 0.2s cubic-bezier(0.4, 0, 0.2, 1), border-color 0.2s ease;
    padding: 0;
    outline: none;
    flex-shrink: 0;
  }
  .switch-toggle:hover {
    border-color: var(--border-active);
  }
  .switch-toggle.checked {
    background: var(--signal-accent);
    border-color: var(--signal-accent);
  }
  .switch-thumb {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--text-primary);
    transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.2s ease;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
  }
  .switch-toggle.checked .switch-thumb {
    transform: translateX(20px);
    background: #111110;
  }

  /* =========================================================================
     SPOTIFY HOME ARCHITECTURE: Category Chips, Shelves & Cards
     ========================================================================= */
  .home-view {
    padding: 0 !important;
  }

  /* Sticky Top Category Filter Chips & Actions */
  .home-category-chips {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px 24px;
    position: sticky;
    top: 0;
    background: rgba(18, 18, 18, 0.95);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    z-index: 20;
    border-top-left-radius: 8px;
    border-top-right-radius: 8px;
  }

  .home-filter-pills {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .home-category-chip {
    background: #232323;
    color: #ffffff;
    font-size: 14px;
    font-weight: 500;
    padding: 8px 18px;
    border-radius: 500px;
    border: none;
    cursor: pointer;
    transition: all 0.15s ease;
    white-space: nowrap;
  }
  .home-category-chip:hover {
    background: #2a2a2a;
  }
  .home-category-chip.active {
    background: #ffffff;
    color: #000000;
    font-weight: 700;
  }

  .home-actions-rail {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .home-action-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 7px 14px;
    border-radius: 500px;
    background: #242424;
    color: #ffffff;
    font-size: 13px;
    font-weight: 600;
    border: 1px solid rgba(255, 255, 255, 0.08);
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .home-action-btn:hover {
    background: #2e2e2e;
    border-color: rgba(255, 255, 255, 0.2);
    transform: translateY(-1px);
  }

  .home-shuffle-btn {
    background: rgba(255, 255, 255, 0.08);
    color: #ffffff;
  }
  .home-shuffle-btn:hover {
    background: #ffffff;
    color: #000000;
  }

  .home-sort-dropdown-wrap {
    position: relative;
  }

  .sort-popover-menu {
    position: absolute;
    top: calc(100% + 6px);
    right: 0;
    width: 170px;
    background: #282828;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 8px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6);
    padding: 6px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    z-index: 100;
  }

  .sort-option-item {
    display: flex;
    align-items: center;
    width: 100%;
    padding: 8px 12px;
    border-radius: 6px;
    font-size: 13px;
    font-weight: 500;
    color: #b3b3b3;
    background: transparent;
    border: none;
    cursor: pointer;
    text-align: left;
    transition: all 0.12s ease;
  }
  .sort-option-item:hover {
    background: rgba(255, 255, 255, 0.08);
    color: #ffffff;
  }
  .sort-option-item.active {
    background: rgba(30, 215, 96, 0.15);
    color: #1ed760;
    font-weight: 600;
  }

  /* Shelves */
  .shelf-section {
    padding: 16px 24px 28px;
  }

  .shelf-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .shelf-title {
    font-size: 22px;
    font-weight: 700;
    color: #ffffff;
    letter-spacing: -0.4px;
    margin: 0;
  }

  .shelf-sub-label {
    font-size: 13px;
    color: #b3b3b3;
    margin-top: 2px;
    display: block;
  }

  .shelf-show-all-btn {
    background: transparent;
    border: none;
    color: #b3b3b3;
    font-size: 13px;
    font-weight: 700;
    cursor: pointer;
    transition: color 0.15s ease;
  }
  .shelf-show-all-btn:hover {
    color: #ffffff;
    text-decoration: underline;
  }

  /* Shelf Grid */
  .shelf-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 20px;
  }

  /* Shelf Standard Card */
  .shelf-card {
    background: #181818;
    padding: 14px;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    cursor: pointer;
    position: relative;
    transition: background-color 0.25s cubic-bezier(0.3, 0, 0, 1);
    outline: none;
  }
  .shelf-card:hover, .shelf-card:focus-visible {
    background: #242424;
  }

  .shelf-card-art-box {
    position: relative;
    width: 100%;
    aspect-ratio: 1;
    border-radius: 6px;
    overflow: hidden;
    margin-bottom: 12px;
    background: #282828;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  }

  .shelf-card-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 6px;
    display: block;
  }
  .artist-avatar-box,
  .artist-circle-img {
    border-radius: 50% !important;
  }

  .shelf-card-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #727272;
    background: #282828;
  }

  .liked-gradient-box {
    background: linear-gradient(135deg, #450af5 0%, #8e8ee5 50%, #c4efd9 100%);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .playlist-bg {
    background: linear-gradient(135deg, #333333 0%, #1e1e1e 100%);
  }

  /* Floating Circular Play Button on Card Hover */
  .card-hover-play-btn {
    position: absolute;
    right: 8px;
    bottom: 8px;
    width: 46px;
    height: 46px;
    border-radius: 50%;
    background: #1ed760;
    color: #000000;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.45);
    opacity: 0;
    transform: translateY(8px);
    transition: all 0.25s cubic-bezier(0.3, 0, 0, 1);
    cursor: pointer;
    border: none;
    z-index: 5;
  }
  .card-hover-play-btn:hover {
    transform: scale(1.08) translateY(0);
    background: #1fdf64;
  }
  .shelf-card:hover .card-hover-play-btn,
  .shelf-card:focus-visible .card-hover-play-btn {
    opacity: 1;
    transform: translateY(0);
  }

  .icon-play-white {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
  }

  .shelf-card-info {
    display: flex;
    flex-direction: column;
    min-height: 44px;
  }

  .shelf-card-title {
    font-size: 15px;
    font-weight: 700;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-bottom: 4px;
    line-height: 1.25;
  }

  .shelf-card-sub {
    font-size: 13px;
    color: #b3b3b3;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.35;
  }

  .shelf-empty-state {
    grid-column: 1 / -1;
    padding: 32px;
    background: #181818;
    border-radius: 8px;
    text-align: center;
    color: #b3b3b3;
    font-size: 14px;
  }

  /* =========================================================================
     BOTTOM PLAYER DOCK: Spotify Pitch-Black Dock (#000000)
     ========================================================================= */
  .player-dock {
    height: 76px;
    background: #000000;
    border-top: 1px solid rgba(255, 255, 255, 0.04);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
    flex-shrink: 0;
    z-index: 50;
  }

  /* Left: 56x56 Artwork, Title, Artist & Action Icons */
  .dock-meta {
    flex: 1;
    max-width: 280px;
    min-width: 180px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .artwork-thumb {
    width: 56px;
    height: 56px;
    border-radius: 4px;
    background: #282828;
    border: none;
    overflow: hidden;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    color: #727272;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  }
  .artwork-thumb img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .meta-texts {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    min-width: 0;
    flex: 1;
  }

  .active-title {
    font-size: 14px;
    font-weight: 600;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.25;
  }

  .active-artist {
    font-size: 11px;
    color: #b3b3b3;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-top: 2px;
  }

  .dock-fav-btn {
    color: #b3b3b3;
    background: transparent;
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    padding: 4px;
    border-radius: 50%;
    transition: all 0.15s ease;
  }
  .dock-fav-btn:hover {
    color: #ffffff;
    transform: scale(1.1);
  }
  .dock-fav-btn.favorited {
    color: #1ed760;
  }

  /* Center: Transport Buttons & Scrubber */
  .dock-transport {
    flex: 1;
    max-width: 600px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
  }

  .transport-buttons {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .ctrl-btn {
    color: #b3b3b3;
    background: transparent;
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.12s ease;
    padding: 4px;
  }
  .ctrl-btn:hover {
    color: #ffffff;
    transform: scale(1.06);
  }

  .ctrl-btn.sub-btn {
    color: #727272;
  }
  .ctrl-btn.sub-btn:hover {
    color: #ffffff;
  }
  .ctrl-btn.sub-btn.active {
    color: #1ed760;
  }

  /* Big Circular White Play/Pause Button */
  .play-main-btn {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #ffffff;
    color: #000000;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
    transition: transform 0.12s ease;
  }
  .play-main-btn:hover {
    transform: scale(1.08) !important;
    background: #ffffff;
  }

  .scrub-container {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }

  .time-text {
    font-family: var(--font-mono);
    font-size: 11px;
    color: #a0a0a0;
    min-width: 34px;
  }
  .time-text.right {
    text-align: right;
  }

  .slider-box {
    flex: 1;
    display: flex;
    align-items: center;
    position: relative;
  }

  .slider-box input[type='range'] {
    width: 100%;
    height: 4px;
    border-radius: 2px;
    background: #4d4d4d;
    accent-color: #1ed760;
    cursor: pointer;
    outline: none;
    transition: height 0.12s ease;
  }
  .slider-box:hover input[type='range'] {
    height: 6px;
    accent-color: #1fdf64;
  }

  /* Right: Telemetry, Lyrics, Queue, Volume & Fullscreen */
  .dock-telemetry {
    flex: 1;
    max-width: 480px;
    min-width: 260px;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
  }

  .dock-icon-action {
    background: transparent;
    border: none;
    color: #b3b3b3;
    padding: 6px;
    cursor: pointer;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s ease;
    flex-shrink: 0;
  }
  .dock-icon-action:hover {
    color: #ffffff;
    transform: scale(1.08);
  }
  .dock-icon-action.active {
    color: #1ed760;
  }

  .dock-spec-pill {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 3px 8px;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 500px;
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 700;
    color: #1ed760;
    border: 1px solid rgba(30, 215, 96, 0.2);
    white-space: nowrap;
    flex-shrink: 0;
    user-select: none;
    cursor: default;
  }
  .dot-live-sm {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #1ed760;
  }

  .spectrum-visualizer {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 24px;
    background: transparent;
    border: none;
    cursor: pointer;
    padding: 2px;
    flex-shrink: 0;
  }

  .spec-bar {
    width: 2px;
    background: #1ed760;
    border-radius: 1px;
    transition: height 0.08s ease;
  }

  .vol-cluster {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }

  .vol-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #b3b3b3;
    background: transparent;
    border: none;
    cursor: pointer;
    padding: 4px;
    transition: color 0.15s ease;
  }
  .vol-btn:hover {
    color: #ffffff;
  }

  .vol-slider {
    width: 68px;
    height: 4px;
    border-radius: 2px;
    background: #4d4d4d;
    accent-color: #1ed760;
    cursor: pointer;
    outline: none;
    transition: height 0.12s ease;
    flex-shrink: 0;
  }
  .vol-cluster:hover .vol-slider {
    height: 6px;
    accent-color: #1fdf64;
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    text-align: center;
    gap: 8px;
  }
  .empty-state h3 {
    font-size: 15px;
    font-weight: 700;
  }
  .empty-state p {
    font-size: 12px;
    color: var(--text-muted);
    max-width: 320px;
    margin-bottom: 6px;
  }

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  .inline-spinner {
    width: 12px;
    height: 12px;
    border: 2px solid rgba(224, 90, 43, 0.2);
    border-top-color: var(--signal-accent);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    display: inline-block;
  }

  .spinner-large {
    width: 32px;
    height: 32px;
    border: 3px solid rgba(224, 90, 43, 0.2);
    border-top-color: var(--signal-accent);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    display: inline-block;
  }

  .section-title-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .cloud-tracks-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 12px;
  }

  .cloud-track-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    border-radius: 10px;
    transition: all 0.15s ease;
  }

  .cloud-track-card:hover {
    background: var(--bg-elevated);
    border-color: var(--border-strong);
  }

  .cloud-track-card.active {
    border-color: var(--signal-accent);
    background: rgba(224, 90, 43, 0.06);
  }

  .track-card-art-box {
    width: 60px;
    height: 60px;
    border-radius: 8px;
    position: relative;
    overflow: hidden;
    background: var(--bg-primary);
    flex-shrink: 0;
  }

  .track-card-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  .track-card-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
  }

  .card-play-action {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.45);
    color: #fff;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .cloud-track-card:hover .card-play-action,
  .cloud-track-card.active .card-play-action {
    opacity: 1;
  }

  .track-card-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .track-card-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .cloud-track-card.active .track-card-title {
    color: var(--signal-accent);
  }

  .track-card-artist {
    font-size: 11px;
    color: var(--text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .track-card-bottom {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    width: 100%;
  }

  .track-duration-text {
    font-size: 11px;
    font-family: var(--font-mono);
    color: var(--text-muted);
    flex-shrink: 0;
  }

  .card-btn-cluster {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }

  .download-action-btn {
    color: var(--text-muted);
    padding: 4px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
    background: transparent;
    border: none;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .download-action-btn:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.12);
  }

  .shuffle-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    padding: 4px 10px;
    border-radius: 6px;
  }

  .cloud-loading-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    gap: 14px;
    color: var(--text-muted);
  }

  .loading-label {
    font-size: 13px;
    color: var(--text-secondary);
  }

  /* Toast Notification */
  .veyl-toast {
    position: fixed;
    bottom: 96px;
    left: 50%;
    transform: translateX(-50%);
    background: var(--bg-surface);
    color: var(--text-primary);
    border: 1px solid var(--border-default);
    padding: 10px 20px;
    border-radius: 9999px;
    font-size: 13px;
    font-weight: 600;
    box-shadow: 0 12px 36px rgba(0, 0, 0, 0.6);
    z-index: 9999;
    pointer-events: none;
    animation: toast-in 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  }

  @keyframes toast-in {
    from {
      opacity: 0;
      transform: translate(-50%, 12px);
    }
    to {
      opacity: 1;
      transform: translate(-50%, 0);
    }
  }

  /* Modals */
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.75);
    backdrop-filter: blur(6px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9990;
    animation: fadeIn 0.15s ease-out;
  }

  .modal-card {
    background: var(--bg-surface);
    border: 1px solid var(--border-default);
    border-radius: 16px;
    width: 480px;
    max-width: 90vw;
    padding: 24px;
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.8);
    display: flex;
    flex-direction: column;
    gap: 16px;
    color: var(--text-primary);
    animation: modalScaleIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  }

  @keyframes modalScaleIn {
    from {
      opacity: 0;
      transform: scale(0.95);
    }
    to {
      opacity: 1;
      transform: scale(1);
    }
  }

  .modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .modal-header-with-icon {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .modal-title {
    font-size: 17px;
    font-weight: 700;
    margin: 0;
    color: var(--text-primary);
  }

  .modal-close-btn {
    background: transparent;
    border: none;
    color: var(--text-muted);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    border-radius: 6px;
  }

  .modal-close-btn:hover {
    color: var(--text-primary);
    background: var(--bg-elevated);
  }

  .modal-body {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .modal-desc {
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.5;
    margin: 0 0 8px 0;
  }

  .modal-label {
    font-size: 12px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-muted);
  }

  .modal-input {
    background: var(--bg-void);
    border: 1px solid var(--border-default);
    border-radius: 10px;
    padding: 10px 14px;
    font-size: 13px;
    color: var(--text-primary);
    outline: none;
    transition: border-color 0.15s ease;
  }

  .modal-input:focus {
    border-color: var(--signal-accent);
  }

  .modal-error-text {
    font-size: 12px;
    color: #ef4444;
    background: rgba(239, 68, 68, 0.1);
    padding: 8px 12px;
    border-radius: 8px;
    margin-top: 4px;
  }

  .modal-footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 8px;
  }

  .btn-spotify {
    background: #1DB954;
    color: #000;
    font-weight: 700;
    border-radius: 10px;
    padding: 8px 16px;
    border: none;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    transition: all 0.15s ease;
  }

  .btn-spotify:hover:not(:disabled) {
    background: #1ed760;
    transform: translateY(-1px);
  }

  .btn-spotify:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .spotify-brand-icon {
    color: #1DB954;
  }

  .adding-track-preview {
    background: var(--bg-void);
    border: 1px solid var(--border-subtle);
    border-radius: 10px;
    padding: 10px 14px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-bottom: 8px;
  }

  .preview-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .preview-artist {
    font-size: 12px;
    color: var(--text-muted);
  }

  .modal-playlist-list {
    max-height: 240px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .modal-playlist-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    background: transparent;
    border: none;
    color: var(--text-primary);
    cursor: pointer;
    text-align: left;
    width: 100%;
    transition: background 0.15s ease;
  }

  .modal-playlist-item:hover {
    background: var(--bg-elevated);
  }

  .pl-item-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .pl-item-name {
    font-size: 13px;
    font-weight: 600;
  }

  .pl-item-count {
    font-size: 11px;
    color: var(--text-muted);
  }

  /* Smart Playlists & Playlists Grid */
  .smart-playlists-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
  }

  .smart-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    border-radius: 14px;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    cursor: pointer;
    transition: all 0.2s ease;
    position: relative;
  }

  .smart-card:hover {
    border-color: var(--border-default);
    transform: translateY(-2px);
  }

  .smart-card-art {
    width: 52px;
    height: 52px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .smart-card-art.heart-bg {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(239, 68, 68, 0.05));
    color: #ef4444;
  }

  .smart-card-art.history-bg {
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.03));
    color: var(--signal-accent);
  }

  .smart-card-info {
    flex: 1;
    min-width: 0;
  }

  .smart-card-title {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
  }

  .smart-card-sub {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 2px;
  }

  .playlists-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 16px;
  }

  .playlist-card {
    display: flex;
    flex-direction: column;
    gap: 10px;
    background: var(--bg-surface);
    border: 1px solid var(--border-subtle);
    border-radius: 14px;
    padding: 12px;
    cursor: pointer;
    transition: all 0.2s ease;
  }

  .playlist-card:hover {
    border-color: var(--border-default);
    transform: translateY(-2px);
  }

  .playlist-card-art {
    position: relative;
    aspect-ratio: 1;
    border-radius: 10px;
    overflow: hidden;
    background: var(--bg-void);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .playlist-card-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .playlist-card-placeholder {
    color: var(--text-muted);
  }

  .playlist-card-body {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .playlist-card-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .playlist-card-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    color: var(--text-muted);
  }

  .btn-danger-subtle {
    background: transparent;
    border: 1px solid var(--border-subtle);
    color: var(--text-muted);
    padding: 8px;
    border-radius: 8px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .btn-danger-subtle:hover {
    color: #ef4444;
    border-color: #ef4444;
    background: rgba(239, 68, 68, 0.1);
  }

  .delete-btn:hover {
    color: #ef4444 !important;
  }

  .empty-actions-row {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-top: 14px;
  }

  /* Search view & action buttons */
  .search-category-section {
    margin-top: 8px;
  }

  .title-with-spinner {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .empty-substate {
    padding: 24px;
    text-align: center;
    color: var(--text-muted);
    font-size: 13px;
  }
</style>
