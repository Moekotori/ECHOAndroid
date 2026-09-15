package app.echo.android.data

data class AlbumTrackOrderRow(
    val id: String,
    val discNumber: Int?,
    val trackNumber: Int?,
)

object LibraryAlbumTrackOrderPolicy {
    fun discKey(discNumber: Int?): Int = discNumber?.takeIf { it > 0 } ?: 1

    fun canMove(tracks: List<AlbumTrackOrderRow>, fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return false
        return discKey(tracks[fromIndex].discNumber) == discKey(tracks[toIndex].discNumber)
    }

    fun reorder(
        tracks: List<AlbumTrackOrderRow>,
        fromIndex: Int,
        toIndex: Int,
    ): List<AlbumTrackOrderRow> {
        if (!canMove(tracks, fromIndex, toIndex)) return tracks
        val disc = discKey(tracks[fromIndex].discNumber)
        val next = tracks.toMutableList()
        val moved = next.removeAt(fromIndex)
        next.add(toIndex, moved)
        var number = 1
        return next.map { row ->
            if (discKey(row.discNumber) != disc) {
                row
            } else {
                row.copy(trackNumber = number++)
            }
        }
    }
}
