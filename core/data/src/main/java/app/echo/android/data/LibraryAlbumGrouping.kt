package app.echo.android.data

/**
 * Album keys stay "album + album artist", then a folder pass merges
 * incomplete tags that would otherwise split one release:
 * missing album artist on some tracks, compilations, disc suffixes.
 */
internal object LibraryAlbumGrouping {
    fun reconcile(tracks: List<LibraryTrackEntity>): List<LibraryTrackEntity> {
        val changed = ArrayList<LibraryTrackEntity>()
        tracks
            .filter { LibraryScanPolicy.isLocalLibrarySource(it.source) }
            .groupBy(::groupingIdentity)
            .forEach { (identity, group) ->
                if (!identity.canReconcile || group.size < 2) return@forEach
                val winner = winningAlbumKey(identity.album, group) ?: return@forEach
                group.forEach { track ->
                    if (track.albumKey != winner) changed += track.copy(albumKey = winner)
                }
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
        }
        if (albumArtists.isNotEmpty()) {
            val preferred = albumArtists.firstOrNull(LibraryMetadataSentinels::isVariousArtists)
                ?: albumArtists.groupingBy { it }.eachCount().maxBy { it.value }.key
            return libraryAlbumKey(album, preferred, group.first().normalizedArtist)
        }
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
