package app.echo.android.lyrics

import android.content.Context
import app.echo.android.connect.EchoPhoneLyrics
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.EchoSharedLocalLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Resolves one shared local song on demand, without online fetches or a resident cache. */
internal class SharedPhoneLyricsResolver(context: Context, private val library: EchoSharedLocalLibrary) {
    private val stored = ImportedLyricsStore(context)
    private val local = LocalLyricsResolver(context)
    private val settings = EchoSettingsStore(context)

    suspend fun read(id: String): EchoPhoneLyrics? = withContext(Dispatchers.IO) {
        val track = library.track(id) ?: return@withContext null
        val selected = LyricsContentPolicy.readable(stored.readSaved(id, selected = true, touchLastAccess = false))
        val imported = if (selected == null) stored.lyricsUriForTrack(id)?.let(local::loadFromUri) else null
        val sourceOrder = settings.appSettings.first().lyricsOptions.sourceOrder
        val localLyrics = if (selected == null && imported == null) runCatching { local.loadForTrack(track, sourceOrder) }.getOrNull() else null
        val cached = if (selected == null && imported == null && localLyrics == null) {
            stored.readSaved(id, selected = false, touchLastAccess = false)?.takeIf {
                OnlineLyricsCachePolicy.matches(it, EchoLyricsSearchRequest(track.title, track.artist, track.album, track.durationMs))
            }
        } else null
        val document = (selected ?: imported ?: localLyrics ?: cached)?.takeIf { it.lines.isNotEmpty() }
            ?: return@withContext null
        EchoPhoneLyrics(track.title, track.artist, track.album, track.durationMs, document, stored.lyricsOffsetForTrack(id))
    }
}
