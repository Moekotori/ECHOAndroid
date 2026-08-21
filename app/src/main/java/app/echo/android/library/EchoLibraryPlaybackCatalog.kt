package app.echo.android.library

import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.LibraryFavoriteEntity
import app.echo.android.data.LibraryFavoritePolicy
import app.echo.android.data.LibraryFavoriteSnapshot
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.model.i18n.echoText
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.playback.EchoPlaybackBrowseItem
import app.echo.android.playback.EchoPlaybackBrowseKind
import app.echo.android.playback.EchoPlaybackCatalog
import app.echo.android.playback.EchoPlaybackLibraryIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EchoLibraryPlaybackCatalog(
    private val database: EchoLibraryDatabase,
) : EchoPlaybackCatalog {
    override suspend fun root(): EchoPlaybackBrowseItem = categoryItem(
        mediaId = EchoPlaybackLibraryIds.ROOT,
        title = "ECHO",
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
                database.trackDao().listArtistsForBrowse(limit, offset).map { it.toBrowseItem() }
            EchoPlaybackLibraryIds.PLAYLISTS ->
                database.playlistDao()
                    .listPlaylistsForBrowse(LibrarySource.MediaStore.id, limit, offset)
                    .map { row ->
                        EchoPlaybackBrowseItem(
                            mediaId = EchoPlaybackLibraryIds.playlist(row.id),
                            title = row.name,
                            subtitle = echoText(
                                en = "${row.trackCount} tracks",
                                zh = "${row.trackCount} 首",
                                ja = "${row.trackCount} 曲",
                            ),
                            artworkUri = row.artworkUri,
                            browsable = true,
                            playable = row.trackCount > 0,
                            kind = EchoPlaybackBrowseKind.Playlist,
                        )
                    }
            EchoPlaybackLibraryIds.FAVORITES ->
                database.playlistDao().listFavoriteTracksForBrowse(limit, offset).map { it.toBrowseItem() }
            EchoPlaybackLibraryIds.TRACKS ->
                database.trackDao().listRecentTracksForBrowse(limit, offset).map { it.toBrowseItem() }
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
                    return@withContext database.playlistDao()
                        .listPlaylistTracksForBrowse(playlistId, limit, offset)
                        .map { it.toBrowseItem() }
                }
                emptyList()
            }
        }
    }

    override suspend fun item(mediaId: String): EchoPlaybackBrowseItem? = withContext(Dispatchers.IO) {
        when (mediaId) {
            EchoPlaybackLibraryIds.ROOT -> root()
            EchoPlaybackLibraryIds.ALBUMS -> rootChildren()[0]
            EchoPlaybackLibraryIds.ARTISTS -> rootChildren()[1]
            EchoPlaybackLibraryIds.PLAYLISTS -> rootChildren()[2]
            EchoPlaybackLibraryIds.FAVORITES -> rootChildren()[3]
            EchoPlaybackLibraryIds.TRACKS -> rootChildren()[4]
            else -> {
                EchoPlaybackLibraryIds.albumKey(mediaId)?.let { key ->
                    return@withContext database.trackDao().getAlbumSummary(key)?.toBrowseItem()
                }
                EchoPlaybackLibraryIds.artistKey(mediaId)?.let { key ->
                    return@withContext database.trackDao().getArtistSummary(key)?.toBrowseItem()
                }
                EchoPlaybackLibraryIds.playlistId(mediaId)?.let { id ->
                    val playlist = database.playlistDao().getPlaylist(id) ?: return@withContext null
                    return@withContext EchoPlaybackBrowseItem(
                        mediaId = EchoPlaybackLibraryIds.playlist(playlist.id),
                        title = playlist.name,
                        subtitle = echoText(
                            en = "${playlist.trackCount} tracks",
                            zh = "${playlist.trackCount} 首",
                            ja = "${playlist.trackCount} 曲",
                        ),
                        artworkUri = playlist.artworkUri,
                        browsable = true,
                        playable = playlist.trackCount > 0,
                        kind = EchoPlaybackBrowseKind.Playlist,
                    )
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
            addAll(dao.searchArtists(trimmed, perType).map { it.toBrowseItem() })
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
                        return@withContext database.playlistDao()
                            .listPlaylistTracksForBrowse(id, limit, 0)
                            .map { it.toBrowseItem() }
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

    private fun rootChildren(): List<EchoPlaybackBrowseItem> = listOf(
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.ALBUMS,
            title = echoText(en = "Albums", zh = "专辑", ja = "アルバム"),
            kind = EchoPlaybackBrowseKind.Albums,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.ARTISTS,
            title = echoText(en = "Artists", zh = "艺术家", ja = "アーティスト"),
            kind = EchoPlaybackBrowseKind.Artists,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.PLAYLISTS,
            title = echoText(en = "Playlists", zh = "播放列表", ja = "プレイリスト"),
            kind = EchoPlaybackBrowseKind.Playlists,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.FAVORITES,
            title = echoText(en = "Favorites", zh = "喜欢", ja = "お気に入り"),
            kind = EchoPlaybackBrowseKind.Favorites,
            playable = true,
        ),
        categoryItem(
            mediaId = EchoPlaybackLibraryIds.TRACKS,
            title = echoText(en = "Songs", zh = "歌曲", ja = "曲"),
            kind = EchoPlaybackBrowseKind.Tracks,
            playable = true,
        ),
    )

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
    )

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

private fun ArtistSummary.toBrowseItem(): EchoPlaybackBrowseItem =
    EchoPlaybackBrowseItem(
        mediaId = EchoPlaybackLibraryIds.artist(artistKey),
        title = name,
        subtitle = echoText(
            en = "$trackCount tracks",
            zh = "$trackCount 首",
            ja = "$trackCount 曲",
        ),
        artworkUri = artworkUri,
        browsable = true,
        playable = trackCount > 0,
        durationMs = durationMs,
        kind = EchoPlaybackBrowseKind.Artist,
    )
