package app.echo.android

import androidx.compose.runtime.saveable.listSaver
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.GenreSummary

// Save only the selected detail's metadata, never a library or a playlist's track collection.
internal val AlbumNavigationSaver = listSaver<AlbumSummary?, Any?>(
    save = { if (it == null) emptyList() else listOf(it.albumKey, it.title, it.albumArtist, it.artist, it.artworkUri,
        it.trackCount, it.durationMs, it.year, it.addedAtSeconds) },
    restore = { if (it.isEmpty()) null else AlbumSummary(it[0] as String, it[1] as String, it[2] as String?,
        it[3] as String?, it[4] as String?, it[5] as Int, it[6] as Long, it[7] as Int?, it[8] as Long) },
)

internal val ArtistNavigationSaver = listSaver<ArtistSummary?, Any?>(
    save = { if (it == null) emptyList() else listOf(it.artistKey, it.name, it.artworkUri, it.albumCount, it.trackCount, it.durationMs) },
    restore = { if (it.isEmpty()) null else ArtistSummary(it[0] as String, it[1] as String, it[2] as String?,
        it[3] as Int, it[4] as Int, it[5] as Long) },
)

internal val GenreNavigationSaver = listSaver<GenreSummary?, Any?>(
    save = { if (it == null) emptyList() else listOf(it.genreKey, it.name, it.artworkUri, it.albumCount, it.trackCount, it.durationMs) },
    restore = { if (it.isEmpty()) null else GenreSummary(it[0] as String, it[1] as String, it[2] as String?,
        it[3] as Int, it[4] as Int, it[5] as Long) },
)

internal val FolderNavigationSaver = listSaver<FolderSummary?, Any?>(
    save = { if (it == null) emptyList() else listOf(it.folderKey, it.path, it.artworkUri, it.trackCount, it.albumCount,
        it.artistCount, it.durationMs, it.totalSizeBytes, it.latestModifiedSeconds) },
    restore = { if (it.isEmpty()) null else FolderSummary(it[0] as String, it[1] as String?, it[2] as String?,
        it[3] as Int, it[4] as Int, it[5] as Int, it[6] as Long, it[7] as Long, it[8] as Long) },
)

internal val PlaylistNavigationSaver = listSaver<EchoPlaylist?, Any?>(
    save = { if (it == null) emptyList() else listOf(it.id, it.name, it.trackCount, it.artworkUri, it.updatedAtEpochMs, it.source, it.pinnedToHome) },
    // The library owner loads detail tracks and resolves actions by playlist ID.
    restore = { if (it.isEmpty()) null else EchoPlaylist(id = it[0] as String, name = it[1] as String,
        trackCount = it[2] as Int, artworkUri = it[3] as String?, updatedAtEpochMs = it[4] as Long,
        source = it[5] as String, pinnedToHome = it[6] as Boolean) },
)
