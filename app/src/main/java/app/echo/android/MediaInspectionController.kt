package app.echo.android

import android.content.Context
import app.echo.android.data.*

/** Data owners for explicit media inspection; no work begins in the constructor. */
internal class MediaInspectionController(context: Context, repository: EchoLibraryRepository) {
    val encoding = TrackEncodingRepairRepository(context, repository)
    val images = AlbumImagesRepository(context)
    val playlists = ScannedPlaylistRepository(repository)
}
