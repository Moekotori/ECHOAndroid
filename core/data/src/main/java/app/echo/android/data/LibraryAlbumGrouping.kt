package app.echo.android.data

import java.security.MessageDigest

/**
 * Explicit release owners use "album + album artist". A folder pass repairs
 * incomplete credits and disc suffixes; inferred compilations carry a folder
 * scope so unrelated releases with the same title cannot share a synthetic owner.
 */
/** Projection carrying only the fields regrouping reads, for full-library passes. */
data class LibraryAlbumRegroupRow(
    val id: String,
    val source: String,
    val albumKey: String,
    val artistKey: String,
    val genreKey: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val normalizedAlbum: String?,
    val normalizedAlbumArtist: String?,
    val normalizedArtist: String?,
    val relativePath: String?,
    val artworkUri: String?,
    val metadataEditedAtEpochMs: Long?,
)

internal object LibraryAlbumGrouping {
    internal fun LibraryTrackEntity.toRegroupRow(): LibraryAlbumRegroupRow = LibraryAlbumRegroupRow(
        id = id,
        source = source,
        albumKey = albumKey,
        artistKey = artistKey,
        genreKey = genreKey,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        normalizedAlbum = normalizedAlbum,
        normalizedAlbumArtist = normalizedAlbumArtist,
        normalizedArtist = normalizedArtist,
        relativePath = relativePath,
        artworkUri = artworkUri,
        metadataEditedAtEpochMs = metadataEditedAtEpochMs,
    )

    fun reconcileRows(rows: List<LibraryAlbumRegroupRow>): List<LibraryAlbumRegroupRow> {
        val changed = ArrayList<LibraryAlbumRegroupRow>()
        val previousKeys = rows.associate { it.id to it.albumKey }
        val desired = rows
            .filter { LibraryScanPolicy.isLocalLibrarySource(it.source) }
            .map { row -> row.copy(albumKey = libraryAlbumKey(
                albumNameForKey(row.normalizedAlbum ?: row.album?.normalizedForSearch()),
                row.normalizedAlbumArtist ?: row.albumArtist?.normalizedForSearch(),
                row.normalizedArtist ?: row.artist.normalizedForSearch(),
            )) }
            .groupBy { groupingIdentity(it.normalizedAlbum, it.album, it.relativePath) }
            .flatMap { (identity, group) ->
                if (!identity.canReconcile || group.size < 2) group else {
                    val winner = winningAlbumKey(identity, group, previousKeys)
                    if (winner != null) group.map { it.copy(albumKey = winner) }
                    else if (isSharedFolder(identity.folder)) group.groupBy { it.artworkUri }.values.flatMap { subset ->
                        val subsetKey = if (subset.size >= 2) winningAlbumKey(identity, subset, previousKeys) else null
                        if (subsetKey == null) subset else subset.map { it.copy(albumKey = subsetKey) }
                    } else group
                }
            }
            .associateBy { it.id }
        rows.forEach { row ->
            desired[row.id]?.takeIf { it.albumKey != row.albumKey }?.let(changed::add)
        }
        return changed
    }

    fun reconcile(tracks: List<LibraryTrackEntity>): List<LibraryTrackEntity> {
        val changedRows = reconcileRows(tracks.map { it.toRegroupRow() })
        if (changedRows.isEmpty()) return emptyList()
        val byId = tracks.associateBy { it.id }
        return changedRows.mapNotNull { row -> byId[row.id]?.copy(albumKey = row.albumKey) }
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

        internal fun groupingIdentity(normalizedAlbum: String?, albumName: String?, relativePath: String?): GroupIdentity {
        val album = albumNameForKey(normalizedAlbum ?: albumName?.normalizedForSearch())
            .orEmpty()
        return GroupIdentity(
            album = album,
            folder = groupingFolder(relativePath),
        )
    }

    private fun winningAlbumKey(
        identity: GroupIdentity,
        group: List<LibraryAlbumRegroupRow>,
        previousKeys: Map<String, String>,
    ): String? {
        val album = identity.album
        val folder = requireNotNull(identity.folder)
        val sharedFolder = isSharedFolder(folder)
        val keys = group.mapTo(linkedSetOf()) { it.albumKey }
        if (keys.size <= 1) return null
        val previousKey = group.map { previousKeys[it.id] }.distinct().singleOrNull()?.takeIf { it.isNotBlank() }
        val previousCompilationKey = previousKey?.takeIf {
            it.startsWith("$album::inferred-${folder.albumScopeHash()}::") ||
                it.startsWith("$album::inferred-${folder.albumScopeHash()}-")
        }?.takeIf { it.endsWith("::${VariousArtistsKey}") }
        val albumArtists = group.mapNotNull { track ->
            track.normalizedAlbumArtist?.takeIf { it.isNotBlank() }
                ?: track.albumArtist?.normalizedForSearch()?.takeIf { it.isNotBlank() }
        }.mapNotNull(::canonicalAlbumArtistKey).distinct()
        val commonArtist = commonCreditedArtist(group)
        val hasReleaseEvidence = !sharedFolder || sharedArtwork(group) || previousCompilationKey != null
        // Some files copy each song's credits into ALBUMARTIST, including compilations
        // whose songs have no artist in common. The album folder (or shared artwork in a
        // general folder) supplies the release identity when every owner matches its song.
        // Distinct release owners and manually edited tags remain authoritative.
        if (albumArtists.size > 1) {
            val compilationOwner = VariousArtistsKey.takeIf { it in albumArtists && hasReleaseEvidence }
            if ((commonArtist == null && !hasReleaseEvidence) || group.any { track ->
                    canonicalAlbumArtistKey(
                            track.normalizedAlbumArtist ?: track.albumArtist?.normalizedForSearch(),
                        )?.let { owner ->
                            owner != compilationOwner &&
                                owner != canonicalAlbumArtistKey(track.artist.normalizedForSearch())
                        } == true
                }) return null
            // A partially tagged compilation can mix Various Artists, missing owners,
            // and owners copied from individual songs. Keep the explicit compilation owner.
            val winner = if (compilationOwner != null) libraryAlbumKey(album, compilationOwner, null)
                else if (commonArtist != null) libraryAlbumKey(album, commonArtist, null)
                else previousCompilationKey ?: inferredCompilationKey(album, folder, if (sharedFolder) group.first().artworkUri else null)
            // A title/lyrics/order edit on an already grouped release is not a new owner.
            // An edited row with a different key still prevents inferred owner repair.
            if (group.any { it.metadataEditedAtEpochMs != null && previousKeys[it.id] != winner } &&
                previousKey != winner && previousKey != libraryAlbumKey(album, VariousArtistsKey, null)
            ) return null
            return winner
        }
        if (albumArtists.isNotEmpty()) {
            val preferred = albumArtists.single()
            if (!hasReleaseEvidence && commonArtist != preferred) return null
            return libraryAlbumKey(album, preferred, group.first().normalizedArtist)
        }
        if (commonArtist != null) return libraryAlbumKey(album, commonArtist, null)
        // Same title alone is insufficient evidence in a general Music/Download folder.
        if (!hasReleaseEvidence) return null
        return previousCompilationKey ?: inferredCompilationKey(album, folder, if (sharedFolder) group.first().artworkUri else null)
    }

    internal fun inferredCompilationKey(album: String, folder: String, artwork: String? = null): String =
        "$album::inferred-${folder.albumScopeHash()}${artwork?.let { "-${it.albumScopeHash()}" }.orEmpty()}::$VariousArtistsKey"

    private fun isSharedFolder(folder: String?): Boolean {
        if (folder == null) return true
        val parts = folder.split('/')
        return parts.last() in SharedFolderNames ||
            (parts.size <= 2 && parts.first() == "removable")
    }

    private fun sharedArtwork(group: List<LibraryAlbumRegroupRow>): Boolean {
        val artwork = group.first().artworkUri?.takeIf { it.isNotBlank() } ?: return false
        return group.all { it.artworkUri == artwork }
    }

    private fun commonCreditedArtist(group: List<LibraryAlbumRegroupRow>): String? {
        val credited = group.map { LibraryArtistPolicy.keys(it.artist) }
        val common = credited.reduce { first, next -> first.intersect(next) }
            .filterNot(LibraryMetadataSentinels::isUnknown)
        if (common.isNotEmpty()) return common.sorted().joinToString("; ")
        if (credited.any { it.size != 1 }) return null

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

    internal data class GroupIdentity(
        val album: String,
        val folder: String?,
    ) {
        val canReconcile: Boolean = album.isNotBlank() && !LibraryMetadataSentinels.isUnknown(album) && folder != null
    }
}

internal fun String.stripDiscSuffixForAlbumKey(): String {
    val stripped = DiscSuffixRegex.replace(this, "").trim()
    return stripped.ifBlank { this }
}

private val DiscSuffixRegex = Regex(
    """(?:[\s\-_]+[\(\[\{]?\s*|[\(\[\{]\s*)(?:disc|disk|cd|光盘|碟)\s*\.?\s*\d+\s*[\)\]\}]?\s*$""",
    RegexOption.IGNORE_CASE,
)

private val DiscFolderRegex = Regex(
    """^(?:disc|disk|cd|光盘|碟)\s*\.?\s*\d+$""",
    RegexOption.IGNORE_CASE,
)

private val SharedFolderNames = setOf("download", "downloads", "music", "audio", "音乐", "下载")

private fun String.albumScopeHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    return CharArray(bytes.size * 2) { index ->
        val value = bytes[index / 2].toInt() and 0xff
        digits[if (index % 2 == 0) value ushr 4 else value and 0x0f]
    }.concatToString()
}
