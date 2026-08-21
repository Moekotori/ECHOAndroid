package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@UnstableApi
internal class EchoPlaybackSessionRestorer(
    private val scope: CoroutineScope,
    private val store: () -> EchoPlaybackSessionStore,
    private val player: () -> Player?,
    private val enginePolicy: () -> EchoPlaybackEnginePolicy?,
) {
    private val mutex = Mutex()

    @Volatile
    var restoreCompleted: Boolean = false
        private set

    @Volatile
    private var lastPersistSignature: String? = null

    @Volatile
    private var lastPersistPositionBucket: Long = -1L

    @Volatile
    private var heldSavedPlayWhenReady: Boolean? = null

    suspend fun restore(userRequestedPlay: Boolean): EchoPlaybackSessionSnapshot? =
        mutex.withLock {
            val current = player() ?: return@withLock null
            if (!shouldRestoreIntoEmptyPlayer(current.mediaItemCount)) {
                restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
                if (userRequestedPlay) {
                    withContext(Dispatchers.Main.immediate) {
                        player()?.play()
                    }
                    heldSavedPlayWhenReady = null
                }
                return@withLock current.toPlaybackSessionSnapshot()
            }
            val snapshot = try {
                withContext(Dispatchers.IO) { store().load() }
            } catch (_: Exception) {
                restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = true)
                return@withLock null
            }
            if (snapshot == null || snapshot.queue.isEmpty() || snapshot.currentIndex !in snapshot.queue.indices) {
                restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
                return@withLock null
            }
            val resolvedQueue = snapshot.queue.map { track ->
                val playUri = EchoPlaybackProcessRuntime.resolvePlayUri(track.id, track.uri)
                if (playUri == track.uri) track else track.copy(uri = playUri)
            }
            val resolved = snapshot.copy(queue = resolvedQueue)
            val queueUris = resolved.queue.map { it.uri }
            val unresolvedEchoLink = queueHasUnresolvedEchoLinkUris(queueUris)
            val play = shouldPlayAfterSessionRestore(userRequestedPlay) &&
                !unresolvedEchoLink &&
                shouldAllowRestoredPlayWhenReady(
                    playWhenReady = true,
                    queueRequiresWebDavAuth = queueRequiresWebDavAuth(queueUris),
                    webDavAuthReady = EchoRemotePlaybackAuthRegistry.isWebDavAuthReadyForUris(queueUris),
                    queueRequiresSubsonicAuth = queueRequiresSubsonicAuth(queueUris),
                    subsonicAuthReady = EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(queueUris),
                )
            heldSavedPlayWhenReady = if (
                shouldHoldSavedPlayWhenReadyAfterForcedPause(
                    savedPlayWhenReady = snapshot.playWhenReady,
                    restoredPlayWhenReady = play,
                )
            ) {
                true
            } else {
                null
            }
            withContext(Dispatchers.Main.immediate) {
                val live = player() ?: return@withContext
                if (!shouldRestoreIntoEmptyPlayer(live.mediaItemCount)) return@withContext
                enginePolicy()?.mergeSampleRates(resolved.queue.associate { it.id to it.sampleRateHz })
                enginePolicy()?.mergeReplayGainUris(resolved.queue.associate { it.id to it.uri })
                live.applyPlaybackSessionSnapshot(
                    resolved,
                    play = play,
                    preparePlayer = shouldPrepareRestoredQueue(unresolvedEchoLink),
                )
            }
            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
            resolved
        }

    fun persistFromPlayer(force: Boolean = false, persistBecauseOfSeek: Boolean = false) {
        if (!restoreCompleted) return
        val live = player() ?: return
        if (live.mediaItemCount <= 0) {
            if (!PlaybackSessionPolicy.shouldPersistNullSavedSession(0)) return
            lastPersistSignature = null
            lastPersistPositionBucket = -1L
            EchoPlaybackProcessRuntime.launchIo {
                runCatching { store().save(null) }
            }
            return
        }
        val snapshot = live.toPlaybackSessionSnapshot() ?: return
        val (playWhenReady, nextHeld) = persistSnapshotPlayWhenReady(
            playerPlayWhenReady = snapshot.playWhenReady,
            heldSavedPlayWhenReady = heldSavedPlayWhenReady,
        )
        heldSavedPlayWhenReady = nextHeld
        val persistSnapshot = snapshot.copy(playWhenReady = playWhenReady)
        val mediaIds = persistSnapshot.queue.map { it.id }
        val signature = playbackSessionPersistSignature(
            currentIndex = persistSnapshot.currentIndex,
            playWhenReady = persistSnapshot.playWhenReady,
            mediaIds = mediaIds,
            shuffleEnabled = persistSnapshot.shuffleEnabled,
            repeatMode = persistSnapshot.repeatMode.toPlayerRepeatMode(),
            playbackSpeed = persistSnapshot.playbackSpeed,
            playbackPitch = persistSnapshot.playbackPitch,
        )
        val positionBucket = persistSnapshot.positionMs / PersistPositionBucketMs
        if (
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = force,
                signature = signature,
                lastSignature = lastPersistSignature,
                positionBucket = positionBucket,
                lastPositionBucket = lastPersistPositionBucket,
                persistBecauseOfSeek = persistBecauseOfSeek,
            )
        ) {
            return
        }
        lastPersistSignature = signature
        lastPersistPositionBucket = positionBucket
        EchoPlaybackProcessRuntime.launchIo {
            runCatching { store().save(persistSnapshot) }
        }
    }
}

private const val PersistPositionBucketMs = 15_000L
