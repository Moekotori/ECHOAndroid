package app.echo.android.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.security.MessageDigest
import app.echo.android.model.library.EchoMusicRelationKind
import app.echo.android.model.library.EchoMusicCredit

@Entity(tableName = "library_music_relations", primaryKeys = ["trackId", "kind", "entityKey"],
    foreignKeys = [ForeignKey(entity = LibraryTrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["kind", "entityKey", "trackId"])])
data class LibraryMusicRelationEntity(
    val trackId: String, val kind: String, val entityKey: String, val name: String, val context: String,
)

internal fun musicRelationMemberships(track: LibraryTrackEntity): List<LibraryMusicRelationEntity> {
    if (!LibraryScanPolicy.isLocalLibrarySource(track.source)) return emptyList()
    val tags = decodeMusicRelationTags(track.musicRelationsJson)
    val result = mutableListOf<LibraryMusicRelationEntity>()
    fun key(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
        .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun add(kind: EchoMusicRelationKind, name: String?, context: String = "", identity: String? = null, scope: String = context): String? {
        val title = name?.trim()?.take(2000)?.takeIf(String::isNotBlank) ?: return null
        val id = key(kind.name, identity ?: title.normalizedForSearch(), scope.normalizedForSearch())
        result += LibraryMusicRelationEntity(track.id, kind.name, id, title, context)
        return id
    }
    val effectiveComposers = LibraryArtistPolicy.names(track.composer).map { it.normalizedForSearch() }.toSet()
    val effectiveArtists = LibraryArtistPolicy.names(track.artist).map { it.normalizedForSearch() }.toSet()
    val credits = tags.credits.filterNot {
        (it.role == "composer" && it.name.normalizedForSearch() !in effectiveComposers) ||
            (it.role == "artist" && it.name.normalizedForSearch() !in effectiveArtists)
    }.toMutableList()
    fun fallback(role: String, names: String?) {
        LibraryArtistPolicy.names(names).take(32).forEach { name ->
            if (credits.none { it.role == role && it.name.normalizedForSearch() == name.normalizedForSearch() })
                credits += EchoMusicCredit(name, role)
        }
    }
    fallback("artist", track.artist.takeUnlessUnknownMetadata())
    fallback("composer", track.composer)
    fallback("conductor", tags.conductor)
    fallback("performer", tags.performer)
    fun personKey(credit: EchoMusicCredit) = credit.personId?.let(::musicPersonId)?.let { "mbid:$it" }
        ?: "name:${credit.name.normalizedForSearch()}"
    val personRoles = credits.groupBy(::personKey).mapValues { (_, people) -> people.map { it.role }.distinct().sorted() }
    val personKeys = mutableMapOf<String, MutableList<String>>()
    credits.distinct().take(128).forEach { credit ->
        val identity = personKey(credit)
        val context = listOfNotNull(personRoles[identity]?.joinToString(" · "), credit.personId?.let { "MusicBrainz · ${it.take(8)}" }).joinToString(" · ")
        add(EchoMusicRelationKind.Contributor, credit.name, context, identity, scope = "")
        val kind = when {
            credit.role == "composer" -> EchoMusicRelationKind.Composer
            credit.role == "conductor" -> EchoMusicRelationKind.Conductor
            credit.role == "performer" || credit.role.startsWith("performer:") -> EchoMusicRelationKind.Performer
            else -> null
        }
        if (kind != null) {
            add(kind, credit.name, credit.personId?.let { "MusicBrainz · ${it.take(8)}" }.orEmpty(), identity, scope = "")
            personKeys.getOrPut(kind.name) { mutableListOf() } += identity
        }
        add(EchoMusicRelationKind.CreditRole, credit.role.substringAfter(':'))
    }
    val composerContext = personKeys[EchoMusicRelationKind.Composer.name].orEmpty().distinct().sorted().joinToString("; ")
    val workContext = track.composer.orEmpty().ifBlank { track.album.orEmpty() }
    val workKey = add(EchoMusicRelationKind.Work, tags.work, workContext, tags.workId,
        if (tags.workId != null) "" else composerContext.ifBlank { track.albumKey })
    val cue = track.clipStartMs > 0 || track.clipEndMs > 0 || track.id.contains("#cue:")
    if (!cue) add(EchoMusicRelationKind.Movement, tags.movement, tags.work ?: track.album.orEmpty(), scope = workKey ?: track.albumKey)
    LibraryArtistPolicy.names(tags.label).take(16).forEach { add(EchoMusicRelationKind.Label, it) }
    if (!cue && (workKey != null || tags.recording != null || tags.recordingId != null)) {
        val release = track.albumKey.takeIf { !track.album.isNullOrBlank() && it.isNotBlank() } ?: track.id
        val conductors = personKeys[EchoMusicRelationKind.Conductor.name].orEmpty().distinct().sorted()
        val performers = personKeys[EchoMusicRelationKind.Performer.name].orEmpty().distinct().sorted()
            .ifEmpty { LibraryArtistPolicy.names(track.artist).map { it.normalizedForSearch() }.sorted() }
        // A work's movements share one performance; recording IDs can differ per movement.
        val identity = key(release, workKey.orEmpty(), conductors.joinToString(";"), performers.joinToString(";"),
            tags.recording.orEmpty().normalizedForSearch(), tags.recordingId.orEmpty().takeIf { workKey == null }.orEmpty())
        val context = listOfNotNull(track.album, track.albumArtist, track.year?.toString(),
            credits.filter { it.role == "conductor" || it.role.startsWith("performer") }.map { it.name }.distinct()
                .takeIf { it.isNotEmpty() }?.joinToString("; ")).joinToString(" · ")
        add(EchoMusicRelationKind.Recording, tags.recording ?: track.album ?: track.title, context, identity, scope = "")
    }
    return result.distinctBy { it.kind to it.entityKey }
}
