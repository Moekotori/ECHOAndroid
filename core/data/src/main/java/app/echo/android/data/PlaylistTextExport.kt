package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.PlaylistExportFormat

/** A portable reading list; text exports never contain device-specific media URLs. */
fun exportPlaylistText(tracks: List<EchoTrack>, format: PlaylistExportFormat): String {
    require(format != PlaylistExportFormat.M3u)
    fun field(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    val rows = tracks.mapIndexed { index, track ->
        listOf((index + 1).toString(), track.title, track.artist, track.album.orEmpty(),
            "${track.durationMs.coerceAtLeast(0) / 60_000}:${(track.durationMs.coerceAtLeast(0) / 1_000 % 60).toString().padStart(2, '0')}")
    }
    return if (format == PlaylistExportFormat.Csv) "\uFEFF" +
        (listOf(listOf("#", "Title", "Artist", "Album", "Duration")) + rows).joinToString("\r\n", postfix = "\r\n") { it.joinToString(",", transform = ::field) }
    else rows.joinToString("\n", postfix = "\n") { row -> row.joinToString("\t") { it.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ') } }
}
