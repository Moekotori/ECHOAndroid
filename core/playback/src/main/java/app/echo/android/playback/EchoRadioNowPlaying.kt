package app.echo.android.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.radio.EchoRadioStation

data class EchoRadioDisplay(val title: String, val artist: String)

/**
 * Live radio song text. ExoPlayer keeps [MediaItem] title ahead of in-stream ICY, so the song is
 * stored beside the station name instead of replacing it.
 */
object EchoRadioNowPlaying {
    const val MaxChars = 512

    fun resolve(
        stationName: String,
        host: String,
        liveTitle: String?,
        rememberedTitle: String?,
    ): EchoRadioDisplay {
        val station = stationName.trim()
        val song = songOrNull(liveTitle, station) ?: songOrNull(rememberedTitle, station)
        return if (song != null) EchoRadioDisplay(title = song, artist = station)
        else EchoRadioDisplay(title = station, artist = host)
    }

    /** Cleaned song title to store, or null when this block should not replace the current one. */
    fun rememberedUpdate(stationName: String, current: String?, icyRaw: String?): String? {
        val song = songOrNull(icyRaw, stationName) ?: return null
        return song.takeUnless { it == current }
    }

    fun songOrNull(raw: String?, stationName: String): String? {
        val cleaned = raw?.let(::clean)?.takeIf { it.isNotEmpty() } ?: return null
        if (cleaned == stationName.trim()) return null
        return cleaned
    }

    fun clean(raw: String): String {
        val decoded = raw
            .replace(Ampersand, "&")
            .replace(HexEntity) { decodeCodePoint(it.groupValues[1], 16, it.value) }
            .replace(DecimalEntity) { decodeCodePoint(it.groupValues[1], 10, it.value) }
            .replace(Nbsp, " ")
            .replace(Quote, "\"")
            .replace(Apostrophe, "'")
            .replace(LessThan, "<")
            .replace(GreaterThan, ">")
        val collapsed = Whitespace.replace(decoded, " ").trim()
        if (collapsed.length <= MaxChars) return collapsed
        var end = MaxChars
        if (Character.isHighSurrogate(collapsed[end - 1])) end -= 1
        return collapsed.substring(0, end)
    }

    private fun decodeCodePoint(raw: String, radix: Int, original: String): String {
        val code = raw.toIntOrNull(radix) ?: return original
        if (code < 1 || code > 0x10FFFF || code in 0xD800..0xDFFF) return original
        return try {
            String(Character.toChars(code))
        } catch (_: IllegalArgumentException) {
            original
        }
    }

    private val Ampersand = Regex("&amp;", RegexOption.IGNORE_CASE)
    private val HexEntity = Regex("&#x([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)
    private val DecimalEntity = Regex("&#(\\d+);")
    private val Nbsp = Regex("&nbsp;", RegexOption.IGNORE_CASE)
    private val Quote = Regex("&quot;", RegexOption.IGNORE_CASE)
    private val Apostrophe = Regex("&apos;", RegexOption.IGNORE_CASE)
    private val LessThan = Regex("&lt;", RegexOption.IGNORE_CASE)
    private val GreaterThan = Regex("&gt;", RegexOption.IGNORE_CASE)
    private val Whitespace = Regex("\\s+")
}

internal const val EchoRadioStreamTitleExtra = "app.echo.android.playback.RADIO_STREAM_TITLE"
private const val RadioSourceId = "radio"

fun Player.radioNowPlaying(): EchoRadioDisplay? {
    val item = currentMediaItem ?: return null
    if (!EchoRadioStation.isRadio(item.mediaId)) return null
    return EchoRadioNowPlaying.resolve(
        stationName = item.mediaMetadata.title?.toString().orEmpty(),
        host = item.mediaMetadata.artist?.toString().orEmpty(),
        liveTitle = null,
        rememberedTitle = item.mediaMetadata.extras?.getString(EchoRadioStreamTitleExtra)
            ?: mediaMetadata.extras?.getString(EchoRadioStreamTitleExtra),
    )
}

internal fun MediaMetadata.radioNowPlayingOrNull(): EchoRadioDisplay? {
    val remembered = extras?.getString(EchoRadioStreamTitleExtra)
    val isRadio = extras?.getString(EchoPlaybackSourceExtra) == RadioSourceId || !remembered.isNullOrBlank()
    if (!isRadio) return null
    return EchoRadioNowPlaying.resolve(
        stationName = title?.toString().orEmpty(),
        host = artist?.toString().orEmpty(),
        liveTitle = null,
        rememberedTitle = remembered,
    )
}

internal fun EchoTrackRef.withRadioNowPlaying(display: EchoRadioDisplay?): EchoTrackRef {
    if (display == null) return this
    return copy(
        title = display.title.ifBlank { title },
        artist = display.artist.ifBlank { artist },
    )
}

internal fun MediaItem.withRadioStreamTitle(title: String): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY)
    extras.putString(EchoRadioStreamTitleExtra, title)
    return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
}
