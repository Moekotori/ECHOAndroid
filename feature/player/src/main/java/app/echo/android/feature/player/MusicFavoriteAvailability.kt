package app.echo.android.feature.player

import app.echo.android.model.playback.EchoTrackRef

/** Private files stay outside music-library favorites, including on restored queues. */
internal fun EchoTrackRef?.supportsMusicFavorite(): Boolean =
    this?.sourceId != "file-playlist" && this?.id?.startsWith("file:") != true
