package app.echo.android.model.radio

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import java.net.URI

/** Radio IDs survive queue/session persistence without changing the song database. */
data class EchoRadioStation(
    val id: String, val name: String, val url: String,
    val artworkUrl: String? = null, val favorite: Boolean = false, val lastPlayedMs: Long = 0,
    val description: String = "", val genre: String = "", val homepage: String? = null,
    val codec: String? = null, val bitrateKbps: Int? = null, val countryCode: String = "", val language: String = "",
    val streams: List<EchoRadioStream> = emptyList(), val defaultStreamUrl: String? = null, val videoUrl: String? = null,
) {
    fun toTrack(streamUrl: String? = null): EchoTrack {
        val chosen = streamUrl?.takeIf { it == url || streams.any { stream -> stream.url == it } }
            ?: defaultStreamUrl?.takeIf { it == url || streams.any { stream -> stream.url == it } } ?: url
        val selectedCodec = if (chosen == url) codec else streams.firstOrNull { it.url == chosen }?.codec
        return EchoTrack(
        id = MediaIdPrefix + id,
        uri = chosen,
        title = name,
        artist = URI(url).host.orEmpty(),
        source = LibrarySource("radio"),
        artworkUri = artworkUrl,
        genre = genre.takeIf { it.isNotBlank() },
        mimeType = if (selectedCodec.equals("HLS", ignoreCase = true)) "application/x-mpegURL" else null,
    )
    }

    companion object {
        const val MediaIdPrefix = "radio:"
        const val MaxStations = 500
        fun isRadio(mediaId: String?): Boolean = mediaId?.startsWith(MediaIdPrefix) == true

        fun validUrl(raw: String): Boolean = try {
            val uri = URI(raw.trim())
            raw.trim().length <= 4096 &&
                uri.scheme?.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null &&
                (uri.port == -1 || uri.port in 1..65535)
        } catch (_: Exception) { false }
    }
}

data class EchoRadioStream(val url: String, val codec: String? = null, val bitrateKbps: Int? = null)
