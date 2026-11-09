package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import app.echo.android.model.playback.EchoTrackRef

/** Private files stay outside music-library favorites, including on restored queues. */
internal fun EchoTrackRef?.supportsMusicFavorite(): Boolean =
    this?.sourceId != "file-playlist" && this?.id?.startsWith("file:") != true

internal val LocalMusicFavoritesAvailable = staticCompositionLocalOf { true }

@Composable
@ReadOnlyComposable
internal fun EchoTrackRef?.showsMusicFavorite(): Boolean =
    LocalMusicFavoritesAvailable.current && supportsMusicFavorite()
