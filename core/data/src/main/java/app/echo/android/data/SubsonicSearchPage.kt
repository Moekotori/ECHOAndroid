package app.echo.android.data

import app.echo.android.model.library.NavidromeKind
import app.echo.android.model.library.NavidromePage
import app.echo.android.model.library.NavidromeQuery
import org.json.JSONObject

/** Fill one bounded page even when a compatible server caps each response below our requested count. */
internal fun SubsonicClient.searchUserLibrary(endpoint: SubsonicEndpoint, query: NavidromeQuery.Search): NavidromePage {
    require(query.text.isNotBlank() && query.text.length <= 500 && query.page in 0..10_000)
    val kinds = listOf("artist" to NavidromeKind.Artist, "album" to NavidromeKind.Album, "song" to NavidromeKind.Track)
    val rows = List(3) { ArrayList<JSONObject>(17) }
    val exhausted = BooleanArray(3)
    repeat(17) {
        if (rows.indices.all { index -> exhausted[index] || rows[index].size == 17 }) return@repeat
        val counts = rows.indices.map { if (exhausted[it]) 0 else 17 - rows[it].size }
        val root = request("search3.view", listOf("query" to query.text) + kinds.flatMapIndexed { index, (name, _) ->
            listOf("${name}Count" to counts[index].toString(),
                "${name}Offset" to (query.page * 16 + rows[index].size).toString())
        }).requiredUserObject("searchResult3")
        kinds.forEachIndexed { index, (name, _) ->
            if (counts[index] > 0) {
                val batch = root.validUserRows(name, counts[index])
                if (batch.isEmpty()) exhausted[index] = true
                else {
                    val known = rows[index].mapTo(HashSet()) { it.getString("id") }
                    check(batch.all { known.add(it.getString("id")) }) { "Subsonic search repeated entries" }
                    rows[index].addAll(batch)
                }
            }
        }
    }
    return NavidromePage(kinds.flatMapIndexed { index, (_, kind) -> rows[index].take(16).map { it.userEntry(endpoint, kind) } },
        query.page, rows.any { it.size == 17 })
}
