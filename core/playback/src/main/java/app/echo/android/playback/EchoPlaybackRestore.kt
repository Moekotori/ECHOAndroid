package app.echo.android.playback

import app.echo.android.model.playback.EchoLinkPlaybackUri

fun shouldSkipSavedSessionRestore(pendingReplacesQueue: Iterable<Boolean>): Boolean =
    pendingReplacesQueue.any { it }

fun shouldRestoreSavedSessionBeforeFlushingPending(pendingReplacesQueue: Iterable<Boolean>): Boolean =
    !shouldSkipSavedSessionRestore(pendingReplacesQueue)

fun shouldQueueControllerActionUntilSessionReady(sessionReadyForCommands: Boolean): Boolean =
    !sessionReadyForCommands

fun pendingPlayPauseShouldPlay(playWhenReady: Boolean): Boolean = !playWhenReady

fun shouldMarkSavedSessionRestoreComplete(sessionLoadFailed: Boolean): Boolean = !sessionLoadFailed

fun shouldAbandonSavedSessionRestoreForPendingQueueReplace(
    pendingReplacesQueue: Iterable<Boolean>,
): Boolean = shouldSkipSavedSessionRestore(pendingReplacesQueue)

fun shouldAllowRestoredPlayWhenReady(
    playWhenReady: Boolean,
    queueRequiresWebDavAuth: Boolean,
    webDavAuthReady: Boolean,
    queueRequiresSubsonicAuth: Boolean = false,
    subsonicAuthReady: Boolean = true,
): Boolean = playWhenReady &&
    (!queueRequiresWebDavAuth || webDavAuthReady) &&
    (!queueRequiresSubsonicAuth || subsonicAuthReady)

fun shouldApplyPendingRestorePlay(
    pendingRestorePlayUntilAuth: Boolean,
    queueRequiresWebDavAuth: Boolean,
    webDavAuthReady: Boolean,
    queueRequiresSubsonicAuth: Boolean = false,
    subsonicAuthReady: Boolean = true,
): Boolean = pendingRestorePlayUntilAuth &&
    shouldAllowRestoredPlayWhenReady(
        playWhenReady = true,
        queueRequiresWebDavAuth = queueRequiresWebDavAuth,
        webDavAuthReady = webDavAuthReady,
        queueRequiresSubsonicAuth = queueRequiresSubsonicAuth,
        subsonicAuthReady = subsonicAuthReady,
    )

fun shouldRestoreIntoEmptyPlayer(mediaItemCount: Int): Boolean = mediaItemCount <= 0

fun shouldPlayAfterSessionRestore(userRequestedPlay: Boolean): Boolean = userRequestedPlay

fun shouldHoldSavedPlayWhenReadyAfterForcedPause(
    savedPlayWhenReady: Boolean,
    restoredPlayWhenReady: Boolean,
): Boolean = savedPlayWhenReady && !restoredPlayWhenReady

fun persistSnapshotPlayWhenReady(
    playerPlayWhenReady: Boolean,
    heldSavedPlayWhenReady: Boolean?,
): Pair<Boolean, Boolean?> {
    if (heldSavedPlayWhenReady == true && !playerPlayWhenReady) {
        return true to null
    }
    return playerPlayWhenReady to if (playerPlayWhenReady) null else heldSavedPlayWhenReady
}

fun shouldPrepareRestoredQueue(unresolvedEchoLinkUris: Boolean): Boolean = !unresolvedEchoLinkUris

fun queueHasUnresolvedEchoLinkUris(uris: Iterable<String>): Boolean =
    uris.any { uri -> EchoLinkPlaybackUri.trackIdFromPersistUri(uri) != null }

fun shouldReplaceRegisteredRemoteCredentials(
    incomingEmpty: Boolean,
    registryAlreadyReady: Boolean,
    allowClearIfEmpty: Boolean,
): Boolean {
    if (!incomingEmpty) return true
    if (allowClearIfEmpty) return true
    return !registryAlreadyReady
}

fun replayGainUriForMediaId(
    mediaId: String?,
    replayGainUrisByMediaId: Map<String, String>,
): String? = mediaId?.let { id -> replayGainUrisByMediaId[id]?.takeIf(String::isNotBlank) }

fun mergePlayerQueueReplayGainUris(
    existingUrisByMediaId: Map<String, String>,
    playerQueueUrisByMediaId: Map<String, String>,
): Map<String, String> {
    if (playerQueueUrisByMediaId.isEmpty()) return existingUrisByMediaId
    val merged = LinkedHashMap<String, String>(existingUrisByMediaId.size + playerQueueUrisByMediaId.size)
    merged.putAll(existingUrisByMediaId)
    playerQueueUrisByMediaId.forEach { (mediaId, uri) ->
        if (mediaId.isNotBlank() && uri.isNotBlank()) {
            merged[mediaId] = uri
        }
    }
    return merged
}
