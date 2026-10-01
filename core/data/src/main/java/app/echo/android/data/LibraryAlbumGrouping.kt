package app.echo.android.data

/**
 * Album keys stay "album + album artist", then a folder pass merges
 * incomplete tags that would otherwise split one release:
 * missing album artist on some tracks, compilations, disc suffixes.
 */
internal object LibraryAlbumGrouping {
    fun reconcile(tracks: List<LibraryTrackEntity>): List<LibraryTrackEntity> {
        val changed = ArrayList<LibraryTrackEntity>()
        val desired = tracks
            .filter { LibraryScanPolicy.isLocalLibrarySource(it.source) }
            .map { track -> track.copy(albumKey = libraryAlbumKey(
                albumNameForKey(track.normalizedAlbum ?: track.album?.normalizedForSearch()),
                track.normalizedAlbumArtist ?: track.albumArtist?.normalizedForSearch(),
                track.normalizedArtist ?: track.artist.normalizedForSearch(),
            )) }
            .groupBy(::groupingIdentity)
            .flatMap { (identity, group) ->
                val winner = if (identity.canReconcile && group.size >= 2 &&
                    identity.folder?.substringAfterLast('/') !in SharedFolderNames) winningAlbumKey(identity.album, group) else null
                if (winner == null) group else group.map { it.copy(albumKey = winner) }
            }
            .associateBy { it.id }
        tracks.forEach { track ->
            desired[track.id]?.takeIf { it.albumKey != track.albumKey }?.let(changed::add)
        }
        return changed
    }

    fun albumNameForKey(normalizedAlbum: String?): String? =
        normalizedAlbum?.stripDiscSuffixForAlbumKey()

    fun groupingFolder(relativePath: String?): String? {
        val path = relativePath?.replace('\\', '/')?.trim('/')?.takeIf { it.isNotBlank() } ?: return null
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val last = parts.last().lowercase()
        val folders = if (parts.size > 1 && DiscFolderRegex.matches(last)) parts.dropLast(1) else parts
        return folders.joinToString("/").lowercase()
    }

    fun groupingFolders(relativePaths: Iterable<String?>): Set<String> =
        relativePaths.mapNotNullTo(linkedSetOf(), ::groupingFolder)

    fun trackIdsInGroupingFolders(
        rows: Iterable<TrackIdPathRow>,
        folders: Set<String>,
    ): List<String> {
        if (folders.isEmpty()) return emptyList()
        return rows.mapNotNull { row ->
            val folder = groupingFolder(row.relativePath)
            if (folder != null && folder in folders) row.id else null
        }
    }

    private fun groupingIdentity(track: LibraryTrackEntity): GroupIdentity {
        val album = albumNameForKey(track.normalizedAlbum ?: track.album?.normalizedForSearch())
            .orEmpty()
        return GroupIdentity(
            album = album,
            folder = groupingFolder(track.relativePath),
        )
    }

    private fun winningAlbumKey(album: String, group: List<LibraryTrackEntity>): String? {
        val keys = group.mapTo(linkedSetOf()) { it.albumKey }
        if (keys.size <= 1) return null
        val albumArtists = group.mapNotNull { track ->
            track.normalizedAlbumArtist?.takeIf { it.isNotBlank() }
                ?: track.albumArtist?.normalizedForSearch()?.takeIf { it.isNotBlank() }
        }.mapNotNull(::canonicalAlbumArtistKey).distinct()
        // Explicit conflicting owners are separate releases, even in a shared download folder.
        if (albumArtists.size > 1) return null
        if (albumArtists.isNotEmpty()) {
            val preferred = albumArtists.single()
            return libraryAlbumKey(album, preferred, group.first().normalizedArtist)
        }
        val credited = group.map { LibraryArtistPolicy.keys(it.artist) }
        val common = credited.reduce { first, next -> first.intersect(next) }
        if (common.size == 1) return libraryAlbumKey(album, common.single(), null)
        val artistKeys = group.mapTo(linkedSetOf()) { it.artistKey }
        if (artistKeys.size > 1) {
            return libraryAlbumKey(album, VariousArtistsKey, null)
        }
        return keys.first()
    }

    private data class GroupIdentity(
        val album: String,
        val folder: String?,
    ) {
        val canReconcile: Boolean = album.isNotBlank() && folder != null
    }
}

internal fun String.stripDiscSuffixForAlbumKey(): String {
    val stripped = DiscSuffixRegex.replace(this, "").trim()
    return stripped.ifBlank { this }
}

private val DiscSuffixRegex = Regex(
    """[\s\-_]*[\(\[\{]?\s*(?:disc|disk|cd|光盘|碟)\s*\.?\s*\d+\s*[\)\]\}]?\s*$""",
    RegexOption.IGNORE_CASE,
)

private val DiscFolderRegex = Regex(
    """^(?:disc|disk|cd|光盘|碟)\s*\.?\s*\d+$""",
    RegexOption.IGNORE_CASE,
)

private val SharedFolderNames = setOf("download", "downloads", "music", "audio", "音乐", "下载")
