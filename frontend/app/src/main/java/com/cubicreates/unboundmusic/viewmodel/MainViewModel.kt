/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModel.kt
 * Purpose: Central ViewModel coordinating reactive state flows, Media3 playback service,
 *          Go engine daemon lifecycle, and domain ViewModel extensions.
 * Subsystem: Application UI / Domain Layer
 * Concurrency: StateFlow reactive architecture running background I/O on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.daemon.DaemonLifecycleState
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.service.PlaybackUiState
import com.cubicreates.unboundmusic.service.ServiceConnection
import com.cubicreates.unboundmusic.service.StorageInitializer
import com.cubicreates.unboundmusic.ui.album.AlbumPlaylistData
import com.cubicreates.unboundmusic.ui.artist.ArtistProfileData
import com.cubicreates.unboundmusic.ui.components.RecognizedTrackVariant
import com.cubicreates.unboundmusic.ui.components.ShazamMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqHeadphoneItem
import com.cubicreates.unboundmusic.ui.recap.RecapData
import com.cubicreates.unboundmusic.ui.search.SearchCategory
import com.cubicreates.unboundmusic.ui.theme.AppThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Central ViewModel orchestrating all app state: playback, search, lyrics, analytics, library.
 * Uses Media3 ServiceConnection for audio playback and BackendClient for Go daemon IPC.
 * Domain logic is modularized across domain extension files in com.cubicreates.unboundmusic.viewmodel.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        internal const val TAG = "MainViewModel"
    }

    internal val daemonManager = DaemonManager.getInstance(application)
    internal val client = daemonManager.client
    internal val serviceConnection = ServiceConnection.getInstance(application)
    internal val downloadNotificationHelper = com.cubicreates.unboundmusic.notification.DownloadNotificationHelper(application)

    // ==================== Domain ViewModels Delegation ====================
    val playbackDomainViewModel by lazy { PlaybackViewModel(application) }
    val libraryDomainViewModel by lazy { LibraryViewModel(application) }
    val searchDomainViewModel by lazy { SearchViewModel(application) }
    val equalizerDomainViewModel by lazy { EqualizerViewModel(application) }
    val lyricsDomainViewModel by lazy { LyricsViewModel(application) }
    val downloadsDomainViewModel by lazy { DownloadsViewModel(application) }
    val shazamDomainViewModel by lazy { ShazamViewModel(application) }

    val daemonState: StateFlow<DaemonLifecycleState> = daemonManager.state

    /** Reactive playback state from the Media3 foreground service. */
    val playbackState: StateFlow<PlaybackUiState> = serviceConnection.playbackState

    /** Isolated playback boolean stream to prevent ticking position from triggering recompositions across the app tree */
    val isPlaying: StateFlow<Boolean> = serviceConnection.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Isolated active track / session existence stream to toggle mini player without position ticker recompositions */
    val hasActivePlayback: StateFlow<Boolean> = serviceConnection.playbackState
        .map { (it.isPlaying || it.currentPositionMs > 0) && it.currentTrack != null && it.currentTrack.id.isNotBlank() && it.currentTrack.title != "Unknown" }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ==================== Playback State ====================

    internal val _currentTrack = MutableStateFlow(TrackItem())
    val currentTrack: StateFlow<TrackItem> = _currentTrack.asStateFlow()

    internal val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ==================== Return YouTube Dislike (RYD) State ====================

    internal val _rydVotes = MutableStateFlow<RydVoteData?>(null)
    val rydVotes: StateFlow<RydVoteData?> = _rydVotes.asStateFlow()

    // ==================== Playback Quality & Automation ====================

    internal val _autoDownloadLikedSongs = MutableStateFlow(false)
    val autoDownloadLikedSongs: StateFlow<Boolean> = _autoDownloadLikedSongs.asStateFlow()

    internal val _skipSilenceEnabled = MutableStateFlow(false)
    val skipSilenceEnabled: StateFlow<Boolean> = _skipSilenceEnabled.asStateFlow()

    internal val _normalizeVolumeEnabled = MutableStateFlow(false)
    val normalizeVolumeEnabled: StateFlow<Boolean> = _normalizeVolumeEnabled.asStateFlow()

    internal val _sponsorBlockEnabled = MutableStateFlow(true)
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    internal val _streamingQuality = MutableStateFlow(PlaybackStateStore.getStreamingQuality(application))
    val streamingQuality: StateFlow<AudioQuality> = _streamingQuality.asStateFlow()

    internal val _downloadQuality = MutableStateFlow(PlaybackStateStore.getDownloadQuality(application))
    val downloadQuality: StateFlow<AudioQuality> = _downloadQuality.asStateFlow()

    internal val _isLockscreenWallpaperEnabled = MutableStateFlow(PlaybackStateStore.isLockscreenWallpaperEnabled(application))
    val isLockscreenWallpaperEnabled: StateFlow<Boolean> = _isLockscreenWallpaperEnabled.asStateFlow()

    internal val _isVlcAutoScanEnabled = MutableStateFlow(PlaybackStateStore.isVlcAutoScanEnabled(application))
    val isVlcAutoScanEnabled: StateFlow<Boolean> = _isVlcAutoScanEnabled.asStateFlow()

    // ==================== Home Mood Filtering ====================

    internal val _selectedHomeMood = MutableStateFlow("All")
    val selectedHomeMood: StateFlow<String> = _selectedHomeMood.asStateFlow()

    internal val _moodTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val moodTracks: StateFlow<List<TrackItem>> = _moodTracks.asStateFlow()

    internal val _isMoodLoading = MutableStateFlow(false)
    val isMoodLoading: StateFlow<Boolean> = _isMoodLoading.asStateFlow()

    internal val moodCache = java.util.concurrent.ConcurrentHashMap<String, List<TrackItem>>()

    // ==================== Equalizer & DSP State ====================

    internal val _equalizerCurve = MutableStateFlow(EqualizerCurve.FLAT)
    val equalizerCurve: StateFlow<EqualizerCurve> = _equalizerCurve.asStateFlow()

    internal val _autoEqResults = MutableStateFlow<List<AutoEqHeadphoneItem>>(emptyList())
    val autoEqResults: StateFlow<List<AutoEqHeadphoneItem>> = _autoEqResults.asStateFlow()

    internal val _isSearchingAutoEq = MutableStateFlow(false)
    val isSearchingAutoEq: StateFlow<Boolean> = _isSearchingAutoEq.asStateFlow()

    // Stream resolution & playback diagnostics
    internal val _streamDebugMessage = MutableStateFlow<String?>(null)
    val streamDebugMessage: StateFlow<String?> = _streamDebugMessage.asStateFlow()

    fun clearStreamDebugMessage() {
        _streamDebugMessage.value = null
    }

    // ==================== Theme Engine, DSP & Settings ====================

    internal val _selectedTheme = MutableStateFlow(AppThemePreset.STUDIO_DARK)
    val selectedTheme: StateFlow<AppThemePreset> = _selectedTheme.asStateFlow()

    internal val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    internal val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    internal val _loudnessGainMb = MutableStateFlow(0)
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    internal val _reverbPreset = MutableStateFlow<Short>(0)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    internal val _recentlyPlayedTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val recentlyPlayedTracks: StateFlow<List<TrackItem>> = _recentlyPlayedTracks.asStateFlow()

    internal val _favoriteTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val favoriteTracks: StateFlow<List<TrackItem>> = _favoriteTracks.asStateFlow()

    internal val _customEqPresets = MutableStateFlow<List<UserEqPresetDto>>(emptyList())
    val customEqPresets: StateFlow<List<UserEqPresetDto>> = _customEqPresets.asStateFlow()

    internal val _cachePurgeStatus = MutableStateFlow<String?>(null)
    val cachePurgeStatus: StateFlow<String?> = _cachePurgeStatus.asStateFlow()

    // ==================== Genre & Mood Boards State ====================

    internal val _genreSections = MutableStateFlow<List<GenreSectionDto>>(emptyList())
    val genreSections: StateFlow<List<GenreSectionDto>> = _genreSections.asStateFlow()

    internal val _activeGenreShelves = MutableStateFlow<List<PlaylistShelfDto>>(emptyList())
    val activeGenreShelves: StateFlow<List<PlaylistShelfDto>> = _activeGenreShelves.asStateFlow()

    internal val _isLoadingGenreDetail = MutableStateFlow(false)
    val isLoadingGenreDetail: StateFlow<Boolean> = _isLoadingGenreDetail.asStateFlow()

    internal val _selectedGenreTitle = MutableStateFlow("Genre")
    val selectedGenreTitle: StateFlow<String> = _selectedGenreTitle.asStateFlow()

    // ==================== Artist & Album State ====================

    internal val _artistProfile = MutableStateFlow<ArtistProfileData?>(null)
    val artistProfile: StateFlow<ArtistProfileData?> = _artistProfile.asStateFlow()

    internal val _isLoadingArtist = MutableStateFlow(false)
    val isLoadingArtist: StateFlow<Boolean> = _isLoadingArtist.asStateFlow()

    internal val _albumPlaylistData = MutableStateFlow<AlbumPlaylistData?>(null)
    val albumPlaylistData: StateFlow<AlbumPlaylistData?> = _albumPlaylistData.asStateFlow()

    // ==================== Recap State ====================

    internal val _recapData = MutableStateFlow(RecapData())
    val recapData: StateFlow<RecapData> = _recapData.asStateFlow()

    // ==================== Search State ====================

    internal val _searchCategory = MutableStateFlow(SearchCategory.ALL)
    val searchCategory: StateFlow<SearchCategory> = _searchCategory.asStateFlow()

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    internal val _searchResults = MutableStateFlow<List<TrackItem>>(emptyList())
    val searchResults: StateFlow<List<TrackItem>> = _searchResults.asStateFlow()

    internal val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    internal val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    internal val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    internal val _isListeningShazam = MutableStateFlow(false)
    val isListeningShazam: StateFlow<Boolean> = _isListeningShazam.asStateFlow()

    internal val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    val audioWaveAmplitude: StateFlow<Float> = com.cubicreates.unboundmusic.audio.AmbientAudioRecorder.audioAmplitude

    internal val _recognizedMessage = MutableStateFlow<String?>(null)
    val recognizedMessage: StateFlow<String?> = _recognizedMessage.asStateFlow()

    internal val _lastRecognizedTrack = MutableStateFlow<TrackItem?>(null)
    val lastRecognizedTrack: StateFlow<TrackItem?> = _lastRecognizedTrack.asStateFlow()

    internal val _recognizedVariants = MutableStateFlow<List<RecognizedTrackVariant>>(emptyList())
    val recognizedVariants: StateFlow<List<RecognizedTrackVariant>> = _recognizedVariants.asStateFlow()

    internal val _shazamHistory = MutableStateFlow<List<TrackItem>>(emptyList())
    val shazamHistory: StateFlow<List<TrackItem>> = _shazamHistory.asStateFlow()

    internal val _hummingHistory = MutableStateFlow<List<TrackItem>>(emptyList())
    val hummingHistory: StateFlow<List<TrackItem>> = _hummingHistory.asStateFlow()

    internal val _lastHummedTrack = MutableStateFlow<TrackItem?>(null)
    val lastHummedTrack: StateFlow<TrackItem?> = _lastHummedTrack.asStateFlow()

    internal val _hummedVariants = MutableStateFlow<List<RecognizedTrackVariant>>(emptyList())
    val hummedVariants: StateFlow<List<RecognizedTrackVariant>> = _hummedVariants.asStateFlow()

    internal val _shazamMode = MutableStateFlow(ShazamMode.ACOUSTIC)
    val shazamMode: StateFlow<ShazamMode> = _shazamMode.asStateFlow()

    // ==================== Application Lifecycle & Update State ====================

    internal val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    internal val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun checkForAppUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                // First check direct GitHub OTA release manifest
                val directInfo = com.cubicreates.unboundmusic.updater.AppUpdateManager.checkForUpdates(
                    getApplication(),
                    silent = !manual
                )
                if (directInfo != null && directInfo.hasUpdate) {
                    _availableUpdate.value = directInfo
                    return@launch
                }

                val currentVer = "2.0.0"
                val res = withContext(Dispatchers.IO) {
                    client.checkForUpdates(currentVer)
                }
                if (res.first in 200..299 && res.second.isNotBlank()) {
                    val info = AppUpdateInfo.fromJson(res.second)
                    if (info != null && info.hasUpdate && info.latestVersion.isNotBlank()) {
                        _availableUpdate.value = info
                    } else if (manual) {
                        withContext(Dispatchers.Main) {
                            com.cubicreates.unboundmusic.util.UnboundToast.show(
                                getApplication(),
                                "Unbound Music is up to date (v$currentVer)"
                            )
                        }
                    }
                } else if (manual) {
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Unbound Music is up to date"
                        )
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Update check: ${e.message}"
                        )
                    }
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    val audioPermissionRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voiceSearchRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val humSearchRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val storagePermissionRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ==================== Library & Storage State ====================

    internal val _libraryTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val libraryTracks: StateFlow<List<TrackItem>> = _libraryTracks.asStateFlow()

    internal val _musicTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val musicTracks: StateFlow<List<TrackItem>> = _musicTracks.asStateFlow()

    internal val _mixedAudioTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val mixedAudioTracks: StateFlow<List<TrackItem>> = _mixedAudioTracks.asStateFlow()

    internal val _artistPlaylists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
    val artistPlaylists: StateFlow<List<CustomPlaylist>> = _artistPlaylists.asStateFlow()

    internal val _albumicPlaylists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
    val albumicPlaylists: StateFlow<List<CustomPlaylist>> = _albumicPlaylists.asStateFlow()

    internal val _albumCompletions = MutableStateFlow<List<AlbumCompletionStatus>>(emptyList())
    val albumCompletions: StateFlow<List<AlbumCompletionStatus>> = _albumCompletions.asStateFlow()

    internal val _savedGB = MutableStateFlow(0.0)
    val savedGB: StateFlow<Double> = _savedGB.asStateFlow()

    internal val _downloadsCount = MutableStateFlow(0)
    val downloadsCount: StateFlow<Int> = _downloadsCount.asStateFlow()

    internal val _whatsappCount = MutableStateFlow(0)
    val whatsappCount: StateFlow<Int> = _whatsappCount.asStateFlow()

    internal val _telegramCount = MutableStateFlow(0)
    val telegramCount: StateFlow<Int> = _telegramCount.asStateFlow()

    internal val _youtubeCount = MutableStateFlow(0)
    val youtubeCount: StateFlow<Int> = _youtubeCount.asStateFlow()

    // ==================== Offline Downloader & Storage Engine ====================

    internal val _downloadTasks = MutableStateFlow<Map<String, DownloadTaskDto>>(emptyMap())
    val downloadTasks: StateFlow<Map<String, DownloadTaskDto>> = _downloadTasks.asStateFlow()

    internal val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    internal val _downloadSpeedBps = MutableStateFlow(0L)
    val downloadSpeedBps: StateFlow<Long> = _downloadSpeedBps.asStateFlow()

    internal val _downloadedMusicTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val downloadedMusicTracks: StateFlow<List<TrackItem>> = _downloadedMusicTracks.asStateFlow()

    internal val _cacheSizeMB = MutableStateFlow(0.0)
    val cacheSizeMB: StateFlow<Double> = _cacheSizeMB.asStateFlow()

    internal val _downloadsSizeMB = MutableStateFlow(0.0)
    val downloadsSizeMB: StateFlow<Double> = _downloadsSizeMB.asStateFlow()

    internal val _freeStorageGB = MutableStateFlow(0.0)
    val freeStorageGB: StateFlow<Double> = _freeStorageGB.asStateFlow()

    // ==================== Custom User Playlists State ====================

    internal val _customPlaylists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
    val customPlaylists: StateFlow<List<CustomPlaylist>> = _customPlaylists.asStateFlow()

    internal val _activeCustomPlaylist = MutableStateFlow<CustomPlaylist?>(null)
    val activeCustomPlaylist: StateFlow<CustomPlaylist?> = _activeCustomPlaylist.asStateFlow()

    internal val _trackToAddToPlaylist = MutableStateFlow<TrackItem?>(null)
    val trackToAddToPlaylist: StateFlow<TrackItem?> = _trackToAddToPlaylist.asStateFlow()

    internal var isPollingDownloads = false

    // ==================== YouTube Account & Synced Library State ====================

    internal val _isYouTubeConnected = MutableStateFlow(false)
    val isYouTubeConnected: StateFlow<Boolean> = _isYouTubeConnected.asStateFlow()

    internal val _accountName = MutableStateFlow("Local User")
    val accountName: StateFlow<String> = _accountName.asStateFlow()

    internal val _userAvatarUrl = MutableStateFlow<String?>(null)
    val userAvatarUrl: StateFlow<String?> = _userAvatarUrl.asStateFlow()

    internal val _syncedYouTubeTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val syncedYouTubeTracks: StateFlow<List<TrackItem>> = _syncedYouTubeTracks.asStateFlow()

    internal val _userMixes = MutableStateFlow<List<MixDto>>(emptyList())
    val userMixes: StateFlow<List<MixDto>> = _userMixes.asStateFlow()

    internal val _smartShelves = MutableStateFlow<List<SmartShelfDto>>(emptyList())
    val smartShelves: StateFlow<List<SmartShelfDto>> = _smartShelves.asStateFlow()

    internal val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    internal val _deviceAuthData = MutableStateFlow<DeviceCodeData?>(null)
    val deviceAuthData: StateFlow<DeviceCodeData?> = _deviceAuthData.asStateFlow()

    internal val _isStartingDeviceAuth = MutableStateFlow(false)
    val isStartingDeviceAuth: StateFlow<Boolean> = _isStartingDeviceAuth.asStateFlow()

    internal val _isPollingDeviceAuth = MutableStateFlow(false)
    val isPollingDeviceAuth: StateFlow<Boolean> = _isPollingDeviceAuth.asStateFlow()

    internal val _deviceAuthError = MutableStateFlow<String?>(null)
    val deviceAuthError: StateFlow<String?> = _deviceAuthError.asStateFlow()

    internal var deviceAuthJob: Job? = null

    internal val _cascadeSearchResponse = MutableStateFlow<CascadeSearchResponse?>(null)
    val cascadeSearchResponse: StateFlow<CascadeSearchResponse?> = _cascadeSearchResponse.asStateFlow()

    internal val _isLoadingMoreTracks = MutableStateFlow(false)
    val isLoadingMoreTracks: StateFlow<Boolean> = _isLoadingMoreTracks.asStateFlow()

    // ==================== Lyrics State ====================

    internal val _lyricsLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricsLines: StateFlow<List<LyricLine>> = _lyricsLines.asStateFlow()

    internal val _lyricsSource = MutableStateFlow("")
    val lyricsSource: StateFlow<String> = _lyricsSource.asStateFlow()

    internal val _romanizationMode = MutableStateFlow(RomanizationMode.ORIGINAL)
    val romanizationMode: StateFlow<RomanizationMode> = _romanizationMode.asStateFlow()

    internal val _lyricsTimingOffsetMs = MutableStateFlow(0L)
    val lyricsTimingOffsetMs: StateFlow<Long> = _lyricsTimingOffsetMs.asStateFlow()

    internal val _isInstrumental = MutableStateFlow(false)
    val isInstrumental: StateFlow<Boolean> = _isInstrumental.asStateFlow()

    internal var lyricsFetchJob: Job? = null

    // ==================== Canvas / Visual State ====================

    internal val _canvasVideoUrl = MutableStateFlow<String?>(null)
    val canvasVideoUrl: StateFlow<String?> = _canvasVideoUrl.asStateFlow()

    internal val _canvasArtUrl = MutableStateFlow<String?>(null)
    val canvasArtUrl: StateFlow<String?> = _canvasArtUrl.asStateFlow()

    // ==================== Playback Resilience, Skit Skipping & Sleep Engine ====================

    internal val _activeSkipSegments = MutableStateFlow<List<SkipSegmentDto>>(emptyList())
    val activeSkipSegments: StateFlow<List<SkipSegmentDto>> = _activeSkipSegments.asStateFlow()

    internal val _skippedSkitNotice = MutableStateFlow<SkitSkipNotice?>(null)
    val skippedSkitNotice: StateFlow<SkitSkipNotice?> = _skippedSkitNotice.asStateFlow()

    internal val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    internal var skitNoticeDismissJob: Job? = null
    internal var lastSkippedSegmentId: String? = null
    internal var sleepTimerJob: Job? = null

    // ==================== Home Feed State ====================

    internal val _moodCategories = MutableStateFlow<List<MoodCategory>>(emptyList())
    val moodCategories: StateFlow<List<MoodCategory>> = _moodCategories.asStateFlow()

    internal val _chartTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val chartTracks: StateFlow<List<TrackItem>> = _chartTracks.asStateFlow()

    // ==================== Cold Start & Guest State ====================

    internal val _regionalCharts = MutableStateFlow<List<TrackItem>>(emptyList())
    val regionalCharts: StateFlow<List<TrackItem>> = _regionalCharts.asStateFlow()

    internal val _daypartingState = MutableStateFlow<DaypartingState?>(null)
    val daypartingState: StateFlow<DaypartingState?> = _daypartingState.asStateFlow()

    internal val _libraryFolders = MutableStateFlow<Map<String, List<LocalTrack>>>(emptyMap())
    val libraryFolders: StateFlow<Map<String, List<LocalTrack>>> = _libraryFolders.asStateFlow()

    internal val _adaptiveQuickPicks = MutableStateFlow<List<TrackItem>>(emptyList())
    val adaptiveQuickPicks: StateFlow<List<TrackItem>> = _adaptiveQuickPicks.asStateFlow()

    internal val _quickPicksTitle = MutableStateFlow("Trending Quick Picks")
    val quickPicksTitle: StateFlow<String> = _quickPicksTitle.asStateFlow()

    internal val _quickPicksSubtitle = MutableStateFlow("Start a radio or continuous mix")
    val quickPicksSubtitle: StateFlow<String> = _quickPicksSubtitle.asStateFlow()

    // ==================== Affective MIR & Decoupled Vibe State ====================
    internal val _homeVibeState = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val homeVibeState: StateFlow<VibeSearchUiState> = _homeVibeState.asStateFlow()

    internal val _searchVibeState = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val searchVibeState: StateFlow<VibeSearchUiState> = _searchVibeState.asStateFlow()

    // Backward-compatibility bridge
    val vibeSearchResult: StateFlow<VibeSearchUiState> get() = _homeVibeState

    // ==================== Startup & Telemetry State ====================

    internal val _startupPhase = MutableStateFlow("SYSTEM_INIT")
    val startupPhase: StateFlow<String> = _startupPhase.asStateFlow()

    internal val _startupProgress = MutableStateFlow(0.1f)
    val startupProgress: StateFlow<Float> = _startupProgress.asStateFlow()

    internal val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()

    internal val _unboundFolderPrompt = MutableStateFlow<UnboundFolderPromptState?>(null)
    val unboundFolderPrompt: StateFlow<UnboundFolderPromptState?> = _unboundFolderPrompt.asStateFlow()

    internal var lastSavedStateTick = 0L
    internal var lastMagicRadioTriggerTime = 0L
    internal val magicRadioMutex = Mutex()

    internal val _currentQueue = MutableStateFlow<List<TrackItem>>(emptyList())
    val currentQueue: StateFlow<List<TrackItem>> = _currentQueue.asStateFlow()

    internal var guestRadioJob: Job? = null
    internal var automixJob: Job? = null
    internal var searchJob: Job? = null
    internal var suggestionJob: Job? = null
    internal val _fallbackStatus = MutableStateFlow<FallbackStatusDto?>(null)
    val fallbackStatus: StateFlow<FallbackStatusDto?> = _fallbackStatus.asStateFlow()

    internal val isUniversalScanning = java.util.concurrent.atomic.AtomicBoolean(false)
    internal val _isIdentifyingTrack = MutableStateFlow<String?>(null)
    val isIdentifyingTrack: StateFlow<String?> = _isIdentifyingTrack.asStateFlow()
    internal val recentlyCancelledIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        // Connect to Media3 playback service safely
        try {
            serviceConnection.connect()
        } catch (e: Throwable) {
            Log.e(TAG, "serviceConnection.connect failed: ${e.message}")
        }

        // Start position ticker for smooth progress bar updates
        try {
            startPositionTicker()
        } catch (_: Throwable) {}

        // Sync current track and reload lyrics on track change
        viewModelScope.launch {
            try {
                serviceConnection.playbackState.collect { state ->
                    state.currentTrack?.let { track ->
                        if (track.title != "Unknown" && track.title.isNotBlank()) {
                            val prev = _currentTrack.value
                            val changed = prev.id != track.id || !prev.title.equals(track.title, ignoreCase = true)
                            if (changed) {
                                _currentTrack.value = track
                                _isFavorite.value = try {
                                    PlaybackStateStore.isFavoriteTrack(getApplication(), track.id, track.title)
                                } catch (_: Throwable) { false }
                                loadLyricsForTrack(track)
                                launch(Dispatchers.IO) {
                                    try {
                                        fetchCanvas(track)
                                        fetchSkipSegments(track)
                                        fetchRydVotes(track)
                                    } catch (_: Throwable) {}
                                }
                                saveCurrentPlaybackState(0L)

                                // Guest Mode Strategy A: proactively extend radio queue when approaching the end
                                if (!_isYouTubeConnected.value && track.id.isNotBlank() && !track.id.startsWith("local:")) {
                                    val q = _currentQueue.value
                                    val idx = q.indexOfFirst { it.id == track.id }
                                    if (idx >= 0 && idx >= q.size - 3) {
                                        fetchGuestAlgorithmicRadio(track, append = true)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Playback state collector loop note: ${e.message}")
            }
        }

        // Restore persistent YouTube Music session from local disk store immediately
        try {
            val sessionStore = SessionStore.getInstance(application)
            if (sessionStore.hasActiveSession) {
                _isYouTubeConnected.value = true
                sessionStore.accountName?.let { _accountName.value = it }
                sessionStore.avatarUrl?.let { _userAvatarUrl.value = it }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "SessionStore init note: ${e.message}")
        }

        // Load Playback Quality & Automation Preferences
        try {
            _autoDownloadLikedSongs.value = PlaybackStateStore.isAutoDownloadLiked(application)
            _skipSilenceEnabled.value = PlaybackStateStore.isSkipSilence(application)
            _normalizeVolumeEnabled.value = PlaybackStateStore.isNormalizeVolume(application)
            _sponsorBlockEnabled.value = PlaybackStateStore.isSponsorBlockEnabled(application)
            if (_normalizeVolumeEnabled.value) {
                serviceConnection.setLoudness(1000)
            }
            if (_skipSilenceEnabled.value) {
                serviceConnection.setSkipSilence(true)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Playback automation prefs note: ${e.message}")
        }

        // Restore persistent queue and active track across app restarts safely
        try {
            restoreLastPlaybackState()
        } catch (e: Throwable) {
            Log.e(TAG, "restoreLastPlaybackState failed: ${e.message}")
        }

        // Load persistent search history
        try {
            loadSearchHistory()
        } catch (_: Throwable) {}

        // Orchestrate startup hydration with splash screen telemetry
        try {
            startStartupHydration()
        } catch (e: Throwable) {
            Log.e(TAG, "startStartupHydration failed: ${e.message}")
            _isAppReady.value = true
        }

        // Resume download polling if previous active tasks exist
        try {
            startDownloadPollingLoop()
            loadDownloadedMusicFiles()
            updateStorageMetrics()
            loadCustomPlaylists()
        } catch (e: Throwable) {
            Log.w(TAG, "Background tasks init note: ${e.message}")
        }

        try {
            _recentlyPlayedTracks.value = PlaybackStateStore.getRecentlyPlayed(application)
            _favoriteTracks.value = PlaybackStateStore.getFavoriteTracks(application)
            refreshAdaptiveQuickPicks()
        } catch (_: Throwable) {}

        // Auto-advance to next track when playback of current song ends
        serviceConnection.onTrackEndedListener = {
            viewModelScope.launch(Dispatchers.Main) {
                nextTrack()
            }
        }
        serviceConnection.onSkipToNextListener = {
            viewModelScope.launch(Dispatchers.Main) {
                nextTrack()
            }
        }
        serviceConnection.onSkipToPreviousListener = {
            viewModelScope.launch(Dispatchers.Main) {
                prevTrack()
            }
        }
    }

    internal fun restoreLastPlaybackState() {
        try {
            val saved = PlaybackStateStore.loadPlaybackState(getApplication()) ?: return
            if (saved.track.id.isNotBlank() || saved.track.title.isNotBlank()) {
                _currentTrack.value = saved.track
                if (saved.queue.isNotEmpty()) {
                    _currentQueue.value = saved.queue
                    try {
                        serviceConnection.setQueue(saved.queue)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed restoring queue in ServiceConnection: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed restoring last playback state: ${e.message}")
            try {
                PlaybackStateStore.clearPlaybackState(getApplication())
            } catch (_: Throwable) {}
        }
    }

    internal fun saveCurrentPlaybackState(positionMs: Long? = null) {
        val track = _currentTrack.value
        if (track.id.isBlank() && track.title.isBlank()) return
        val queue = getEffectiveQueue()
        val pos = positionMs ?: playbackState.value.currentPositionMs
        PlaybackStateStore.savePlaybackState(getApplication(), track, queue, pos)
    }

    private fun startStartupHydration() {
        viewModelScope.launch(Dispatchers.IO) {
            _startupPhase.value = "STORAGE_CHECK"
            _startupProgress.value = 0.15f

            // Ensure canonical Unbound root folder exists
            com.cubicreates.unboundmusic.service.UnboundStorageManager.getCanonicalUnboundRoot(getApplication())
            val prefs = getApplication<Application>().getSharedPreferences("unbound_boot_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_checked_existing_folder", true).apply()
            deployDaemonAndHydrate()
        }
    }

    fun confirmDeleteExistingUnboundFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            _unboundFolderPrompt.value = null
            deployDaemonAndHydrate()
        }
    }

    fun keepExistingUnboundFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            _unboundFolderPrompt.value = null
            deployDaemonAndHydrate()
        }
    }

    private fun deployDaemonAndHydrate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _startupPhase.value = "SYSTEM_INIT"
                _startupProgress.value = 0.35f

                // Unpack assets and directories safely
                try {
                    StorageInitializer.initialize(getApplication())
                } catch (e: Exception) {
                    Log.w(TAG, "StorageInitializer error: ${e.message}")
                }

                _startupPhase.value = "DAEMON_CONNECT"
                _startupProgress.value = 0.60f

                // Start Go Engine Daemon
                daemonManager.startDaemonAuto(force = true)

                // Quick handshake with daemon (max 1 second)
                for (i in 1..8) {
                    try {
                        val (code, _) = client.healthCheck()
                        if (code in 200..299) break
                    } catch (_: Exception) {}
                    delay(120)
                }

                _startupPhase.value = "CACHE_HYDRATE"
                _startupProgress.value = 0.90f
            } catch (e: Exception) {
                Log.e(TAG, "Startup hydration core exception: ${e.message}", e)
            } finally {
                _startupProgress.value = 1.0f
                _startupPhase.value = "READY"
                _isAppReady.value = true

                // Asynchronously hydrate online feeds & sync in background without delaying UI presentation
                launch {
                    try { StorageInitializer.unpackModelsIfPending(getApplication()) } catch (_: Exception) {}
                    val userCountry = com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
                    val userLanguage = com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
                    initializeColdStart(userCountry, userLanguage)
                    loadHomeFeed()
                    refreshLibrary()
                    val sessionStore = SessionStore.getInstance(getApplication())
                    if (sessionStore.hasActiveSession && !sessionStore.cookie.isNullOrBlank()) {
                        try {
                            client.syncAccount(sessionStore.cookie!!)
                        } catch (e: Exception) {
                            Log.w(TAG, "Session re-sync on startup note: ${e.message}")
                        }
                    }
                    checkAccountStatus()
                    loadAppSettings()
                    loadCustomEqPresets()
                    loadMoodsAndGenres(userCountry, userLanguage)
                    checkForAppUpdates(manual = false)
                    try {
                        daemonManager.trimMemory()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun completeStartup() {
        _isAppReady.value = true
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection.disconnect()
    }
}

/**
 * Prompt state for existing Unbound folder detection on clean boot.
 */
data class UnboundFolderPromptState(
    val folderPaths: List<String>
)

/**
 * Represents a single line of synchronized lyrics with millisecond timestamps and optional phonetic Romanization.
 */
data class LyricLine(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val romanized: String = ""
)

/**
 * Represents a mood/moment category from the Explore feed.
 */
data class MoodCategory(
    val title: String,
    val description: String,
    val color: String
)

/**
 * Represents an active notification when an intro/outro/skit was automatically skipped.
 */
data class SkitSkipNotice(
    val segment: SkipSegmentDto,
    val fromMs: Long,
    val toMs: Long,
    val message: String
)
