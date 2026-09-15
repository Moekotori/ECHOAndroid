package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryOfflinePin
import app.echo.android.model.library.LibraryOfflinePinStatus
import app.echo.android.model.library.LibraryOfflinePolicy

@Composable
internal fun libraryOfflinePinLabel(pin: LibraryOfflinePin?): String = when (pin?.status) {
    null -> stringResource(L10nR.string.library_offline_download)
    LibraryOfflinePinStatus.Queued -> stringResource(L10nR.string.library_offline_queued)
    LibraryOfflinePinStatus.Downloading -> stringResource(
        L10nR.string.library_offline_downloading,
        pin.readyCount,
        pin.trackCount,
    )
    LibraryOfflinePinStatus.Ready -> stringResource(L10nR.string.library_offline_ready)
    LibraryOfflinePinStatus.Partial -> stringResource(
        L10nR.string.library_offline_partial,
        pin.readyCount,
        pin.trackCount,
    )
    LibraryOfflinePinStatus.Failed -> stringResource(L10nR.string.library_offline_failed)
}

internal fun libraryOfflinePinIcon(pin: LibraryOfflinePin?): ImageVector =
    if (pin?.status == LibraryOfflinePinStatus.Ready) {
        Icons.Rounded.DownloadDone
    } else {
        Icons.Rounded.Download
    }

internal fun canShowAlbumOffline(albumKey: String, pin: LibraryOfflinePin?): Boolean =
    pin != null || LibraryOfflinePolicy.canPinAlbumKey(albumKey)

internal fun canShowPlaylistOffline(
    playlistSource: String,
    tracks: List<EchoTrack>,
    pin: LibraryOfflinePin?,
): Boolean {
    if (pin != null) return true
    if (LibraryOfflinePolicy.canPinSource(playlistSource)) return true
    return tracks.any { LibraryOfflinePolicy.canPinSource(it.source.id) }
}
