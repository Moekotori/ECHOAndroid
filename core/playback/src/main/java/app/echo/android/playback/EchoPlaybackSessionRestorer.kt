package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    suspend fun restore(userRequestedPlay: Boolean): EchoPlaybackSessionSnapshot? =
        mutex.withLock {
            val current = player() ?: return@withLock null
            if (!shouldRestoreIntoEmptyPlayer(current.mediaItemCount)) {
                restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
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
            val queueUris = snapshot.queue.map { it.uri }
            val play = shouldPlayAfterSessionRestore(userRequestedPlay) &&
                shouldAllowRestoredPlayWhenReady(
                    playWhenReady = true,
                    queueRequiresWebDavAuth = queueRequiresWebDavAuth(queueUris),
                    webDavAuthReady = EchoRemotePlaybackAuthRegistry.isWebDavAuthReadyForUris(queueUris),
                    queueRequiresSubsonicAuth = queueRequiresSubsonicAuth(queueUris),
                    subsonicAuthReady = EchoRemotePlaybackAuthRegistry.isSubsonicAuthReadyForUris(queueUris),
                )
            withContext(Dispatchers.Main.immediate) {
                val live = player() ?: return@withContext
                if (!shouldRestoreIntoEmptyPlayer(live.mediaItemCount)) return@withContext
                enginePolicy()?.mergeSampleRates(snapshot.queue.associate { it.id to it.sampleRateHz })
                enginePolicy()?.mergeReplayGainUris(snapshot.queue.associate { it.id to it.uri })
                live.applyPlaybackSessionSnapshot(snapshot, play = play)
            }
            restoreCompleted = shouldMarkSavedSessionRestoreComplete(sessionLoadFailed = false)
            snapshot
        }

    fun persistFromPlayer(force: Boolean = false) {
        if (!restoreCompleted) return
        val live = player() ?: return
        val snapshot = live.toPlaybackSessionSnapshot() ?: return
        val mediaIds = snapshot.queue.map { it.id }
        val signature = playbackSessionPersistSignature(
            currentIndex = snapshot.currentIndex,
            playWhenReady = snapshot.playWhenReady,
            mediaIds = mediaIds,
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = snapshot.repeatMode.toPlayerRepeatMode(),
            playbackSpeed = snapshot.playbackSpeed,
            playbackPitch = snapshot.playbackPitch,
        )
        val positionBucket = snapshot.positionMs / PersistPositionBucketMs
        if (
            PlaybackSessionPolicy.shouldSkipUnchangedSessionPersist(
                force = force,
                signature = signature,
                lastSignature = lastPersistSignature,
                positionBucket = positionBucket,
                lastPositionBucket = lastPersistPositionBucket,
            )
        ) {
            return
        }
        lastPersistSignature = signature
        lastPersistPositionBucket = positionBucket
        scope.launch(Dispatchers.IO) {
            runCatching { store().save(snapshot) }
        }
    }
}

private const val PersistPositionBucketMs = 15_000L
