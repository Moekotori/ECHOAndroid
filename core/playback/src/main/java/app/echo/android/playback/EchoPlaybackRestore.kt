package app.echo.android.playback

fun shouldSkipSavedSessionRestore(pendingReplacesQueue: Iterable<Boolean>): Boolean =
    pendingReplacesQueue.any { it }
