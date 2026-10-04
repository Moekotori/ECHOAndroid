package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.PlaylistTrackOrder
import app.echo.android.model.library.PlaylistTrackSort

/** The paging and anchored playback queries share exactly the same ordering and tie breakers. */
internal object PlaylistOrderingQuery {
    fun build(
        from: String,
        where: String,
        filterArgs: List<Any>,
        order: PlaylistTrackOrder,
        limit: Int?,
        anchorId: String? = null,
    ): SimpleSQLiteQuery {
        val expressions = when (order.sort) {
            PlaylistTrackSort.Title -> listOf("t.title COLLATE NOCASE")
            PlaylistTrackSort.Artist -> listOf("t.artist COLLATE NOCASE", "t.title COLLATE NOCASE")
            PlaylistTrackSort.Album -> listOf("COALESCE(t.album, '') COLLATE NOCASE", "COALESCE(t.discNumber, 0)", "COALESCE(t.trackNumber, 0)", "t.title COLLATE NOCASE")
            PlaylistTrackSort.Duration -> listOf("t.durationMs", "t.title COLLATE NOCASE")
            PlaylistTrackSort.RecentlyAdded -> listOf("t.dateModifiedSeconds", "t.title COLLATE NOCASE")
            PlaylistTrackSort.Default -> error("Default order belongs to the collection query")
        } + "t.id"
        // Reverse the chosen field, while retaining natural album/track and stable tie order.
        val directions = expressions.indices.map { order.descending && it == 0 }
        val args = ArrayList<Any>()
        val anchorJoin = if (anchorId == null) "" else {
            args.addAll(filterArgs)
            args += anchorId
            val keys = expressions.mapIndexed { index, expression -> "$expression AS k$index" }.joinToString()
            " CROSS JOIN (SELECT $keys FROM $from WHERE ($where) AND t.id = ?) anchor"
        }
        args.addAll(filterArgs)
        val anchorWhere = if (anchorId == null) "" else {
            val branches = expressions.indices.map { index ->
                val equals = (0 until index).map { "${expressions[it]} = anchor.k$it" }
                val comparator = if (directions[index]) "<" else ">"
                (equals + "${expressions[index]} $comparator anchor.k$index").joinToString(" AND ", "(", ")")
            } + expressions.indices.joinToString(" AND ", "(", ")") { "${expressions[it]} = anchor.k$it" }
            " AND (${branches.joinToString(" OR ")})"
        }
        val orderSql = expressions.mapIndexed { index, expression ->
            "$expression ${if (directions[index]) "DESC" else "ASC"}"
        }.joinToString()
        val limitSql = if (limit == null) "" else " LIMIT ?".also { args += limit.coerceAtLeast(1) }
        return SimpleSQLiteQuery("SELECT t.* FROM $from$anchorJoin WHERE ($where)$anchorWhere ORDER BY $orderSql$limitSql", args.toTypedArray())
    }
}
