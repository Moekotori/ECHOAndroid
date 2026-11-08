package app.echo.android.connect

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
