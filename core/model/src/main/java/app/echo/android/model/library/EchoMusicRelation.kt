package app.echo.android.model.library

enum class EchoMusicRelationKind { Composer, Work, Movement, Conductor, Performer, Recording, Contributor, Label, CreditRole }

data class EchoMusicCredit(val name: String, val role: String, val personId: String? = null)

/** Read-only, explicit file tags. Missing relationships are never inferred from filenames. */
data class EchoMusicRelationMetadata(
    val work: String? = null,
    val movement: String? = null,
    val conductor: String? = null,
    val performer: String? = null,
    val recording: String? = null,
    val workId: String? = null,
    val recordingId: String? = null,
    val label: String? = null,
    val credits: List<EchoMusicCredit> = emptyList(),
)

data class EchoMusicRelation(
    val kind: EchoMusicRelationKind,
    val key: String,
    val title: String,
    val subtitle: String = "",
)

data class EchoMusicRelationSummary(
    val key: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int,
)

data class EchoMusicRelationOverview(
    val trackCount: Int = 0,
    val taggedTrackCount: Int = 0,
    val counts: Map<EchoMusicRelationKind, Int> = emptyMap(),
)
