package app.echo.android.feature.library

import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryTrackSortMode

internal data class LinkedLibraryCatalog(
    val tracks: List<EchoRemoteTrack>,
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
    val playlists: List<EchoRemotePlaylist>,
) {
    companion object {
        val Empty = LinkedLibraryCatalog(
            tracks = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
        )

        fun build(
            tracks: List<EchoRemoteTrack>,
            playlists: List<EchoRemotePlaylist>,
            query: String,
            remoteQuery: String,
            sortMode: LibraryTrackSortMode,
        ): LinkedLibraryCatalog {
            val normalizedQuery = query.trim()
            val normalizedRemoteQuery = remoteQuery.trim()
            val filteredTracks = if (normalizedQuery.isNotBlank() && normalizedQuery == normalizedRemoteQuery) {
                tracks
            } else {
                tracks.filterLinkedLibraryQuery(normalizedQuery)
            }
            val filteredPlaylists = if (normalizedQuery.isNotBlank() && normalizedQuery == normalizedRemoteQuery) {
                playlists
            } else {
                playlists.filterLinkedPlaylistQuery(normalizedQuery)
            }
            return LinkedLibraryCatalog(
                tracks = filteredTracks.sortedForLinkedLibrary(sortMode),
                albums = filteredTracks.toLinkedAlbums(),
                artists = filteredTracks.toLinkedArtists(),
                playlists = filteredPlaylists,
            )
        }
    }
}

internal fun EchoRemoteTrack.linkedAlbumKey(): String =
    "echo-link:${album?.trim().orEmpty().lowercase()}|${artist.trim().lowercase()}"

internal fun EchoRemoteTrack.linkedArtistKey(): String =
    "echo-link:${artist.trim().ifBlank { "PC ECHO" }.lowercase()}"

private fun List<EchoRemoteTrack>.toLinkedAlbums(): List<AlbumSummary> =
    groupBy { it.linkedAlbumKey() }
        .values
        .map { albumTracks ->
            val first = albumTracks.first()
            AlbumSummary(
                albumKey = first.linkedAlbumKey(),
                title = first.album?.takeIf { it.isNotBlank() } ?: "未知专辑",
                albumArtist = first.artist.takeIf { it.isNotBlank() },
                artist = first.artist.takeIf { it.isNotBlank() },
                artworkUri = albumTracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                trackCount = albumTracks.size,
                durationMs = albumTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
                year = null,
            )
        }
        .sortedWith(compareBy<AlbumSummary> { it.title.lowercase() }.thenBy { it.albumArtist.orEmpty().lowercase() })

private fun List<EchoRemoteTrack>.toLinkedArtists(): List<ArtistSummary> =
    groupBy { it.linkedArtistKey() }
        .values
        .map { artistTracks ->
            val first = artistTracks.first()
            ArtistSummary(
                artistKey = first.linkedArtistKey(),
                name = first.artist.takeIf { it.isNotBlank() } ?: "未知艺术家",
                artworkUri = artistTracks.firstNotNullOfOrNull { it.artworkUrl?.takeIf(String::isNotBlank) },
                albumCount = artistTracks.map { it.linkedAlbumKey() }.distinct().size,
                trackCount = artistTracks.size,
                durationMs = artistTracks.sumOf { it.durationMs.coerceAtLeast(0L) },
            )
        }
        .sortedWith(compareBy<ArtistSummary> { it.name.lowercase() }.thenByDescending { it.trackCount })

private fun List<EchoRemoteTrack>.filterLinkedLibraryQuery(query: String): List<EchoRemoteTrack> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filter { track ->
        val searchableText = searchableLibraryText(
            track.title,
            track.artist,
            track.album.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private fun List<EchoRemotePlaylist>.filterLinkedPlaylistQuery(query: String): List<EchoRemotePlaylist> {
    val terms = normalizedSearchTerms(query)
    if (terms.isEmpty()) return this
    return filter { playlist ->
        val searchableText = searchableLibraryText(
            playlist.name,
            playlist.sourceLabel.orEmpty(),
        )
        terms.all(searchableText::contains)
    }
}

private fun List<EchoRemoteTrack>.sortedForLinkedLibrary(
    sortMode: LibraryTrackSortMode,
): List<EchoRemoteTrack> =
    when (sortMode) {
        LibraryTrackSortMode.Title,
        LibraryTrackSortMode.FrequentlyPlayed,
        LibraryTrackSortMode.RecentlyUpdated,
        -> sortedWith(
            compareBy<EchoRemoteTrack> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() },
        )
        LibraryTrackSortMode.Duration -> sortedWith(
            compareByDescending<EchoRemoteTrack> { it.durationMs }
                .thenBy { it.title.lowercase() },
        )
        LibraryTrackSortMode.Random -> shuffled()
        LibraryTrackSortMode.Artist -> sortedWith(
            compareBy<EchoRemoteTrack> { it.artist.lowercase() }
                .thenBy { it.album.orEmpty().lowercase() }
                .thenBy { it.title.lowercase() },
        )
        LibraryTrackSortMode.Album -> sortedWith(
            compareBy<EchoRemoteTrack> { it.album.orEmpty().lowercase() }
                .thenBy { it.artist.lowercase() }
                .thenBy { it.title.lowercase() },
        )
    }

private fun normalizedSearchTerms(query: String): List<String> =
    query.trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

private fun searchableLibraryText(vararg parts: String): String =
    buildString {
        parts.filter(String::isNotBlank).forEach { part ->
            if (isNotEmpty()) append(' ')
            append(part.lowercase())
            val pinyin = linkedLibrarySearchPinyin(part)
            if (pinyin.isNotBlank()) {
                append(' ')
                append(pinyin)
            }
        }
    }

private fun linkedLibrarySearchPinyin(text: String): String {
    if (text.isBlank()) return ""
    if (text.none(Char::needsPinyin)) return text.lowercase()
    val full = StringBuilder(text.length * 6)
    val initials = StringBuilder(text.length)
    return try {
        val outputFormat = net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat().apply {
            caseType = net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.LOWERCASE
            toneType = net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
            vCharType = net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
        }
        text.forEach { ch ->
            val pinyinArray = net.sourceforge.pinyin4j.PinyinHelper.toHanyuPinyinStringArray(ch, outputFormat)
            if (!pinyinArray.isNullOrEmpty()) {
                val syllable = pinyinArray[0]
                full.append(syllable)
                initials.append(syllable.first())
            } else if (ch.isLetterOrDigit()) {
                val normalized = ch.lowercaseChar()
                full.append(normalized)
                initials.append(normalized)
            }
        }
        buildList {
            val fullPinyin = full.toString()
            val initialsPinyin = initials.toString()
            if (fullPinyin.isNotBlank()) add(fullPinyin)
            if (initialsPinyin.isNotBlank() && initialsPinyin != fullPinyin) add(initialsPinyin)
        }.joinToString(" ")
    } catch (_: Exception) {
        text.lowercase()
    }
}

private fun Char.needsPinyin(): Boolean =
    this in '\u3400'..'\u4DBF' ||
        this in '\u4E00'..'\u9FFF' ||
        this in '\uF900'..'\uFAFF'
