package app.echo.android.model.library

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Associations never change track identities, playlists or metadata. */
data class EchoTrackVersions(
    val anchor: EchoTrack,
    val tracks: List<EchoTrack>,
    val preferredTrackId: String? = null,
    val quality: Map<String, EchoTrackAudioQuality> = emptyMap(),
    val recommendedTrackId: String? = null,
    val locations: Map<String, String> = emptyMap(),
    val preferHighestQuality: Boolean = false,
)

object EchoTrackVersionPolicy {
    private val whitespace = Regex("\\s+")
    private val unknown = setOf("", "unknown", "unknown artist", "unknown album", "unknown track", "unknown title", "<unknown>",
        "未知", "未知歌手", "未知艺术家", "未知专辑", "未知曲目", "不明なアーティスト", "不明なアルバム", "不明な曲")

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

    fun choose(anchor: EchoTrack, candidates: List<EchoTrack>, preferredId: String?, playableIds: Set<String>,
        quality: Map<String, EchoTrackAudioQuality> = emptyMap(), preferQuality: Boolean = false): EchoTrack {
        val eligible = candidates.filter { EchoDuplicateVersionPolicy.matches(anchor, it) && it.id in playableIds }
        val automatic = if (preferQuality) eligible.filter {
            (quality.isEmpty() || !it.source.isLocalAudioFile || it.id in quality || it.id == anchor.id) } else eligible
        return eligible.firstOrNull { it.id == preferredId }
            ?: (if (preferQuality) automatic.filter { it.source.isLocalAudioFile } else null)?.maxWithOrNull(
                compareBy<EchoTrack> { (quality[it.id] ?: EchoTrackAudioQuality()).score(it) }.thenByDescending { it.id })
            ?: automatic.firstOrNull { it.source.isLocalAudioFile }
            ?: automatic.firstOrNull { it.id == anchor.id }
            ?: automatic.firstOrNull()
            ?: anchor
    }
}
