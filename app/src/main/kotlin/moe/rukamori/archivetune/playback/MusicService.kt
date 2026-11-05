/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EqualizerAutoHeadroomEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.widget.LoadWidgetInsightsUseCase
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaSessionService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var equalizerPlaybackController: EqualizerPlaybackController

    @Inject
    internal lateinit var loadWidgetInsightsUseCase: LoadWidgetInsightsUseCase

    private val binder = MusicBinder(this)
    private var boundClientCount = 0
    private val hasBoundClients: Boolean
        get() = boundClientCount > 0
    private var idleStopJob: Job? = null

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakelockEnabled = false
    private var audioDeviceCallbackRegistered = false
    private lateinit var audioOutputResolver: AudioOutputResolver

    val activeAudioDevice get() = audioOutputResolver.activeAudioDevice

    fun refreshActiveDevice() = audioOutputResolver.refresh()

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                if (addedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                if (removedDevices.any { it.isSink }) onAudioOutputDeviceChanged()
            }
        }

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)
    private val ioScope = CoroutineScope(Dispatchers.IO + scopeJob)

    private var lastAudioOutputDeviceSignature: String? = null
    private var blockedArtistIds: Set<String> = emptySet()
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val queueRestoreCompleted = MutableStateFlow(false)

    lateinit var player: ExoPlayer
        private set
    lateinit var localPlayer: ExoPlayer
        private set
    lateinit var sleepTimer: SleepTimer
        private set

    private lateinit var widgetUpdater: MusicServiceWidgetUpdater

    // ── Equalizer state ──────────────────────────────────────────────

    val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)
    private val desiredEqSettings =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
                autoHeadroomEnabled = false,
            ),
        )
    private var audioEffectsSessionId: Int? = null
    private var audioEffectsInitializationJob: Job? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    override fun onCreate() {
        super.onCreate()
        equalizerPlaybackController.attach(this)

        localPlayer =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory())
                .setLoadControl(createPrimaryLoadControl())
                .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setAudioAttributes(playbackAudioAttributes(), false)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setDeviceVolumeControlEnabled(true)
                .build()
                .apply {
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                    addListener(audioEffectPlayerListener)
                }

        player = localPlayer
        player.addListener(this)
        sleepTimer = SleepTimer(scope, player, this)
        player.addListener(sleepTimer)
        widgetUpdater =
            MusicServiceWidgetUpdater(
                service = this,
                player = player,
                scope = scope,
                loadWidgetInsights = loadWidgetInsightsUseCase,
            )

        mediaSession =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()

        database
            .blockedArtistIds()
            .map { ids -> ids.toSet() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .collectInScope { updatedBlockedArtistIds ->
                blockedArtistIds = updatedBlockedArtistIds
            }

        dataStore.data
            .map(::readEqSettingsFromPrefs)
            .distinctUntilChanged()
            .collectInScope { settings ->
                desiredEqSettings.value = settings
                applyEqSettingsToEffects(settings)
            }

        dataStore.data
            .map { it[WakelockKey] ?: false }
            .distinctUntilChanged()
            .collectInScope { enabled ->
                wakelockEnabled = enabled
                updateWakeLock()
            }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioOutputResolver = AudioOutputResolver(audioManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioManager.setAllowedCapturePolicy(android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        }
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArchiveTune:Playback")
                .also { it.setReferenceCounted(false) }
        setupAudioFocusRequest()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(mainLooper))
        audioDeviceCallbackRegistered = true
        audioOutputResolver.refresh()
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.collectInScope(action: suspend (T) -> Unit) {
        scope.launch {
            collect { action(it) }
        }
    }

    private lateinit var mediaSession: MediaSession

    fun playQueue(queue: Queue) {
        scope.launch {
            val status = queue.getInitialStatus()
            val items = status.items
            if (items.isEmpty()) return@launch
            queueTitle = status.title
            currentMediaMetadata.value = items.firstOrNull()?.metadata
            player.apply {
                setMediaItems(items, 0, C.TIME_UNSET)
                prepare()
                play()
            }
            queueRestoreCompleted.value = true
        }
    }

    fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val insertIndex = (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
        player.addMediaItems(insertIndex, items)
    }

    fun addToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        player.addMediaItems(items)
    }

    fun toggleLike() {
        val current = player.currentMediaItem?.mediaId ?: return
        scope.launch {
            val liked =
                withContext(Dispatchers.IO) {
                    val song = database.song(current).first()?.song ?: return@withContext false
                    database.update(song.toggleLike())
                    !song.liked
                }
            currentMediaMetadata.value =
                currentMediaMetadata.value?.let {
                    if (it.id == current) it.copy(liked = liked) else it
                }
        }
    }

    fun moveQueueItemToNext(mediaItemIndex: Int) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || mediaItemIndex < 0 || mediaItemIndex == currentIndex) return
        if (mediaItemIndex > currentIndex) {
            player.moveMediaItem(mediaItemIndex, currentIndex + 1)
        } else {
            player.moveMediaItem(mediaItemIndex, currentIndex)
        }
    }

    internal fun removeQueueItems(windows: List<Timeline.Window>): QueueRemoval? =
        QueueRemoval.remove(player, windows)

    internal fun restoreQueueItems(removal: QueueRemoval): Boolean = removal.restore(player)

    internal fun discardQueueRemoval(removal: QueueRemoval) {
        // Nothing to persist locally
    }

    fun seekToNext() {
        player.seekToNext()
        player.prepare()
        player.playWhenReady = true
    }

    fun seekToPrevious() {
        player.seekToPrevious()
        player.prepare()
        player.playWhenReady = true
    }

    fun pauseFromSleepTimer() {
        player.pause()
    }

    fun updateWidget() {
        if (!this::widgetUpdater.isInitialized) return
        widgetUpdater.update()
        widgetUpdater.updateProgressTracking()
    }

    fun stopAndClearPlayback(clearPersistentState: Boolean = true) {
        player.stop()
        player.clearMediaItems()
        currentMediaMetadata.value = null
        queueTitle = null
        sleepTimer.clear()
        releaseAudioEffects()
        if (clearPersistentState) queueRestoreCompleted.value = false
    }

    private fun currentAudioOutputDeviceSignature(): String? =
        runCatching {
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.isSink }
                ?.address
        }.getOrNull()

    private fun onAudioOutputDeviceChanged() {
        val signature = currentAudioOutputDeviceSignature()
        if (signature == lastAudioOutputDeviceSignature) return
        lastAudioOutputDeviceSignature = signature
        audioOutputResolver.refresh()
        releaseAudioEffects()
        if (player.playWhenReady) {
            scope.launch {
                delay(AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS)
                ensureAudioEffects(player.audioSessionId)
            }
        }
    }

    private fun setupAudioFocusRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes
                            .Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    ).setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        onAudioFocusChange(focusChange)
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
                    ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            } else {
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun onAudioFocusChange(focusChange: Int) {
        lastAudioFocusState = focusChange
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                player.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                }
            }
        }
    }

    override fun onDestroy() {
        equalizerPlaybackController.detach(this)
        abandonAudioFocus()
        if (audioDeviceCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallbackRegistered = false
        }
        releaseAudioEffects()
        wakeLock?.release()
        scopeJob.cancel()
        if (this::mediaSession.isInitialized) {
            runCatching { mediaSession.release() }
        }
        binder.release()
        if (this::player.isInitialized) {
            player.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        boundClientCount++
        cancelIdleStop()
        requestAudioFocus()
        val result = super.onBind(intent) ?: binder
        if (player.mediaItemCount > 0 && player.currentMediaItem != null) {
            currentMediaMetadata.value = player.currentMediaItem?.metadata
        }
        return result
    }

    override fun onUnbind(intent: Intent?): Boolean {
        boundClientCount = (boundClientCount - 1).coerceAtLeast(0)
        scheduleStopIfIdle()
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        boundClientCount++
        cancelIdleStop()
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    private fun scheduleStopIfIdle() {
        // Local-only: keep the service alive while playback is active.
    }

    private fun cancelIdleStop() {
        idleStopJob?.cancel()
    }

    // ── Player.Listener ───────────────────────────────────────────────

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            requestAudioFocus()
            ensureAudioEffects(player.audioSessionId)
        }
        updateWakeLock()
        updateWidget()
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
            releaseAudioEffects()
        }
        updateWakeLock()
        updateWidget()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        currentMediaMetadata.value = mediaItem?.metadata
        updateWidget()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        queueRestoreCompleted.value = true
    }

    override fun onPlayerErrorChanged(playbackError: androidx.media3.common.PlaybackException?) {
        Timber.e(playbackError, "Player error")
    }

    // ── PlaybackStatsListener.Callback ────────────────────────────────

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val songId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return
        val playTime = playbackStats.totalPlayTimeMs
        if (playTime < MinHistoryPlayTimeMs) return

        ioScope.launch {
            runCatching {
                database.insert(
                    Event(
                        songId = songId,
                        timestamp = LocalDateTime.now(),
                        playTime = playTime,
                    ),
                )
            }.onFailure { Timber.w(it, "Could not record local playback history") }
        }
    }

    // ── Data source / player factories ────────────────────────────────

    private fun createMediaSourceFactory(): androidx.media3.exoplayer.source.MediaSource.Factory =
        DefaultMediaSourceFactory(createDataSourceFactory(), DefaultExtractorsFactory())

    private fun createDataSourceFactory(): DefaultDataSource.Factory =
        DefaultDataSource.Factory(this)

    private fun createPrimaryLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder().build()

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        SilenceSkippingAudioProcessor(
                            1_500_000L,
                            0.35f,
                            500_000L,
                            10,
                            150.toShort(),
                        ),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    private fun playbackAudioAttributes(): AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    private val audioEffectPlayerListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(
                        Player.EVENT_AUDIO_SESSION_ID,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                    )
                ) {
                    reconcileAudioEffectSession()
                }
            }
        }

    private fun updateWakeLock() {
        val wl = wakeLock ?: return
        val shouldHold = wakelockEnabled && player.isPlaying
        if (shouldHold && !wl.isHeld) {
            wl.acquire()
        } else if (!shouldHold && wl.isHeld) {
            wl.release()
        }
    }

    // ── Equalizer ─────────────────────────────────────────────────────

    private fun decodeBandLevelsMb(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
    }

    private fun encodeBandLevelsMb(levelsMb: List<Int>): String =
        runCatching {
            EqualizerJson.json.encodeToString(levelsMb)
        }.getOrNull().orEmpty()

    private fun readEqSettingsFromPrefs(prefs: Preferences): EqSettings {
        val levels = decodeBandLevelsMb(prefs[EqualizerBandLevelsMbKey])
        return EqSettings(
            enabled = prefs[EqualizerEnabledKey] ?: false,
            bandLevelsMb = levels,
            outputGainEnabled = prefs[EqualizerOutputGainEnabledKey] ?: false,
            outputGainMb = prefs[EqualizerOutputGainMbKey] ?: 0,
            bassBoostEnabled = prefs[EqualizerBassBoostEnabledKey] ?: false,
            bassBoostStrength = (prefs[EqualizerBassBoostStrengthKey] ?: 0).coerceIn(0, 1000),
            virtualizerEnabled = prefs[EqualizerVirtualizerEnabledKey] ?: false,
            virtualizerStrength = (prefs[EqualizerVirtualizerStrengthKey] ?: 0).coerceIn(0, 1000),
            autoHeadroomEnabled = prefs[EqualizerAutoHeadroomEnabledKey] ?: false,
        )
    }

    fun applyEqFlatPreset() {
        ioScope.launch {
            val caps = eqCapabilities.value
            val bandCount =
                caps?.bandCount ?: equalizer?.let {
                    readAudioEffectValue("equalizer band count") { it.numberOfBands.toInt() }
                } ?: 0
            val encoded = encodeBandLevelsMb(List(bandCount.coerceAtLeast(0)) { 0 })
            dataStore.edit { prefs ->
                prefs[EqualizerEnabledKey] = true
                prefs[EqualizerBandLevelsMbKey] = encoded
                prefs[EqualizerSelectedProfileIdKey] = "flat"
            }
        }
    }

    fun applySystemEqPreset(presetIndex: Int) {
        scope.launch {
            ensureAudioEffects(player.audioSessionId)
            val eq = equalizer ?: return@launch
            val maxPreset = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt() } ?: 0
            if (presetIndex !in 0 until maxPreset) return@launch

            runCatching { eq.usePreset(presetIndex.toShort()) }.getOrNull() ?: return@launch

            val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
            val levels =
                (0 until bandCount).map { band ->
                    readAudioEffectValue("equalizer band level for band $band") {
                        eq.getBandLevel(band.toShort()).toInt()
                    } ?: 0
                }

            val encoded = encodeBandLevelsMb(levels)
            if (encoded.isBlank()) return@launch

            ioScope.launch {
                dataStore.edit { prefs ->
                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerBandLevelsMbKey] = encoded
                    prefs[EqualizerSelectedProfileIdKey] = "system:$presetIndex"
                }
            }
        }
    }

    private fun resampleLevelsByIndex(
        levelsMb: List<Int>,
        targetCount: Int,
    ): List<Int> {
        if (targetCount <= 0) return emptyList()
        if (levelsMb.isEmpty()) return List(targetCount) { 0 }
        if (levelsMb.size == targetCount) return levelsMb
        if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)

        val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
        return List(targetCount) { i ->
            val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
            val lo =
                kotlin.math
                    .floor(pos)
                    .toInt()
                    .coerceIn(0, levelsMb.lastIndex)
            val hi =
                kotlin.math
                    .ceil(pos)
                    .toInt()
                    .coerceIn(0, levelsMb.lastIndex)
            val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
            val a = levelsMb[lo]
            val b = levelsMb[hi]
            (a + ((b - a) * t)).toInt()
        }
    }

    private inline fun <T> readAudioEffectValue(
        operation: String,
        block: () -> T,
    ): T? =
        runCatching(block)
            .onFailure { error ->
                Timber.tag("MusicService").w(error, "Audio effect query failed: %s", operation)
            }.getOrNull()

    private fun updateEqCapabilitiesFromEffect(eq: Equalizer) {
        val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt().coerceAtLeast(0) } ?: 0
        val range = readAudioEffectValue("equalizer band range") { eq.bandLevelRange }
        val minMb = range?.getOrNull(0)?.toInt() ?: -1500
        val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
        val center =
            (0 until bandCount).map { band ->
                (
                    readAudioEffectValue("equalizer center frequency for band $band") {
                        eq.getCenterFreq(band.toShort())
                    } ?: 0
                ) / 1000
            }
        val presetCount = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt().coerceAtLeast(0) } ?: 0
        val presets =
            (0 until presetCount).map { idx ->
                readAudioEffectValue("equalizer preset name for preset $idx") {
                    eq.getPresetName(idx.toShort()).toString()
                } ?: "Preset ${idx + 1}"
            }
        val capabilities =
            EqCapabilities(
                bandCount = bandCount,
                minBandLevelMb = minMb,
                maxBandLevelMb = maxMb,
                centerFreqHz = center,
                systemPresets = presets,
            )
        eqCapabilities.value = capabilities
        equalizerPlaybackController.updateCapabilities(capabilities)
    }

    private fun releaseAudioEffectInstances() {
        audioEffectsSessionId = null
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        eqCapabilities.value = null
        equalizerPlaybackController.updateCapabilities(null)
    }

    private fun releaseAudioEffects() {
        audioEffectsInitializationJob?.cancel()
        audioEffectsInitializationJob = null
        releaseAudioEffectInstances()
    }

    private fun ensureAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        if (audioEffectsSessionId == sessionId && equalizer != null) return

        audioEffectsInitializationJob?.cancel()
        audioEffectsInitializationJob = null
        if (initializeAudioEffects(sessionId)) return

        audioEffectsInitializationJob =
            scope.launch {
                repeat(AUDIO_EFFECT_INITIALIZATION_MAX_ATTEMPTS - 1) {
                    delay(AUDIO_EFFECT_INITIALIZATION_RETRY_DELAY_MS)
                    if (player.audioSessionId != sessionId || !shouldKeepAudioEffectSessionOpen()) {
                        return@launch
                    }
                    if (initializeAudioEffects(sessionId)) return@launch
                }
            }
    }

    private fun initializeAudioEffects(sessionId: Int): Boolean {
        releaseAudioEffectInstances()
        audioEffectsSessionId = sessionId

        equalizer = createAudioEffect("Equalizer", sessionId) { Equalizer(0, sessionId) }
        bassBoost = createAudioEffect("BassBoost", sessionId) { BassBoost(0, sessionId) }
        virtualizer = createAudioEffect("Virtualizer", sessionId) { Virtualizer(0, sessionId) }
        loudnessEnhancer = createAudioEffect("LoudnessEnhancer", sessionId) { LoudnessEnhancer(sessionId) }

        equalizer?.let(::updateEqCapabilitiesFromEffect)
        applyEqSettingsToEffects(desiredEqSettings.value)
        return equalizer != null
    }

    private inline fun <T> createAudioEffect(
        name: String,
        sessionId: Int,
        factory: () -> T,
    ): T? =
        runCatching(factory)
            .onFailure { error ->
                Timber.tag(TAG).w(error, "%s initialization failed for audio session %d", name, sessionId)
            }.getOrNull()

    private fun applyEqSettingsToEffects(settings: EqSettings) {
        val eq = equalizer ?: return
        val caps = eqCapabilities.value
        val bandCount = caps?.bandCount ?: readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
        val minMb =
            caps?.minBandLevelMb ?: readAudioEffectValue("equalizer minimum band level") {
                eq.bandLevelRange.getOrNull(0)?.toInt()
            } ?: -1500
        val maxMb =
            caps?.maxBandLevelMb ?: readAudioEffectValue("equalizer maximum band level") {
                eq.bandLevelRange.getOrNull(1)?.toInt()
            } ?: 1500

        val levels = resampleLevelsByIndex(settings.bandLevelsMb, bandCount)
        runCatching { eq.enabled = settings.enabled }

        for (band in 0 until bandCount) {
            val levelMb = levels.getOrNull(band)?.coerceIn(minMb, maxMb) ?: 0
            runCatching { eq.setBandLevel(band.toShort(), levelMb.toShort()) }
        }

        bassBoost?.let { bb ->
            runCatching { bb.enabled = settings.enabled && settings.bassBoostEnabled }
            runCatching { bb.setStrength(settings.bassBoostStrength.toShort()) }
        }

        virtualizer?.let { v ->
            runCatching { v.enabled = settings.enabled && settings.virtualizerEnabled }
            runCatching { v.setStrength(settings.virtualizerStrength.toShort()) }
        }

        loudnessEnhancer?.let { le ->
            val automaticHeadroomMb = -(levels.maxOrNull()?.coerceAtLeast(0) ?: 0)
            val gainMb =
                when {
                    settings.autoHeadroomEnabled -> automaticHeadroomMb
                    settings.outputGainEnabled -> settings.outputGainMb.coerceIn(-1500, 1500)
                    else -> 0
                }
            runCatching { le.setTargetGain(gainMb) }
            runCatching {
                le.enabled = settings.enabled && (settings.autoHeadroomEnabled || settings.outputGainEnabled)
            }
        }
    }

    private fun shouldKeepAudioEffectSessionOpen(): Boolean {
        val playbackState = player.playbackState
        return playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY
    }

    private fun reconcileAudioEffectSession() {
        if (!shouldKeepAudioEffectSessionOpen()) {
            releaseAudioEffects()
            return
        }
        val sessionId = player.audioSessionId
        if (sessionId > 0) {
            ensureAudioEffects(sessionId)
        }
    }

    class MusicBinder internal constructor(service: MusicService) : Binder() {
        @Volatile
        private var serviceReference: MusicService? = service

        val service: MusicService
            get() = checkNotNull(serviceReference) { "MusicService has been destroyed" }

        internal fun release() {
            serviceReference = null
        }
    }

    companion object {
        private const val TAG = "MusicService"
        private const val AUDIO_EFFECT_INITIALIZATION_MAX_ATTEMPTS = 4
        private const val AUDIO_EFFECT_INITIALIZATION_RETRY_DELAY_MS = 250L
        private const val AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS = 200L
        private const val MinHistoryPlayTimeMs = 5_000L
        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
    }
}
