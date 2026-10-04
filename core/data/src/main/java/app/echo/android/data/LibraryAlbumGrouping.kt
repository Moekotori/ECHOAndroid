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
                val winner = if (identity.canReconcile && group.size >= 2) winningAlbumKey(
                    identity.album,
                    group,
                    sharedFolder = identity.folder?.substringAfterLast('/') in SharedFolderNames,
                ) else null
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

    private fun winningAlbumKey(
        album: String,
        group: List<LibraryTrackEntity>,
        sharedFolder: Boolean,
    ): String? {
        val keys = group.mapTo(linkedSetOf()) { it.albumKey }
        if (keys.size <= 1) return null
        val albumArtists = group.mapNotNull { track ->
            track.normalizedAlbumArtist?.takeIf { it.isNotBlank() }
                ?: track.albumArtist?.normalizedForSearch()?.takeIf { it.isNotBlank() }
        }.mapNotNull(::canonicalAlbumArtistKey).distinct()
        val commonArtist = commonCreditedArtist(group)
        // Some files copy the song's changing guest credits into ALBUMARTIST. Only repair
        // that case when every owner matches its song credits and they share an artist.
        // Distinct release owners and manually edited tags remain authoritative.
        if (albumArtists.size > 1) {
            if (commonArtist == null || group.any { track ->
                    track.metadataEditedAtEpochMs != null ||
                        canonicalAlbumArtistKey(track.albumArtist?.normalizedForSearch())?.let { owner ->
                            owner != canonicalAlbumArtistKey(track.artist.normalizedForSearch())
                        } == true
                }) return null
            return libraryAlbumKey(album, commonArtist, null)
        }
        if (albumArtists.isNotEmpty()) {
            val preferred = albumArtists.single()
            if (sharedFolder && commonArtist != preferred) return null
            return libraryAlbumKey(album, preferred, group.first().normalizedArtist)
        }
        if (commonArtist != null) return libraryAlbumKey(album, commonArtist, null)
        // Same title alone is insufficient evidence in a general Music/Download folder.
        if (sharedFolder) return null
        val artistKeys = group.mapTo(linkedSetOf()) { it.artistKey }
        if (artistKeys.size > 1) {
            return libraryAlbumKey(album, VariousArtistsKey, null)
        }
        return keys.first()
    }

    private fun commonCreditedArtist(group: List<LibraryTrackEntity>): String? {
        val credited = group.map { LibraryArtistPolicy.keys(it.artist) }
        val common = credited.reduce { first, next -> first.intersect(next) }
            .filterNot(LibraryMetadataSentinels::isUnknown)
        if (common.size == 1) return common.single()
        if (common.isNotEmpty() || credited.any { it.size != 1 }) return null

        // Slash credits are ambiguous (AC/DC is also a band name). Do not split artist
        // identities globally: within this album/folder only, keep the longest shared
        // leading credit, including embedded slashes when the full band name is shared.
        val values = credited.map { it.single() }
        var prefix = values.first()
        while (prefix.isNotBlank()) {
            if (!LibraryMetadataSentinels.isUnknown(prefix) && values.all { value ->
                    value == prefix || value.startsWith("$prefix/")
                }) return prefix
            val separator = prefix.lastIndexOf('/')
            if (separator < 0) break
            prefix = prefix.substring(0, separator).trimEnd()
        }
        return null
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
