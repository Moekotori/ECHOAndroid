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
