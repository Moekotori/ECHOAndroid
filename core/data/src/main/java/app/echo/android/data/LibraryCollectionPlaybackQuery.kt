package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.LibraryPlaybackOrigin
import app.echo.android.model.library.LibrarySmartPlaylistKind

/** A bounded queue starting at an anchor, using row-value comparisons supported since Android 8. */
internal object LibraryCollectionPlaybackQuery {
    fun build(origin: LibraryPlaybackOrigin, anchorId: String?, limit: Int): SimpleSQLiteQuery {
        var from = "library_tracks t"
        val args = ArrayList<Any>()
        val local = "t.source IN ('mediastore', 'saf')"
        val albumOrder = listOf("COALESCE(t.discNumber, 0)", "COALESCE(t.trackNumber, 0)", "t.title COLLATE NOCASE", "t.id")
        var order = listOf("COALESCE(t.album, '') COLLATE NOCASE") + albumOrder
        val where = when (origin) {
            is LibraryPlaybackOrigin.Album -> {
                order = albumOrder
                val remote = origin.albumKey.takeIf { it.startsWith("remote||") }?.split("||", limit = 3)
                if (remote != null && remote.size == 3) {
                    args += remote[1]; args += remote[2]
                    "t.source = ? AND t.albumKey = ?"
                } else {
                    args += origin.albumKey
                    "$local AND t.albumKey = ?"
                }
            }
            is LibraryPlaybackOrigin.Artist -> {
                args += origin.artistKey
                "$local AND t.id IN (SELECT trackId FROM library_track_artists WHERE artistKey = ?)"
            }
            is LibraryPlaybackOrigin.Folder -> {
                args += origin.folderKey
                "$local AND COALESCE(t.relativePath, '') = ?"
            }
            is LibraryPlaybackOrigin.Playlist -> when (LibrarySmartPlaylistKind.fromId(origin.playlistId)) {
                LibrarySmartPlaylistKind.Recent -> {
                    from += " JOIN library_playback_stats s ON s.trackId = t.id"
                    order = listOf("-s.lastPlayedAtEpochMs", "t.title COLLATE NOCASE", "t.id")
                    "$local AND s.lastPlayedAtEpochMs > 0"
                }
                LibrarySmartPlaylistKind.Frequent -> {
                    from += " JOIN library_playback_stats s ON s.trackId = t.id"
                    order = listOf("-s.playCount", "-s.lastPlayedAtEpochMs", "t.title COLLATE NOCASE", "t.id")
                    "$local AND s.playCount > 0"
                }
                LibrarySmartPlaylistKind.Never -> {
                    from += " LEFT JOIN library_playback_stats s ON s.trackId = t.id"
                    order = listOf("t.title COLLATE NOCASE", "t.id")
                    "$local AND COALESCE(s.playCount, 0) = 0"
                }
                LibrarySmartPlaylistKind.Added -> {
                    order = listOf("-t.dateModifiedSeconds", "t.title COLLATE NOCASE", "t.id")
                    local
                }
                null -> if (LibraryFavoritePolicy.isLikedSongsId(origin.playlistId)) {
                    from += " JOIN library_favorites f ON f.trackId = t.id"
                    order = listOf("-f.favoritedAtEpochMs", "t.id")
                    "1 = 1"
                } else {
                    from += " JOIN library_playlist_tracks p ON p.trackId = t.id"
                    order = listOf("p.position", "t.id")
                    args += origin.playlistId
                    "p.playlistId = ?"
                }
            }
            LibraryPlaybackOrigin.Songs -> error("Songs use the library queue query")
        }
        val anchor = if (anchorId == null) "" else {
            val keys = order.joinToString(", ")
            val filter = " AND ($keys) >= (SELECT $keys FROM $from WHERE ($where) AND t.id = ?)"
            args.addAll(args.toList())
            args += anchorId
            filter
        }
        args += limit.coerceAtLeast(1)
        return SimpleSQLiteQuery("SELECT t.* FROM $from WHERE ($where)$anchor ORDER BY ${order.joinToString(", ")} LIMIT ?", args.toTypedArray())
    }
}
