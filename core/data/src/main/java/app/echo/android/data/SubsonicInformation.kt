package app.echo.android.data

import app.echo.android.model.library.*
import org.json.JSONObject

internal fun SubsonicClient.information(endpoint: SubsonicEndpoint, entry: NavidromeEntry): NavidromeInformation {
    require(entry.id.isNotBlank() && entry.kind != NavidromeKind.Radio)
    val track = entry.kind == NavidromeKind.Track
    val artist = entry.kind == NavidromeKind.Artist
    val detailKey = if (track) "song" else if (artist) "artist" else "album"
    val detail = request(if (track) "getSong.view" else if (artist) "getArtist.view" else "getAlbum.view",
        listOf("id" to entry.id)).optJSONObject(detailKey) ?: error("Incomplete Navidrome metadata")
    require(detail.optString("id") == entry.id)
    var enriched = detail.userEntry(endpoint, entry.kind)
    val info = if (track) null else try {
        request(if (artist) "getArtistInfo2.view" else "getAlbumInfo2.view", listOf("id" to entry.id))
            .optJSONObject(if (artist) "artistInfo2" else "albumInfo")
    } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    if (artist) {
        val image = listOf("largeImageUrl", "mediumImageUrl", "smallImageUrl").firstNotNullOfOrNull { key ->
            info?.metadataText(key)?.takeIf(::isSubsonicWebUrl)
        }
        if (image != null) enriched = enriched.copy(artworkUri = image)
    }
    val facts = ArrayList<Pair<String, String>>()
    for (key in listOf("album", "displayAlbumArtist", "genre", "displayComposer", "year", "track", "discNumber",
        "duration", "samplingRate", "bitDepth", "bitRate", "channelCount", "explicitStatus", "bpm", "playCount",
        "played", "musicBrainzId", "isrc", "comment", "version", "sortName", "created")) {
        detail.metadataText(key)?.let { facts += key to it }
    }
    for (key in listOf("artists", "albumArtists", "genres", "recordLabels", "moods", "releaseTypes", "discTitles",
        "works", "movements", "groupings", "contributors", "replayGain", "releaseDate", "originalReleaseDate")) {
        val value = detail.opt(key)
        if (value != null && value != JSONObject.NULL) metadataValue(value).takeIf(String::isNotBlank)?.let { facts += key to it }
    }
    return NavidromeInformation(enriched,
        description = (info?.metadataText(if (artist) "biography" else "notes"))?.let(::plainSubsonicText),
        sourceUrl = info?.metadataText("lastFmUrl")?.takeIf(::isSubsonicWebUrl), facts = facts)
}

/** Server biographies may contain HTML. Render text, never active markup or unvalidated links. */
internal fun plainSubsonicText(value: String): String = value.replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")
    .replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim().take(20_000)

private fun JSONObject.metadataText(key: String): String? = if (isNull(key)) null else opt(key)?.toString()?.trim()?.takeIf(String::isNotBlank)

private fun metadataValue(value: Any): String = when (value) {
    is org.json.JSONArray -> (0 until value.length()).mapNotNull { value.opt(it)?.takeUnless { it == JSONObject.NULL } }
        .joinToString(" · ", transform = ::metadataValue)
    is JSONObject -> {
        val name = value.metadataText("name") ?: value.metadataText("title")
        if (name != null) name else value.keys().asSequence().take(32)
            .joinToString(" · ") { key -> "$key: ${metadataValue(value.opt(key) ?: "")}" }
    }
    else -> value.toString()
}.take(4000)
