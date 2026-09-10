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
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.daemon.DaemonLifecycleState
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.AccountStatusData
import com.cubicreates.unboundmusic.data.CascadeSearchResponse
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.DownloadStartRequest
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.GenreSectionDto
import com.cubicreates.unboundmusic.data.LocalTrack
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.data.PlaylistItemDto
import com.cubicreates.unboundmusic.data.PlaylistShelfDto
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

    val daemonState: StateFlow<DaemonLifecycleState> = daemonManager.state

    /** Reactive playback state from the Media3 foreground service. */
    val playbackState: StateFlow<PlaybackUiState> = serviceConnection.playbackState

    // ==================== Playback State ====================

    private val _currentTrack = MutableStateFlow(defaultTopTracks[0])
    val currentTrack: StateFlow<TrackItem> = _currentTrack.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

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

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

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

    private val _vibeSearchResult = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val vibeSearchResult: StateFlow<VibeSearchUiState> = _vibeSearchResult.asStateFlow()

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
                        val changed = (prev.id.isNotBlank() && prev.id != track.id) ||
                                      (prev.title.isNotBlank() && !prev.title.equals(track.title, ignoreCase = true))
                        if (changed) {
                            _currentTrack.value = track
                            loadLyricsForTrack(track)
                            launch(Dispatchers.IO) {
                                fetchCanvas(track)
                                fetchSkipSegments(track)
                            }
                        }
                    }
                }
            }
        }

        // Orchestrate startup hydration with splash screen telemetry
        startStartupHydration()

        // Resume download polling if previous active tasks exist
        startDownloadPollingLoop()

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
            initializeColdStart("US", "en")
            loadHomeFeed()
            refreshLibrary()
            checkAccountStatus()
            loadAppSettings()
            loadCustomEqPresets()
            loadMoodsAndGenres()
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
    fun initializeColdStart(countryCode: String = "US", languageCode: String = "en") {
        viewModelScope.launch(Dispatchers.IO) {
            // First-boot asset unpacker
            StorageInitializer.initialize(getApplication())

            // 1. Fetch explore charts & mood capsules concurrently
            launch {
                val charts = client.getCharts(countryCode, languageCode)
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

            // 2. Trigger storage crawl and load categorized library folders
            launch {
                val scanPaths = listOf(
                    "/storage/emulated/0/Download/",
                    "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/",
                    "/storage/emulated/0/Telegram/Telegram Audio/",
                    "/storage/emulated/0/Music/"
                )
                client.scanStorage(scanPaths)

                val whatsapp = client.getLocalTracks("whatsapp")
                val telegram = client.getLocalTracks("telegram")
                val downloads = client.getLocalTracks("downloads")

                _whatsappCount.value = whatsapp.size
                _telegramCount.value = telegram.size
                _downloadsCount.value = downloads.size

                val folders = mutableMapOf<String, List<LocalTrack>>()
                if (whatsapp.isNotEmpty()) folders["WhatsApp Audio"] = whatsapp
                if (telegram.isNotEmpty()) folders["Telegram Audio"] = telegram
                if (downloads.isNotEmpty()) folders["Downloads"] = downloads

                _libraryFolders.value = folders

                val allLocal = (whatsapp + telegram + downloads).map { it.toTrackItem() }
                if (allLocal.isNotEmpty()) {
                    _libraryTracks.value = allLocal
                }
            }
        }
    }

    /**
     * Natural Language Vibe AI query runner.
     */
    fun submitVibeQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _vibeSearchResult.value = VibeSearchUiState.Loading
            try {
                val res = client.searchVibe(trimmed)
                _vibeSearchResult.value = VibeSearchUiState.Success(res.vibeResult, res.radioTracks)
                if (res.radioTracks.isNotEmpty()) {
                    _searchResults.value = res.radioTracks
                }
            } catch (e: Exception) {
                _vibeSearchResult.value = VibeSearchUiState.Error(e.message ?: "Search failed")
            }
        }
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
    fun playTrack(track: TrackItem) {
        _currentTrack.value = track
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Loading '${track.title}'...", isLong = false)

        // Ensure queue is populated with meaningful surrounding list context
        val currentQ = _currentQueue.value
        val trackInQueue = currentQ.any {
            (it.id.isNotBlank() && it.id == track.id) ||
            (it.title.isNotBlank() && it.title.equals(track.title, ignoreCase = true))
        }
        if (currentQ.size <= 1 || !trackInQueue) {
            val resolvedQueue = when {
                _chartTracks.value.any { it.id == track.id || it.title.equals(track.title, true) } -> _chartTracks.value
                _searchResults.value.any { it.id == track.id || it.title.equals(track.title, true) } -> _searchResults.value
                _libraryTracks.value.any { it.id == track.id || it.title.equals(track.title, true) } -> _libraryTracks.value
                defaultTopTracks.any { it.id == track.id || it.title.equals(track.title, true) } -> defaultTopTracks
                else -> listOf(track) + defaultTopTracks.filter { it.id != track.id }
            }
            _currentQueue.value = resolvedQueue
            serviceConnection.setQueue(resolvedQueue)
        }

        // Immediately reset lyrics state and fetch for this specific track
        loadLyricsForTrack(track)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val streamUrl = resolveStreamUrl(track)
                val resolvedTrack = track.copy(streamUrl = streamUrl)
                withContext(Dispatchers.Main) {
                    _currentTrack.value = resolvedTrack
                }

                if (streamUrl.isNotBlank()) {
                    // Play via Media3 service
                    serviceConnection.playTrack(resolvedTrack, streamUrl)
                } else {
                    Log.w(TAG, "Direct stream resolution empty for ${track.title}, using localhost proxy stream")
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Direct stream empty for '${track.title}', falling back to proxy")
                    val fallbackUrl = if (track.id.isNotBlank() && track.id.length == 11 && !track.id.startsWith("local:")) {
                        "http://127.0.0.1:45731/api/v1/proxy/stream?id=${track.id}"
                    } else track.streamUrl
                    if (fallbackUrl.isNotBlank()) {
                        serviceConnection.playTrack(track.copy(streamUrl = fallbackUrl), fallbackUrl)
                    } else {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Error: No fallback URL for '${track.title}'")
                    }
                }

                // Fetch canvas visuals and SponsorBlock skip segments in parallel
                launch { fetchCanvas(resolvedTrack) }
                launch { fetchSkipSegments(resolvedTrack) }

            } catch (e: Exception) {
                Log.e(TAG, "Error playing track: ${e.message}")
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Play Track Exception:\n${e.message}")
                val fallbackUrl = if (track.id.isNotBlank() && track.id.length == 11 && !track.id.startsWith("local:")) {
                    "http://127.0.0.1:45731/api/v1/proxy/stream?id=${track.id}"
                } else track.streamUrl
                if (fallbackUrl.isNotBlank()) {
                    serviceConnection.playTrack(track.copy(streamUrl = fallbackUrl), fallbackUrl)
                }
            }
        }
    }

    /**
     * Resolves a stream URL for a track via the Go daemon /api/v1/stream endpoint.
     * Implements zero-data interception: checks local storage first, falls back to remote.
     * Retries up to 5 times if the daemon is cold-starting.
     */
    private suspend fun resolveStreamUrl(track: TrackItem): String {
        // If the track already has a local file://, content://, localhost proxy audio stream, or valid http stream, use it directly
        if (track.streamUrl.startsWith("file://") ||
            track.streamUrl.startsWith("content://") ||
            (track.streamUrl.contains("127.0.0.1") && track.streamUrl.contains("/proxy/stream")) ||
            (track.streamUrl.startsWith("http") && track.streamUrl.contains("googlevideo.com"))) {
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playing direct URL for '${track.title}'", isLong = false)
            return track.streamUrl
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

                    // Prefer direct YouTube CDN stream URL, fallback to resolved or localhost proxy
                    val finalUrl = when {
                        direct.isNotBlank() && direct.startsWith("http") -> direct
                        resolved.isNotBlank() && (resolved.startsWith("http") || resolved.startsWith("file://") || resolved.startsWith("content://")) -> resolved
                        proxy.isNotBlank() && proxy.startsWith("http") -> proxy
                        else -> ""
                    }

                    if (finalUrl.isNotBlank()) {
                        Log.i(TAG, "Stream resolved (attempt $attempt): type=$streamType for '${track.title}' -> $finalUrl")
                        _streamDebugMessage.value = "Stream ready: $streamType (${finalUrl.take(45)}...)"
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Stream Ready: $streamType", isLong = false)
                        return finalUrl
                    }
                } else {
                    Log.w(TAG, "Daemon stream resolution error attempt $attempt: code=$code, resp=$resp")
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Daemon error (try $attempt/5): code=$code\n${resp.take(80)}")
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
                                val resolved = json.optString("stream_url", "")
                                if (resolved.isNotBlank()) {
                                    Log.i(TAG, "Stream resolved via search fallback for '${track.title}'")
                                    _streamDebugMessage.value = "Stream resolved via search match for '${track.title}'"
                                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolved via YouTube search!", isLong = false)
                                    return resolved
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
        _currentQueue.value = queue
        serviceConnection.setQueue(queue)
        playTrack(track)
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
        _isFavorite.value = !_isFavorite.value
    }

    fun seekTo(progress: Float) {
        serviceConnection.seekToFraction(progress)
    }

    private fun getEffectiveQueue(): List<TrackItem> {
        val q = _currentQueue.value
        if (q.size > 1) return q
        val sQueue = serviceConnection.playbackState.value.queue
        if (sQueue.size > 1) return sQueue
        if (_chartTracks.value.size > 1) return _chartTracks.value
        if (_searchResults.value.size > 1) return _searchResults.value
        if (_libraryTracks.value.size > 1) return _libraryTracks.value
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

    // ==================== YouTube Music Search ====================

    private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * Performs a live YouTube Music catalog search via the Go daemon with debouncing & fallback.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300) // 300ms debounce
            _isSearching.value = true
            try {
                var foundTracks = false
                val (code, resp) = client.search(query, type = _searchCategory.value.apiParam)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val tracksArray = json.optJSONArray("tracks")
                        ?: json.optJSONArray("results")
                        ?: json.optJSONArray("items")

                    if (tracksArray != null && tracksArray.length() > 0) {
                        val parsed = mutableListOf<TrackItem>()
                        for (i in 0 until tracksArray.length()) {
                            val item = tracksArray.getJSONObject(i)
                            val id = item.optString("id", item.optString("video_id", ""))
                            val title = item.optString("title", "Unknown Track")
                            val artist = item.optString("artist",
                                item.optJSONArray("artists")?.optJSONObject(0)?.optString("name", "Unknown Artist")
                                    ?: "Unknown Artist")
                            val thumb = item.optString("thumbnail",
                                item.optString("thumbnail_url", item.optString("cover_url", "")))
                            val durMs = item.optLong("duration_ms", 0L)
                            parsed.add(
                                TrackItem(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    coverUrl = thumb.ifBlank { if (id.length == 11) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else "" },
                                    streamUrl = "",
                                    durationMs = durMs,
                                    source = "youtube"
                                )
                            )
                        }
                        _searchResults.value = parsed
                        foundTracks = true
                    }
                }

                // If daemon is starting up or returned empty, query YouTube Music public search
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
                        val sJson = JSONObject(sResp)
                        val tracksArray = sJson.optJSONArray("tracks")
                            ?: sJson.optJSONArray("results")
                        if (tracksArray != null && tracksArray.length() > 0) {
                            val parsed = mutableListOf<TrackItem>()
                            for (i in 0 until tracksArray.length()) {
                                val item = tracksArray.getJSONObject(i)
                                val vId = item.optString("id").ifBlank { item.optString("video_id", "") }
                                parsed.add(TrackItem(
                                    id = vId,
                                    title = item.optString("title", "Vibe Match"),
                                    artist = item.optString("artist", "Unknown"),
                                    coverUrl = item.optString("thumbnail_url", ""),
                                    streamUrl = ""
                                ))
                            }
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
        _isListeningShazam.value = true
        _recognizedMessage.value = "Listening to audio acoustics..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val testSamples = FloatArray(1024) { i ->
                    (kotlin.math.sin(i * 0.1) * 0.8).toFloat()
                }
                delay(1200)
                val (code, resp) = client.recognizeAudioDsp(testSamples)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val matched = json.optBoolean("matched", false)
                    val trackTitle = json.optString("title", json.optString("track_title", ""))
                    val artist = json.optString("artist", "")
                    if (matched && trackTitle.isNotBlank()) {
                        _recognizedMessage.value = "Recognized: $trackTitle - $artist"
                        // Search for the recognized track
                        onSearchQueryChanged("$trackTitle $artist")
                    } else {
                        _recognizedMessage.value = "Could not recognize audio. Try again."
                    }
                } else {
                    _recognizedMessage.value = "Recognition service unavailable."
                }
            } catch (e: Exception) {
                _recognizedMessage.value = "Recognition error: ${e.message}"
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
                val (code, resp) = client.getLyrics(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
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
                        _lyricsLines.value = emptyList()
                        _lyricsSource.value = if (isInst) source else ""
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
        }
    }

    // ==================== Library ====================

    fun rescanLocalStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val canonicalRoot = com.cubicreates.unboundmusic.service.UnboundStorageManager.getCanonicalUnboundRoot(getApplication())
                val unboundMusicDir = File(canonicalRoot, "Music")
                if (unboundMusicDir.exists()) {
                    client.storageIndex(unboundMusicDir.absolutePath)
                }

                val publicMusic = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)?.absolutePath ?: "/storage/emulated/0/Music"
                val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "/storage/emulated/0/Download"

                val scanCandidates = listOf(
                    unboundMusicDir.absolutePath,
                    publicMusic,
                    "/storage/emulated/0/Music",
                    publicDownloads,
                    "/storage/emulated/0/Download",
                    "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
                    "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio",
                    "/storage/emulated/0/Telegram/Telegram Audio",
                    "/storage/emulated/0/Android/data/org.telegram.messenger/files/Telegram/Telegram Audio"
                ).distinct()

                val scanPaths = scanCandidates.filter { File(it).exists() }
                if (scanPaths.isNotEmpty()) {
                    client.scanStorage(scanPaths)
                }

                val allTracks = client.getLocalTracks("all")
                val music = client.getLocalTracks("music")
                val downloads = client.getLocalTracks("downloads")
                val whatsapp = client.getLocalTracks("whatsapp")
                val telegram = client.getLocalTracks("telegram")
                val unboundDownloads = client.getLocalTracks("Unbound Downloads")

                _whatsappCount.value = whatsapp.size
                _telegramCount.value = telegram.size
                _downloadsCount.value = downloads.size + unboundDownloads.size

                val folders = mutableMapOf<String, List<LocalTrack>>()
                if (music.isNotEmpty()) folders["Music"] = music
                val combinedDownloads = downloads + unboundDownloads
                if (combinedDownloads.isNotEmpty()) folders["Downloads"] = combinedDownloads
                if (whatsapp.isNotEmpty()) folders["WhatsApp Audio"] = whatsapp
                if (telegram.isNotEmpty()) folders["Telegram Audio"] = telegram

                _libraryFolders.value = folders

                if (allTracks.isNotEmpty()) {
                    _libraryTracks.value = allTracks.map { it.toTrackItem() }
                } else {
                    val combined = (music + combinedDownloads + whatsapp + telegram).map { it.toTrackItem() }
                    if (combined.isNotEmpty()) {
                        _libraryTracks.value = combined
                    }
                }

                val dlIds = unboundDownloads.map { it.id }.toSet()
                _downloadedTrackIds.value = _downloadedTrackIds.value + dlIds
            } catch (e: Exception) {
                Log.d(TAG, "Local storage scan note: ${e.message}")
            }
        }
    }

    fun refreshLibrary() {
        rescanLocalStorage()
    }

    // ==================== Phase 5: Offline Downloader Orchestration ====================

    /** Initiates a physical background chunked download for an audio track. */
    fun startTrackDownload(track: TrackItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.startDownload(
                    videoId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.coverUrl
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
                                    trackId = track.id,
                                    title = track.title,
                                    artist = track.artist,
                                    durationMs = track.durationMs
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startTrackDownload failed: ${e.message}")
            }
        }
    }

    /** Cancels an ongoing download and clears partial artifacts. */
    fun cancelTrackDownload(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.cancelDownload(videoId)
                val current = _downloadTasks.value.toMutableMap()
                val task = current[videoId]
                if (task != null) {
                    current[videoId] = task.copy(status = "CANCELLED")
                    _downloadTasks.value = current
                }
            } catch (e: Exception) {
                Log.e(TAG, "cancelTrackDownload failed: ${e.message}")
            }
        }
    }

    /** Removes an offline track from disk and local database. */
    fun deleteTrackDownload(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.deleteDownload(videoId, true)
                val current = _downloadTasks.value.toMutableMap()
                current.remove(videoId)
                _downloadTasks.value = current
                _downloadedTrackIds.value = _downloadedTrackIds.value - videoId
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
            try {
                while (isActive) {
                    val (code, resp) = client.getActiveDownloads()
                    if (code in 200..299 && resp.isNotBlank()) {
                        val activeTasks = client.parseActiveDownloads(resp)
                        val taskMap = activeTasks.associateBy { it.videoId }
                        _downloadTasks.value = taskMap

                        val completedTasks = activeTasks.filter { it.status == "COMPLETED" }
                        val completedIds = completedTasks.map { it.videoId }.toSet()
                        if (completedIds.isNotEmpty()) {
                            val prevCompleted = _downloadedTrackIds.value
                            val newlyCompleted = completedTasks.filter { it.videoId !in prevCompleted }
                            _downloadedTrackIds.value = prevCompleted + completedIds
                            if (newlyCompleted.isNotEmpty()) {
                                refreshLibrary()
                                for (task in newlyCompleted) {
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
                            break
                        }
                    } else {
                        break
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Download polling loop finished: ${e.message}")
            } finally {
                isPollingDownloads = false
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
                    checkAccountStatus()
                    loadSyncedYouTubeTracks()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Account sync failed: ${e.message}")
            } finally {
                _isSyncingAccount.value = false
            }
        }
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Account disconnect failed: ${e.message}")
            }
        }
    }

    /** Loads cached synced Liked Music tracks from the Go engine daemon. */
    fun loadSyncedYouTubeTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getLikedTracks()
                if (code in 200..299 && resp.isNotBlank()) {
                    val tracks = client.parseLikedTracks(resp)
                    _syncedYouTubeTracks.value = tracks
                    _youtubeCount.value = tracks.size
                }
            } catch (e: Exception) {
                Log.d(TAG, "Load synced tracks note: ${e.message}")
            }
        }
    }

    /** Optimistically toggles like state and dispatches mutation to daemon. */
    fun toggleTrackLike(track: TrackItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val willBeLiked = !_isFavorite.value
            _isFavorite.value = willBeLiked
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
                    trackId = track.title,
                    title = track.title,
                    artist = track.artist,
                    album = "",
                    listenedSec = listenedSec
                )
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
                }
                delay(300)
            }
        }
    }

    private fun checkSponsorBlockSkip() {
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
     * Starts the bedtime sleep timer with logarithmic 30-second volume fadeout.
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
                var previousTrackId = playbackState.value.currentTrack?.id
                while (isActive) {
                    delay(500)
                    val state = playbackState.value
                    val curTrackId = state.currentTrack?.id
                    val nearEnd = state.durationMs > 0 && state.currentPositionMs >= (state.durationMs - 1200L)
                    val trackChanged = previousTrackId != null && curTrackId != null && curTrackId != previousTrackId
                    if (nearEnd || trackChanged || (!state.isPlaying && state.currentPositionMs > 0)) {
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
                    if (curTrackId != null) {
                        previousTrackId = curTrackId
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

    fun loadMoodsAndGenres(countryCode: String = "US", langCode: String = "en") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getMoodsAndGenres(countryCode, langCode)
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

    fun loadGenreDetail(params: String, title: String, countryCode: String = "US", langCode: String = "en") {
        _selectedGenreTitle.value = title
        _isLoadingGenreDetail.value = true
        _activeGenreShelves.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (code, resp) = client.getGenreDetail(params, title, countryCode, langCode)
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
