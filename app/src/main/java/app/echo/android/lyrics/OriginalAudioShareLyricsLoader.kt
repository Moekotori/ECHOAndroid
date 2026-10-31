package app.echo.android.lyrics

import android.content.Context
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.OriginalAudioShareLyrics
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.settings.EchoLyricsSource

/** Worker-only, offline export. Uses the saved selection before local and matching cached lyrics. */
internal class OriginalAudioShareLyricsLoader(context: Context, private val sourceOrder: List<EchoLyricsSource>) {
    private val store = ImportedLyricsStore(context.applicationContext)
    private val resolver = LocalLyricsResolver(context.applicationContext)
    private val dao = EchoLibraryDatabase.create(context.applicationContext).trackDao()

    suspend fun load(track: EchoTrack): OriginalAudioShareLyrics? {
        val saved = store.readSaved(track.id, selected = true)
        val bound = if (saved == null) store.lyricsUriForTrack(track.id)?.let(resolver::loadFromUri) else null
        val local = if (saved == null && bound == null) dao.getTrackById(track.id)?.let { resolver.loadForTrack(it, sourceOrder) } else null
        val cached = if (saved == null && bound == null && local == null)
            store.readSaved(track.id, selected = false)?.takeIf {
                OnlineLyricsCachePolicy.matches(it, EchoLyricsSearchRequest(track.title, track.artist, track.album, track.durationMs))
            } else null
        return (saved ?: bound ?: local ?: cached)?.let {
            OriginalAudioShareLyricsFormatter.format(it, store.lyricsOffsetForTrack(track.id), track.clipStartMs)
        }
    }
}
