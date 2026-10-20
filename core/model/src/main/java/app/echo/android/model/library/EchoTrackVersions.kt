package app.echo.android.model.library

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Associations never change track identities, playlists or metadata. */
data class EchoTrackVersions(
    val anchor: EchoTrack,
    val tracks: List<EchoTrack>,
    val preferredTrackId: String? = null,
)

object EchoTrackVersionPolicy {
    private val whitespace = Regex("\\s+")
    private val unknown = setOf("", "unknown", "unknown artist", "unknown album", "<unknown>", "未知", "未知歌手", "未知专辑")

    fun normalized(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(whitespace, " ")

    /** Keep all edition markers in both title and album, including live/remix/remaster. */
    fun key(track: EchoTrack): String? {
        val fields = listOf(track.title, track.artist, track.album.orEmpty()).map(::normalized)
        if (fields.any { it in unknown } || track.durationMs <= 0) return null
        return fields.joinToString("\u0000")
    }

    fun matches(anchor: EchoTrack, candidate: EchoTrack): Boolean =
        key(anchor)?.let { it == key(candidate) && abs(anchor.durationMs - candidate.durationMs) <= 2_000L &&
            anchor.clipStartMs == candidate.clipStartMs && anchor.clipEndMs == candidate.clipEndMs } == true

    fun choose(anchor: EchoTrack, candidates: List<EchoTrack>, preferredId: String?, playableIds: Set<String>): EchoTrack {
        val eligible = candidates.filter { matches(anchor, it) && it.id in playableIds }
        return eligible.firstOrNull { it.id == preferredId }
            ?: eligible.firstOrNull { it.source.isLocalAudioFile }
            ?: eligible.firstOrNull { it.id == anchor.id }
            ?: eligible.firstOrNull()
            ?: anchor
    }
}
