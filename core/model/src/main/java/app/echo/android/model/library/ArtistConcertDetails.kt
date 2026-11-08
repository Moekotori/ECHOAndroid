package app.echo.android.model.library

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class ArtistConcertPeriod { Upcoming, Past }
enum class ArtistConcertPhase { Upcoming, Ongoing, Started, Past }

data class ConcertPerformer(val name: String, val role: String? = null)
data class ConcertSourceStamp(val source: String, val updatedAt: String? = null, val fetchedAt: String? = null)

/** Optional source facts; missing values must never be inferred from a ticket link. */
data class ArtistConcertDetails(
    val opensAt: String? = null,
    val endsAt: String? = null,
    val lineup: List<ConcertPerformer> = emptyList(),
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val ticketed: Boolean? = null,
    val priceText: String? = null,
    val salesStartAt: String? = null,
    val salesEndAt: String? = null,
    val notes: String? = null,
    val sources: List<ConcertSourceStamp> = emptyList(),
)

/** Calendar-only and unknown-zone facts retain their precision. Never invent a show duration. */
fun ArtistConcert.phase(now: Instant): ArtistConcertPhase {
    val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    val today = now.atZone(zone ?: ZoneOffset.UTC).toLocalDate()
    fun instant(value: String): Instant? = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: zone?.let { runCatching { LocalDateTime.parse(value).atZone(it).toInstant() }.getOrNull() }
    val start = time?.let { instant("${date}T$it") }
    val end = details.endsAt
    if (end != null) {
        val endInstant = instant(end)
        if (endInstant != null) {
            if (endInstant <= now) return ArtistConcertPhase.Past
            if (start != null && start <= now) return ArtistConcertPhase.Ongoing
        } else if (runCatching { LocalDate.parse(end.take(10)) < today }.getOrDefault(false)) {
            return ArtistConcertPhase.Past
        }
    } else if (runCatching { LocalDate.parse(date) < today }.getOrDefault(false)) return ArtistConcertPhase.Past
    return if (start != null && start <= now) ArtistConcertPhase.Started else ArtistConcertPhase.Upcoming
}
