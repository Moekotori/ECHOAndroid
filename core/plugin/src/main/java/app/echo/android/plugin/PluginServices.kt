package app.echo.android.plugin

data class PlaybackSnapshot(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

sealed class TransportCommand {
    data object Play : TransportCommand()
    data object Pause : TransportCommand()
    data object Next : TransportCommand()
    data object Previous : TransportCommand()
    data class Seek(val positionMs: Long) : TransportCommand()
}

data class LibraryTrackHit(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
)

data class PluginHttpResponse(
    val ok: Boolean,
    val status: Int,
    val body: String,
    val error: String?,
)

interface PluginServices {
    fun playbackSnapshot(): PlaybackSnapshot
    fun transport(command: TransportCommand)
    fun searchLibrary(query: String): List<LibraryTrackHit>
    fun fetch(url: String): PluginHttpResponse
}

internal fun LibraryTrackHit.forScript(): LibraryTrackHit = LibraryTrackHit(
    id = id.takeIf(PluginPaths::isOpaqueTrackId).orEmpty(),
    title = title.take(200),
    artist = artist.take(200),
    album = album.take(200),
)
