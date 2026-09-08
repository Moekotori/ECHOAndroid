package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics
import java.security.MessageDigest

/** Automatic downloads must be re-matched after metadata or matching policy changes. */
object OnlineLyricsCachePolicy {
    private const val Key = "online_match_fingerprint"
    private const val Revision = "2"

    fun matches(lyrics: EchoLyrics, request: EchoLyricsSearchRequest): Boolean =
        lyrics.metadata[Key] == fingerprint(request)

    fun stamp(lyrics: EchoLyrics, request: EchoLyricsSearchRequest): EchoLyrics =
        lyrics.copy(metadata = lyrics.metadata + (Key to fingerprint(request)))

    private fun fingerprint(request: EchoLyricsSearchRequest): String {
        val fields = listOf(Revision, request.title, request.artist, request.album.orEmpty(), request.durationMs.toString())
        // Length prefixes avoid ambiguity when user tags contain separators.
        val raw = fields.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
