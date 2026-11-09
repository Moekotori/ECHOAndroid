package app.echo.android.connect

import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

// Parsing and event presence detection must agree, including an explicit null cover.
private val ArtworkFields = listOf(
    "artworkUrl", "coverUrl", "coverThumb", "cover", "albumArtUrl", "albumArt",
    "imageUrl", "thumbnailUrl", "artwork",
)

internal fun JSONObject.hasEchoLinkArtwork(): Boolean = ArtworkFields.any(::has)

internal fun JSONObject.optArtworkUrl(): String? = ArtworkFields.firstNotNullOfOrNull { field ->
    when (val value = opt(field)) {
        is String -> value.trim().takeIf { it.isNotEmpty() && it != "null" }
        is JSONObject -> value.optText("url")
        else -> null
    }
}

internal fun String.playlistArtworkOrNull(): String? {
    val path = toHttpUrlOrNull()?.encodedPath ?: return this
    val prefix = "/echo-link/v1/artwork/s1."
    if (!path.startsWith(prefix)) return this
    // The PC signs a placeholder URL even when coverId is null. Inspect it only for display.
    val payload = path.removePrefix(prefix).substringBefore('.')
    if (payload.length > 4096) return this
    val cover = runCatching {
        JSONObject(Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8))
    }.getOrNull()
    return takeUnless { cover?.has("coverId") == true && cover.isNull("coverId") }
}
