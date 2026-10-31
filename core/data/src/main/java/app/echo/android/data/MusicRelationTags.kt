package app.echo.android.data

import app.echo.android.model.library.EchoMusicRelationMetadata
import app.echo.android.model.library.EchoMusicCredit
import org.json.JSONObject
import java.util.Locale

internal fun isMusicRelationTag(key: String): Boolean {
    val name = key.uppercase(Locale.ROOT).replace(" ", "").replace("_", "")
    return name in setOf("ARTIST", "COMPOSER", "CONDUCTOR", "PERFORMER", "SOLOIST", "SOLOISTS", "PRODUCER", "ENGINEER",
        "ARRANGER", "LYRICIST", "REMIXER", "LABEL", "PUBLISHER", "ORGANIZATION", "WORK", "WORKTITLE", "MOVEMENT", "MOVEMENTNAME",
        "RECORDING", "RECORDINGTITLE", "VERSION", "MUSICBRAINZARTISTID", "MUSICBRAINZCOMPOSERID", "MUSICBRAINZCONDUCTORID",
        "MUSICBRAINZPERFORMERID", "MUSICBRAINZWORKID", "MUSICBRAINZTRACKID", "MUSICBRAINZRECORDINGID") ||
        name.startsWith("PERFORMER:") || name.startsWith("CREDIT:")
}

/** Shared aliases for Vorbis/APE comments and ID3/MP4 custom text tags. */
internal fun musicRelationTags(values: Iterable<Pair<String, String>>): EchoMusicRelationMetadata {
    val tags = values.groupBy({ it.first.uppercase(Locale.ROOT).replace(" ", "").replace("_", "") }, { it.second })
    fun text(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
        tags[key]?.let(LibraryArtistPolicy::display)?.take(2000)
    }
    val credits = mutableListOf<EchoMusicCredit>()
    fun creditValues(value: String) = value.split(';', '；', '\u0000').map(String::trim).filter(String::isNotEmpty)
    fun people(role: String, keys: List<String>, idKeys: List<String> = emptyList()) {
        val names = keys.flatMap { tags[it].orEmpty() }.flatMap(::creditValues).take(32)
        val ids = idKeys.firstNotNullOfOrNull { tags[it] }?.flatMap(::creditValues).orEmpty()
        // A single ID must never be assigned to several names, or vice versa.
        names.forEachIndexed { index, name ->
            credits += EchoMusicCredit(name.take(256), role, ids.getOrNull(index)?.takeIf { names.size == ids.size }?.let(::musicPersonId))
        }
    }
    people("artist", listOf("ARTIST"), listOf("MUSICBRAINZARTISTID"))
    people("composer", listOf("COMPOSER"), listOf("MUSICBRAINZCOMPOSERID"))
    people("conductor", listOf("CONDUCTOR"), listOf("MUSICBRAINZCONDUCTORID"))
    people("performer", listOf("PERFORMER", "SOLOISTS", "SOLOIST"), listOf("MUSICBRAINZPERFORMERID"))
    for (role in listOf("PRODUCER", "ENGINEER", "ARRANGER", "LYRICIST", "REMIXER")) people(role.lowercase(Locale.ROOT), listOf(role))
    // Vorbis instrument-qualified credits retain their role instead of losing it in the name.
    values.filter { it.first.substringBefore(':').uppercase(Locale.ROOT) in listOf("PERFORMER", "CREDIT") && ':' in it.first }
        .take(64).forEach { (key, value) ->
        LibraryArtistPolicy.names(value).take(32).forEach { name ->
            val role = key.substringAfter(':').trim().lowercase(Locale.ROOT).take(70)
            if (role.isNotEmpty()) credits += EchoMusicCredit(name.take(256),
                if (key.startsWith("PERFORMER:", true)) "performer:$role" else role)
        }
    }
    return EchoMusicRelationMetadata(
        work = text("WORK", "WORKTITLE"), movement = text("MOVEMENT", "MOVEMENTNAME"),
        conductor = text("CONDUCTOR"), performer = text("PERFORMER", "SOLOISTS", "SOLOIST"),
        recording = text("RECORDING", "RECORDINGTITLE", "VERSION"),
        workId = text("MUSICBRAINZWORKID"), recordingId = text("MUSICBRAINZTRACKID", "MUSICBRAINZRECORDINGID"),
        label = text("LABEL", "PUBLISHER", "ORGANIZATION"),
        credits = credits.distinct().take(64),
    )
}

internal fun EchoMusicRelationMetadata.encoded(): String? {
    val values = listOf(work, movement, conductor, performer, recording, workId, recordingId, label)
    if (values.all { it.isNullOrBlank() } && credits.isEmpty()) return null
    return JSONObject().apply {
        listOf("work", "movement", "conductor", "performer", "recording", "workId", "recordingId", "label")
            .zip(values).forEach { (key, value) -> value?.trim()?.takeIf(String::isNotEmpty)?.let { put(key, it.take(2000)) } }
        if (credits.isNotEmpty()) put("credits", org.json.JSONArray().apply {
            credits.take(64).forEach { credit -> put(JSONObject().apply {
                put("name", credit.name.take(256)); put("role", credit.role.take(80))
                credit.personId?.let { put("personId", it.take(80)) }
            }) }
        })
    }.toString()
}

internal fun decodeMusicRelationTags(json: String?): EchoMusicRelationMetadata {
    if (json == null || json.length > 65536) return EchoMusicRelationMetadata()
    return try {
        val obj = JSONObject(json)
        fun text(key: String) = obj.optString(key).takeIf(String::isNotBlank)?.take(2000)
        val credits = obj.optJSONArray("credits")?.let { array -> (0 until minOf(array.length(), 64)).mapNotNull { index ->
            val credit = array.optJSONObject(index) ?: return@mapNotNull null
            val name = credit.optString("name").trim().take(256).takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val role = credit.optString("role").trim().take(80).takeIf(String::isNotEmpty) ?: return@mapNotNull null
            EchoMusicCredit(name, role, credit.optString("personId").takeIf(String::isNotBlank)?.let(::musicPersonId))
        } }.orEmpty()
        EchoMusicRelationMetadata(text("work"), text("movement"), text("conductor"), text("performer"),
            text("recording"), text("workId"), text("recordingId"), text("label"), credits)
    } catch (_: org.json.JSONException) { EchoMusicRelationMetadata() }
}

internal fun EchoMusicRelationMetadata.withFallback(other: EchoMusicRelationMetadata) = EchoMusicRelationMetadata(
    work ?: other.work, movement ?: other.movement, conductor ?: other.conductor, performer ?: other.performer,
    recording ?: other.recording, workId ?: other.workId, recordingId ?: other.recordingId,
    label ?: other.label, (credits + other.credits.filter { old -> credits.none { it.role == old.role && it.name == old.name } }).take(64),
)

/** Accept only an explicit UUID or an official MusicBrainz person URL as a person identity. */
internal fun musicPersonId(value: String): String? {
    val raw = value.trim().removeSuffix("/")
    val id = if (raw.startsWith("https://musicbrainz.org/artist/")) raw.substringAfterLast('/') else raw
    return id.lowercase(Locale.ROOT).takeIf { it.matches(MusicPersonIdPattern) }
}

private val MusicPersonIdPattern = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
