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
import app.echo.android.model.playback.EchoAudioErrorKind
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
    private var replayGainJob: Job? = null
    private var usbMuteInProgress: Boolean = false
    private var consecutiveErrorSkips: Int = 0
    private var lastReplayGainTrackId: String? = null
    private var activeReplayGainTrackId: String? = null
    var activeReplayGainTrackGainDb: Float? = null
        private set
    private val sampleRatesByMediaId = mutableMapOf<String, Int?>()
    private val replayGainUrisByMediaId = mutableMapOf<String, String>()
    private val replayGainTrackGainsByMediaId = mutableMapOf<String, Float?>()
    private val subsonicTranscodeFallbackAttempts = hashSetOf<String>()

    fun attachTo(player: Player) {
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(this)
        attachedPlayer = player
        player.addListener(this)
        (player as? ExoPlayer)?.pauseAtEndOfMediaItems =
            EchoSleepTimerPolicy.shouldPauseAtEndOfMediaItem(EchoPlaybackProcessRuntime.sleepTimerMode)
    }

    fun boundPlayer(): Player? = attachedPlayer

    fun setPauseAtEndOfMediaItems(enabled: Boolean) {
        (attachedPlayer as? ExoPlayer)?.pauseAtEndOfMediaItems = enabled
    }

    fun detach() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        usbTransitionJob?.cancel()
        replayGainJob?.cancel()
    }

    fun replaceQueueLookups(tracks: List<EchoTrack>) {
        sampleRatesByMediaId.clear()
        replayGainUrisByMediaId.clear()
        replayGainTrackGainsByMediaId.clear()
        subsonicTranscodeFallbackAttempts.clear()
        tracks.forEach(::mergeQueueLookups)
    }

    fun mergeQueueLookups(track: EchoTrack) {
        sampleRatesByMediaId[track.id] = track.sampleRateHz
        replayGainUrisByMediaId[track.id] = track.uri
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

    fun onReplayGainEnabledChanged() {
        loadReplayGainForTrack(activeReplayGainTrackId)
        applyReplayGain()
    }

    fun retryUncachedReplayGain() {
        loadReplayGainForTrack(activeReplayGainTrackId)
    }

    fun applyReplayGain() {
        val player = attachedPlayer ?: return
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
            output.playerVolume *
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
        prepareUsbForMediaItemTransition(mediaItem)
        val mediaId = mediaItem?.mediaId
        activeReplayGainTrackId = mediaId
        activeReplayGainTrackGainDb = replayGainAfterMediaItemChange(
            mediaId = mediaId,
            cachedGains = replayGainTrackGainsByMediaId,
            previousGainDb = activeReplayGainTrackGainDb,
        )
        if (mediaId != lastReplayGainTrackId) {
            lastReplayGainTrackId = mediaId
            consecutiveErrorSkips = 0
            loadReplayGainForTrack(mediaId)
        }
        applyReplayGain()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
            applyReplayGain()
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
        val player = attachedPlayer ?: return
        val mapped = error.toEchoPlaybackError()
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

    private fun prepareUsbForMediaItemTransition(mediaItem: MediaItem?) {
        val sampleRateHz = mediaItem?.mediaId?.let(sampleRatesByMediaId::get)
        if (!usbAudioMonitor.status.value.exclusiveEnabled) {
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            return
        }
        val player = attachedPlayer ?: return
        usbTransitionJob?.cancel()
        usbMuteInProgress = true
        player.volume = 0f
        usbTransitionJob = EchoPlaybackProcessRuntime.scope.launch {
            usbAudioMonitor.prepareForTrack(sampleRateHz)
            delay(USB_MUTE_MS.milliseconds)
            usbMuteInProgress = false
            if (attachedPlayer === player) {
                applyReplayGain()
            }
        }
    }

    private fun loadReplayGainForTrack(trackId: String?) {
        if (!EchoPlaybackProcessRuntime.replayGainEnabled) return
        if (trackId == null || replayGainTrackGainsByMediaId.containsKey(trackId)) return
        val uri = replayGainUriForMediaId(trackId, replayGainUrisByMediaId) ?: return
        replayGainJob?.cancel()
        replayGainJob = EchoPlaybackProcessRuntime.scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val stream = runCatching { openReplayGainStream(uri) }.getOrNull()
                if (stream == null) {
                    ReplayGainReadOutcome.Failed
                } else {
                    replayGainReadOutcome(
                        streamOpened = true,
                        parseResult = runCatching { stream.use(EchoReplayGainReader::readTrackGainDb) },
                    )
                }
            }
            if (!shouldCacheReplayGainRead(outcome)) return@launch
            val parsed = (outcome as ReplayGainReadOutcome.Parsed).trackGainDb
            replayGainTrackGainsByMediaId[trackId] = parsed
            if (activeReplayGainTrackId == trackId) {
                activeReplayGainTrackGainDb = parsed
                applyReplayGain()
            }
        }
    }

    private fun openReplayGainStream(uri: String): InputStream? {
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        val webDavReady = EchoRemotePlaybackAuthRegistry.isWebDavAuthReadyForUris(listOf(uri))
        val subsonicReady = EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(listOf(uri))
        if (!canOpenReplayGainStream(uri, webDavReady, subsonicReady)) return null
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
