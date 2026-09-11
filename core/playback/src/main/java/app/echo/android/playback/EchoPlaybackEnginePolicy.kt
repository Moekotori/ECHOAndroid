package app.echo.android.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.model.playback.EchoReplayGainTags
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class EchoPlaybackEnginePolicy(
    context: Context,
    private val usbAudioMonitor: EchoUsbAudioMonitor,
) : Player.Listener {
    private val appContext = context.applicationContext
    private var attachedPlayer: Player? = null
    private var usbTransitionJob: Job? = null
    private var usbMuteInProgress: Boolean = false
    private var consecutiveErrorSkips: Int = 0
    private var activeReplayGainTrackId: String? = null
    var activeReplayGainTrackGainDb: Float? = null
        private set

    fun replayGainDb(mediaId: String?): Float? {
        if (mediaId.isNullOrBlank()) return null
        return replayGainTagsByMediaId[mediaId]?.selectedGainDb(EchoPlaybackProcessRuntime.replayGainMode)
    }
    val activeReplayGainTagsLoaded: Boolean
        get() = replayGainTagsLoaded(activeReplayGainTrackId, replayGainTagsByMediaId)
    private val sampleRatesByMediaId = mutableMapOf<String, Int?>()
    private val replayGainUrisByMediaId = mutableMapOf<String, String>()
    private val replayGainTagsByMediaId = mutableMapOf<String, EchoReplayGainTags>()
    private val replayGainJobs = mutableMapOf<String, Job>()
    private val subsonicTranscodeFallbackAttempts = hashSetOf<String>()
    private val echoLinkRefreshAttempts = mutableMapOf<String, Int>()
    private val echoLinkRefreshAttemptAtMs = mutableMapOf<String, Long>()
    private val echoLinkRefreshInFlight = mutableSetOf<String>()

    fun attachTo(player: Player) {
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(this)
        attachedPlayer = player
        player.addListener(this)
        (player as? ExoPlayer)?.pauseAtEndOfMediaItems =
            EchoSleepTimerPolicy.shouldPauseAtEndOfMediaItem(EchoPlaybackProcessRuntime.sleepTimerMode)
        val mediaId = player.currentMediaItem?.mediaId
        activeReplayGainTrackId = mediaId
        activeReplayGainTrackGainDb = replayGainAfterMediaItemChange(
            mediaId = mediaId,
            cachedTags = replayGainTagsByMediaId,
            mode = EchoPlaybackProcessRuntime.replayGainMode,
            previousGainDb = activeReplayGainTrackGainDb,
        )
        loadReplayGainForTrack(mediaId)
        loadReplayGainForTrack(nextReplayGainPrefetchId(mediaId, nextMediaId(player)))
        applyReplayGain()
    }

    fun boundPlayer(): Player? = attachedPlayer

    fun setPauseAtEndOfMediaItems(enabled: Boolean) {
        (attachedPlayer as? ExoPlayer)?.pauseAtEndOfMediaItems = enabled
    }

    fun detach() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        usbTransitionJob?.cancel()
        usbMuteInProgress = false
        cancelReplayGainJobs()
    }

    fun replaceQueueLookups(tracks: List<EchoTrack>) {
        sampleRatesByMediaId.clear()
        replayGainUrisByMediaId.clear()
        replayGainTagsByMediaId.clear()
        cancelReplayGainJobs()
        subsonicTranscodeFallbackAttempts.clear()
        resetEchoLinkStreamRefresh()
        tracks.forEach(::mergeQueueLookups)
    }

    fun resetEchoLinkStreamRefresh(mediaId: String? = null) {
        if (mediaId == null) {
            echoLinkRefreshAttempts.clear()
            echoLinkRefreshAttemptAtMs.clear()
            echoLinkRefreshInFlight.clear()
            return
        }
        echoLinkRefreshAttempts.remove(mediaId)
        echoLinkRefreshAttemptAtMs.remove(mediaId)
        echoLinkRefreshInFlight.remove(mediaId)
    }

    fun invalidateReplayGain(trackId: String) {
        val id = trackId.trim()
        if (id.isEmpty()) return
        replayGainJobs[id]?.cancel()
        replayGainJobs.remove(id)
        replayGainTagsByMediaId.remove(id)
        if (activeReplayGainTrackId == id) {
            activeReplayGainTrackGainDb = null
            loadReplayGainForTrack(id)
        }
    }

    fun mergeQueueLookups(track: EchoTrack) {
        sampleRatesByMediaId[track.id] = track.sampleRateHz
        replayGainUrisByMediaId[track.id] = track.uri
    }

    fun retainQueueLookups(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) return
        sampleRatesByMediaId.keys.retainAll(mediaIds)
        replayGainUrisByMediaId.keys.retainAll(mediaIds)
        replayGainTagsByMediaId.keys.retainAll(mediaIds)
        subsonicTranscodeFallbackAttempts.retainAll(mediaIds)
        echoLinkRefreshAttempts.keys.retainAll(mediaIds)
        echoLinkRefreshAttemptAtMs.keys.retainAll(mediaIds)
        echoLinkRefreshInFlight.retainAll(mediaIds)
        val staleJobs = replayGainJobs.keys.filterNot(mediaIds::contains)
        staleJobs.forEach { id ->
            replayGainJobs.remove(id)?.cancel()
        }
    }

    fun mergeSampleRates(ratesByMediaId: Map<String, Int?>) {
        ratesByMediaId.forEach { (mediaId, sampleRateHz) ->
            if (mediaId.isNotBlank() && sampleRateHz != null && sampleRateHz > 0) {
                sampleRatesByMediaId[mediaId] = sampleRateHz
            }
        }
    }

    fun mergeReplayGainUris(urisByMediaId: Map<String, String>) {
        val merged = mergePlayerQueueReplayGainUris(replayGainUrisByMediaId, urisByMediaId)
        if (merged != replayGainUrisByMediaId) {
            replayGainUrisByMediaId.clear()
            replayGainUrisByMediaId.putAll(merged)
        }
    }

    fun onReplayGainEnabledChanged() = onReplayGainPreferenceChanged()

    fun onReplayGainPreferenceChanged() {
        if (EchoPlaybackProcessRuntime.replayGainEnabled) {
            loadReplayGainForTrack(activeReplayGainTrackId)
            loadReplayGainForTrack(nextReplayGainPrefetchId(activeReplayGainTrackId, nextMediaId()))
        }
        applySelectedReplayGainFromCache()
    }

    fun onReplayGainModeChanged() {
        applySelectedReplayGainFromCache()
        loadReplayGainForTrack(activeReplayGainTrackId)
    }

    fun retryUncachedReplayGain() {
        loadReplayGainForTrack(activeReplayGainTrackId)
    }

    fun applyReplayGain() {
        val player = attachedPlayer ?: return
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
            player.volume = 1f
            EchoPlaybackProcessRuntime.setExclusiveMakeupGain(1f)
            EchoPlaybackProcessRuntime.syncLoudnessEnhancer(C.AUDIO_SESSION_ID_UNSET, 0)
            return
        }
        if (!shouldApplyReplayGainPlayerVolume(usbMuteInProgress)) return
        val output = echoReplayGainOutput(
            enabled = EchoPlaybackProcessRuntime.replayGainEnabled,
            preampDb = EchoPlaybackProcessRuntime.replayGainPreampDb,
            trackGainDb = activeReplayGainTrackGainDb,
        )
        val durationMs = player.duration.takeIf { it > 0L } ?: 0L
        val remainingMs = EchoPlaybackProcessRuntime.sleepTimerRemainingMs(
            trackRemainingMs = (durationMs - player.currentPosition).coerceAtLeast(0L),
            trackDurationKnown = durationMs > 0L,
        )
        val exclusiveLive = EchoPlaybackProcessRuntime.usbExclusiveSinkStatus?.streaming == true
        player.volume = (
            output.playerVolume * EchoPlaybackProcessRuntime.trackFadeGain *
                EchoSleepTimerPolicy.fadeMultiplier(
                    remainingMs,
                    mode = EchoPlaybackProcessRuntime.sleepTimerMode,
                )
            ).coerceIn(0f, 1f)
        if (exclusiveLive) {
            EchoPlaybackProcessRuntime.setExclusiveMakeupGain(
                echoReplayGainMakeupLinear(output.enhancerGainMb),
            )
            EchoPlaybackProcessRuntime.syncLoudnessEnhancer(
                audioSessionId = C.AUDIO_SESSION_ID_UNSET,
                enhancerGainMb = 0,
            )
        } else {
            EchoPlaybackProcessRuntime.setExclusiveMakeupGain(1f)
            EchoPlaybackProcessRuntime.syncLoudnessEnhancer(
                audioSessionId = player.audioSessionId,
                enhancerGainMb = output.enhancerGainMb,
            )
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
            EchoSleepTimerPolicy.shouldCancelEndOfTrackOnSeek(EchoPlaybackProcessRuntime.sleepTimerMode)
        ) {
            EchoPlaybackProcessRuntime.cancelSleepTimer()
        }
        prepareUsbForMediaItemTransition(mediaItem, reason)
        val mediaId = mediaItem?.mediaId
        activeReplayGainTrackId = mediaId
        activeReplayGainTrackGainDb = replayGainAfterMediaItemChange(
            mediaId = mediaId,
            cachedTags = replayGainTagsByMediaId,
            mode = EchoPlaybackProcessRuntime.replayGainMode,
            previousGainDb = activeReplayGainTrackGainDb,
        )
        loadReplayGainForTrack(mediaId)
        loadReplayGainForTrack(nextReplayGainPrefetchId(mediaId, nextMediaId()))
        applyReplayGain()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
            applyReplayGain()
        }
        if (
            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
            player.playbackState == Player.STATE_READY &&
            player.playerError == null
        ) {
            consecutiveErrorSkips = 0
            player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let(::resetEchoLinkStreamRefresh)
        }
        if (
            events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
            ) &&
            PlaybackSessionPolicy.shouldPrepareAfterExternalSkip(
                hasPlayerError = player.playerError != null,
                playbackStateIdle = player.playbackState == Player.STATE_IDLE,
                mediaItemCount = player.mediaItemCount,
            )
        ) {
            player.prepare()
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        val mapped = error.toEchoPlaybackError()
        EchoErrorLog.record(
            source = EchoErrorSource.Playback,
            summary = mapped.message,
            detail = mapped.kind.name,
            throwable = error,
        )
        val player = attachedPlayer ?: return
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
            player.pause()
            if (EchoPlaybackProcessRuntime.bitPerfectStatus.state in listOf(
                app.echo.android.model.playback.EchoBitPerfectState.Direct,
                app.echo.android.model.playback.EchoBitPerfectState.Waiting)) {
                EchoPlaybackProcessRuntime.bitPerfectStatus = EchoBitPerfectSnapshot(
                    app.echo.android.model.playback.EchoBitPerfectState.PlaybackError)
            }
            return // Strict mode must not auto-transcode, skip the track, or switch output paths.
        }
        if (tryEchoLinkStreamRefresh(player)) {
            return
        }
        if (mapped.kind == EchoAudioErrorKind.UnsupportedFormat &&
            trySubsonicTranscodeFallback(player)
        ) {
            return
        }
        if (mapped.shouldAutoSkipTrack()) {
            skipToNextAfterError(player)
        }
    }

    private fun trySubsonicTranscodeFallback(player: Player): Boolean {
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return false
        val item = player.getMediaItemAt(index)
        val mediaId = item.mediaId
        if (mediaId.isBlank() || !subsonicTranscodeFallbackAttempts.add(mediaId)) return false
        val currentUri = item.localConfiguration?.uri?.toString() ?: return false
        val fallbackUri = subsonicUnsupportedFormatFallbackUrl(currentUri) ?: return false
        player.replaceMediaItem(
            index,
            item.buildUpon().setUri(android.net.Uri.parse(fallbackUri)).build(),
        )
        player.prepare()
        player.play()
        return true
    }

    private fun prepareUsbForMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            // The sink drains/reuses the real output format. Metadata can be missing or late;
            // it must never introduce an artificial mute into an automatic queue boundary.
            usbTransitionJob?.cancel()
            usbMuteInProgress = false
            return
        }
        if (EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
            usbTransitionJob?.cancel()
            usbMuteInProgress = false
            return // The strict sink drains and reopens the DAC itself; never change its digital gain.
        }
        val sampleRateHz = mediaItem?.mediaId?.let(sampleRatesByMediaId::get)
        if (!usbAudioMonitor.status.value.exclusiveEnabled) {
            if (usbMuteInProgress) {
                usbMuteInProgress = false
                applyReplayGain()
            }
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            return
        }
        val player = attachedPlayer ?: return
        val sinkStatus = EchoPlaybackProcessRuntime.usbExclusiveSinkStatus
        if (
            !PlaybackSessionPolicy.shouldMuteUsbTransition(
                exclusiveStreaming = sinkStatus?.streaming == true,
                streamingSampleRateHz = sinkStatus?.sampleRateHz,
                nextTrackSampleRateHz = sampleRateHz,
            )
        ) {
            // 同采样率的 USB 会话可直接复用，静音反而会打断 gapless。
            usbTransitionJob?.cancel()
            if (usbMuteInProgress) {
                usbMuteInProgress = false
                applyReplayGain()
            }
            usbTransitionJob = EchoPlaybackProcessRuntime.scope.launch {
                usbAudioMonitor.prepareForTrack(sampleRateHz)
            }
            return
        }
        usbTransitionJob?.cancel()
        usbMuteInProgress = true
        player.volume = 0f
        usbTransitionJob = EchoPlaybackProcessRuntime.scope.launch {
            try {
                usbAudioMonitor.prepareForTrack(sampleRateHz)
                delay(USB_MUTE_MS.milliseconds)
            } finally {
                usbMuteInProgress = false
                if (attachedPlayer === player) {
                    applyReplayGain()
                }
            }
        }
    }

    private fun applySelectedReplayGainFromCache() {
        activeReplayGainTrackGainDb = replayGainAfterMediaItemChange(
            mediaId = activeReplayGainTrackId,
            cachedTags = replayGainTagsByMediaId,
            mode = EchoPlaybackProcessRuntime.replayGainMode,
            previousGainDb = null,
        )
        applyReplayGain()
    }

    private fun nextMediaId(player: Player? = attachedPlayer): String? {
        player ?: return null
        val index = player.nextMediaItemIndex
        if (index == C.INDEX_UNSET || index < 0 || index >= player.mediaItemCount) return null
        return player.getMediaItemAt(index).mediaId.takeIf { it.isNotBlank() }
    }

    private fun cancelReplayGainJobs() {
        replayGainJobs.values.forEach { it.cancel() }
        replayGainJobs.clear()
    }

    private fun loadReplayGainForTrack(trackId: String?) {
        if (!EchoPlaybackProcessRuntime.replayGainEnabled) return
        if (trackId.isNullOrBlank() || replayGainTagsByMediaId.containsKey(trackId)) return
        if (replayGainJobs[trackId]?.isActive == true) return
        val uri = replayGainUriForMediaId(trackId, replayGainUrisByMediaId) ?: return
        replayGainJobs[trackId] = EchoPlaybackProcessRuntime.scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val stream = runCatching { openReplayGainStream(uri) }.getOrNull()
                    if (stream == null) {
                        ReplayGainReadOutcome.Failed
                    } else {
                        replayGainReadOutcome(
                            streamOpened = true,
                            parseResult = runCatching {
                                stream.use { input -> EchoReplayGainReader.readTags(input) }
                            },
                        )
                    }
                }
                if (!shouldCacheReplayGainRead(outcome)) {
                    if (outcome is ReplayGainReadOutcome.Failed && activeReplayGainTrackId == trackId) {
                        applySelectedReplayGainFromCache()
                    }
                    return@launch
                }
                val tags = (outcome as ReplayGainReadOutcome.Parsed).tags
                replayGainTagsByMediaId[trackId] = tags
                if (activeReplayGainTrackId == trackId) {
                    activeReplayGainTrackGainDb = tags.selectedGainDb(EchoPlaybackProcessRuntime.replayGainMode)
                    applyReplayGain()
                }
            } finally {
                replayGainJobs.remove(trackId)
            }
        }
    }

    private fun openReplayGainStream(uri: String): InputStream? {
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        val webDavReady = EchoRemotePlaybackAuthRegistry.isWebDavAuthReadyForUris(listOf(uri))
        val subsonicReady = EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(listOf(uri))
        val jellyfinReady = EchoRemotePlaybackAuthRegistry.isJellyfinAuthReadyForUris(listOf(uri))
        if (!canOpenReplayGainStream(uri, webDavReady, subsonicReady, jellyfinReady)) return null
        return when (replayGainStreamKind(uri)) {
            ReplayGainStreamKind.LocalContent -> appContext.contentResolver.openInputStream(parsed)
            ReplayGainStreamKind.LocalFile -> parsed.path?.takeIf { it.isNotBlank() }?.let(::FileInputStream)
            ReplayGainStreamKind.RemoteHttp -> openRemoteReplayGainStream(parsed)
            null -> null
        }
    }

    private fun openRemoteReplayGainStream(uri: android.net.Uri): InputStream? {
        val dataSource = echoRemoteAuthDataSourceFactory(appContext).createDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setLength(ReplayGainRemoteReadMaxBytes.toLong())
            .build()
        val stream = DataSourceInputStream(dataSource, dataSpec)
        return runCatching {
            stream.open()
            LimitedInputStream(stream, ReplayGainRemoteReadMaxBytes)
        }.getOrElse {
            runCatching { stream.close() }
            throw it
        }
    }

    private fun tryEchoLinkStreamRefresh(player: Player): Boolean {
        val index = player.currentMediaItemIndex
        if (index < 0 || index >= player.mediaItemCount) return false
        val item = player.getMediaItemAt(index)
        val mediaId = item.mediaId
        val currentUri = item.localConfiguration?.uri?.toString().orEmpty()
        if (mediaId.isBlank()) return false
        if (
            !EchoLinkPlaybackUri.requiresStreamResolve(mediaId, currentUri) &&
            !EchoLinkPlaybackUri.isOneShotStreamUri(currentUri)
        ) {
            return false
        }
        if (mediaId in echoLinkRefreshInFlight) return true
        val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
        val previousAttempts = echoLinkRefreshAttempts[mediaId] ?: 0
        val lastAttemptAt = echoLinkRefreshAttemptAtMs[mediaId] ?: 0L
        if (
            !EchoLinkStreamRefreshPolicy.shouldAttempt(
                previousAttempts = previousAttempts,
                lastAttemptElapsedMs = lastAttemptAt,
                nowElapsedMs = nowElapsedMs,
            )
        ) {
            return false
        }
        echoLinkRefreshAttempts[mediaId] = previousAttempts + 1
        echoLinkRefreshAttemptAtMs[mediaId] = nowElapsedMs
        echoLinkRefreshInFlight.add(mediaId)
        EchoPlaybackProcessRuntime.scope.launch {
            try {
                EchoPlaybackProcessRuntime.reResolveBoundPlayerQueue()
            } finally {
                echoLinkRefreshInFlight.remove(mediaId)
            }
        }
        return true
    }

    private fun skipToNextAfterError(player: Player) {
        val shuffledNextIndex = if (player.shuffleModeEnabled) {
            shuffledNextIndex(player)
        } else {
            null
        }
        val targetIndex = nextIndexAfterPlaybackError(
            currentIndex = player.currentMediaItemIndex,
            mediaItemCount = player.mediaItemCount,
            repeatAll = player.repeatMode == Player.REPEAT_MODE_ALL,
            consecutiveErrorSkips = consecutiveErrorSkips,
            shuffledNextIndex = shuffledNextIndex,
        ) ?: return
        consecutiveErrorSkips += 1
        player.seekTo(targetIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun shuffledNextIndex(player: Player): Int? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        val nextOff = timeline.getNextWindowIndex(
            player.currentMediaItemIndex,
            Player.REPEAT_MODE_OFF,
            true,
        )
        if (nextOff != C.INDEX_UNSET) return nextOff
        if (player.repeatMode != Player.REPEAT_MODE_ALL) return null
        val nextAll = timeline.getNextWindowIndex(
            player.currentMediaItemIndex,
            Player.REPEAT_MODE_ALL,
            true,
        )
        return nextAll.takeIf { it != C.INDEX_UNSET }
    }

    private companion object {
        const val USB_MUTE_MS = 90L
    }
}
