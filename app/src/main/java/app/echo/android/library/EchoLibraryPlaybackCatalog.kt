package app.echo.android.library

import android.content.Context
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.LibraryFavoriteEntity
import app.echo.android.data.LibraryFavoritePolicy
import app.echo.android.data.LibraryFavoriteSnapshot
import app.echo.android.data.LibrarySmartPlaylistPolicy
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.feature.library.R as LibraryR
import app.echo.android.R as AppR
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.GenreSummary
import app.echo.android.model.library.LibrarySmartPlaylistKind
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.model.radio.EchoRadioStation
import app.echo.android.playback.EchoPlaybackBrowseItem
import app.echo.android.playback.EchoPlaybackBrowseKind
import app.echo.android.playback.EchoPlaybackCatalog
import app.echo.android.playback.EchoPlaybackLibraryIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class EchoLibraryPlaybackCatalog(
    private val database: EchoLibraryDatabase,
    private val context: Context,
    private val loadRadioStations: suspend () -> List<EchoRadioStation> = { emptyList() },
) : EchoPlaybackCatalog {
    override suspend fun root(): EchoPlaybackBrowseItem = categoryItem(
        mediaId = EchoPlaybackLibraryIds.ROOT,
        title = context.getString(AppR.string.app_name),
        kind = EchoPlaybackBrowseKind.Root,
    )

    override suspend fun children(
        parentId: String,
        page: Int,
        pageSize: Int,
    ): List<EchoPlaybackBrowseItem> = withContext(Dispatchers.IO) {
        val (limit, offset) = EchoPlaybackLibraryIds.browseRange(page, pageSize)
        when (parentId) {
            EchoPlaybackLibraryIds.ROOT ->
                rootChildren().let { items ->
                    val from = offset.coerceAtMost(items.size)
                    items.subList(from, (from + limit).coerceAtMost(items.size))
                }
            EchoPlaybackLibraryIds.ALBUMS ->
                database.trackDao().listAlbumsForBrowse(limit, offset).map { it.toBrowseItem() }
            EchoPlaybackLibraryIds.ARTISTS ->
                database.trackDao().listArtistsForBrowse(limit, offset).map { it.toBrowseItem(context) }
            EchoPlaybackLibraryIds.PLAYLISTS -> playlistBrowsePage(limit, offset)
            EchoPlaybackLibraryIds.FAVORITES ->
                database.playlistDao().listFavoriteTracksForBrowse(limit, offset).map { it.toBrowseItem() }
            EchoPlaybackLibraryIds.TRACKS ->
                database.trackDao().listRecentTracksForBrowse(limit, offset).map { it.toBrowseItem() }
            EchoPlaybackLibraryIds.FOLDERS ->
                database.trackDao().listFoldersForBrowse(limit, offset).map { it.toBrowseItem(context) }
            EchoPlaybackLibraryIds.GENRES ->
                database.trackDao().listGenresForBrowse(limit, offset).map { it.toBrowseItem(context) }
            EchoPlaybackLibraryIds.RADIO -> radioBrowsePage(limit, offset)
            else -> {
                val albumKey = EchoPlaybackLibraryIds.albumKey(parentId)
                if (albumKey != null) {
                    return@withContext database.trackDao()
                        .listTracksByAlbumForBrowse(albumKey, limit, offset)
                        .map { it.toBrowseItem() }
                }
                val artistKey = EchoPlaybackLibraryIds.artistKey(parentId)
                if (artistKey != null) {
                    return@withContext database.trackDao()
                        .listTracksByArtistForBrowse(artistKey, limit, offset)
                        .map { it.toBrowseItem() }
                }
                val playlistId = EchoPlaybackLibraryIds.playlistId(parentId)
                if (playlistId != null) {
                    return@withContext smartPlaylistTracksForBrowse(playlistId, limit, offset)
                        ?: database.playlistDao()
                            .listPlaylistTracksForBrowse(playlistId, limit, offset)
                            .map { it.toBrowseItem() }
                }
                val folderKey = EchoPlaybackLibraryIds.folderKey(parentId)
                if (folderKey != null) {
                    return@withContext database.trackDao()
                        .listTracksByFolderForBrowse(folderKey, limit, offset)
                        .map { it.toBrowseItem() }
                }
                val genreKey = EchoPlaybackLibraryIds.genreKey(parentId)
                if (genreKey != null) {
                    return@withContext database.trackDao()
                        .listTracksByGenreForBrowse(genreKey, limit, offset)
                        .map { it.toBrowseItem() }
                }
                emptyList()
            }
        }
    }

    override suspend fun item(mediaId: String): EchoPlaybackBrowseItem? = withContext(Dispatchers.IO) {
        when (mediaId) {
            EchoPlaybackLibraryIds.ROOT -> root()
            EchoPlaybackLibraryIds.ALBUMS,
            EchoPlaybackLibraryIds.ARTISTS,
            EchoPlaybackLibraryIds.PLAYLISTS,
            EchoPlaybackLibraryIds.FAVORITES,
            EchoPlaybackLibraryIds.TRACKS,
            EchoPlaybackLibraryIds.FOLDERS,
            EchoPlaybackLibraryIds.GENRES,
            EchoPlaybackLibraryIds.RADIO,
            -> rootChildren().firstOrNull { it.mediaId == mediaId }
            else -> {
                EchoPlaybackLibraryIds.albumKey(mediaId)?.let { key ->
                    return@withContext database.trackDao().getAlbumSummary(key)?.toBrowseItem()
                }
                EchoPlaybackLibraryIds.artistKey(mediaId)?.let { key ->
                    return@withContext database.trackDao().getArtistSummary(key)?.toBrowseItem(context)
                }
                EchoPlaybackLibraryIds.playlistId(mediaId)?.let { id ->
                    virtualPlaylistItem(id)?.let { return@withContext it }
                    val playlist = database.playlistDao().getPlaylist(id) ?: return@withContext null
                    return@withContext EchoPlaybackBrowseItem(
                        mediaId = EchoPlaybackLibraryIds.playlist(playlist.id),
                        title = playlist.name,
                        subtitle = playlistCountSubtitle(playlist.trackCount),
                        artworkUri = playlist.artworkUri,
                        browsable = true,
                        playable = playlist.trackCount > 0,
                        kind = EchoPlaybackBrowseKind.Playlist,
                    )
                }
                EchoPlaybackLibraryIds.folderKey(mediaId)?.let { key ->
                    return@withContext database.trackDao().getFolderSummary(key)?.toBrowseItem(context)
                }
                EchoPlaybackLibraryIds.genreKey(mediaId)?.let { key ->
                    return@withContext database.trackDao().getGenreSummary(key)?.toBrowseItem(context)
                }
                if (EchoRadioStation.isRadio(mediaId)) {
                    return@withContext radioStationItem(mediaId)
                }
                database.trackDao().getTrackById(mediaId)?.toBrowseItem()
            }
        }
    }

    override suspend fun search(
        query: String,
        page: Int,
        pageSize: Int,
    ): List<EchoPlaybackBrowseItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val (limit, offset) = EchoPlaybackLibraryIds.browseRange(page, pageSize)
        val perType = (limit + 2).coerceAtLeast(6)
        val dao = database.trackDao()
        val combined = buildList {
            addAll(dao.searchTracks(trimmed, perType).map { it.toBrowseItem() })
            addAll(dao.searchAlbums(trimmed, perType).map { it.toBrowseItem() })
            addAll(dao.searchArtists(trimmed, perType).map { it.toBrowseItem(context) })
            addAll(searchPlaylists(trimmed, perType))
            addAll(
                loadRadioStations()
                    .asSequence()
                    .filter { it.name.contains(trimmed, ignoreCase = true) }
                    .take(perType)
                    .map { it.toBrowseItem() }
                    .toList(),
            )
        }
        val from = offset.coerceAtMost(combined.size)
        combined.subList(from, (from + limit).coerceAtMost(combined.size))
    }

    override suspend fun playableQueue(mediaId: String): List<EchoPlaybackBrowseItem> =
        withContext(Dispatchers.IO) {
            val limit = EchoPlaybackLibraryIds.PLAYABLE_QUEUE_LIMIT
            when (mediaId) {
                EchoPlaybackLibraryIds.FAVORITES ->
                    database.playlistDao().listFavoriteTracksForBrowse(limit, 0).map { it.toBrowseItem() }
                EchoPlaybackLibraryIds.TRACKS ->
                    database.trackDao().listRecentTracksForBrowse(limit, 0).map { it.toBrowseItem() }
                EchoPlaybackLibraryIds.RADIO ->
                    loadRadioStations().take(limit).map { it.toBrowseItem() }
                else -> {
                    EchoPlaybackLibraryIds.albumKey(mediaId)?.let { key ->
                        return@withContext database.trackDao()
                            .listTracksByAlbumForBrowse(key, limit, 0)
                            .map { it.toBrowseItem() }
                    }
                    EchoPlaybackLibraryIds.artistKey(mediaId)?.let { key ->
                        return@withContext database.trackDao()
                            .listTracksByArtistForBrowse(key, limit, 0)
                            .map { it.toBrowseItem() }
                    }
                    EchoPlaybackLibraryIds.playlistId(mediaId)?.let { id ->
                        return@withContext smartPlaylistTracksForBrowse(id, limit, 0)
                            ?: database.playlistDao()
                                .listPlaylistTracksForBrowse(id, limit, 0)
                                .map { it.toBrowseItem() }
                    }
                    EchoPlaybackLibraryIds.folderKey(mediaId)?.let { key ->
                        return@withContext database.trackDao()
                            .listTracksByFolderForBrowse(key, limit, 0)
                            .map { it.toBrowseItem() }
                    }
                    EchoPlaybackLibraryIds.genreKey(mediaId)?.let { key ->
                        return@withContext database.trackDao()
                            .listTracksByGenreForBrowse(key, limit, 0)
                            .map { it.toBrowseItem() }
                    }
                    if (EchoRadioStation.isRadio(mediaId)) {
                        return@withContext listOfNotNull(radioStationItem(mediaId))
                    }
                    if (EchoPlaybackLibraryIds.isTrackMediaId(mediaId)) {
                        val track = database.trackDao().getTrackById(mediaId) ?: return@withContext emptyList()
                        val albumKey = track.albumKey.takeIf { it.isNotBlank() }
                        val albumTracks = albumKey?.let { key ->
                            database.trackDao().listTracksByAlbumForBrowse(key, limit, 0)
                        }.orEmpty()
                        if (albumTracks.size > 1 && albumTracks.any { it.id == mediaId }) {
                            albumTracks.map { it.toBrowseItem() }
                        } else {
                            listOf(track.toBrowseItem())
                        }
                    } else {
                        emptyList()
                    }
                }
            }
        }

    override suspend fun isFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val id = trackId.trim()
        if (id.isEmpty()) return@withContext false
        database.playlistDao().isFavorite(id)
    }

    override suspend fun toggleFavorite(trackId: String): Boolean? = withContext(Dispatchers.IO) {
        val id = trackId.trim()
        if (id.isEmpty()) return@withContext null
        val dao = database.playlistDao()
        val current = LibraryFavoriteSnapshot(dao.getFavoriteTrackIds().toSet())
        val next = LibraryFavoritePolicy.toggle(current, id)
        val liked = LibraryFavoritePolicy.isLiked(next, id)
        if (liked) {
            dao.upsertFavorite(
                LibraryFavoriteEntity(
                    trackId = id,
                    favoritedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } else {
            dao.deleteFavorite(id)
        }
        liked
    }

    private suspend fun playlistBrowsePage(limit: Int, offset: Int): List<EchoPlaybackBrowseItem> {
        val pinned = pinnedPlaylistItems()
        val userOffset = (offset - pinned.size).coerceAtLeast(0)
        val userLimit = when {
            offset >= pinned.size -> limit
            else -> (limit - (pinned.size - offset)).coerceAtLeast(0)
        }
        val pinnedSlice = if (offset < pinned.size) {
            pinned.subList(offset, (offset + limit).coerceAtMost(pinned.size))
        } else {
            emptyList()
        }
        val user = if (userLimit > 0) {
            database.playlistDao()
                .listPlaylistsForBrowse(LibrarySource.MediaStore.id, userLimit, userOffset)
                .map { row ->
                    EchoPlaybackBrowseItem(
                        mediaId = EchoPlaybackLibraryIds.playlist(row.id),
                        title = row.name,
                        subtitle = playlistCountSubtitle(row.trackCount),
                        artworkUri = row.artworkUri,
                        browsable = true,
                        playable = row.trackCount > 0,
                        kind = EchoPlaybackBrowseKind.Playlist,
                    )
                }
        } else {
            emptyList()
        }
        return pinnedSlice + user
    }

    private suspend fun pinnedPlaylistItems(): List<EchoPlaybackBrowseItem> {
        val favoriteCount = database.playlistDao().getFavoriteTrackIds().size
        val favoriteArt = database.playlistDao().observeFavoriteAlbums(1).first().firstOrNull()?.artworkUri
        val liked = EchoPlaybackBrowseItem(
            mediaId = EchoPlaybackLibraryIds.playlist(EchoPlaylist.LikedSongsId),
            title = context.getString(LibraryR.string.feature_library_liked_songs_8d6245),
            subtitle = playlistCountSubtitle(favoriteCount),
            artworkUri = favoriteArt,
            browsable = true,
            playable = favoriteCount > 0,
            kind = EchoPlaybackBrowseKind.Playlist,
        )
        val stats = database.trackDao().observeSmartPlaylistStats().first()
        val smart = LibrarySmartPlaylistPolicy.pinned(stats).map { playlist ->
            EchoPlaybackBrowseItem(
                mediaId = EchoPlaybackLibraryIds.playlist(playlist.id),
                title = smartPlaylistTitle(playlist.smartKind),
                subtitle = playlistCountSubtitle(playlist.trackCount),
                artworkUri = playlist.artworkUri,
                browsable = true,
                playable = playlist.trackCount > 0,
                kind = EchoPlaybackBrowseKind.Playlist,
            )
        }
        return listOf(liked) + smart
    }

    private suspend fun virtualPlaylistItem(id: String): EchoPlaybackBrowseItem? {
        if (LibraryFavoritePolicy.isLikedSongsId(id)) {
            return pinnedPlaylistItems().firstOrNull {
                EchoPlaybackLibraryIds.playlistId(it.mediaId) == EchoPlaylist.LikedSongsId
            }
        }
        if (!LibrarySmartPlaylistPolicy.isSmartPlaylistId(id)) return null
        return pinnedPlaylistItems().firstOrNull {
            EchoPlaybackLibraryIds.playlistId(it.mediaId) == id
        }
    }

    private suspend fun smartPlaylistTracksForBrowse(
        playlistId: String,
        limit: Int,
        offset: Int,
    ): List<EchoPlaybackBrowseItem>? {
        if (LibraryFavoritePolicy.isLikedSongsId(playlistId)) {
            return database.playlistDao().listFavoriteTracksForBrowse(limit, offset).map { it.toBrowseItem() }
        }
        val tracks = when (LibrarySmartPlaylistKind.fromId(playlistId)) {
            LibrarySmartPlaylistKind.Recent ->
                database.trackDao().listRecentlyPlayedTracksForBrowse(limit, offset)
            LibrarySmartPlaylistKind.Frequent ->
                database.trackDao().listFrequentlyPlayedTracksForBrowse(limit, offset)
            LibrarySmartPlaylistKind.Never ->
                database.trackDao().listNeverPlayedTracksForBrowse(limit, offset)
            LibrarySmartPlaylistKind.Added ->
                database.trackDao().listRecentlyAddedTracksForBrowse(limit, offset)
            null -> return null
        }
        return tracks.map { it.toBrowseItem() }
    }

    private fun smartPlaylistTitle(kind: LibrarySmartPlaylistKind?): String = when (kind) {
        LibrarySmartPlaylistKind.Recent -> context.getString(LibraryR.string.feature_library_smart_recent)
        LibrarySmartPlaylistKind.Frequent -> context.getString(LibraryR.string.feature_library_smart_frequent)
        LibrarySmartPlaylistKind.Never -> context.getString(LibraryR.string.feature_library_smart_never)
        LibrarySmartPlaylistKind.Added -> context.getString(LibraryR.string.feature_library_smart_added)
        null -> ""
    }

    private fun playlistCountSubtitle(count: Int): String =
        context.getString(LibraryR.string.library_track_count, count)

    private fun rootChildren(): List<EchoPlaybackBrowseItem> = listOf(
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.ALBUMS,
            title = context.getString(LibraryR.string.feature_library_albums_e68c2b),
            kind = EchoPlaybackBrowseKind.Albums,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.ARTISTS,
            title = context.getString(LibraryR.string.feature_library_artists_1e19fb),
            kind = EchoPlaybackBrowseKind.Artists,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.PLAYLISTS,
            title = context.getString(LibraryR.string.feature_library_playlists_56bf76),
            kind = EchoPlaybackBrowseKind.Playlists,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.FAVORITES,
            title = context.getString(LibraryR.string.feature_library_liked_c8ac9d),
            kind = EchoPlaybackBrowseKind.Favorites,
            playable = true,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.TRACKS,
            title = context.getString(LibraryR.string.feature_library_songs_107b60),
            kind = EchoPlaybackBrowseKind.Tracks,
            playable = true,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.FOLDERS,
            title = context.getString(LibraryR.string.feature_library_folders_cc514a),
            kind = EchoPlaybackBrowseKind.Folders,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.GENRES,
            title = context.getString(LibraryR.string.feature_library_genres_8c2a11),
            kind = EchoPlaybackBrowseKind.Genres,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.RADIO,
            title = context.getString(LibraryR.string.radio_title),
            kind = EchoPlaybackBrowseKind.Radio,
            playable = true,
        ),
    )

    private suspend fun radioBrowsePage(limit: Int, offset: Int): List<EchoPlaybackBrowseItem> {
        val stations = loadRadioStations()
        val from = offset.coerceAtMost(stations.size)
        return stations.subList(from, (from + limit).coerceAtMost(stations.size)).map { it.toBrowseItem() }
    }

    private suspend fun radioStationItem(mediaId: String): EchoPlaybackBrowseItem? =
        loadRadioStations().firstOrNull { EchoRadioStation.MediaIdPrefix + it.id == mediaId }?.toBrowseItem()

    private suspend fun searchPlaylists(query: String, limit: Int): List<EchoPlaybackBrowseItem> {
        val pinned = pinnedPlaylistItems().filter { it.title.contains(query, ignoreCase = true) }
        val user = database.playlistDao()
            .searchPlaylistsForBrowse(LibrarySource.MediaStore.id, query, limit)
            .map { row ->
                EchoPlaybackBrowseItem(
                    mediaId = EchoPlaybackLibraryIds.playlist(row.id),
                    title = row.name,
                    subtitle = playlistCountSubtitle(row.trackCount),
                    artworkUri = row.artworkUri,
                    browsable = true,
                    playable = row.trackCount > 0,
                    kind = EchoPlaybackBrowseKind.Playlist,
                )
            }
        return (pinned + user).distinctBy { it.mediaId }.take(limit)
    }

    private fun categoryItem(
        mediaId: String,
        title: String,
        kind: EchoPlaybackBrowseKind,
        playable: Boolean = false,
    ) = EchoPlaybackBrowseItem(
        mediaId = mediaId,
        title = title,
        browsable = true,
        playable = playable,
        kind = kind,
    )
}

private fun LibraryTrackEntity.toBrowseItem(): EchoPlaybackBrowseItem =
    EchoPlaybackBrowseItem(
        mediaId = id,
        title = title,
        subtitle = artist,
        artworkUri = artworkUri,
        playUri = contentUri,
        persistUri = EchoLinkPlaybackUri.persistableUri(id, contentUri),
        browsable = false,
        playable = true,
        durationMs = durationMs,
        kind = EchoPlaybackBrowseKind.Track,
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
    )

private fun FolderSummary.toBrowseItem(context: Context): EchoPlaybackBrowseItem =
    EchoPlaybackBrowseItem(
        mediaId = EchoPlaybackLibraryIds.folder(folderKey),
        title = browseTitle(context.getString(LibraryR.string.feature_library_unknown_path_e282e8)),
        subtitle = context.getString(LibraryR.string.library_track_count, trackCount),
        artworkUri = artworkUri,
        browsable = true,
        playable = trackCount > 0,
        durationMs = durationMs,
        kind = EchoPlaybackBrowseKind.Folder,
    )

private fun GenreSummary.toBrowseItem(context: Context): EchoPlaybackBrowseItem =
    EchoPlaybackBrowseItem(
        mediaId = EchoPlaybackLibraryIds.genre(genreKey),
        title = name,
        subtitle = context.getString(LibraryR.string.library_track_count, trackCount),
        artworkUri = artworkUri,
        browsable = true,
        playable = trackCount > 0,
        durationMs = durationMs,
        kind = EchoPlaybackBrowseKind.Genre,
    )

private fun EchoRadioStation.toBrowseItem(): EchoPlaybackBrowseItem {
    val track = toTrack()
    return EchoPlaybackBrowseItem(
        mediaId = track.id,
        title = track.title,
        subtitle = track.artist.takeIf { it.isNotBlank() },
        playUri = track.uri,
        persistUri = track.uri,
        browsable = false,
        playable = true,
        kind = EchoPlaybackBrowseKind.RadioStation,
    )
}

private fun AlbumSummary.toBrowseItem(): EchoPlaybackBrowseItem =
    EchoPlaybackBrowseItem(
        mediaId = EchoPlaybackLibraryIds.album(albumKey),
        title = title,
        subtitle = albumArtist ?: artist,
        artworkUri = artworkUri,
        browsable = true,
        playable = trackCount > 0,
        durationMs = durationMs,
        kind = EchoPlaybackBrowseKind.Album,
    )

private fun ArtistSummary.toBrowseItem(context: Context): EchoPlaybackBrowseItem =
    EchoPlaybackBrowseItem(
        mediaId = EchoPlaybackLibraryIds.artist(artistKey),
        title = name,
        subtitle = context.getString(LibraryR.string.library_track_count, trackCount),
        artworkUri = artworkUri,
        browsable = true,
        playable = trackCount > 0,
        durationMs = durationMs,
        kind = EchoPlaybackBrowseKind.Artist,
    )
