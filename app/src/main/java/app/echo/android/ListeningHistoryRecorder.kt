package app.echo.android

import app.echo.android.data.ListeningHistoryListen
import app.echo.android.data.ListeningHistoryPolicy
import app.echo.android.data.ListeningHistoryRepository
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.model.radio.EchoRadioStation
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 把播放状态折算成“有效收听”写入 [ListeningHistoryRepository]。
 *
 * 达到 [ListeningHistoryPolicy.countsAsListen] 的阈值时立刻插入一行，之后在暂停、切歌或
 * 每累计一分钟时抬高 listenedMs。这样即使进程被杀，已经算数的收听也不会丢。
 * 所有数据库写入经同一个 channel 串行执行，保证“先插入、后更新”的顺序。
 */
internal class ListeningHistoryRecorder(
    private val scope: CoroutineScope,
    private val repository: ListeningHistoryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Record {
        var eventId: Long = 0L
    }

    private data class ActiveListen(
        val track: EchoTrackRef,
        val durationMs: Long,
        val startedAtEpochMs: Long,
        val accumulatedMs: Long = 0L,
        val lastTickEpochMs: Long = 0L,
        val lastPositionMs: Long = 0L,
        val wasPlaying: Boolean = false,
        val record: Record? = null,
        val lastPersistedMs: Long = 0L,
    )

    private var active: ActiveListen? = null
    private var collectJob: Job? = null
    private val writes = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for (write in writes) {
                try {
                    write()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    EchoErrorLog.record(EchoErrorSource.Library, "Listening history write failed", throwable = error)
                }
            }
        }
    }

    /** 从播放服务发布的 surface 快照驱动，只依赖播放进程，不依赖界面是否存在。 */
    fun start(surface: StateFlow<EchoPlaybackSurfaceSnapshot>) {
        collectJob?.cancel()
        collectJob = scope.launch {
            surface
                .distinctUntilChanged { previous, next ->
                    previous.mediaId == next.mediaId &&
                        previous.hasTrack == next.hasTrack &&
                        previous.isPlaying == next.isPlaying &&
                        previous.positionMs / 1_000L == next.positionMs / 1_000L
                }
                .collect { snapshot ->
                    val (status, position) = snapshot.toHistoryPlayback()
                    handle(status, position)
                }
        }
    }

    private fun handle(status: EchoPlaybackStatus, position: PlaybackPositionState) {
        val nowMs = now()
        val track = status.track?.takeUnless { EchoRadioStation.isRadio(it.id) }
        val current = active

        if (track == null) {
            if (current != null) {
                flush(tick(current, playing = false, positionMs = current.lastPositionMs, nowMs = nowMs))
            }
            if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(status.state)) active = null
            return
        }

        val positionMs = position.positionMs.coerceAtLeast(0L)
        val durationMs = maxOf(track.durationMs, position.durationMs, 0L)
        val restarted = current != null && current.track.id == track.id &&
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = current.record != null,
                previousPositionMs = current.lastPositionMs,
                currentPositionMs = positionMs,
            )

        val next = if (current == null || current.track.id != track.id || restarted) {
            current?.let { flush(tick(it, playing = false, positionMs = it.lastPositionMs, nowMs = nowMs)) }
            ActiveListen(
                track = track,
                durationMs = durationMs,
                startedAtEpochMs = nowMs,
                lastTickEpochMs = if (status.isPlaying) nowMs else 0L,
                lastPositionMs = positionMs,
                wasPlaying = status.isPlaying,
            )
        } else {
            tick(current.copy(durationMs = maxOf(current.durationMs, durationMs)), status.isPlaying, positionMs, nowMs)
        }

        active = when {
            next.record == null && ListeningHistoryPolicy.countsAsListen(next.durationMs, next.accumulatedMs) -> insert(next)
            next.record != null && (!status.isPlaying || next.accumulatedMs - next.lastPersistedMs >= PERSIST_EVERY_MS) -> persist(next)
            else -> next
        }
    }

    private fun tick(listen: ActiveListen, playing: Boolean, positionMs: Long, nowMs: Long): ActiveListen =
        listen.copy(
            accumulatedMs = LastFmScrobbleRules.accumulatedPlayMs(
                previouslyAccumulatedMs = listen.accumulatedMs,
                wasPlaying = listen.wasPlaying,
                lastTickEpochMs = listen.lastTickEpochMs,
                nowEpochMs = nowMs,
            ),
            lastTickEpochMs = if (playing) nowMs else 0L,
            lastPositionMs = positionMs,
            wasPlaying = playing,
        )

    /** 结束一次收听：已记录的补上最终时长，未记录但刚好达标的补插一行。 */
    private fun flush(listen: ActiveListen) {
        when {
            listen.record != null -> persist(listen)
            ListeningHistoryPolicy.countsAsListen(listen.durationMs, listen.accumulatedMs) -> insert(listen)
        }
    }

    private fun insert(listen: ActiveListen): ActiveListen {
        val record = Record()
        val row = ListeningHistoryListen(
            trackId = listen.track.id,
            title = listen.track.title.trim(),
            artist = listen.track.artist.trim(),
            album = listen.track.album?.trim()?.takeIf(String::isNotBlank),
            artworkUri = listen.track.artworkUri,
            source = listen.track.sourceId,
            durationMs = listen.durationMs,
            listenedMs = listen.accumulatedMs,
            startedAtEpochMs = listen.startedAtEpochMs,
        )
        writes.trySend { record.eventId = repository.recordListen(row) }
        return listen.copy(record = record, lastPersistedMs = listen.accumulatedMs)
    }

    private fun persist(listen: ActiveListen): ActiveListen {
        val record = listen.record ?: return listen
        if (listen.accumulatedMs <= listen.lastPersistedMs) return listen
        val listenedMs = listen.accumulatedMs
        writes.trySend { repository.updateListenedMs(record.eventId, listenedMs) }
        return listen.copy(lastPersistedMs = listenedMs)
    }

    private companion object {
        const val PERSIST_EVERY_MS = 60_000L
    }
}

private fun EchoPlaybackSurfaceSnapshot.toHistoryPlayback(): Pair<EchoPlaybackStatus, PlaybackPositionState> {
    val track = mediaId
        ?.takeIf { hasTrack && it.isNotBlank() }
        ?.let { id ->
            EchoTrackRef(
                id = id,
                uri = playUri.orEmpty(),
                title = title,
                artist = artist,
                album = album,
                artworkUri = artworkUri,
                durationMs = durationMs,
            )
        }
    val status = EchoPlaybackStatus(
        state = when {
            isPlaying -> EchoPlaybackState.Playing
            hasTrack -> EchoPlaybackState.Paused
            else -> EchoPlaybackState.Idle
        },
        track = track,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
    )
    return status to PlaybackPositionState(positionMs = positionMs, durationMs = durationMs)
}
