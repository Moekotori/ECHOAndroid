package app.echo.android.connect

import app.echo.android.model.connect.EchoRemotePlaylist
import org.json.JSONArray
import org.json.JSONObject

internal const val EchoLinkMaxLibraryPages = 40

internal data class EchoLinkPlaylistPage(
    val playlists: List<EchoRemotePlaylist>,
    val nextPage: Int = 0,
)

internal fun JSONObject.playlistProvider(): String? =
    optText("sourceProvider") ?: optText("source") ?: optText("provider") ?: optText("sourceLabel")

internal fun JSONArray.localPlaylistPage(
    page: Int,
    pageSize: Int,
    totalCount: Int,
    toPlaylist: (JSONObject) -> EchoRemotePlaylist?,
): EchoLinkPlaylistPage {
    val playlists = buildList {
        for (index in 0 until length()) {
            val row = optJSONObject(index) ?: continue
            if (row.playlistProvider().equals("local", ignoreCase = true)) {
                toPlaylist(row)?.let(::add)
            }
        }
    }
    // The PC total and offsets include every provider. A filtered page can be empty mid-list.
    val hasMore = (page - 1L) * pageSize + length() < totalCount
    if (hasMore && length() == 0) throw EchoLinkHttpException("PC returned an incomplete playlist page")
    return EchoLinkPlaylistPage(playlists, nextPage = if (hasMore) page + 1 else 0)
}
