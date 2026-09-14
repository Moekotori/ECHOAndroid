package app.echo.android.model.radio

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import java.net.URI

/** Radio IDs survive queue/session persistence without changing the song database. */
data class EchoRadioStation(val id: String, val name: String, val url: String) {
    fun toTrack(): EchoTrack = EchoTrack(
        id = MediaIdPrefix + id,
        uri = url,
        title = name,
        artist = URI(url).host.orEmpty(),
        source = LibrarySource("radio"),
    )

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
