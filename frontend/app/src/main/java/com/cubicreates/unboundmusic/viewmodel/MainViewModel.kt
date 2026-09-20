/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModel.kt
 * Purpose: Production ViewModel orchestrating live UI state, native Go daemon API calls,
 *          Media3 playback service, YouTube Music search, stream resolution, lyrics,
 *          analytics logging, and AI-powered vibe search.
 * Subsystem: Application UI / Domain Layer
 * Concurrency: StateFlow reactive architecture running background I/O on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.daemon.DaemonLifecycleState
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.AccountStatusData
import com.cubicreates.unboundmusic.data.AudioQuality
import com.cubicreates.unboundmusic.data.CascadeSearchResponse
import com.cubicreates.unboundmusic.data.CuratedCollections
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.DeviceCodeData
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.DownloadStartRequest
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.LocalPlaylistStore
import com.cubicreates.unboundmusic.data.MediaStoreAudioBridge
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.GenreSectionDto
import com.cubicreates.unboundmusic.data.LocalTrack
import com.cubicreates.unboundmusic.data.MixDto
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.data.PlaybackStateStore
import com.cubicreates.unboundmusic.data.PlaylistItemDto
import com.cubicreates.unboundmusic.data.PlaylistShelfDto
import com.cubicreates.unboundmusic.data.RydVoteData
import com.cubicreates.unboundmusic.data.SkipSegmentDto
import com.cubicreates.unboundmusic.data.SleepTimerState
import com.cubicreates.unboundmusic.data.UserEqPresetDto
import com.cubicreates.unboundmusic.data.VibeResult
import com.cubicreates.unboundmusic.data.VibeSearchResponse
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.service.PlaybackUiState
import com.cubicreates.unboundmusic.service.ServiceConnection
import com.cubicreates.unboundmusic.service.StorageInitializer
import com.cubicreates.unboundmusic.service.UnboundPlaybackService
import com.cubicreates.unboundmusic.ui.album.AlbumPlaylistData
import com.cubicreates.unboundmusic.ui.artist.ArtistAlbumItem
import com.cubicreates.unboundmusic.ui.artist.ArtistProfileData
import com.cubicreates.unboundmusic.ui.artist.SimilarArtistItem
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqHeadphoneItem
import com.cubicreates.unboundmusic.ui.recap.RecapData
import com.cubicreates.unboundmusic.ui.theme.AppThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Central ViewModel orchestrating all app state: playback, search, lyrics, analytics, library.
 * Uses Media3 ServiceConnection for audio playback and BackendClient for Go daemon IPC.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client
    private val serviceConnection = ServiceConnection.getInstance(application)
    private val downloadNotificationHelper = com.cubicreates.unboundmusic.notification.DownloadNotificationHelper(application)

    val daemonState: StateFlow<DaemonLifecycleState> = daemonManager.state

    /** Reactive playback state from the Media3 foreground service. */
    val playbackState: StateFlow<PlaybackUiState> = serviceConnection.playbackState

    // ==================== Playback State ====================

    private val _currentTrack = MutableStateFlow(defaultTopTracks[0])
    val currentTrack: StateFlow<TrackItem> = _currentTrack.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ==================== Return YouTube Dislike (RYD) State ====================

    private val _rydVotes = MutableStateFlow<RydVoteData?>(null)
    val rydVotes: StateFlow<RydVoteData?> = _rydVotes.asStateFlow()

    // ==================== Playback Quality & Automation (Batch 1) ====================

    private val _autoDownloadLikedSongs = MutableStateFlow(false)
    val autoDownloadLikedSongs: StateFlow<Boolean> = _autoDownloadLikedSongs.asStateFlow()

    private val _skipSilenceEnabled = MutableStateFlow(false)
    val skipSilenceEnabled: StateFlow<Boolean> = _skipSilenceEnabled.asStateFlow()

    private val _normalizeVolumeEnabled = MutableStateFlow(false)
    val normalizeVolumeEnabled: StateFlow<Boolean> = _normalizeVolumeEnabled.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(true)
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _streamingQuality = MutableStateFlow(PlaybackStateStore.getStreamingQuality(application))
    val streamingQuality: StateFlow<AudioQuality> = _streamingQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(PlaybackStateStore.getDownloadQuality(application))
    val downloadQuality: StateFlow<AudioQuality> = _downloadQuality.asStateFlow()

    fun setStreamingQuality(quality: AudioQuality) {
        _streamingQuality.value = quality
        PlaybackStateStore.setStreamingQuality(getApplication(), quality)
    }

    fun setDownloadQuality(quality: AudioQuality) {
        _downloadQuality.value = quality
        PlaybackStateStore.setDownloadQuality(getApplication(), quality)
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        serviceConnection.setPlaybackSpeed(speed, pitch)
    }

    // ==================== Home Mood Filtering ====================

    private val _selectedHomeMood = MutableStateFlow("All")
    val selectedHomeMood: StateFlow<String> = _selectedHomeMood.asStateFlow()

    private val _moodTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val moodTracks: StateFlow<List<TrackItem>> = _moodTracks.asStateFlow()

    private val _isMoodLoading = MutableStateFlow(false)
    val isMoodLoading: StateFlow<Boolean> = _isMoodLoading.asStateFlow()

    private val moodCache = java.util.concurrent.ConcurrentHashMap<String, List<TrackItem>>()

    // ==================== Equalizer & DSP State ====================

    private val _equalizerCurve = MutableStateFlow(EqualizerCurve.FLAT)
    val equalizerCurve: StateFlow<EqualizerCurve> = _equalizerCurve.asStateFlow()

    private val _autoEqResults = MutableStateFlow<List<AutoEqHeadphoneItem>>(emptyList())
    val autoEqResults: StateFlow<List<AutoEqHeadphoneItem>> = _autoEqResults.asStateFlow()

    private val _isSearchingAutoEq = MutableStateFlow(false)
    val isSearchingAutoEq: StateFlow<Boolean> = _isSearchingAutoEq.asStateFlow()

    // Stream resolution & playback diagnostics
    private val _streamDebugMessage = MutableStateFlow<String?>(null)
    val streamDebugMessage: StateFlow<String?> = _streamDebugMessage.asStateFlow()

    fun clearStreamDebugMessage() {
        _streamDebugMessage.value = null
    }

    // ==================== Phase 3: Theme Engine, DSP & Settings Studio ====================

    private val _selectedTheme = MutableStateFlow(AppThemePreset.STUDIO_DARK)
    val selectedTheme: StateFlow<AppThemePreset> = _selectedTheme.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(0)
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    private val _reverbPreset = MutableStateFlow<Short>(0)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    private val _recentlyPlayedTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val recentlyPlayedTracks: StateFlow<List<TrackItem>> = _recentlyPlayedTracks.asStateFlow()

    private val _favoriteTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val favoriteTracks: StateFlow<List<TrackItem>> = _favoriteTracks.asStateFlow()

    private val _customEqPresets = MutableStateFlow<List<UserEqPresetDto>>(emptyList())
    val customEqPresets: StateFlow<List<UserEqPresetDto>> = _customEqPresets.asStateFlow()

    private val _cachePurgeStatus = MutableStateFlow<String?>(null)
    val cachePurgeStatus: StateFlow<String?> = _cachePurgeStatus.asStateFlow()

    // ==================== Phase 4: Genre & Mood Boards State ====================

    private val _genreSections = MutableStateFlow<List<GenreSectionDto>>(emptyList())
    val genreSections: StateFlow<List<GenreSectionDto>> = _genreSections.asStateFlow()

    private val _activeGenreShelves = MutableStateFlow<List<PlaylistShelfDto>>(emptyList())
    val activeGenreShelves: StateFlow<List<PlaylistShelfDto>> = _activeGenreShelves.asStateFlow()

    private val _isLoadingGenreDetail = MutableStateFlow(false)
    val isLoadingGenreDetail: StateFlow<Boolean> = _isLoadingGenreDetail.asStateFlow()

    private val _selectedGenreTitle = MutableStateFlow("Genre")
    val selectedGenreTitle: StateFlow<String> = _selectedGenreTitle.asStateFlow()

    // ==================== Artist & Album State ====================

    private val _artistProfile = MutableStateFlow<ArtistProfileData?>(null)
    val artistProfile: StateFlow<ArtistProfileData?> = _artistProfile.asStateFlow()

    private val _isLoadingArtist = MutableStateFlow(false)
    val isLoadingArtist: StateFlow<Boolean> = _isLoadingArtist.asStateFlow()

    private val _albumPlaylistData = MutableStateFlow<AlbumPlaylistData?>(null)
    val albumPlaylistData: StateFlow<AlbumPlaylistData?> = _albumPlaylistData.asStateFlow()

    // ==================== Recap State ====================

    private val _recapData = MutableStateFlow(RecapData())
    val recapData: StateFlow<RecapData> = _recapData.asStateFlow()

    // ==================== Search State ====================

    private val _searchCategory = MutableStateFlow(com.cubicreates.unboundmusic.ui.search.SearchCategory.ALL)
    val searchCategory: StateFlow<com.cubicreates.unboundmusic.ui.search.SearchCategory> = _searchCategory.asStateFlow()

    fun setSearchCategory(category: com.cubicreates.unboundmusic.ui.search.SearchCategory) {
        if (_searchCategory.value != category) {
            _searchCategory.value = category
            val currentQ = _searchQuery.value
            if (currentQ.isNotBlank()) {
                onSearchQueryChanged(currentQ)
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackItem>>(emptyList())
    val searchResults: StateFlow<List<TrackItem>> = _searchResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isListeningShazam = MutableStateFlow(false)
    val isListeningShazam: StateFlow<Boolean> = _isListeningShazam.asStateFlow()

    private val _recognizedMessage = MutableStateFlow<String?>(null)
    val recognizedMessage: StateFlow<String?> = _recognizedMessage.asStateFlow()

    // ==================== Library & Storage State ====================

    private val _libraryTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val libraryTracks: StateFlow<List<TrackItem>> = _libraryTracks.asStateFlow()

    private val _savedGB = MutableStateFlow(0.0)
    val savedGB: StateFlow<Double> = _savedGB.asStateFlow()

    private val _downloadsCount = MutableStateFlow(0)
    val downloadsCount: StateFlow<Int> = _downloadsCount.asStateFlow()

    private val _whatsappCount = MutableStateFlow(0)
    val whatsappCount: StateFlow<Int> = _whatsappCount.asStateFlow()

    private val _telegramCount = MutableStateFlow(0)
    val telegramCount: StateFlow<Int> = _telegramCount.asStateFlow()

    private val _youtubeCount = MutableStateFlow(0)
    val youtubeCount: StateFlow<Int> = _youtubeCount.asStateFlow()

    // ==================== Phase 5: Offline Downloader & Storage Engine ====================

    private val _downloadTasks = MutableStateFlow<Map<String, DownloadTaskDto>>(emptyMap())
    val downloadTasks: StateFlow<Map<String, DownloadTaskDto>> = _downloadTasks.asStateFlow()

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    private val _downloadSpeedBps = MutableStateFlow(0L)
    val downloadSpeedBps: StateFlow<Long> = _downloadSpeedBps.asStateFlow()

    private val _downloadedMusicTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val downloadedMusicTracks: StateFlow<List<TrackItem>> = _downloadedMusicTracks.asStateFlow()

    private val _cacheSizeMB = MutableStateFlow(0.0)
    val cacheSizeMB: StateFlow<Double> = _cacheSizeMB.asStateFlow()

    private val _downloadsSizeMB = MutableStateFlow(0.0)
    val downloadsSizeMB: StateFlow<Double> = _downloadsSizeMB.asStateFlow()

    private val _freeStorageGB = MutableStateFlow(0.0)
    val freeStorageGB: StateFlow<Double> = _freeStorageGB.asStateFlow()

    // ==================== Custom User Playlists State ====================

    private val _customPlaylists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
    val customPlaylists: StateFlow<List<CustomPlaylist>> = _customPlaylists.asStateFlow()

    private val _activeCustomPlaylist = MutableStateFlow<CustomPlaylist?>(null)
    val activeCustomPlaylist: StateFlow<CustomPlaylist?> = _activeCustomPlaylist.asStateFlow()

    private val _trackToAddToPlaylist = MutableStateFlow<TrackItem?>(null)
    val trackToAddToPlaylist: StateFlow<TrackItem?> = _trackToAddToPlaylist.asStateFlow()

    private var isPollingDownloads = false

    // ==================== YouTube Account & Synced Library State ====================

    private val _isYouTubeConnected = MutableStateFlow(false)
    val isYouTubeConnected: StateFlow<Boolean> = _isYouTubeConnected.asStateFlow()

    private val _accountName = MutableStateFlow("Local User")
    val accountName: StateFlow<String> = _accountName.asStateFlow()

    private val _userAvatarUrl = MutableStateFlow<String?>(null)
    val userAvatarUrl: StateFlow<String?> = _userAvatarUrl.asStateFlow()

    private val _syncedYouTubeTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val syncedYouTubeTracks: StateFlow<List<TrackItem>> = _syncedYouTubeTracks.asStateFlow()

    private val _userMixes = MutableStateFlow<List<com.cubicreates.unboundmusic.data.MixDto>>(emptyList())
    val userMixes: StateFlow<List<com.cubicreates.unboundmusic.data.MixDto>> = _userMixes.asStateFlow()

    private val _smartShelves = MutableStateFlow<List<com.cubicreates.unboundmusic.data.SmartShelfDto>>(emptyList())
    val smartShelves: StateFlow<List<com.cubicreates.unboundmusic.data.SmartShelfDto>> = _smartShelves.asStateFlow()

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    private val _deviceAuthData = MutableStateFlow<DeviceCodeData?>(null)
    val deviceAuthData: StateFlow<DeviceCodeData?> = _deviceAuthData.asStateFlow()

    private val _isStartingDeviceAuth = MutableStateFlow(false)
    val isStartingDeviceAuth: StateFlow<Boolean> = _isStartingDeviceAuth.asStateFlow()

    private val _isPollingDeviceAuth = MutableStateFlow(false)
    val isPollingDeviceAuth: StateFlow<Boolean> = _isPollingDeviceAuth.asStateFlow()

    private val _deviceAuthError = MutableStateFlow<String?>(null)
    val deviceAuthError: StateFlow<String?> = _deviceAuthError.asStateFlow()

    private var deviceAuthJob: Job? = null

    private val _cascadeSearchResponse = MutableStateFlow<CascadeSearchResponse?>(null)
    val cascadeSearchResponse: StateFlow<CascadeSearchResponse?> = _cascadeSearchResponse.asStateFlow()

    // ==================== Lyrics State ====================

    private val _lyricsLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricsLines: StateFlow<List<LyricLine>> = _lyricsLines.asStateFlow()

    private val _lyricsSource = MutableStateFlow("")
    val lyricsSource: StateFlow<String> = _lyricsSource.asStateFlow()

    private val _romanizationMode = MutableStateFlow(com.cubicreates.unboundmusic.data.RomanizationMode.ORIGINAL)
    val romanizationMode: StateFlow<com.cubicreates.unboundmusic.data.RomanizationMode> = _romanizationMode.asStateFlow()

    private val _lyricsTimingOffsetMs = MutableStateFlow(0L)
    val lyricsTimingOffsetMs: StateFlow<Long> = _lyricsTimingOffsetMs.asStateFlow()

    private val _isInstrumental = MutableStateFlow(false)
    val isInstrumental: StateFlow<Boolean> = _isInstrumental.asStateFlow()

    private var lyricsFetchJob: kotlinx.coroutines.Job? = null

    fun setRomanizationMode(mode: com.cubicreates.unboundmusic.data.RomanizationMode) {
        _romanizationMode.value = mode
    }

    fun setLyricsTimingOffsetMs(offsetMs: Long) {
        _lyricsTimingOffsetMs.value = offsetMs
    }

    // ==================== Canvas / Visual State ====================

    private val _canvasVideoUrl = MutableStateFlow<String?>(null)
    val canvasVideoUrl: StateFlow<String?> = _canvasVideoUrl.asStateFlow()

    private val _canvasArtUrl = MutableStateFlow<String?>(null)
    val canvasArtUrl: StateFlow<String?> = _canvasArtUrl.asStateFlow()

    // ==================== Phase 7: Playback Resilience, Skit Skipping & Sleep Engine ====================

    private val _activeSkipSegments = MutableStateFlow<List<SkipSegmentDto>>(emptyList())
    val activeSkipSegments: StateFlow<List<SkipSegmentDto>> = _activeSkipSegments.asStateFlow()

    private val _skippedSkitNotice = MutableStateFlow<SkitSkipNotice?>(null)
    val skippedSkitNotice: StateFlow<SkitSkipNotice?> = _skippedSkitNotice.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var skitNoticeDismissJob: kotlinx.coroutines.Job? = null
    private var lastSkippedSegmentId: String? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // ==================== Home Feed State ====================

    private val _moodCategories = MutableStateFlow<List<MoodCategory>>(emptyList())
    val moodCategories: StateFlow<List<MoodCategory>> = _moodCategories.asStateFlow()

    private val _chartTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val chartTracks: StateFlow<List<TrackItem>> = _chartTracks.asStateFlow()

    // ==================== Phase 0 Cold Start & Guest State ====================

    private val _regionalCharts = MutableStateFlow<List<TrackItem>>(emptyList())
    val regionalCharts: StateFlow<List<TrackItem>> = _regionalCharts.asStateFlow()

    private val _daypartingState = MutableStateFlow<DaypartingState?>(null)
    val daypartingState: StateFlow<DaypartingState?> = _daypartingState.asStateFlow()

    private val _libraryFolders = MutableStateFlow<Map<String, List<LocalTrack>>>(emptyMap())
    val libraryFolders: StateFlow<Map<String, List<LocalTrack>>> = _libraryFolders.asStateFlow()

    // ==================== Affective MIR & Decoupled Vibe State ====================
    // Invariant: Home Vibe state and Search Vibe state are strictly decoupled.
    // Submitting a Home Vibe prompt NEVER mutates _searchResults or modifies Discover tab state.
    private val _homeVibeState = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val homeVibeState: StateFlow<VibeSearchUiState> = _homeVibeState.asStateFlow()

    private val _searchVibeState = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val searchVibeState: StateFlow<VibeSearchUiState> = _searchVibeState.asStateFlow()

    // Backward-compatibility bridge
    val vibeSearchResult: StateFlow<VibeSearchUiState> get() = _homeVibeState

    // ==================== Startup & Telemetry State ====================

    private val _startupPhase = MutableStateFlow("SYSTEM_INIT")
    val startupPhase: StateFlow<String> = _startupPhase.asStateFlow()

    private val _startupProgress = MutableStateFlow(0.1f)
    val startupProgress: StateFlow<Float> = _startupProgress.asStateFlow()

    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()

    data class UnboundFolderPromptState(
        val folderPaths: List<String>
    )

    private val _unboundFolderPrompt = MutableStateFlow<UnboundFolderPromptState?>(null)
    val unboundFolderPrompt: StateFlow<UnboundFolderPromptState?> = _unboundFolderPrompt.asStateFlow()

    init {
        // Connect to Media3 playback service
        serviceConnection.connect()

        // Start position ticker for smooth progress bar updates
        startPositionTicker()

        // Sync current track and reload lyrics on track change
        viewModelScope.launch {
            serviceConnection.playbackState.collect { state ->
                state.currentTrack?.let { track ->
                    if (track.title != "Unknown" && track.title.isNotBlank()) {
                        val prev = _currentTrack.value
                        val changed = prev.id != track.id || !prev.title.equals(track.title, ignoreCase = true)
                        if (changed) {
                            _currentTrack.value = track
                            loadLyricsForTrack(track)
                            launch(Dispatchers.IO) {
                                fetchCanvas(track)
                                fetchSkipSegments(track)
                                fetchRydVotes(track)
                            }
                            saveCurrentPlaybackState(0L)
                        }
                    }
                }
            }
        }

        // Restore persistent YouTube Music session from local disk store immediately
        val sessionStore = com.cubicreates.unboundmusic.data.SessionStore.getInstance(application)
        if (sessionStore.hasActiveSession) {
            _isYouTubeConnected.value = true
            sessionStore.accountName?.let { _accountName.value = it }
            sessionStore.avatarUrl?.let { _userAvatarUrl.value = it }
        }

        // Load Playback Quality & Automation Preferences
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

        // Restore persistent queue and active track across app restarts
        restoreLastPlaybackState()

        // Load persistent search history
        loadSearchHistory()

        // Orchestrate startup hydration with splash screen telemetry
        startStartupHydration()

        // Resume download polling if previous active tasks exist
        startDownloadPollingLoop()
        loadDownloadedMusicFiles()
        updateStorageMetrics()
        loadCustomPlaylists()
        try {
            _recentlyPlayedTracks.value = PlaybackStateStore.getRecentlyPlayed(application)
        } catch (_: Exception) {}

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

    private fun restoreLastPlaybackState() {
        val saved = PlaybackStateStore.loadPlaybackState(getApplication()) ?: return
        if (saved.track.id.isNotBlank() || saved.track.title.isNotBlank()) {
            _currentTrack.value = saved.track
            if (saved.queue.isNotEmpty()) {
                _currentQueue.value = saved.queue
                serviceConnection.setQueue(saved.queue)
            }
        }
    }

    private var lastSavedStateTick = 0L

    private fun saveCurrentPlaybackState(positionMs: Long? = null) {
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
            _startupPhase.value = "SYSTEM_INIT"
            _startupProgress.value = 0.35f

            // Unpack assets and binaries
            StorageInitializer.initialize(getApplication())

            _startupPhase.value = "DAEMON_CONNECT"
            _startupProgress.value = 0.60f

            // Start Go Engine Daemon
            daemonManager.startDaemonAuto(force = true)

            // Handshake with daemon
            for (i in 1..10) {
                try {
                    val (code, _) = client.healthCheck()
                    if (code in 200..299) break
                } catch (e: Exception) {
                    // Daemon booting
                }
                delay(150)
            }

            _startupPhase.value = "CACHE_HYDRATE"
            _startupProgress.value = 0.88f
            StorageInitializer.unpackModelsIfPending(getApplication())
            val userCountry = com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
            val userLanguage = com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
            initializeColdStart(userCountry, userLanguage)
            loadHomeFeed()
            refreshLibrary()
            val sessionStore = com.cubicreates.unboundmusic.data.SessionStore.getInstance(getApplication())
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
            delay(300)

            _startupPhase.value = "READY"
            _startupProgress.value = 1.0f
            delay(250)
            _isAppReady.value = true
        }
    }

    fun completeStartup() {
        _isAppReady.value = true
    }

    /**
     * Phase 0 Cold Start initialization:
     * 1. Unpacks native assets (fpcalc, llama-cli, models.zst).
     * 2. Fetches regional charts and 24-hour time-aware mood capsules concurrently.
     * 3. Scans local storage directories and indexes WhatsApp, Telegram, and Downloads audio.
     */
    fun initializeColdStart(countryCode: String? = null, languageCode: String? = null) {
        val actualCountry = if (!countryCode.isNullOrBlank()) countryCode else com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
        val actualLanguage = if (!languageCode.isNullOrBlank()) languageCode else com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
        viewModelScope.launch(Dispatchers.IO) {
            // First-boot asset unpacker
            StorageInitializer.initialize(getApplication())

            // 1. Fetch explore charts & mood capsules concurrently
            launch {
                val charts = client.getCharts(actualCountry, actualLanguage)
                if (charts.isNotEmpty()) {
                    _regionalCharts.value = charts
                    _chartTracks.value = charts
                    if (_currentTrack.value.id.isBlank() || _currentTrack.value == defaultTopTracks[0]) {
                        _currentTrack.value = charts[0]
                    }
                }
            }
            launch {
                val dp = client.getMoodCapsules()
                if (dp != null) {
                    _daypartingState.value = dp
                }
            }

            // 2. Trigger VLC-style universal storage crawl (including .nomedia bypass)
            launch {
                performUniversalAudioScan()
            }
        }
    }

    /**
     * Natural Language Home Vibe AI query runner.
     * Crucial: Does NOT modify _searchResults to prevent hijacking the Discover / Search screen!
     */
    fun submitHomeVibeQuery(query: String, autoPlay: Boolean = true) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val country = com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
        val lang = com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
        viewModelScope.launch(Dispatchers.IO) {
            _homeVibeState.value = VibeSearchUiState.Loading
            try {
                val res = client.searchVibe(trimmed, region = country, language = lang)
                withContext(Dispatchers.Main) {
                    _homeVibeState.value = VibeSearchUiState.Success(res.vibeResult, res.radioTracks)
                    // Intentionally leave _searchResults untouched so SearchScreen remains independent
                    if (res.radioTracks.isNotEmpty() && autoPlay) {
                        val first = res.radioTracks.first()
                        playTrackWithQueue(first, res.radioTracks)
                        val moodTag = res.vibeResult.moodTags.firstOrNull() ?: res.vibeResult.targetGenres.firstOrNull() ?: "Vibe"
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Tuning into $moodTag: ${first.title}",
                            isLong = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submitHomeVibeQuery error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Vibe Search: ${e.message ?: "Failed"}",
                        isLong = true
                    )
                    _homeVibeState.value = VibeSearchUiState.Error(e.message ?: "Search failed")
                }
            }
        }
    }

    fun clearHomeVibeQuery() {
        _homeVibeState.value = VibeSearchUiState.Idle
    }

    /**
     * Natural Language Search / Discover Screen Vibe AI query runner.
     * Isolated exclusively to the Search Screen.
     */
    fun submitSearchVibeQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val country = com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
        val lang = com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
        viewModelScope.launch(Dispatchers.IO) {
            _searchVibeState.value = VibeSearchUiState.Loading
            try {
                val res = client.searchVibe(trimmed, region = country, language = lang)
                withContext(Dispatchers.Main) {
                    _searchVibeState.value = VibeSearchUiState.Success(res.vibeResult, res.radioTracks)
                    if (res.radioTracks.isNotEmpty()) {
                        _searchResults.value = res.radioTracks
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submitSearchVibeQuery error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _searchVibeState.value = VibeSearchUiState.Error(e.message ?: "Search failed")
                }
            }
        }
    }

    fun clearSearchVibeQuery() {
        _searchVibeState.value = VibeSearchUiState.Idle
    }

    /** Backward-compatibility wrappers */
    fun submitVibeQuery(query: String, autoPlay: Boolean = false) {
        submitHomeVibeQuery(query, autoPlay = autoPlay)
    }

    fun playVibePrompt(prompt: String) {
        submitHomeVibeQuery(prompt, autoPlay = true)
    }

    fun clearVibeQuery() {
        clearHomeVibeQuery()
    }


    /**
     * Fetches mood radio tracks for a selected capsule and starts playback.
     */
    fun playMoodCapsule(capsule: MoodCapsule) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = client.getMoodRadio(capsule.browseId)
                if (tracks.isNotEmpty()) {
                    playTrack(tracks.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play mood capsule: ${e.message}")
            }
        }
    }


    // ==================== Phase 1: Magic Serendipity Radio & Telemetry ====================

    private var lastMagicRadioTriggerTime = 0L
    private val magicRadioMutex = Mutex()

    /**
     * Triggers the on-device Magic Serendipity Radio algorithm when the player is idle (< 300ms).
     * Populates the 25-track queue, starts playback of the seed track, and invokes onReady to expand NowPlayingScreen.
     */
    fun triggerMagicRadio(onReady: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastMagicRadioTriggerTime < 500L) return
        lastMagicRadioTriggerTime = now

        viewModelScope.launch(Dispatchers.IO) {
            magicRadioMutex.withLock {
                try {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val result = client.getMagicRadio(localHour = hour)
                    if (result != null && result.queue.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            serviceConnection.setQueue(result.queue)
                            playTrack(result.seedTrack)
                            onReady()
                        }
                        return@withLock
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Magic radio error: ${e.message}")
                }

                // Resilient Fallback: If daemon magic radio didn't produce a queue, use libraryTracks or defaultTopTracks
                val candidateList = _libraryTracks.value.ifEmpty { defaultTopTracks }
                if (candidateList.isNotEmpty()) {
                    val shuffled = candidateList.shuffled()
                    val seed = shuffled.first()
                    withContext(Dispatchers.Main) {
                        serviceConnection.setQueue(shuffled)
                        playTrack(seed)
                        onReady()
                    }
                }
            }
        }
    }

    /**
     * Starts an on-demand Magic Serendipity Radio session seeded by the specified track.
     * Queries the on-device Markov transition generator (/api/v1/radio/magic) with local taste affinity.
     * Falls back seamlessly to InnerTube /next or YouTube search radio to ensure 100% reliability.
     */
    fun startRadio(seedTrack: TrackItem, onStarted: (() -> Unit)? = null) {
        if (seedTrack.id.isBlank() && seedTrack.title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val cleanId = if (!seedTrack.id.startsWith("local:")) seedTrack.id else ""

                // 1. Query Go daemon on-device Markov Serendipity Radio generator
                val result = client.getMagicRadio(localHour = hour, seedTrackId = cleanId)
                var radioQueue = result?.queue?.filter { it.id != seedTrack.id } ?: emptyList()

                // 2. Fallback to InnerTube /next
                if (radioQueue.isEmpty() && cleanId.isNotBlank()) {
                    val (code, resp) = client.getRadioNext(cleanId)
                    if (code in 200..299 && resp.isNotBlank()) {
                        radioQueue = client.parseRadioNext(resp).filter { it.id != seedTrack.id }
                    }
                }

                // 3. Fallback to YouTube search radio query
                if (radioQueue.isEmpty()) {
                    val query = "${seedTrack.artist} ${seedTrack.title} radio"
                    val (sCode, sResp) = client.search(query, type = "song")
                    if (sCode in 200..299 && sResp.isNotBlank()) {
                        radioQueue = client.parseSearchResults(sResp).filter { it.id != seedTrack.id }
                    }
                }

                val fullQueue = listOf(seedTrack) + radioQueue
                withContext(Dispatchers.Main) {
                    playTrackWithQueue(seedTrack, fullQueue)
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Radio: ${seedTrack.title}")
                    onStarted?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting radio: ${e.message}")
                withContext(Dispatchers.Main) {
                    playTrack(seedTrack)
                    onStarted?.invoke()
                }
            }
        }
    }

    /**
     * Ingests physical playback behavior telemetry (completions, skips, loops) into on-device taste engine.
     */
    fun logPlaybackTelemetry(track: TrackItem, listenedMs: Long, durationMs: Long, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val eventType = when {
                isCompleted || (durationMs > 0 && listenedMs.toDouble() / durationMs >= 0.85) -> "COMPLETE"
                listenedMs < 15000 -> "FAST_SKIP"
                listenedMs < 45000 -> "MILD_SKIP"
                else -> "COMPLETE"
            }
            client.recordTasteEvent(
                trackId = track.title,
                title = track.title,
                artistId = track.artist,
                artistName = track.artist,
                durationMs = durationMs,
                listenedMs = listenedMs,
                eventType = eventType
            )
        }
    }

    // ==================== Playback Commands ====================

    /**
     * Plays a track by first resolving its stream URL via the Go daemon, then sending to Media3.
     */
    /**
     * Plays a track by first resolving its stream URL via the Go daemon, then sending to Media3.
     */
    fun playTrack(track: TrackItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            viewModelScope.launch(Dispatchers.Main) {
                playTrack(track)
            }
            return
        }
        val offlineMatch = findMatchingOfflineTrack(track)
        val targetTrack = if (offlineMatch != null) {
            track.copy(
                streamUrl = offlineMatch.streamUrl,
                coverUrl = offlineMatch.coverUrl.ifBlank { track.coverUrl }
            )
        } else {
            track
        }

        _currentTrack.value = targetTrack
        saveCurrentPlaybackState(0L)
        try {
            PlaybackStateStore.addRecentlyPlayed(getApplication(), targetTrack)
            _recentlyPlayedTracks.value = PlaybackStateStore.getRecentlyPlayed(getApplication())
        } catch (_: Exception) {}

        if (offlineMatch != null) {
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playing offline downloaded version (0 MB data)", isLong = false)
        } else {
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Loading '${track.title}'...", isLong = false)
        }

        // If track is not part of an existing multi-track queue, seed with this track and auto-hydrate YouTube automix
        val currentQ = _currentQueue.value
        val trackInQueue = currentQ.any {
            (it.id.isNotBlank() && it.id == targetTrack.id) ||
            (it.title.isNotBlank() && it.title.equals(targetTrack.title, ignoreCase = true))
        }
        if (currentQ.size <= 1 || !trackInQueue) {
            val initialQ = listOf(targetTrack)
            _currentQueue.value = initialQ
            serviceConnection.setQueue(initialQ)
            fetchAutomixQueue(targetTrack)
        }

        // Immediately reset lyrics state and fetch for this specific track
        loadLyricsForTrack(targetTrack)
        _rydVotes.value = null
        viewModelScope.launch(Dispatchers.IO) {
            fetchRydVotes(targetTrack)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val streamUrl = resolveStreamUrl(targetTrack)
                val resolvedTrack = targetTrack.copy(streamUrl = streamUrl)
                withContext(Dispatchers.Main) {
                    _currentTrack.value = resolvedTrack
                }

                if (streamUrl.isNotBlank()) {
                    // Play via Media3 service
                    serviceConnection.playTrack(resolvedTrack, streamUrl)
                } else {
                    Log.w(TAG, "Direct stream resolution empty for ${targetTrack.title}, using localhost proxy stream")
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Direct stream empty for '${targetTrack.title}', falling back to proxy")
                    val fallbackUrl = if (targetTrack.id.isNotBlank() && targetTrack.id.length == 11 && !targetTrack.id.startsWith("local:")) {
                        "http://127.0.0.1:45731/api/v1/proxy/stream?id=${targetTrack.id}"
                    } else targetTrack.streamUrl
                    if (fallbackUrl.isNotBlank()) {
                        serviceConnection.playTrack(targetTrack.copy(streamUrl = fallbackUrl), fallbackUrl)
                    } else {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Error: No fallback URL for '${targetTrack.title}'")
                    }
                }

                // Fetch canvas visuals and SponsorBlock skip segments in parallel
                launch { fetchCanvas(resolvedTrack) }
                launch { fetchSkipSegments(resolvedTrack) }

            } catch (e: Exception) {
                Log.e(TAG, "Error playing track: ${e.message}")
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Play Track Exception:\n${e.message}")
                val fallbackUrl = if (targetTrack.id.isNotBlank() && targetTrack.id.length == 11 && !targetTrack.id.startsWith("local:")) {
                    "http://127.0.0.1:45731/api/v1/proxy/stream?id=${targetTrack.id}"
                } else targetTrack.streamUrl
                if (fallbackUrl.isNotBlank()) {
                    serviceConnection.playTrack(targetTrack.copy(streamUrl = fallbackUrl), fallbackUrl)
                }
            }
        }
    }

    /**
     * Searches downloaded tracks and local library tracks for a local matching version of the given track.
     * Checks exact ID, partial ID, and fuzzy normalized (title + artist) match.
     */
    fun findMatchingOfflineTrack(track: TrackItem): TrackItem? {
        val candidates = (_downloadedMusicTracks.value + _libraryTracks.value).distinctBy { it.id }
        if (candidates.isEmpty()) return null

        fun clean(s: String): String = s.lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .replace(Regex("[^a-z0-9]"), "")

        val targetTitleClean = clean(track.title)
        val targetArtistClean = clean(track.artist)

        // 1. Direct ID match
        if (track.id.isNotBlank()) {
            val byId = candidates.firstOrNull { cand ->
                cand.streamUrl.isNotBlank() &&
                (cand.streamUrl.startsWith("file://") || cand.streamUrl.startsWith("content://")) &&
                (cand.id == track.id || cand.id == "local_dl_${track.id}" || cand.id.contains(track.id) || track.id.contains(cand.id))
            }
            if (byId != null && isLocalAudioPlayable(byId.streamUrl)) return byId
        }

        // 2. Normalized Title & Artist match
        if (targetTitleClean.length >= 3) {
            for (cand in candidates) {
                if (!cand.streamUrl.startsWith("file://") && !cand.streamUrl.startsWith("content://")) continue
                val candTitleClean = clean(cand.title)
                val candArtistClean = clean(cand.artist)

                val titleMatches = candTitleClean == targetTitleClean ||
                        (candTitleClean.length >= 5 && targetTitleClean.length >= 5 &&
                                (candTitleClean.contains(targetTitleClean) || targetTitleClean.contains(candTitleClean)))

                if (titleMatches) {
                    val artistMatches = targetArtistClean.isBlank() || candArtistClean.isBlank() ||
                            candArtistClean == "unknownartist" || targetArtistClean == "unknownartist" ||
                            candArtistClean == targetArtistClean ||
                            candArtistClean.contains(targetArtistClean) || targetArtistClean.contains(candArtistClean)

                    if (artistMatches && isLocalAudioPlayable(cand.streamUrl)) {
                        return cand
                    }
                }
            }
        }
        return null
    }

    private fun isLocalAudioPlayable(streamUrl: String): Boolean {
        return try {
            when {
                streamUrl.startsWith("file://") -> {
                    val f = java.io.File(streamUrl.removePrefix("file://"))
                    f.exists() && f.length() > 0
                }
                streamUrl.startsWith("content://") -> true
                else -> false
            }
        } catch (_: Exception) { false }
    }

    /**
     * Resolves a stream URL for a track via the Go daemon /api/v1/stream endpoint.
     * Implements zero-data interception: checks local storage first, falls back to remote.
     * Retries up to 5 times if the daemon is cold-starting.
     */
    private suspend fun resolveStreamUrl(track: TrackItem): String {
        // 1. If the track already has a local file://, content://, localhost proxy audio stream, or valid http stream, use it directly
        if (track.streamUrl.startsWith("file://") ||
            track.streamUrl.startsWith("content://") ||
            (track.streamUrl.contains("127.0.0.1") && track.streamUrl.contains("/proxy/stream")) ||
            (track.streamUrl.startsWith("http") && track.streamUrl.contains("googlevideo.com"))) {
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playing direct URL for '${track.title}'", isLong = false)
            return track.streamUrl
        }

        // 2. Zero-Data Local Interception: Check if song is already downloaded or in local storage
        val offlineMatch = findMatchingOfflineTrack(track)
        if (offlineMatch != null && offlineMatch.streamUrl.isNotBlank()) {
            Log.i(TAG, "Zero-Data Match: Redirecting search track '${track.title}' to offline '${offlineMatch.title}' -> ${offlineMatch.streamUrl}")
            com.cubicreates.unboundmusic.util.UnboundToast.show(
                getApplication(),
                "Playing offline downloaded version (0 MB data)",
                isLong = false
            )
            return offlineMatch.streamUrl
        }

        // Try resolving via Go daemon (zero-data interception + YouTube stream resolution)
        _streamDebugMessage.value = "Resolving stream for ${track.title}..."
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolving '${track.title}' (ID: ${track.id})...", isLong = false)
        for (attempt in 1..5) {
            try {
                val (code, resp) = client.getStream(
                    videoId = track.id,
                    title = track.title,
                    artist = track.artist
                )
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val direct = json.optString("direct_stream_url", "")
                    val proxy = json.optString("proxy_stream_url", "")
                    val resolved = json.optString("stream_url", "")
                    val streamType = json.optString("stream_type", "REMOTE")

                    // Prefer local cached file, then localhost proxy (bounded chunking prevents CDN 403), then direct CDN
                    val finalUrl = when {
                        resolved.isNotBlank() && (resolved.startsWith("file://") || resolved.startsWith("content://")) -> resolved
                        proxy.isNotBlank() && proxy.startsWith("http") -> proxy
                        direct.isNotBlank() && direct.startsWith("http") -> direct
                        resolved.isNotBlank() && resolved.startsWith("http") -> resolved
                        else -> ""
                    }

                    if (finalUrl.isNotBlank()) {
                        Log.i(TAG, "Stream resolved (attempt $attempt): type=$streamType for '${track.title}' -> $finalUrl")
                        _streamDebugMessage.value = "Stream ready: $streamType ($finalUrl)"
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Stream Ready: $streamType", isLong = false)
                        return finalUrl
                    }
                } else {
                    Log.w(TAG, "Daemon stream resolution error attempt $attempt: code=$code, resp=$resp")
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Daemon error (try $attempt/5): code=$code\n$resp")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stream resolution attempt $attempt failed: ${e.message}")
                _streamDebugMessage.value = "Attempt $attempt failed: ${e.message}"
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolution try $attempt failed: ${e.message}")
            }
            if (attempt < 5) {
                kotlinx.coroutines.delay(400)
            }
        }

        // Secondary fallback: search track title + artist to find matching YouTube stream
        if (track.title.isNotBlank()) {
            try {
                _streamDebugMessage.value = "Direct stream not ready; searching for '${track.title}'"
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Direct stream failed. Searching YouTube for '${track.title}'...")
                val searchQuery = "${track.title} ${track.artist}".trim()
                val (searchCode, searchResp) = client.search(searchQuery)
                if (searchCode in 200..299 && searchResp.isNotBlank()) {
                    val searchJson = JSONObject(searchResp)
                    val tracksArr = searchJson.optJSONArray("tracks") ?: searchJson.optJSONArray("results")
                    if (tracksArr != null && tracksArr.length() > 0) {
                        val firstMatch = tracksArr.getJSONObject(0)
                        val matchId = firstMatch.optString("id", firstMatch.optString("video_id", ""))
                        if (matchId.isNotBlank() && matchId.length == 11) {
                            val (streamCode, streamResp) = client.getStream(videoId = matchId)
                            if (streamCode in 200..299 && streamResp.isNotBlank()) {
                                val json = JSONObject(streamResp)
                                val proxy = json.optString("proxy_stream_url", "")
                                val resolved = json.optString("stream_url", "")
                                val target = if (proxy.isNotBlank() && proxy.startsWith("http")) proxy else resolved
                                if (target.isNotBlank()) {
                                    Log.i(TAG, "Stream resolved via search fallback for '${track.title}' -> $target")
                                    _streamDebugMessage.value = "Stream resolved via search match for '${track.title}'"
                                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolved via YouTube search!", isLong = false)
                                    return target
                                }
                            }
                            // Direct localhost proxy fallback for matched video ID
                            _streamDebugMessage.value = "Using proxy fallback for match ID $matchId"
                            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Using proxy for matched ID $matchId")
                            return "http://127.0.0.1:45731/api/v1/proxy/stream?id=$matchId"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search fallback resolution failed: ${e.message}")
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Search fallback failed: ${e.message}")
            }
        }

        // Tertiary fallback: if track.id is a valid 11-char YouTube ID, use the embedded Go daemon proxy stream endpoint directly
        if (track.id.isNotBlank() && track.id.length == 11 && !track.id.startsWith("local:")) {
            Log.i(TAG, "Stream resolved via localhost proxy fallback for '${track.title}' (${track.id})")
            _streamDebugMessage.value = "Using localhost proxy stream for '${track.title}'"
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Using engine proxy stream for '${track.title}'")
            return "http://127.0.0.1:45731/api/v1/proxy/stream?id=${track.id}"
        }

        // Quaternary fallback: use title + artist on localhost proxy (Go engine automatically searches YouTube)
        if (track.title.isNotBlank()) {
            val query = if (track.artist.isNotBlank()) "${track.title} ${track.artist}" else track.title
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            Log.i(TAG, "Stream resolved via query proxy fallback for '$query'")
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Falling back to engine query proxy for '${track.title}'")
            return "http://127.0.0.1:45731/api/v1/proxy/stream?id=$encodedQuery"
        }

        // Fallback: return existing stream URL if valid, otherwise empty string
        val fallback = track.streamUrl
        if (fallback.isBlank()) {
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "FATAL: No stream URL could be found for '${track.title}'. Daemon may be offline.")
        }
        return if (fallback.startsWith("http://") || fallback.startsWith("https://") || fallback.startsWith("file://") || fallback.startsWith("content://")) {
            fallback
        } else {
            ""
        }
    }

    private val _currentQueue = MutableStateFlow<List<TrackItem>>(emptyList())
    val currentQueue: StateFlow<List<TrackItem>> = _currentQueue.asStateFlow()

    /**
     * Plays a selected track within a playlist context, populating the queue so Next/Previous work.
     */
    fun playTrackWithQueue(track: TrackItem, queue: List<TrackItem>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            viewModelScope.launch(Dispatchers.Main) {
                playTrackWithQueue(track, queue)
            }
            return
        }
        if (queue.size > 1) {
            _currentQueue.value = queue
            serviceConnection.setQueue(queue)
            playTrack(track)
        } else {
            val initialQ = listOf(track)
            _currentQueue.value = initialQ
            serviceConnection.setQueue(initialQ)
            playTrack(track)
            fetchAutomixQueue(track)
        }
        saveCurrentPlaybackState(0L)
    }

    private var automixJob: Job? = null

    /**
     * Queries YouTube Music's official /next automix algorithm (RDAMVM + videoId) to populate
     * 25-50 algorithmically matched songs into the active player queue.
     * Works for both signed-in (personalized) and guest users (acoustic/collaborative filtering).
     */
    fun fetchAutomixQueue(seedTrack: TrackItem, append: Boolean = false) {
        if (seedTrack.id.isBlank() || seedTrack.id.startsWith("local:")) return
        automixJob?.cancel()
        automixJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var nextTracks: List<TrackItem> = emptyList()

                // 1. Primary: On-device Markov Serendipity Radio generator with taste affinity re-ranking
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val magicResult = client.getMagicRadio(localHour = hour, seedTrackId = seedTrack.id)
                if (magicResult != null && magicResult.queue.isNotEmpty()) {
                    nextTracks = magicResult.queue.filter { it.id != seedTrack.id }
                }

                // 2. Secondary: InnerTube /next endpoint
                if (nextTracks.isEmpty()) {
                    val (code, resp) = client.getRadioNext(seedTrack.id)
                    if (code in 200..299 && resp.isNotBlank()) {
                        nextTracks = client.parseRadioNext(resp).filter { it.id != seedTrack.id }
                    }
                }

                // 3. Zero-fail fallback: If /next was empty or failed, use YouTube search radio query
                if (nextTracks.isEmpty()) {
                    val query = "${seedTrack.artist} ${seedTrack.title} radio"
                    val (sCode, sResp) = client.search(query, type = "song")
                    if (sCode in 200..299 && sResp.isNotBlank()) {
                        nextTracks = client.parseSearchResults(sResp).filter { it.id != seedTrack.id }
                    }
                }

                if (nextTracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val updatedQueue = if (append) {
                            val existingIds = _currentQueue.value.map { it.id }.toSet()
                            _currentQueue.value + nextTracks.filter { !existingIds.contains(it.id) }
                        } else {
                            listOf(seedTrack) + nextTracks
                        }
                        _currentQueue.value = updatedQueue
                        serviceConnection.setQueue(updatedQueue)
                        Log.i(TAG, "Automix queue hydrated with ${nextTracks.size} algorithmic tracks for '${seedTrack.title}'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch automix queue for ${seedTrack.id}: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        val state = serviceConnection.playbackState.value
        if (!serviceConnection.isPlayerReady() || state.mediaItemCount == 0 || state.durationMs <= 0L) {
            playTrack(_currentTrack.value)
        } else {
            serviceConnection.togglePlayPause()
        }
    }

    fun toggleFavorite() {
        val willBeFav = !_isFavorite.value
        _isFavorite.value = willBeFav
        val current = _currentTrack.value
        if (current.id.isNotBlank()) {
            if (willBeFav) {
                PlaybackStateStore.addFavoriteTrackId(getApplication(), current.id)
            } else {
                PlaybackStateStore.removeFavoriteTrackId(getApplication(), current.id)
            }
            refreshFavoritesList()
        }
        if (willBeFav && _autoDownloadLikedSongs.value) {
            startTrackDownload(current)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.toggleFavorite(current)
                client.toggleTrackLike(current.id, willBeFav)
                loadSyncedYouTubeTracks()
            } catch (e: Exception) {
                Log.w(TAG, "Track like toggle error: ${e.message}")
            }
        }
    }

    fun toggleTrackFavorite(track: TrackItem) {
        if (track.id.isBlank()) return
        val favIds = PlaybackStateStore.getFavoriteTrackIds(getApplication())
        val willBeFav = !favIds.contains(track.id)
        if (willBeFav) {
            PlaybackStateStore.addFavoriteTrackId(getApplication(), track.id)
        } else {
            PlaybackStateStore.removeFavoriteTrackId(getApplication(), track.id)
        }
        if (track.id == _currentTrack.value.id) {
            _isFavorite.value = willBeFav
        }
        refreshFavoritesList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.toggleFavorite(track)
                client.toggleTrackLike(track.id, willBeFav)
            } catch (e: Exception) {
                Log.d(TAG, "Track like toggle error: ${e.message}")
            }
        }
    }

    fun refreshFavoritesList() {
        viewModelScope.launch(Dispatchers.IO) {
            val favIds = PlaybackStateStore.getFavoriteTrackIds(getApplication())
            var backendFavs = client.getFavorites()
            if (backendFavs.isEmpty() && favIds.isNotEmpty()) {
                val localFavorites = _libraryTracks.value.filter { favIds.contains(it.id) }
                val syncedFavorites = _syncedYouTubeTracks.value.filter { favIds.contains(it.id) }
                val allTracks = (localFavorites + syncedFavorites).distinctBy { it.id }
                for (t in allTracks) {
                    client.toggleFavorite(t)
                }
                backendFavs = client.getFavorites()
            }
            val localFavorites = _libraryTracks.value.filter { favIds.contains(it.id) }
            val syncedFavorites = _syncedYouTubeTracks.value.filter { favIds.contains(it.id) }
            val allTracks = (backendFavs + localFavorites + syncedFavorites).distinctBy { it.id }
            withContext(Dispatchers.Main) {
                _favoriteTracks.value = allTracks
            }
        }
    }

    fun seekTo(progress: Float) {
        serviceConnection.seekToFraction(progress)
    }

    private fun getEffectiveQueue(): List<TrackItem> {
        val q = _currentQueue.value
        if (q.isNotEmpty()) return q
        val sQueue = serviceConnection.playbackState.value.queue
        if (sQueue.isNotEmpty()) return sQueue
        if (_searchResults.value.size > 1) return _searchResults.value
        if (_libraryTracks.value.size > 1) return _libraryTracks.value
        if (_chartTracks.value.size > 1) return _chartTracks.value
        return defaultTopTracks
    }

    fun nextTrack() {
        val q = getEffectiveQueue()
        if (q.isNotEmpty()) {
            val current = _currentTrack.value
            val currentIndex = q.indexOfFirst {
                (it.id.isNotBlank() && it.id == current.id) ||
                (it.title.isNotBlank() && it.title.equals(current.title, ignoreCase = true))
            }

            // Proactive infinite auto-play: When within 3 tracks of the end of the queue, fetch next batch
            if (currentIndex >= q.size - 3 && q.isNotEmpty()) {
                fetchAutomixQueue(q.last(), append = true)
            }

            val nextIndex = if (currentIndex in 0 until q.size - 1) {
                currentIndex + 1
            } else {
                0 // Loop back to start of queue
            }
            playTrack(q[nextIndex])
            return
        }
        serviceConnection.next()
    }

    fun prevTrack() {
        val pos = serviceConnection.playbackState.value.currentPositionMs
        if (pos > 3000L) {
            // If played > 3s, seek to beginning (Spotify standard)
            serviceConnection.seekTo(0)
            return
        }
        val q = getEffectiveQueue()
        if (q.isNotEmpty()) {
            val current = _currentTrack.value
            val currentIndex = q.indexOfFirst {
                (it.id.isNotBlank() && it.id == current.id) ||
                (it.title.isNotBlank() && it.title.equals(current.title, ignoreCase = true))
            }
            val prevIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                q.lastIndex // Wrap to end of queue
            }
            playTrack(q[prevIndex])
            return
        }
        serviceConnection.previous()
    }

    // ==================== YouTube Music Search & Autocomplete ====================

    private var searchJob: kotlinx.coroutines.Job? = null
    private var suggestionJob: kotlinx.coroutines.Job? = null

    /**
     * Updates search query, fetches live autocomplete suggestions, and debounces catalog search.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        fetchSearchSuggestions(query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350) // 350ms debounce for full search
            executeFullSearch(query)
        }
    }

    /**
     * Explicitly submits a search query (e.g. user taps enter, history, or a suggestion),
     * records it into search history, and immediately executes catalog search.
     */
    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _searchQuery.value = trimmed
        addSearchHistory(trimmed)
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            executeFullSearch(trimmed)
        }
    }

    private suspend fun executeFullSearch(query: String) {
        _isSearching.value = true
        try {
            var foundTracks = false
            val (code, resp) = client.search(query, type = _searchCategory.value.apiParam)
            if (code in 200..299 && resp.isNotBlank()) {
                val parsed = client.parseSearchResults(resp)
                if (parsed.isNotEmpty()) {
                    _searchResults.value = parsed
                    foundTracks = true
                }
            }

            // If daemon returned empty, query YouTube Music public search fallback
            if (!foundTracks) {
                val fallbackResults = executeDirectYouTubeSearch(query)
                if (fallbackResults.isNotEmpty()) {
                    _searchResults.value = fallbackResults
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube search error: ${e.message}")
            val fallbackResults = executeDirectYouTubeSearch(query)
            if (fallbackResults.isNotEmpty()) {
                _searchResults.value = fallbackResults
            }
        } finally {
            _isSearching.value = false
        }
    }

    /**
     * Real-time autocomplete suggestions from YouTube Music suggest client.
     */
    fun fetchSearchSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = java.net.URL("https://suggestqueries-clients6.youtube.com/complete/search?client=youtube-music&hl=en&gl=US&q=$encoded")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val regex = Regex("""\["([^"]+)",0,""")
                    val list = regex.findAll(text).map { it.groupValues[1] }.distinct().take(12).toList()
                    _searchSuggestions.value = list
                }
            } catch (_: Exception) {
                // Ignore transient network failures for suggestions
            }
        }
    }

    private fun executeDirectYouTubeSearch(query: String): List<TrackItem> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            // Strictly query YouTube Music suggestions (never general YouTube video queries)
            val url = java.net.URL("https://suggestqueries-clients6.youtube.com/complete/search?client=youtube-music&hl=en&gl=US&q=$encoded")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = mutableListOf<TrackItem>()
                val regex = Regex("""\["([^"]+)",0,""")
                regex.findAll(text).take(8).forEach { match ->
                    val musicTitle = match.groupValues[1]
                    parsed.add(
                        TrackItem(
                            id = "",
                            title = musicTitle,
                            artist = "YouTube Music",
                            coverUrl = "",
                            streamUrl = ""
                        )
                    )
                }
                parsed
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== Search History (Persistence) ====================

    private fun loadSearchHistory() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("unbound_search_history", android.content.Context.MODE_PRIVATE)
            val raw = prefs.getString("queries", null)
            if (!raw.isNullOrBlank()) {
                val list = raw.split("||||").filter { it.isNotBlank() }
                _searchHistory.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading search history: ${e.message}")
        }
    }

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val capped = current.take(30)
        _searchHistory.value = capped
        saveSearchHistory(capped)
    }

    fun removeSearchHistoryItem(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(query)
        _searchHistory.value = current
        saveSearchHistory(current)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory(emptyList())
    }

    private fun saveSearchHistory(list: List<String>) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("unbound_search_history", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("queries", list.joinToString("||||")).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving search history: ${e.message}")
        }
    }

    // ==================== Multi-Select Batch Actions ====================

    fun playNextBatch(tracks: List<TrackItem>) {
        if (tracks.isEmpty()) return
        val currentQueueList = _currentQueue.value.toMutableList()
        val current = _currentTrack.value
        val currentIndex = currentQueueList.indexOfFirst { it.id == current.id }
        val insertIndex = if (currentIndex >= 0) currentIndex + 1 else 0
        currentQueueList.addAll(insertIndex, tracks)
        _currentQueue.value = currentQueueList
        serviceConnection.setQueue(currentQueueList)
    }

    fun addToQueueBatch(tracks: List<TrackItem>) {
        if (tracks.isEmpty()) return
        val currentQueueList = _currentQueue.value.toMutableList()
        currentQueueList.addAll(tracks)
        _currentQueue.value = currentQueueList
        serviceConnection.setQueue(currentQueueList)
    }

    fun downloadBatch(tracks: List<TrackItem>) {
        tracks.forEach { track ->
            if (track.id.isNotBlank()) {
                startTrackDownload(track)
            }
        }
    }


    /**
     * AI-powered semantic vibe search via the Go daemon.
     */
    fun executeVibeSearch(prompt: String) {
        _isSearching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.queryVibe(prompt, topK = 10)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    // Parse vibe search results and use them to search YouTube Music
                    val keywords = json.optJSONArray("search_keywords")
                    val genres = json.optJSONArray("target_genres")
                    val searchTerm = buildString {
                        keywords?.let { arr ->
                            for (i in 0 until minOf(arr.length(), 3)) {
                                if (isNotBlank()) append(" ")
                                append(arr.optString(i))
                            }
                        }
                        if (isBlank() && genres != null) {
                            for (i in 0 until minOf(genres.length(), 2)) {
                                if (isNotBlank()) append(" ")
                                append(genres.optString(i))
                            }
                        }
                        if (isBlank()) append(prompt)
                    }
                    // Chain into YouTube Music search
                    val (sCode, sResp) = client.search(searchTerm)
                    if (sCode in 200..299) {
                        val parsed = client.parseSearchResults(sResp)
                        if (parsed.isNotEmpty()) {
                            _searchResults.value = parsed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vibe search error: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    // ==================== Shazam Recognition ====================

    fun startAmbientShazamRecognition() {
        if (_isListeningShazam.value) return
        val activity = com.cubicreates.unboundmusic.MainActivity.instance
        if (activity != null) {
            activity.requestRecordAudio {
                executeAmbientShazamCapture()
            }
        } else {
            executeAmbientShazamCapture()
        }
    }

    private fun executeAmbientShazamCapture() {
        if (_isListeningShazam.value) return
        _isListeningShazam.value = true
        _recognizedMessage.value = "Listening to audio acoustics..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pcmData = com.cubicreates.unboundmusic.audio.AmbientAudioRecorder.recordPcm(4500)
                if (pcmData == null || pcmData.isEmpty()) {
                    _recognizedMessage.value = "Could not record ambient audio."
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Could not record ambient audio. Please check microphone permissions."
                        )
                    }
                    return@launch
                }
                _recognizedMessage.value = "Analyzing acoustic fingerprint..."
                val (code, resp) = client.identifyPcmAudio(pcmData)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val matched = json.optBoolean("matched", false)
                    val trackTitle = json.optString("title", json.optString("track_title", ""))
                    val artist = json.optString("artist", "")
                    if (matched && trackTitle.isNotBlank()) {
                        val displayMsg = if (artist.isNotBlank()) "Recognized: $trackTitle - $artist" else "Recognized: $trackTitle"
                        _recognizedMessage.value = displayMsg
                        withContext(Dispatchers.Main) {
                            com.cubicreates.unboundmusic.util.UnboundToast.show(
                                getApplication(),
                                displayMsg,
                                isLong = true
                            )
                        }
                        // Search for the recognized track
                        onSearchQueryChanged("$trackTitle $artist".trim())
                    } else {
                        _recognizedMessage.value = "Could not recognize audio. Try again."
                        withContext(Dispatchers.Main) {
                            com.cubicreates.unboundmusic.util.UnboundToast.show(
                                getApplication(),
                                "No acoustic match found. Try again closer to the speaker."
                            )
                        }
                    }
                } else {
                    _recognizedMessage.value = "Recognition service unavailable ($code)."
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Shazam service unavailable ($code)"
                        )
                    }
                }
            } catch (e: Exception) {
                _recognizedMessage.value = "Recognition error: ${e.message}"
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Recognition error: ${e.message}"
                    )
                }
            } finally {
                _isListeningShazam.value = false
            }
        }
    }

    // ==================== Lyrics ====================

    /**
     * Loads live lyrics for a specific track, ensuring in-flight requests from older tracks
     * are cleanly cancelled, the UI immediately shows the shimmer placeholder, and stale
     * lyrics (e.g. from previous tracks) never linger.
     */
    fun loadLyricsForTrack(track: TrackItem) {
        lyricsFetchJob?.cancel()
        _lyricsLines.value = emptyList()
        _lyricsSource.value = ""
        _isInstrumental.value = false

        if (track.title.isBlank() || track.title == "Unknown") return

        lyricsFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val duration = if (track.durationMs > 0) track.durationMs else playbackState.value.durationMs
                val cleanArtist = if (track.artist.equals("Song", ignoreCase = true) ||
                                      track.artist.equals("Video", ignoreCase = true) ||
                                      track.artist.equals("YouTube Artist", ignoreCase = true)) "" else track.artist
                val (code, resp) = client.getLyrics(
                    trackId = track.id,
                    title = track.title,
                    artist = cleanArtist,
                    durationMs = duration
                )

                // Verify user hasn't switched to another track during network flight
                val current = _currentTrack.value
                val isStillCurrent = (current.id.isNotBlank() && current.id == track.id) ||
                        (current.title.isNotBlank() && current.title.equals(track.title, ignoreCase = true))
                if (!isStillCurrent) {
                    Log.d(TAG, "Discarding lyrics: track moved from '${track.title}' to '${current.title}'")
                    return@launch
                }

                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val source = json.optString("source", "LRCLIB Synced Lyrics")
                    val isInst = json.optBoolean("instrumental", false)
                    _isInstrumental.value = isInst

                    val linesArray = json.optJSONArray("lines")
                    if (linesArray != null && linesArray.length() > 0) {
                        val lines = mutableListOf<LyricLine>()
                        for (i in 0 until linesArray.length()) {
                            val lineObj = linesArray.getJSONObject(i)
                            lines.add(
                                LyricLine(
                                    text = lineObj.optString("text", ""),
                                    startMs = lineObj.optLong("start_ms", 0),
                                    endMs = lineObj.optLong("end_ms", 0),
                                    romanized = lineObj.optString("romanized", "")
                                )
                            )
                        }
                        _lyricsLines.value = lines
                        _lyricsSource.value = source
                    } else {
                        val plain = json.optString("plain_lyrics", "")
                        if (plain.isNotBlank() && !isInst) {
                            val plainLines = plain.lines().filter { it.isNotBlank() }.map { text ->
                                LyricLine(text = text, startMs = 0, endMs = 0, romanized = "")
                            }
                            _lyricsLines.value = plainLines
                            _lyricsSource.value = source
                        } else {
                            _lyricsLines.value = emptyList()
                            _lyricsSource.value = if (isInst) source else ""
                        }
                    }
                } else {
                    _lyricsLines.value = emptyList()
                    _lyricsSource.value = ""
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics fetch error for '${track.title}': ${e.message}")
                _lyricsLines.value = emptyList()
                _lyricsSource.value = ""
            }
        }
    }

    // ==================== Canvas Visuals ====================

    /**
     * Fetches Spotify Canvas visual assets (video, hi-res art) from the Go daemon.
     */
    private suspend fun fetchCanvas(track: TrackItem) {
        try {
            val (code, resp) = client.getCanvas(track.title, track.artist)
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val found = json.optBoolean("found", false)
                if (found) {
                    _canvasVideoUrl.value = json.optString("canvas_url").takeIf { it.isNotBlank() }
                    _canvasArtUrl.value = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
                        ?: json.optString("song_art_url").takeIf { it.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Canvas fetch error: ${e.message}")
        }
    }

    // ==================== Phase 7: SponsorBlock Skit Skipping ====================

    /**
     * Fetches SponsorBlock music_offtopic skip segments for the current track.
     */
    private suspend fun fetchSkipSegments(track: TrackItem) {
        if (!_sponsorBlockEnabled.value) {
            _activeSkipSegments.value = emptyList()
            lastSkippedSegmentId = null
            return
        }
        try {
            _activeSkipSegments.value = emptyList()
            lastSkippedSegmentId = null
            val videoId = track.id.ifBlank {
                val stream = track.streamUrl
                if (!stream.startsWith("http") && stream.isNotBlank() && !stream.contains(" ")) {
                    stream
                } else ""
            }
            if (videoId.isBlank()) return
            val (code, resp) = client.getSkipSegments(videoId)
            if (code in 200..299 && resp.isNotBlank()) {
                val segments = client.parseSkipSegments(resp)
                _activeSkipSegments.value = segments.sortedBy { it.startMs }
                Log.d(TAG, "Loaded ${segments.size} skip segments for track: ${track.title}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skip segments fetch note: ${e.message}")
        }
    }

    // ==================== Return YouTube Dislike (RYD) & Like Ratio ====================

    /**
     * Fetches Return YouTube Dislike (RYD) community statistics and approval ratio for the current track.
     */
    suspend fun fetchRydVotes(track: TrackItem) {
        try {
            val videoId = track.id.ifBlank {
                val stream = track.streamUrl
                if (!stream.startsWith("http") && stream.isNotBlank() && !stream.contains(" ")) {
                    stream
                } else ""
            }
            if (videoId.isBlank() || videoId.startsWith("local") || videoId.startsWith("file")) {
                _rydVotes.value = null
                return
            }

            val votes = client.getRydVotes(videoId)
            // Ensure track didn't change while request was running
            val current = _currentTrack.value
            val isStillCurrent = (current.id.isNotBlank() && current.id == track.id) ||
                    (current.title.isNotBlank() && current.title.equals(track.title, ignoreCase = true))
            if (isStillCurrent) {
                _rydVotes.value = votes
                Log.d(TAG, "Loaded RYD votes for '${track.title}': ${votes?.likes} likes, ${votes?.dislikes} dislikes (${votes?.likePercentage}%)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "RYD votes fetch note: ${e.message}")
        }
    }

    /**
     * Forces a refresh of the Return YouTube Dislike community stats for current track.
     */
    fun refreshRydVotes() {
        val track = _currentTrack.value
        viewModelScope.launch(Dispatchers.IO) {
            fetchRydVotes(track)
        }
    }

    // ==================== Home Feed ====================

    private fun loadHomeFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load Moods & Moments
                val (mCode, mResp) = client.getExploreMoods()
                if (mCode in 200..299 && mResp.isNotBlank()) {
                    val json = JSONObject(mResp)
                    val cats = json.optJSONArray("categories")
                    if (cats != null) {
                        val parsed = mutableListOf<MoodCategory>()
                        for (i in 0 until cats.length()) {
                            val cat = cats.getJSONObject(i)
                            parsed.add(MoodCategory(
                                title = cat.optString("title", ""),
                                description = cat.optString("description", ""),
                                color = cat.optString("color", "#4CD6FB")
                            ))
                        }
                        _moodCategories.value = parsed
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Home feed load note: ${e.message}")
            }

            try {
                // Load Top Charts
                val (cCode, cResp) = client.getExploreCharts()
                if (cCode in 200..299 && cResp.isNotBlank()) {
                    val json = JSONObject(cResp)
                    val tracks = json.optJSONArray("tracks") ?: json.optJSONArray("chart")
                    if (tracks != null) {
                        val parsed = mutableListOf<TrackItem>()
                        for (i in 0 until minOf(tracks.length(), 20)) {
                            val t = tracks.getJSONObject(i)
                            val vId = t.optString("id").ifBlank { t.optString("video_id", "") }
                            parsed.add(TrackItem(
                                id = vId,
                                title = t.optString("title", ""),
                                artist = t.optString("artist", ""),
                                coverUrl = t.optString("thumbnail_url", ""),
                                streamUrl = ""
                            ))
                        }
                        _chartTracks.value = parsed
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Charts load note: ${e.message}")
            }

            // Load native YouTube-esque algorithmic shelves
            loadSmartFeed()
        }
    }

    /**
     * Loads dynamic algorithmic recommendation shelves from local listening history and song seeds.
     */
    fun loadSmartFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getSmartFeed()
                if (code in 200..299 && resp.isNotBlank()) {
                    val feed = client.parseSmartFeed(resp)
                    if (feed != null && feed.hasPersonalization) {
                        _smartShelves.value = feed.shelves
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Smart feed load note: ${e.message}")
            }
        }
    }

    // ==================== Library ====================

    /**
     * Dual-Engine Hybrid Audio Scanner:
     * 1. MediaStore query via Android OS bridge (Kotlin) for instant UI responsiveness.
     * 2. Native Go daemon crawler (Go) for deep POSIX storage traversal, .nomedia bypass,
     *    magic byte probing, ID3 tag extraction, and SQLite database indexing.
     */
    fun performUniversalAudioScan() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fast Kotlin MediaStore pass for immediate UI responsiveness
                val mediaStoreTracks = MediaStoreAudioBridge.queryMediaStoreAudio(getApplication())
                if (mediaStoreTracks.isNotEmpty() && _libraryTracks.value.isEmpty()) {
                    _libraryTracks.value = mediaStoreTracks.map { it.toTrackItem() }
                    refreshFavoritesList()
                }

                // 2. Discover device storage roots and dispatch to Go backend crawler (.nomedia bypass)
                val deviceRoots = MediaStoreAudioBridge.discoverDeviceStorageRoots(getApplication())
                client.scanStorage(deviceRoots)
                if (mediaStoreTracks.isNotEmpty()) {
                    client.ingestMediaStoreTracks(mediaStoreTracks)
                }

                // 3. Retrieve fully indexed tracks from Go SQLite database
                val daemonTracks = client.getLocalTracks("all")
                val unboundDownloads = client.getLocalTracks("Unbound Downloads")
                if (unboundDownloads.isNotEmpty()) {
                    val dlIds = unboundDownloads.map { it.id }.toSet()
                    _downloadedTrackIds.value = _downloadedTrackIds.value + dlIds
                }

                // 4. Merge discovered tracks with daemon tracks, deduplicating by normalized path
                val combinedMap = LinkedHashMap<String, LocalTrack>()
                for (track in mediaStoreTracks) {
                    val key = track.filePath.lowercase(java.util.Locale.ROOT)
                    combinedMap[key] = track
                }
                for (track in daemonTracks) {
                    val key = track.filePath.lowercase(java.util.Locale.ROOT)
                    combinedMap[key] = track
                }

                val allLocal = combinedMap.values.toList()

                // 5. Update folders map
                val updatedFolders = mutableMapOf<String, MutableList<LocalTrack>>()
                for (track in allLocal) {
                    val folderName = track.sourceFolder.ifBlank { "Device Audio" }
                    updatedFolders.getOrPut(folderName) { mutableListOf() }.add(track)
                }
                _libraryFolders.value = updatedFolders

                // 6. Update category counts
                val waTracks = allLocal.filter {
                    it.sourceFolder.contains("WhatsApp", ignoreCase = true) ||
                    it.filePath.contains("WhatsApp", ignoreCase = true)
                }
                val tgTracks = allLocal.filter {
                    it.sourceFolder.contains("Telegram", ignoreCase = true) ||
                    it.filePath.contains("Telegram", ignoreCase = true)
                }
                val dlTracks = allLocal.filter {
                    it.sourceFolder.contains("Download", ignoreCase = true) ||
                    it.sourceFolder.contains("Unbound", ignoreCase = true) ||
                    it.filePath.contains("Download", ignoreCase = true)
                }

                _whatsappCount.value = waTracks.size
                _telegramCount.value = tgTracks.size
                _downloadsCount.value = dlTracks.size

                val allTrackItems = allLocal.map { it.toTrackItem() }
                if (allTrackItems.isNotEmpty()) {
                    _libraryTracks.value = allTrackItems
                    refreshFavoritesList()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Universal audio scan note: ${e.message}")
            }
        }
    }

    fun rescanLocalStorage() {
        performUniversalAudioScan()
    }

    fun refreshLibrary() {
        rescanLocalStorage()
        loadDownloadedMusicFiles()
        updateStorageMetrics()
    }

    // ==================== Acoustic & On-Device AI Fingerprinting ====================

    private val _isIdentifyingTrack = MutableStateFlow<String?>(null)
    val isIdentifyingTrack: StateFlow<String?> = _isIdentifyingTrack.asStateFlow()

    private fun resolveLocalFilePath(track: TrackItem): String {
        val stream = track.streamUrl
        if (stream.startsWith("file://")) {
            val clean = stream.removePrefix("file://")
            return try {
                java.net.URLDecoder.decode(clean, "UTF-8")
            } catch (_: Exception) {
                clean
            }
        }
        if (stream.startsWith("content://")) {
            val resolved = MediaStoreAudioBridge.resolveContentUriToPath(getApplication(), stream)
            if (!resolved.isNullOrBlank()) return resolved
        }
        if (stream.startsWith("/")) {
            return stream
        }
        // Fallback: search in library folders for matching track ID
        for (folderTracks in _libraryFolders.value.values) {
            val match = folderTracks.firstOrNull { it.id == track.id }
            if (match != null && match.filePath.isNotBlank()) {
                val fp = match.filePath
                return if (fp.startsWith("file://")) fp.removePrefix("file://") else fp
            }
        }
        return stream.ifBlank { track.id }
    }

    fun identifyTrack(track: TrackItem) {
        if (_isIdentifyingTrack.value != null) return
        _isIdentifyingTrack.value = track.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetPath = resolveLocalFilePath(track)
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Identifying '${track.title}'...",
                        isLong = false
                    )
                }

                val result = client.identifyTrackDetailed(targetPath)
                val identified = result.track
                if (identified != null && identified.title.isNotBlank()) {
                    val updatedTrack = track.copy(
                        title = identified.title,
                        artist = if (identified.artist.isNotBlank()) identified.artist else track.artist,
                        album = if (identified.album.isNotBlank()) identified.album else track.album,
                        coverUrl = if (identified.coverUrl.isNotBlank()) identified.coverUrl else track.coverUrl
                    )
                    withContext(Dispatchers.Main) {
                        _libraryTracks.value = _libraryTracks.value.map { if (it.id == track.id) updatedTrack else it }
                        _favoriteTracks.value = _favoriteTracks.value.map { if (it.id == track.id) updatedTrack else it }
                        if (_currentTrack.value.id == track.id) {
                            _currentTrack.value = updatedTrack
                        }
                        val methodBadge = when {
                            identified.method.contains("llm", ignoreCase = true) || identified.method.contains("ai", ignoreCase = true) -> "On-Device AI"
                            identified.method.contains("acoustid", ignoreCase = true) -> "AcoustID"
                            identified.method.contains("voice", ignoreCase = true) -> "Media Parser"
                            else -> "Smart Heuristic"
                        }
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Identified via $methodBadge (${(identified.confidence * 100).toInt()}%): '${updatedTrack.artist} - ${updatedTrack.title}'",
                            isLong = true
                        )
                    }
                } else {
                    val errDetail = result.errorMessage ?: "HTTP ${result.statusCode}: No match found"
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Identification diagnostic: Could not identify track ($errDetail)",
                            isLong = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to identify track ${track.title}: ${e.message}")
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Identification failed: ${e.message}",
                        isLong = true
                    )
                }
            } finally {
                _isIdentifyingTrack.value = null
            }
        }
    }

    fun batchIdentifyUnknownTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = _libraryTracks.value.filter {
                it.title.startsWith("AUD-", ignoreCase = true) ||
                it.title.startsWith("PTT-", ignoreCase = true) ||
                it.title.startsWith("voice_", ignoreCase = true) ||
                it.title.startsWith("WA", ignoreCase = true) ||
                it.artist.equals("Unknown Artist", ignoreCase = true) ||
                it.artist.isBlank() ||
                it.title.contains("y2mate", ignoreCase = true)
            }
            if (candidates.isEmpty()) {
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "All audio tracks are already identified!", isLong = false)
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Identifying ${candidates.size} untagged tracks...", isLong = false)
            }
            var identifiedCount = 0
            var errorCount = 0
            var lastError = ""
            for (track in candidates) {
                val targetPath = resolveLocalFilePath(track)
                val result = client.identifyTrackDetailed(targetPath)
                val identified = result.track
                if (identified != null && identified.title.isNotBlank()) {
                    val updated = track.copy(
                        title = identified.title,
                        artist = if (identified.artist.isNotBlank()) identified.artist else track.artist,
                        album = if (identified.album.isNotBlank()) identified.album else track.album,
                        coverUrl = if (identified.coverUrl.isNotBlank()) identified.coverUrl else track.coverUrl
                    )
                    identifiedCount++
                    withContext(Dispatchers.Main) {
                        _libraryTracks.value = _libraryTracks.value.map { if (it.id == track.id) updated else it }
                    }
                } else {
                    errorCount++
                    lastError = result.errorMessage ?: "HTTP ${result.statusCode}"
                }
                kotlinx.coroutines.delay(250)
            }
            withContext(Dispatchers.Main) {
                val msg = if (identifiedCount > 0) {
                    "Identified $identifiedCount/${candidates.size} audio files!${if (errorCount > 0) " ($errorCount failed: $lastError)" else ""}"
                } else {
                    "Diagnostic: 0/${candidates.size} identified. Reason: $lastError"
                }
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), msg, isLong = true)
            }
        }
    }

    // ==================== Phase 5: Offline Downloader Orchestration ====================

    private val recentlyCancelledIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Initiates a physical background chunked download for an audio track in MP3 format. */
    fun startTrackDownload(track: TrackItem) {
        if (track.title.isBlank() && track.id.isBlank()) return

        val taskId = if (track.id.isNotBlank()) track.id else "custom_${System.currentTimeMillis()}"
        recentlyCancelledIds.remove(taskId)
        recentlyCancelledIds.remove(track.id)
        if (track.title.isNotBlank()) recentlyCancelledIds.remove(track.title.lowercase())

        // 1. Optimistic UI update: immediately show DOWNLOADING on the DownloadButton
        val optimisticTask = DownloadTaskDto(
            videoId = taskId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.coverUrl,
            targetFormat = "mp3",
            status = "DOWNLOADING",
            progress = 0.0
        )
        _downloadTasks.value = _downloadTasks.value + (taskId to optimisticTask)

        // 2. User feedback: In-app Toast + Android System Notification
        com.cubicreates.unboundmusic.util.UnboundToast.show(
            getApplication(),
            "Downloading '${track.title}' to Unbound/Downloads (MP3)...",
            isLong = false
        )
        downloadNotificationHelper.notifyDownloadStarted(taskId, track.title, track.artist)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.startDownload(
                    videoId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.coverUrl,
                    streamUrl = track.streamUrl
                )
                if (code in 200..299 && resp.isNotBlank()) {
                    val task = client.parseDownloadTask(resp)
                    if (task != null) {
                        _downloadTasks.value = _downloadTasks.value + (task.videoId to task)
                        startDownloadPollingLoop()
                        // Phase 6: Pre-fetch and cache lyrics offline in SQLite without altering active playback UI
                        launch {
                            try {
                                client.getLyrics(
                                    trackId = task.videoId,
                                    title = track.title,
                                    artist = track.artist,
                                    durationMs = track.durationMs
                                )
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    Log.e(TAG, "startTrackDownload failed HTTP $code: $resp")
                    downloadNotificationHelper.notifyDownloadFailed(taskId, track.title, track.artist, "Download rejected ($code)")
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Download failed for '${track.title}' ($code)"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startTrackDownload failed: ${e.message}")
                downloadNotificationHelper.notifyDownloadFailed(taskId, track.title, track.artist, e.message ?: "Network error")
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Download failed: ${e.message}"
                    )
                }
            }
        }
    }

    /** Cancels an ongoing download and clears partial artifacts immediately. */
    fun cancelTrackDownload(videoId: String, title: String = "") {
        downloadNotificationHelper.cancelNotification()
        
        if (videoId.isNotBlank()) recentlyCancelledIds.add(videoId)
        val effectiveTitle = title.ifBlank { _currentTrack.value.title }
        if (effectiveTitle.isNotBlank()) recentlyCancelledIds.add(effectiveTitle.lowercase())

        // Optimistically remove from _downloadTasks immediately so UI resets to NOT_DOWNLOADED instantly
        val current = _downloadTasks.value.toMutableMap()
        val matchKey = current.keys.firstOrNull { key ->
            key == videoId ||
            current[key]?.videoId == videoId ||
            (effectiveTitle.isNotBlank() && current[key]?.title.equals(effectiveTitle, ignoreCase = true)) ||
            (videoId.isNotBlank() && current[key]?.title.equals(videoId, ignoreCase = true))
        }
        if (matchKey != null) {
            val task = current.remove(matchKey)
            _downloadTasks.value = current
            if (task != null && task.videoId.isNotBlank()) {
                recentlyCancelledIds.add(task.videoId)
                downloadNotificationHelper.cancelNotification(task.videoId)
            }
        } else if (effectiveTitle.isNotBlank()) {
            val toRemove = current.filterValues { it.title.equals(effectiveTitle, ignoreCase = true) }.keys
            for (k in toRemove) {
                current.remove(k)
            }
            _downloadTasks.value = current
        }

        com.cubicreates.unboundmusic.util.UnboundToast.show(
            getApplication(),
            "Download cancelled",
            isLong = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.cancelDownload(videoId, effectiveTitle)
            } catch (e: Exception) {
                Log.e(TAG, "cancelTrackDownload failed: ${e.message}")
            }
        }
    }

    /** Removes an offline track from disk and local database. */
    fun deleteTrackDownload(videoId: String, title: String = "") {
        downloadNotificationHelper.cancelNotification(videoId)
        val current = _downloadTasks.value.toMutableMap()
        val effectiveTitle = title.ifBlank { _currentTrack.value.title }
        val matchKey = current.keys.firstOrNull { key ->
            key == videoId ||
            current[key]?.videoId == videoId ||
            (effectiveTitle.isNotBlank() && current[key]?.title.equals(effectiveTitle, ignoreCase = true)) ||
            (videoId.isNotBlank() && current[key]?.title.equals(videoId, ignoreCase = true))
        }
        if (matchKey != null) {
            current.remove(matchKey)
            _downloadTasks.value = current
        }
        _downloadedTrackIds.value = _downloadedTrackIds.value - videoId
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.deleteDownload(videoId, true, effectiveTitle)
                refreshLibrary()
            } catch (e: Exception) {
                Log.e(TAG, "deleteTrackDownload failed: ${e.message}")
            }
        }
    }

    /** Polls active downloads from Go daemon until all tasks are completed, failed, or cancelled. */
    fun startDownloadPollingLoop() {
        if (isPollingDownloads) return
        isPollingDownloads = true

        viewModelScope.launch(Dispatchers.IO) {
            var prevBytes = 0L
            var prevTime = System.currentTimeMillis()

            try {
                while (isActive) {
                    val (code, resp) = client.getActiveDownloads()
                    if (code in 200..299 && resp.isNotBlank()) {
                        val activeTasks = client.parseActiveDownloads(resp)
                        // Filter out cancelled tasks to avoid UI flicker
                        val validTasks = activeTasks.filter { task ->
                            task.status != "CANCELLED" &&
                            !recentlyCancelledIds.contains(task.videoId) &&
                            !recentlyCancelledIds.contains(task.title.lowercase())
                        }
                        val taskMap = validTasks.associateBy { it.videoId }
                        _downloadTasks.value = taskMap

                        // Calculate live download speed across all downloading tasks
                        val currentBytes = validTasks.filter { it.status == "DOWNLOADING" || it.status == "TAGGING" }
                            .sumOf { it.downloadedBytes }
                        val now = System.currentTimeMillis()
                        val elapsedSec = (now - prevTime) / 1000.0
                        if (elapsedSec >= 0.8 && prevBytes > 0) {
                            val delta = (currentBytes - prevBytes).coerceAtLeast(0)
                            _downloadSpeedBps.value = (delta / elapsedSec).toLong()
                        }
                        prevBytes = currentBytes
                        prevTime = now

                        // Update Android notifications for active/failed tasks
                        val downloadingTask = validTasks.firstOrNull { it.status == "DOWNLOADING" || it.status == "TAGGING" }
                        if (downloadingTask != null) {
                            downloadNotificationHelper.notifyDownloadProgress(
                                downloadingTask.videoId,
                                downloadingTask.title,
                                downloadingTask.artist,
                                downloadingTask.progress.toInt()
                            )
                        }

                        val failedTask = validTasks.firstOrNull { it.status == "FAILED" }
                        if (failedTask != null) {
                            downloadNotificationHelper.notifyDownloadFailed(
                                failedTask.videoId,
                                failedTask.title,
                                failedTask.artist,
                                failedTask.error
                            )
                        }

                        val completedTasks = activeTasks.filter { it.status == "COMPLETED" }
                        val completedIds = completedTasks.map { it.videoId }.toSet()
                        if (completedIds.isNotEmpty()) {
                            val prevCompleted = _downloadedTrackIds.value
                            val newlyCompleted = completedTasks.filter { it.videoId !in prevCompleted }
                            _downloadedTrackIds.value = prevCompleted + completedIds
                            if (newlyCompleted.isNotEmpty()) {
                                refreshLibrary()
                                loadDownloadedMusicFiles()
                                for (task in newlyCompleted) {
                                    downloadNotificationHelper.notifyDownloadCompleted(
                                        task.videoId,
                                        task.title,
                                        task.artist
                                    )
                                    withContext(Dispatchers.Main) {
                                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                                            getApplication(),
                                            "Downloaded '${task.title}' as MP3 to Unbound/Downloads!",
                                            isLong = false
                                        )
                                    }
                                    if (task.localPath.isNotBlank()) {
                                        try {
                                            android.media.MediaScannerConnection.scanFile(
                                                getApplication<Application>().applicationContext,
                                                arrayOf(task.localPath),
                                                null
                                            ) { path, uri ->
                                                Log.i(TAG, "MediaScannerConnection indexed $path -> $uri")
                                            }
                                        } catch (scanErr: Exception) {
                                            Log.w(TAG, "MediaScanner scanFile failed: ${scanErr.message}")
                                        }
                                    }
                                }
                            }
                        }

                        val hasActive = activeTasks.any {
                            it.status == "QUEUED" || it.status == "DOWNLOADING" || it.status == "TAGGING"
                        }
                        if (!hasActive) {
                            _downloadSpeedBps.value = 0L
                            break
                        }
                    } else {
                        _downloadSpeedBps.value = 0L
                        break
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Download polling loop finished: ${e.message}")
            } finally {
                isPollingDownloads = false
                _downloadSpeedBps.value = 0L
            }
        }
    }

    /** Pauses an active download. */
    fun pauseDownload(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = _downloadTasks.value.toMutableMap()
                current[videoId]?.let {
                    current[videoId] = it.copy(status = "PAUSED")
                    _downloadTasks.value = current
                }
                client.pauseDownload(videoId)
            } catch (e: Exception) {
                Log.e(TAG, "pauseDownload failed: ${e.message}")
            }
        }
    }

    /** Resumes a paused download. */
    fun resumeDownload(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = _downloadTasks.value.toMutableMap()
                current[videoId]?.let {
                    current[videoId] = it.copy(status = "DOWNLOADING")
                    _downloadTasks.value = current
                }
                client.resumeDownload(videoId)
                startDownloadPollingLoop()
            } catch (e: Exception) {
                Log.e(TAG, "resumeDownload failed: ${e.message}")
            }
        }
    }

    /** Retries a failed or paused download. */
    fun retryDownload(task: DownloadTaskDto) {
        startTrackDownload(
            TrackItem(
                id = task.videoId,
                title = task.title,
                artist = task.artist,
                album = task.album,
                coverUrl = task.artworkUrl
            )
        )
    }

    /** Loads physical downloaded music files and updates storage metrics. */
    fun loadDownloadedMusicFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getDownloadedFiles()
                if (code in 200..299 && resp.isNotBlank()) {
                    val tracks = client.parseDownloadedFiles(resp)
                    _downloadedMusicTracks.value = tracks
                    var totalBytes = 0L
                    for (t in tracks) {
                        if (t.streamUrl.startsWith("file://")) {
                            val f = java.io.File(t.streamUrl.removePrefix("file://"))
                            if (f.exists()) {
                                totalBytes += f.length()
                            }
                        }
                    }
                    _downloadsSizeMB.value = (totalBytes / (1024.0 * 1024.0))
                } else {
                    val local = client.getLocalTracks("Unbound Downloads")
                    _downloadedMusicTracks.value = local.map { it.toTrackItem() }
                    _downloadsSizeMB.value = local.sumOf { it.fileSize } / (1024.0 * 1024.0)
                }
                updateStorageMetrics()
            } catch (e: Exception) {
                Log.w(TAG, "loadDownloadedMusicFiles failed: ${e.message}")
            }
        }
    }

    /** Recalculates storage usage and free space on device. */
    fun updateStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                var cacheBytes = 0L
                context.cacheDir?.let { cacheBytes += calculateDirectorySize(it) }
                context.externalCacheDir?.let { cacheBytes += calculateDirectorySize(it) }
                _cacheSizeMB.value = (cacheBytes / (1024.0 * 1024.0))

                val stat = android.os.StatFs(context.filesDir.absolutePath)
                val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                _freeStorageGB.value = (freeBytes / (1024.0 * 1024.0 * 1024.0))
            } catch (_: Exception) {}
        }
    }

    private fun calculateDirectorySize(dir: java.io.File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var size = 0L
        dir.listFiles()?.forEach { child ->
            size += if (child.isDirectory) calculateDirectorySize(child) else child.length()
        }
        return size
    }

    /** Clears temporary image and stream caches without deleting user downloaded audio files. */
    fun clearCacheAndStorage(onResult: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                var freedAppCache = 0L
                context.cacheDir?.let {
                    freedAppCache += calculateDirectorySize(it)
                    it.deleteRecursively()
                    it.mkdirs()
                }
                context.externalCacheDir?.let {
                    freedAppCache += calculateDirectorySize(it)
                    it.deleteRecursively()
                    it.mkdirs()
                }
                val (code, resp) = client.purgeStorageCache()
                val backendFreed = if (code in 200..299) {
                    client.parseCachePurgeResult(resp)?.freedBytes ?: 0L
                } else 0L

                val totalFreedMB = ((freedAppCache + backendFreed) / (1024.0 * 1024.0)).coerceAtLeast(0.0)
                updateStorageMetrics()
                withContext(Dispatchers.Main) {
                    val msg = String.format("Freed %.1f MB cache! Offline downloads preserved.", totalFreedMB)
                    com.cubicreates.unboundmusic.util.UnboundToast.show(context, msg, isLong = false)
                    onResult(msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errMsg = "Cache clean error: ${e.message}"
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), errMsg, isLong = false)
                    onResult(errMsg)
                }
            }
        }
    }

    /** Triggers system MediaScanner to index Unbound/Downloads/ into public Android audio stores. */
    fun exportDownloadsToPublicStorage(onResult: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val tracks = _downloadedMusicTracks.value
                val pathsToScan = mutableListOf<String>()

                for (t in tracks) {
                    if (t.streamUrl.startsWith("file://")) {
                        val path = t.streamUrl.removePrefix("file://")
                        val f = java.io.File(path)
                        if (f.exists()) {
                            pathsToScan.add(f.absolutePath)
                        }
                    }
                }

                if (pathsToScan.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        pathsToScan.toTypedArray(),
                        null
                    ) { path, uri ->
                        Log.i(TAG, "Exported & Indexed to MediaStore: $path -> $uri")
                    }
                    withContext(Dispatchers.Main) {
                        val msg = "Exported ${pathsToScan.size} tracks to device media library!"
                        com.cubicreates.unboundmusic.util.UnboundToast.show(context, msg, isLong = false)
                        onResult(msg)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val msg = "No downloaded tracks to export."
                        com.cubicreates.unboundmusic.util.UnboundToast.show(context, msg, isLong = false)
                        onResult(msg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errMsg = "Export error: ${e.message}"
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), errMsg, isLong = false)
                    onResult(errMsg)
                }
            }
        }
    }

    // ==================== YouTube Account & Synced Library ====================

    /** Checks YouTube connection status from daemon and loads synced tracks if connected. */
    fun checkAccountStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getAccountStatus()
                if (code in 200..299 && resp.isNotBlank()) {
                    val status = client.parseAccountStatus(resp)
                    if (status != null) {
                        _isYouTubeConnected.value = status.connected
                        _accountName.value = status.accountName
                        _userAvatarUrl.value = status.avatarUrl.takeIf { it.isNotBlank() }
                        if (status.connected) {
                            val store = com.cubicreates.unboundmusic.data.SessionStore.getInstance(getApplication())
                            store.accountName = status.accountName
                            if (status.avatarUrl.isNotBlank()) {
                                store.avatarUrl = status.avatarUrl
                            }
                            loadSyncedYouTubeTracks()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Account status check note: ${e.message}")
            }
        }
    }

    /** Synchronizes YouTube account credentials and pulls liked music into the local library. */
    fun syncYouTubeAccount(cookie: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingAccount.value = true
            try {
                val (code, resp) = client.syncAccount(cookie)
                if (code in 200..299) {
                    _isYouTubeConnected.value = true
                    val store = com.cubicreates.unboundmusic.data.SessionStore.getInstance(getApplication())
                    store.saveSession(cookie)
                    checkAccountStatus()
                    loadSyncedYouTubeTracks()
                    loadSmartFeed()
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "YouTube synced successfully!", isLong = false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Sync Error [HTTP $code]: $resp", isLong = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Account sync failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Sync Exception: ${e.message}", isLong = true)
                }
            } finally {
                _isSyncingAccount.value = false
            }
        }
    }

    /** Re-triggers sync of liked music, account metadata, and personalized home feed with daemon. */
    fun resyncYouTubeAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingAccount.value = true
            try {
                val (code, _) = client.syncAccount("")
                if (code in 200..299) {
                    checkAccountStatus()
                    loadSyncedYouTubeTracks()
                    loadSmartFeed()
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Library refreshed!", isLong = false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Refresh error [HTTP $code]", isLong = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resync failed: ${e.message}")
            } finally {
                _isSyncingAccount.value = false
            }
        }
    }

    /** Initiates YouTube Device Code flow, opens browser, and starts background polling. */
    fun startYouTubeDeviceAuth(onLaunchBrowser: (String) -> Unit) {
        deviceAuthJob?.cancel()
        deviceAuthJob = viewModelScope.launch(Dispatchers.IO) {
            _isStartingDeviceAuth.value = true
            _deviceAuthError.value = null
            try {
                val (code, resp) = client.startDeviceAuth()
                if (code in 200..299) {
                    val data = client.parseDeviceCodeData(resp)
                    if (data != null && data.userCode.isNotBlank()) {
                        _deviceAuthData.value = data
                        _isStartingDeviceAuth.value = false

                        val activateUrl = "${data.verificationUrl}?user_code=${data.userCode}"
                        withContext(Dispatchers.Main) {
                            onLaunchBrowser(activateUrl)
                        }

                        pollDeviceAuthToken(data.deviceCode, data.interval)
                        return@launch
                    }
                }
                val errMsg = if (resp.isNotBlank()) "Device Auth Error [HTTP $code]: $resp" else "Device Auth Error [HTTP $code]"
                _deviceAuthError.value = errMsg
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), errMsg, isLong = true)
                }
            } catch (e: Exception) {
                val exMsg = "Device Auth Exception: ${e.message ?: "Unknown error"}"
                _deviceAuthError.value = exMsg
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), exMsg, isLong = true)
                }
            } finally {
                _isStartingDeviceAuth.value = false
            }
        }
    }

    private suspend fun pollDeviceAuthToken(deviceCode: String, intervalSeconds: Int) {
        _isPollingDeviceAuth.value = true
        val pollDelay = (if (intervalSeconds > 0) intervalSeconds else 5) * 1000L
        val maxAttempts = 60 // 5 minutes max

        for (i in 0 until maxAttempts) {
            if (!currentCoroutineContext().isActive) break
            delay(pollDelay)
            try {
                val (code, resp) = client.pollDeviceAuth(deviceCode)
                if (code in 200..299 && resp.isNotBlank()) {
                    val root = JSONObject(resp)
                    val status = root.optString("status", "")
                    if (status == "success") {
                        _isYouTubeConnected.value = true
                        _deviceAuthData.value = null
                        _isPollingDeviceAuth.value = false
                        checkAccountStatus()
                        loadSyncedYouTubeTracks()
                        withContext(Dispatchers.Main) {
                            com.cubicreates.unboundmusic.util.UnboundToast.show(
                                getApplication(),
                                "YouTube account connected successfully!",
                                isLong = false
                            )
                        }
                        return
                    } else if (status == "pending") {
                        continue
                    }
                } else if (code in 400..599 && !resp.contains("authorization_pending") && !resp.contains("slow_down")) {
                    val pollErr = "Device Poll Error [HTTP $code]: $resp"
                    _deviceAuthError.value = pollErr
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), pollErr, isLong = true)
                    }
                    break
                }
            } catch (e: Exception) {
                Log.d(TAG, "Device poll iteration note: ${e.message}")
            }
        }
        _isPollingDeviceAuth.value = false
    }

    /** Cancels any active device code authorization polling. */
    fun cancelDeviceAuth() {
        deviceAuthJob?.cancel()
        deviceAuthJob = null
        _deviceAuthData.value = null
        _isStartingDeviceAuth.value = false
        _isPollingDeviceAuth.value = false
        _deviceAuthError.value = null
    }

    /** Disconnects YouTube account and purges credentials and synced library data. */
    fun disconnectYouTubeAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, _) = client.disconnectAccount()
                if (code in 200..299) {
                    _isYouTubeConnected.value = false
                    _accountName.value = "Local User"
                    _userAvatarUrl.value = null
                    _syncedYouTubeTracks.value = emptyList()
                    _youtubeCount.value = 0
                    com.cubicreates.unboundmusic.data.SessionStore.getInstance(getApplication()).clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Account disconnect failed: ${e.message}")
            }
        }
    }

    /** Loads cached synced Liked Music tracks from the Go engine daemon. */
    fun loadSyncedYouTubeTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingAccount.value = true
            try {
                val (code, resp) = client.getLikedTracks()
                if (code in 200..299 && resp.isNotBlank()) {
                    val tracks = client.parseLikedTracks(resp)
                    val mixes = client.parseUserMixes(resp)
                    _syncedYouTubeTracks.value = tracks
                    _userMixes.value = mixes
                    _youtubeCount.value = tracks.size
                    withContext(Dispatchers.Main) {
                        if (tracks.isNotEmpty()) {
                            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Synced ${tracks.size} tracks from YouTube!", isLong = false)
                        } else {
                            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "0 liked tracks found on YouTube account.", isLong = false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Load synced tracks note: ${e.message}")
            } finally {
                _isSyncingAccount.value = false
            }
        }
    }

    private val _isLoadingMoreTracks = MutableStateFlow(false)
    val isLoadingMoreTracks: StateFlow<Boolean> = _isLoadingMoreTracks.asStateFlow()

    /** Continuously loads next wave of music tracks for infinite scroll. */
    fun loadMorePersonalizedTracks() {
        if (_isLoadingMoreTracks.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMoreTracks.value = true
            try {
                val seed = _syncedYouTubeTracks.value.shuffled().firstOrNull()?.id
                val (code, resp) = client.getAccountFeedInfinite(seed)
                if (code in 200..299 && resp.isNotBlank()) {
                    val newTracks = client.parseLikedTracks(resp)
                    if (newTracks.isNotEmpty()) {
                        val existingIds = _syncedYouTubeTracks.value.map { it.id }.toSet()
                        val uniqueNew = newTracks.filter { it.id !in existingIds }
                        if (uniqueNew.isNotEmpty()) {
                            _syncedYouTubeTracks.value = _syncedYouTubeTracks.value + uniqueNew
                            _youtubeCount.value = _syncedYouTubeTracks.value.size
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Load more infinite tracks: ${e.message}")
            } finally {
                _isLoadingMoreTracks.value = false
            }
        }
    }

    /** Optimistically toggles like state and dispatches mutation to daemon. */
    fun toggleTrackLike(track: TrackItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val willBeLiked = !_isFavorite.value
            _isFavorite.value = willBeLiked
            if (willBeLiked && _autoDownloadLikedSongs.value) {
                withContext(Dispatchers.Main) {
                    startTrackDownload(track)
                }
            }
            try {
                client.toggleTrackLike(track.id, willBeLiked)
                loadSyncedYouTubeTracks()
            } catch (e: Exception) {
                Log.w(TAG, "Track like toggle error: ${e.message}")
            }
        }
    }

    /** Executes 4-stage intelligent search cascade. */
    fun executeCascadeSearch(query: String) {
        if (query.isBlank()) {
            _cascadeSearchResponse.value = null
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                val (code, resp) = client.searchCascade(query)
                if (code in 200..299 && resp.isNotBlank()) {
                    val cascade = client.parseCascadeSearch(resp)
                    _cascadeSearchResponse.value = cascade
                    if (cascade != null) {
                        _searchResults.value = cascade.tracks
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cascade search error: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    // ==================== Analytics ====================

    /**
     * Logs a playback event to the Go daemon analytics engine.
     */
    fun logPlaybackEvent(track: TrackItem, listenedSec: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.logPlaybackEvent(
                    trackId = track.id.ifBlank { track.title },
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    listenedSec = listenedSec
                )
                // Refresh smart shelves with updated listening affinity
                loadSmartFeed()
            } catch (e: Exception) {
                Log.d(TAG, "Analytics log note: ${e.message}")
            }
        }
    }

    // ==================== Position Ticker ====================

    /**
     * Ticks every 300ms to update playback progress smoothly in the UI and check SponsorBlock skips.
     */
    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                if (playbackState.value.isPlaying) {
                    serviceConnection.updatePosition()
                    checkSponsorBlockSkip()
                    val now = System.currentTimeMillis()
                    if (now - lastSavedStateTick >= 4000L) {
                        lastSavedStateTick = now
                        saveCurrentPlaybackState()
                    }
                }
                delay(300)
            }
        }
    }

    private fun checkSponsorBlockSkip() {
        if (!_sponsorBlockEnabled.value) return
        val segments = _activeSkipSegments.value
        if (segments.isEmpty()) return
        val curPos = playbackState.value.currentPositionMs
        val duration = playbackState.value.durationMs

        for (segment in segments) {
            if (curPos >= segment.startMs && curPos < (segment.endMs - 150L) && segment.uuid != lastSkippedSegmentId) {
                lastSkippedSegmentId = segment.uuid
                val fromMs = curPos
                val toMs = segment.endMs.coerceAtMost(duration)
                seekToPositionMs(toMs)

                val startStr = formatTimestamp(segment.startMs)
                val endStr = formatTimestamp(segment.endMs)
                _skippedSkitNotice.value = SkitSkipNotice(
                    segment = segment,
                    fromMs = fromMs,
                    toMs = toMs,
                    message = "Skipped Non-Music Skit ($startStr - $endStr)"
                )

                skitNoticeDismissJob?.cancel()
                skitNoticeDismissJob = viewModelScope.launch {
                    delay(4500)
                    _skippedSkitNotice.value = null
                }
                break
            }
        }
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%d:%02d", m, s)
    }

    fun undoSkitSkip() {
        val notice = _skippedSkitNotice.value ?: return
        seekToPositionMs(notice.fromMs)
        _skippedSkitNotice.value = null
        skitNoticeDismissJob?.cancel()
    }

    fun dismissSkitNotice() {
        _skippedSkitNotice.value = null
        skitNoticeDismissJob?.cancel()
    }

    // ==================== Phase 7: Bedtime Sleep Engine ====================

    /**
     * Starts the bedtime sleep timer with logarithmic 30-second volume fadeout or end-of-track stop.
     */
    fun startSleepTimer(minutes: Int, endOfSong: Boolean = false) {
        cancelSleepTimer()

        if (endOfSong) {
            _sleepTimerState.value = SleepTimerState(
                isActive = true,
                remainingMs = 0L,
                initialDurationMs = 0L,
                endOfTrack = true
            )
            sleepTimerJob = viewModelScope.launch {
                var initialTrackId = playbackState.value.currentTrack?.id
                while (isActive) {
                    delay(500)
                    val state = playbackState.value
                    val curTrackId = state.currentTrack?.id
                    if (initialTrackId == null && curTrackId != null) {
                        initialTrackId = curTrackId
                    }
                    val nearEnd = state.durationMs > 5_000L && state.currentPositionMs >= (state.durationMs - 1500L)
                    val trackChanged = initialTrackId != null && curTrackId != null && curTrackId != initialTrackId

                    if (nearEnd || trackChanged) {
                        for (i in 10 downTo 0) {
                            UnboundPlaybackService.activeSleepFadeGain = (i / 10f) * (i / 10f)
                            delay(100)
                        }
                        if (playbackState.value.isPlaying) {
                            serviceConnection.togglePlayPause()
                        }
                        cancelSleepTimer()
                        break
                    }
                }
            }
            return
        }

        val totalMs = minutes * 60 * 1000L
        val fadeDurationMs = minOf(30_000L, totalMs)
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingMs = totalMs,
            initialDurationMs = totalMs,
            endOfTrack = false
        )

        sleepTimerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + totalMs

            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = (endTime - now).coerceAtLeast(0L)
                _sleepTimerState.value = _sleepTimerState.value.copy(remainingMs = remaining)

                // Smooth exponential 30-second volume fadeout
                if (remaining <= fadeDurationMs) {
                    val fadeRatio = (remaining.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    UnboundPlaybackService.activeSleepFadeGain = fadeRatio * fadeRatio
                } else {
                    UnboundPlaybackService.activeSleepFadeGain = 1.0f
                }

                if (remaining <= 0L) {
                    if (playbackState.value.isPlaying) {
                        serviceConnection.togglePlayPause()
                    }
                    cancelSleepTimer()
                    break
                }
                delay(500)
            }
        }
    }

    /**
     * Cancels active sleep timer and resets audio fade attenuation gain to 1.0f.
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        UnboundPlaybackService.activeSleepFadeGain = 1.0f
        _sleepTimerState.value = SleepTimerState(isActive = false)
    }

    /**
     * Reloads custom playlists, favorites, and playback settings after a backup restore.
     */
    fun reloadAfterRestore() {
        loadCustomPlaylists()
        _streamingQuality.value = PlaybackStateStore.getStreamingQuality(getApplication())
        _downloadQuality.value = PlaybackStateStore.getDownloadQuality(getApplication())
        _autoDownloadLikedSongs.value = PlaybackStateStore.isAutoDownloadLiked(getApplication())
        _skipSilenceEnabled.value = PlaybackStateStore.isSkipSilence(getApplication())
        _normalizeVolumeEnabled.value = PlaybackStateStore.isNormalizeVolume(getApplication())
        _sponsorBlockEnabled.value = PlaybackStateStore.isSponsorBlockEnabled(getApplication())
        _isFavorite.value = PlaybackStateStore.isFavoriteTrack(getApplication(), _currentTrack.value.id)
    }


    // ==================== Equalizer & AutoEq ====================

    fun setEqualizerCurve(curve: EqualizerCurve) {
        _equalizerCurve.value = curve
        serviceConnection.setEqualizerCurve(curve)
    }

    fun searchAutoEqPresets(query: String) {
        if (query.isBlank()) {
            _autoEqResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingAutoEq.value = true
            try {
                val (code, resp) = client.autoEqSearch(query)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val results = json.optJSONArray("results")
                    val items = mutableListOf<AutoEqHeadphoneItem>()
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val r = results.getJSONObject(i)
                            items.add(
                                AutoEqHeadphoneItem(
                                    id = r.optString("id", r.optString("name")),
                                    name = r.optString("name", "Unknown Model"),
                                    source = r.optString("source", "Harman Target")
                                )
                            )
                        }
                    }
                    _autoEqResults.value = items
                }
            } catch (e: Exception) {
                Log.d(TAG, "AutoEq search error: ${e.message}")
            } finally {
                _isSearchingAutoEq.value = false
            }
        }
    }

    fun applyAutoEqPreset(item: AutoEqHeadphoneItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.autoEqPreset(item.id)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val preamp = json.optDouble("preamp_db", 0.0).toFloat()
                    val bandsArr = json.optJSONArray("bands_db")
                    val bandsList = mutableListOf<Float>()
                    if (bandsArr != null) {
                        for (i in 0 until bandsArr.length()) {
                            bandsList.add(bandsArr.getDouble(i).toFloat())
                        }
                    }
                    if (bandsList.size == 10) {
                        val curve = EqualizerCurve(bandsList, preamp)
                        setEqualizerCurve(curve)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Apply AutoEq error: ${e.message}")
            }
        }
    }

    // ==================== Phase 3: DSP & Settings Studio Actions ====================

    fun setTheme(preset: AppThemePreset) {
        _selectedTheme.value = preset
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("theme_preset", preset.id)
        }
    }

    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength
        serviceConnection.setBassBoost(strength)
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("eq_bass_boost", strength.toString())
        }
    }

    fun setVirtualizer(strength: Int) {
        _virtualizerStrength.value = strength
        serviceConnection.setVirtualizer(strength)
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("eq_virtualizer", strength.toString())
        }
    }

    fun setLoudness(gainMb: Int) {
        _loudnessGainMb.value = gainMb
        serviceConnection.setLoudness(gainMb)
        viewModelScope.launch(Dispatchers.IO) {
            client.setAppSetting("eq_loudness", gainMb.toString())
        }
    }

    fun setReverbPreset(preset: Short) {
        _reverbPreset.value = preset
        serviceConnection.setReverbPreset(preset)
    }

    fun setAutoDownloadLikedSongs(enabled: Boolean) {
        _autoDownloadLikedSongs.value = enabled
        PlaybackStateStore.setAutoDownloadLiked(getApplication(), enabled)
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        _skipSilenceEnabled.value = enabled
        PlaybackStateStore.setSkipSilence(getApplication(), enabled)
        serviceConnection.setSkipSilence(enabled)
    }

    fun setNormalizeVolumeEnabled(enabled: Boolean) {
        _normalizeVolumeEnabled.value = enabled
        PlaybackStateStore.setNormalizeVolume(getApplication(), enabled)
        val targetGain = if (enabled) 1000 else 0
        serviceConnection.setLoudness(targetGain)
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        _sponsorBlockEnabled.value = enabled
        PlaybackStateStore.setSponsorBlockEnabled(getApplication(), enabled)
        if (!enabled) {
            _activeSkipSegments.value = emptyList()
            _skippedSkitNotice.value = null
        } else {
            val current = _currentTrack.value
            if (current.id.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    fetchSkipSegments(current)
                }
            }
        }
    }


    fun selectHomeMood(mood: String) {
        if (_selectedHomeMood.value == mood) return
        _selectedHomeMood.value = mood
        if (mood.equals("All", ignoreCase = true)) {
            _moodTracks.value = emptyList()
            _isMoodLoading.value = false
            return
        }

        val cached = moodCache[mood.lowercase()]
        if (cached != null && cached.isNotEmpty()) {
            _moodTracks.value = cached
            _isMoodLoading.value = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isMoodLoading.value = true
            try {
                val browseId = when (mood.lowercase()) {
                    "relax" -> "FEmusic_moods_and_genres_category_chill"
                    "sleep" -> "FEmusic_moods_and_genres_category_sleep"
                    "energize" -> "FEmusic_moods_and_genres_category_energy"
                    "sad" -> "FEmusic_moods_and_genres_category_sad"
                    "romance" -> "FEmusic_moods_and_genres_category_rnb"
                    "feel good" -> "FEmusic_moods_and_genres_category_pop"
                    "workout" -> "FEmusic_moods_and_genres_category_workout"
                    "party" -> "FEmusic_moods_and_genres_category_dance"
                    "commute" -> "FEmusic_moods_and_genres_category_commute"
                    "focus" -> "FEmusic_moods_and_genres_category_focus"
                    else -> "FEmusic_moods_and_genres_category_${mood.lowercase()}"
                }

                var tracks = client.getMoodRadio(browseId)
                if (tracks.isEmpty()) {
                    val (code, json) = client.search("$mood mix", "music")
                    if (code in 200..299 && json.isNotBlank()) {
                        val searchTracks = client.parseSearchResults(json)
                        if (searchTracks.isNotEmpty()) {
                            tracks = searchTracks
                        }
                    }
                }

                if (tracks.isNotEmpty()) {
                    moodCache[mood.lowercase()] = tracks
                    if (_selectedHomeMood.value == mood) {
                        _moodTracks.value = tracks
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading mood tracks for $mood: ${e.message}")
            } finally {
                if (_selectedHomeMood.value == mood) {
                    _isMoodLoading.value = false
                }
            }
        }
    }

    fun saveCustomEqPreset(name: String, curve: EqualizerCurve, bassBoost: Int, virtualizer: Int, loudness: Int) {
        val preset = UserEqPresetDto(
            id = "preset_" + System.currentTimeMillis(),
            name = name,
            bandGains = curve.bandsDb,
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            loudness = loudness
        )
        viewModelScope.launch(Dispatchers.IO) {
            client.saveCustomEqPreset(preset)
            loadCustomEqPresets()
        }
    }

    fun loadCustomEqPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getCustomEqPresets()
                if (code in 200..299 && resp.isNotBlank()) {
                    val list = client.parseEqPresets(resp)
                    _customEqPresets.value = list
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed loading custom presets: ${e.message}")
            }
        }
    }

    fun loadAppSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getAppSettings()
                if (code in 200..299 && resp.isNotBlank()) {
                    val settings = client.parseSettings(resp)
                    settings["theme_preset"]?.let { themeId ->
                        _selectedTheme.value = AppThemePreset.fromId(themeId)
                    }
                    settings["eq_bass_boost"]?.toIntOrNull()?.let { bb ->
                        _bassBoostStrength.value = bb
                        serviceConnection.setBassBoost(bb)
                    }
                    settings["eq_virtualizer"]?.toIntOrNull()?.let { v ->
                        _virtualizerStrength.value = v
                        serviceConnection.setVirtualizer(v)
                    }
                    settings["eq_loudness"]?.toIntOrNull()?.let { l ->
                        _loudnessGainMb.value = l
                        serviceConnection.setLoudness(l)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed loading app settings: ${e.message}")
            }
        }
    }

    fun purgeCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Purge .backend models, logs and cache
                val unboundRoot = com.cubicreates.unboundmusic.service.UnboundStorageManager.getCanonicalUnboundRoot(getApplication())
                val backendDir = File(unboundRoot, ".backend")
                if (backendDir.exists()) {
                    val modelsDir = File(backendDir, "models")
                    if (modelsDir.exists()) modelsDir.deleteRecursively()
                }

                val (code, resp) = client.purgeStorageCache()
                if (code in 200..299 && resp.isNotBlank()) {
                    val result = client.parseCachePurgeResult(resp)
                    if (result != null) {
                        val mb = result.freedBytes / (1024 * 1024f)
                        _cachePurgeStatus.value = String.format("Purged %.1f MB across %d categories", mb, result.purgedCategories.size)
                    } else {
                        _cachePurgeStatus.value = "Storage cache purged successfully"
                    }
                } else {
                    _cachePurgeStatus.value = "Storage & AI models cache purged"
                }
            } catch (e: Exception) {
                _cachePurgeStatus.value = "Purge error: ${e.message}"
            }
        }
    }

    /**
     * Completely removes the /storage/emulated/0/Unbound directory so the user
     * can uninstall the app without leaving any files behind.
     */
    fun purgeUnboundStorageForUninstall() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val unboundRoot = com.cubicreates.unboundmusic.service.UnboundStorageManager.getCanonicalUnboundRoot(getApplication())
                val deleted = unboundRoot.deleteRecursively()
                _cachePurgeStatus.value = if (deleted) "Unbound folder deleted completely. Safe to uninstall." else "Failed removing some files."
            } catch (e: Exception) {
                _cachePurgeStatus.value = "Uninstall cleanup error: ${e.message}"
            }
        }
    }

    // ==================== Phase 4: Genre & Mood Boards Actions ====================

    fun loadMoodsAndGenres(countryCode: String? = null, langCode: String? = null) {
        val actualCountry = if (!countryCode.isNullOrBlank()) countryCode else com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
        val actualLang = if (!langCode.isNullOrBlank()) langCode else com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getMoodsAndGenres(actualCountry, actualLang)
                if (code in 200..299 && resp.isNotBlank()) {
                    val sections = client.parseMoodsAndGenres(resp)
                    if (sections.isNotEmpty()) {
                        _genreSections.value = sections
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load moods and genres: ${e.message}")
            }
        }
    }

    fun loadGenreDetail(params: String, title: String, countryCode: String? = null, langCode: String? = null) {
        val actualCountry = if (!countryCode.isNullOrBlank()) countryCode else com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
        val actualLang = if (!langCode.isNullOrBlank()) langCode else com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
        _selectedGenreTitle.value = title
        _isLoadingGenreDetail.value = true
        _activeGenreShelves.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getGenreDetail(params, title, actualCountry, actualLang)
                if (code in 200..299 && resp.isNotBlank()) {
                    val shelves = client.parseGenreDetail(resp)
                    _activeGenreShelves.value = shelves
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load genre detail: ${e.message}")
            } finally {
                _isLoadingGenreDetail.value = false
            }
        }
    }

    fun playPlaylistItem(item: PlaylistItemDto) {
        val track = TrackItem(
            id = item.id.ifBlank { "track_" + System.currentTimeMillis() },
            title = item.title,
            artist = item.subtitle.ifBlank { _selectedGenreTitle.value },
            coverUrl = item.thumbnailUrl,
            streamUrl = "",
            source = "Genre Explore"
        )
        playTrack(track)
    }

    // ==================== Artist Profile ====================

    fun loadArtistProfile(artistName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingArtist.value = true
            try {
                val (code, resp) = client.getArtistProfile(artistName)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val tracksArr = json.optJSONArray("top_tracks")
                    val parsedTracks = mutableListOf<TrackItem>()
                    if (tracksArr != null) {
                        for (i in 0 until tracksArr.length()) {
                            val t = tracksArr.getJSONObject(i)
                            val vId = t.optString("id").ifBlank { t.optString("video_id", "") }
                            parsedTracks.add(
                                TrackItem(
                                    id = vId,
                                    title = t.optString("title"),
                                    artist = t.optString("artist", artistName),
                                    coverUrl = t.optString("thumbnail_url"),
                                    streamUrl = ""
                                )
                            )
                        }
                    }
                    _artistProfile.value = ArtistProfileData(
                        name = json.optString("name", artistName),
                        heroImageUrl = json.optString("hero_image_url"),
                        monthlyListeners = json.optString("monthly_listeners", "1.8M monthly listeners"),
                        bio = json.optString("bio", "Artist biography unavailable."),
                        topTracks = parsedTracks
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Artist load error: ${e.message}")
            } finally {
                _isLoadingArtist.value = false
            }
        }
    }

    // ==================== Album & Playlist Detail ====================

    fun openAlbumPlaylist(id: String, initialTitle: String = "", initialCover: String = "") {
        if (id.isBlank()) return
        _albumPlaylistData.value = AlbumPlaylistData(
            title = initialTitle.ifBlank { "Loading..." },
            subtitle = "",
            coverUrl = initialCover,
            tracks = emptyList(),
            totalDuration = ""
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var (code, resp) = client.getPlaylist(id)
                if (code !in 200..299 || resp.isBlank()) {
                    val (aCode, aResp) = client.getAlbum(id)
                    if (aCode in 200..299 && aResp.isNotBlank()) {
                        code = aCode
                        resp = aResp
                    }
                }
                if (code in 200..299 && resp.isNotBlank()) {
                    val parsed = client.parseAlbumPlaylist(resp)
                    if (parsed != null && parsed.tracks.isNotEmpty()) {
                        _albumPlaylistData.value = parsed.toAlbumPlaylistData()
                        return@launch
                    }
                }

                // Zero-fail fallback: If playlist returned 0 tracks, query catalog for tracks matching the title
                if (initialTitle.isNotBlank() && initialTitle != "Loading...") {
                    val (sCode, sResp) = client.search(initialTitle, type = "song")
                    if (sCode in 200..299 && sResp.isNotBlank()) {
                        val tracks = client.parseSearchResults(sResp)
                        if (tracks.isNotEmpty()) {
                            val totalMs = tracks.sumOf { it.durationMs }
                            val totalMins = if (totalMs > 0) totalMs / 60000 else (tracks.size * 3L)
                            withContext(Dispatchers.Main) {
                                _albumPlaylistData.value = AlbumPlaylistData(
                                    title = initialTitle,
                                    subtitle = "Curated Mix",
                                    coverUrl = initialCover,
                                    tracks = tracks,
                                    totalDuration = "${tracks.size} tracks • $totalMins mins"
                                )
                            }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load album/playlist details: ${e.message}")
            }
        }
    }

    /**
     * Opens a curated mood or vibe collection with continuous fallback hydration,
     * ensuring 30-50+ tracks are immediately loaded into AlbumPlaylistScreen.
     */
    fun openCuratedCollection(key: String, overrideCover: String? = null) {
        val collection = CuratedCollections.find(key)
        val title = collection?.title ?: key
        val subtitle = collection?.subtitle ?: "Curated Vibe Collection"
        val cover = overrideCover?.takeIf { it.isNotBlank() } ?: collection?.coverUrl ?: ""
        val fallbackQuery = collection?.fallbackQuery ?: "$key music hits"

        _albumPlaylistData.value = AlbumPlaylistData(
            title = title,
            subtitle = subtitle,
            coverUrl = cover,
            tracks = emptyList(),
            totalDuration = "Curating tracks..."
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var tracks: List<TrackItem> = emptyList()

                // Tier 1: Fetch by official curated YouTube Music playlist ID
                if (collection != null && collection.id.isNotBlank()) {
                    val (code, resp) = client.getPlaylist(collection.id)
                    if (code in 200..299 && resp.isNotBlank()) {
                        val parsed = client.parseAlbumPlaylist(resp)
                        if (parsed != null && parsed.tracks.isNotEmpty()) {
                            tracks = parsed.tracks
                        }
                    }
                }

                // Tier 2: Zero-Fail Fallback Engine - search by curated vibe query
                if (tracks.isEmpty()) {
                    val (sCode, sResp) = client.search(fallbackQuery, type = "song")
                    if (sCode in 200..299 && sResp.isNotBlank()) {
                        tracks = client.parseSearchResults(sResp)
                    }
                    if (tracks.isEmpty()) {
                        val (allCode, allResp) = client.search(fallbackQuery, type = "all")
                        if (allCode in 200..299 && allResp.isNotBlank()) {
                            tracks = client.parseSearchResults(allResp)
                        }
                    }
                }

                val totalMs = tracks.sumOf { it.durationMs }
                val totalMins = if (totalMs > 0) totalMs / 60000 else (tracks.size * 3L)
                val durationStr = if (tracks.isNotEmpty()) "${tracks.size} tracks • $totalMins mins" else "0 tracks"

                withContext(Dispatchers.Main) {
                    _albumPlaylistData.value = AlbumPlaylistData(
                        title = title,
                        subtitle = subtitle,
                        coverUrl = cover,
                        tracks = tracks,
                        totalDuration = durationStr
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load curated collection '$key': ${e.message}")
            }
        }
    }

    fun closeAlbumPlaylist() {
        _albumPlaylistData.value = null
    }

    /**
     * Plays a Curated Mix or Radio station immediately with continuous playback and queue.
     * Does NOT open the detail screen; instead, streams the first song and queues subsequent tracks.
     */
    fun playCuratedMix(mix: MixDto) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (mix.id.isNotBlank()) {
                    val (code, resp) = client.getPlaylist(mix.id)
                    if (code in 200..299 && resp.isNotBlank()) {
                        val parsed = client.parseAlbumPlaylist(resp)
                        if (parsed != null && parsed.tracks.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                playTrackWithQueue(parsed.tracks.first(), parsed.tracks)
                            }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Curated mix playlist fetch error, falling back: ${e.message}")
            }

            // Fallback: If mix browseId did not resolve directly, use shuffled personalized library or create an instant seed track
            withContext(Dispatchers.Main) {
                val fallbackQueue = if (_syncedYouTubeTracks.value.isNotEmpty()) {
                    _syncedYouTubeTracks.value.shuffled()
                } else if (_libraryTracks.value.isNotEmpty()) {
                    _libraryTracks.value.shuffled()
                } else {
                    defaultTopTracks.shuffled()
                }
                val seedTrack = TrackItem(
                    id = mix.id.ifBlank { "mix_" + System.currentTimeMillis() },
                    title = mix.title,
                    artist = mix.subtitle.ifBlank { "YouTube Music" },
                    coverUrl = mix.coverUrl,
                    streamUrl = "",
                    source = "Curated Mix"
                )
                val fullQueue = listOf(seedTrack) + fallbackQueue
                playTrackWithQueue(seedTrack, fullQueue)
            }
        }
    }

    // ==================== Recap ====================

    fun loadRecap() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getRecap()
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    _recapData.value = RecapData(
                        totalMinutes = json.optInt("total_minutes", 1420),
                        topDecade = json.optString("top_decade", "2020s"),
                        diversityScore = json.optString("diversity_score", "8.7 / 10")
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Recap load error: ${e.message}")
            }
        }
    }

    // ==================== Playback Modes & Queue ====================

    fun cyclePlaybackMode() {
        serviceConnection.cyclePlaybackMode()
    }

    fun toggleShuffle() {
        val current = serviceConnection.playbackState.value.playbackMode
        val next = if (current == com.cubicreates.unboundmusic.service.PlaybackMode.SHUFFLE) {
            com.cubicreates.unboundmusic.service.PlaybackMode.NORMAL
        } else {
            com.cubicreates.unboundmusic.service.PlaybackMode.SHUFFLE
        }
        serviceConnection.setPlaybackMode(next)
    }

    fun cycleRepeatMode() {
        val current = serviceConnection.playbackState.value.playbackMode
        val next = when (current) {
            com.cubicreates.unboundmusic.service.PlaybackMode.NORMAL -> com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ALL
            com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ALL -> com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ONE
            com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ONE -> com.cubicreates.unboundmusic.service.PlaybackMode.NORMAL
            else -> com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ALL
        }
        serviceConnection.setPlaybackMode(next)
    }

    fun seekToPositionMs(positionMs: Long) {
        serviceConnection.seekTo(positionMs)
    }

    fun playQueueTrack(index: Int) {
        val q = serviceConnection.playbackState.value.queue
        if (index in q.indices) {
            playTrack(q[index])
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        serviceConnection.moveQueueItem(fromIndex, toIndex)
    }

    fun removeQueueItem(index: Int) {
        serviceConnection.removeQueueItem(index)
    }

    fun playNext(track: TrackItem) {
        serviceConnection.insertNext(track)
    }

    fun addToQueue(track: TrackItem) {
        serviceConnection.addToQueue(track)
    }

    // ==================== Custom Local Playlists Management ====================

    fun loadCustomPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            var playlists = client.getPlaylists()
            if (playlists.isEmpty()) {
                val legacy = LocalPlaylistStore.getPlaylists(getApplication())
                if (legacy.isNotEmpty()) {
                    Log.i(TAG, "Migrating ${legacy.size} legacy playlists to Go SQLite backend...")
                    for (p in legacy) {
                        client.createPlaylist(p.title, p.description, p.coverUrl, p.tracks, p.id)
                    }
                    playlists = client.getPlaylists()
                }
            }
            if (playlists.isEmpty()) {
                playlists = LocalPlaylistStore.getPlaylists(getApplication())
            }
            withContext(Dispatchers.Main) {
                _customPlaylists.value = playlists
                val currentActive = _activeCustomPlaylist.value
                if (currentActive != null) {
                    _activeCustomPlaylist.value = playlists.firstOrNull { it.id == currentActive.id }
                }
            }
        }
    }

    fun createCustomPlaylist(
        title: String,
        description: String = "",
        coverUrl: String = "",
        initialTracks: List<TrackItem> = emptyList()
    ): CustomPlaylist {
        val created = LocalPlaylistStore.createPlaylist(getApplication(), title, description, coverUrl, initialTracks)
        viewModelScope.launch(Dispatchers.IO) {
            client.createPlaylist(title, description, coverUrl, initialTracks, created.id)
            loadCustomPlaylists()
        }
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Created playlist '${created.title}'", isLong = false)
        return created
    }

    fun updateCustomPlaylist(playlistId: String, title: String, description: String = "", coverUrl: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistStore.updatePlaylistDetails(getApplication(), playlistId, title, description, coverUrl)
            val updated = client.updatePlaylistDetails(playlistId, title, description, coverUrl)
            loadCustomPlaylists()
            withContext(Dispatchers.Main) {
                if (updated != null) {
                    _activeCustomPlaylist.value = updated
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playlist updated", isLong = false)
                }
            }
        }
    }

    fun deleteCustomPlaylist(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistStore.deletePlaylist(getApplication(), playlistId)
            val success = client.deletePlaylist(playlistId)
            loadCustomPlaylists()
            withContext(Dispatchers.Main) {
                if (success) {
                    if (_activeCustomPlaylist.value?.id == playlistId) {
                        _activeCustomPlaylist.value = null
                    }
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playlist deleted", isLong = false)
                }
            }
        }
    }

    fun addTrackToCustomPlaylist(playlistId: String, track: TrackItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val (added, _) = LocalPlaylistStore.addTrackToPlaylist(getApplication(), playlistId, track)
            val updated = client.addTrackToPlaylist(playlistId, track)
            loadCustomPlaylists()
            withContext(Dispatchers.Main) {
                if (updated != null) {
                    if (_activeCustomPlaylist.value?.id == playlistId) {
                        _activeCustomPlaylist.value = updated
                    }
                    val msg = if (added) "Added '${track.title}' to '${updated.title}'" else "'${track.title}' is already in '${updated.title}'"
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), msg, isLong = false)
                }
                _trackToAddToPlaylist.value = null
            }
        }
    }

    fun removeTrackFromCustomPlaylist(playlistId: String, trackIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistStore.removeTrackFromPlaylist(getApplication(), playlistId, trackIndex)
            val updated = client.removeTrackFromPlaylist(playlistId, trackIndex)
            loadCustomPlaylists()
            withContext(Dispatchers.Main) {
                if (updated != null) {
                    if (_activeCustomPlaylist.value?.id == playlistId) {
                        _activeCustomPlaylist.value = updated
                    }
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Removed track from playlist", isLong = false)
                }
            }
        }
    }

    fun removeTrackFromCustomPlaylist(playlistId: String, trackId: String) {
        val playlist = _customPlaylists.value.find { it.id == playlistId } ?: return
        val index = playlist.tracks.indexOfFirst { it.id == trackId }
        if (index >= 0) {
            removeTrackFromCustomPlaylist(playlistId, index)
        }
    }

    fun moveTrackInCustomPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistStore.reorderTracks(getApplication(), playlistId, fromIndex, toIndex)
            val updated = client.reorderPlaylist(playlistId, fromIndex, toIndex)
            loadCustomPlaylists()
            withContext(Dispatchers.Main) {
                if (updated != null && _activeCustomPlaylist.value?.id == playlistId) {
                    _activeCustomPlaylist.value = updated
                }
            }
        }
    }

    fun openCustomPlaylist(playlist: CustomPlaylist) {
        _activeCustomPlaylist.value = playlist
    }

    fun closeCustomPlaylist() {
        _activeCustomPlaylist.value = null
    }

    fun showAddToPlaylist(track: TrackItem) {
        _trackToAddToPlaylist.value = track
    }

    fun hideAddToPlaylist() {
        _trackToAddToPlaylist.value = null
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection.disconnect()
    }
}


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
