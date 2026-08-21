package app.echo.android.playback

fun shouldSkipSavedSessionRestore(pendingReplacesQueue: Iterable<Boolean>): Boolean =
    pendingReplacesQueue.any { it }

fun shouldRestoreSavedSessionBeforeFlushingPending(pendingReplacesQueue: Iterable<Boolean>): Boolean =
    !shouldSkipSavedSessionRestore(pendingReplacesQueue)

fun shouldQueueControllerActionUntilSessionReady(sessionReadyForCommands: Boolean): Boolean =
    !sessionReadyForCommands

fun pendingPlayPauseShouldPlay(currentlyPlaying: Boolean): Boolean = !currentlyPlaying

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
