package app.echo.android.playback

import app.echo.android.model.library.EchoTrack
import java.util.Locale
import kotlin.random.Random

internal data class AlbumShuffleEntry(
    val index: Int, val album: String?, val artist: String?, val albumArtist: String?,
    val source: String?, val disc: Int?, val track: Int?,
) {
    val key: List<String> get() {
        fun String?.normalized() = this?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val title = album.normalized()
        return if (title.isEmpty()) listOf("missing", index.toString())
            else listOf(source.normalized(), title, albumArtist.normalized().ifEmpty { artist.normalized() })
    }
}

/** Pure planning; callers run grouping and sorting on Default, never in an audio callback. */
internal object AlbumShuffleQueuePolicy {
    private val withinAlbum = compareBy<AlbumShuffleEntry> { it.disc?.takeIf { n -> n > 0 } ?: 1 }
        .thenBy { it.track?.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.index }

    fun firstOfRandomAlbum(entries: List<AlbumShuffleEntry>, random: Random = Random.Default): Int =
        entries.groupBy { it.key }.values.toList().random(random).minWith(withinAlbum).index

    /** Preserve history; finish the current album from the current track, then shuffle whole albums. */
    fun order(existing: List<Int>, anchor: Int, entries: List<AlbumShuffleEntry>, random: Random = Random.Default): List<Int> {
        val at = existing.indexOf(anchor)
        if (at < 0) return existing
        val byIndex = entries.associateBy { it.index }
        val groups = existing.drop(at).mapNotNull(byIndex::get).groupBy { it.key }.toMutableMap()
        val key = byIndex[anchor]?.key ?: return existing
        val current = groups.remove(key).orEmpty().sortedWith(withinAlbum)
        val currentAt = current.indexOfFirst { it.index == anchor }
        if (currentAt < 0) return existing
        return existing.take(at) + current.take(currentAt).map { it.index } +
            current.drop(currentAt).map { it.index } +
            groups.values.toList().shuffled(random).flatMap { it.sortedWith(withinAlbum).map(AlbumShuffleEntry::index) }
    }
}

/** A fresh shuffle starts on track one of a random album, rather than a random track in it. */
fun albumShuffleStartIndex(tracks: List<EchoTrack>): Int {
    if (tracks.isEmpty()) return 0
    return AlbumShuffleQueuePolicy.firstOfRandomAlbum(tracks.mapIndexed { index, track ->
        AlbumShuffleEntry(index, track.album, track.artist, track.albumArtist, track.source.id, track.discNumber, track.trackNumber)
    })
}
