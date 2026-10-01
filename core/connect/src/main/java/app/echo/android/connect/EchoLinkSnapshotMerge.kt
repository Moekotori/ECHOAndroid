package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteMessage
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot

/** V2 events omit V1-only details. Keep omitted fields; explicit empty values still clear them. */
internal fun mergeEchoLinkSnapshot(
    previous: EchoRemotePlaybackSnapshot,
    message: EchoRemoteMessage.StatusSnapshot,
): EchoRemotePlaybackSnapshot {
    val incoming = message.payload
    val track = incoming.track?.let { track ->
        if (message.trackArtworkIncluded || track.id == null) track else {
            val known = previous.track?.takeIf { it.id == track.id }
                ?: previous.queue.items.firstOrNull { it.id == track.id }
            if (known?.artworkUrl == null) track else track.copy(artworkUrl = known.artworkUrl)
        }
    }
    val queue = if (message.queueIncluded) incoming.queue else {
        val currentId = incoming.track?.id ?: previous.queue.currentTrackId
        previous.queue.copy(currentTrackId = currentId,
            revision = if (incoming.queueIdentityAvailable) incoming.queue.revision else previous.queue.revision,
            currentQueueId = if (incoming.queueIdentityAvailable) incoming.queue.currentQueueId else previous.queue.currentQueueId)
    }
    return incoming.copy(
        track = track,
        queue = queue,
        volumeControlEnabled = if (message.volumeControlIncluded) incoming.volumeControlEnabled else previous.volumeControlEnabled,
        volumeLockedReason = if (message.volumeControlIncluded) incoming.volumeLockedReason else previous.volumeLockedReason,
        outputMode = if (message.outputIncluded) incoming.outputMode else previous.outputMode,
        playbackOrder = if (message.playbackOrderIncluded) incoming.playbackOrder else previous.playbackOrder,
        supportsAtomicPhoneQueue = incoming.supportsAtomicPhoneQueue || previous.supportsAtomicPhoneQueue,
        queueIdentityAvailable = incoming.queueIdentityAvailable || previous.queueIdentityAvailable,
    )
}
