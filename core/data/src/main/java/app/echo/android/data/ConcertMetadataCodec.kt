package app.echo.android.data

import app.echo.android.model.library.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

internal object ConcertMetadataCodec {
    fun text(value: String?, limit: Int = 1000): String? = value?.take(limit * 2)
        ?.let(ArtistConcertParser::text)?.take(limit)?.takeIf { it.isNotBlank() }

    fun date(value: String?): String? {
        val normalized = value?.trim()?.replace('/', '-')?.replace(' ', 'T') ?: return null
        val valid = runCatching {
            when {
                normalized.length == 10 -> LocalDate.parse(normalized)
                Regex("(?:Z|[+-][0-9]{2}:[0-9]{2})$").containsMatchIn(normalized) -> OffsetDateTime.parse(normalized)
                else -> LocalDateTime.parse(normalized)
            }
        }.isSuccess
        return normalized.takeIf { valid }
    }

    fun lineup(names: List<ConcertPerformer>): List<ConcertPerformer> = names.asSequence().mapNotNull {
        text(it.name, 160)?.let { name -> ConcertPerformer(name, text(it.role, 80)) }
    }.distinctBy { it.name.lowercase(java.util.Locale.ROOT) }.take(32).toList()

    fun encode(details: ArtistConcertDetails): JSONObject = JSONObject()
        .put("opens", details.opensAt).put("ends", details.endsAt)
        .put("lineup", JSONArray(details.lineup.map { JSONObject().put("name", it.name).put("role", it.role) }))
        .put("address", details.address).put("lat", details.latitude).put("lon", details.longitude)
        .put("ticketed", details.ticketed).put("price", details.priceText)
        .put("saleStart", details.salesStartAt).put("saleEnd", details.salesEndAt).put("notes", details.notes)
        .put("sources", JSONArray(details.sources.map {
            JSONObject().put("source", it.source).put("updated", it.updatedAt).put("fetched", it.fetchedAt)
        }))

    fun decode(json: JSONObject): ArtistConcertDetails {
        val lat = json.optDouble("lat", Double.NaN).takeIf { it.isFinite() && it in -90.0..90.0 }
        val lon = json.optDouble("lon", Double.NaN).takeIf { it.isFinite() && it in -180.0..180.0 }
        return ArtistConcertDetails(
            opensAt = date(json.text("opens")), endsAt = date(json.text("ends")),
            lineup = lineup(json.objects("lineup").map { ConcertPerformer(it.text("name").orEmpty(), it.text("role")) }),
            address = text(json.text("address"), 512), latitude = lat.takeIf { lon != null }, longitude = lon.takeIf { lat != null },
            ticketed = if (json.has("ticketed") && !json.isNull("ticketed")) json.optBoolean("ticketed") else null,
            priceText = text(json.text("price"), 512), salesStartAt = date(json.text("saleStart")), salesEndAt = date(json.text("saleEnd")),
            notes = text(json.text("notes")), sources = json.objects("sources").take(10).mapNotNull {
                it.text("source")?.let { source -> ConcertSourceStamp(source, date(it.text("updated")), date(it.text("fetched"))) }
            },
        )
    }

    fun merge(first: ArtistConcertDetails, second: ArtistConcertDetails) = first.copy(
        opensAt = first.opensAt ?: second.opensAt, endsAt = first.endsAt ?: second.endsAt,
        lineup = lineup(first.lineup + second.lineup), address = first.address ?: second.address,
        latitude = first.latitude ?: second.latitude, longitude = first.longitude ?: second.longitude,
        ticketed = first.ticketed ?: second.ticketed, priceText = first.priceText ?: second.priceText,
        salesStartAt = first.salesStartAt ?: second.salesStartAt, salesEndAt = first.salesEndAt ?: second.salesEndAt,
        notes = first.notes ?: second.notes, sources = (first.sources + second.sources).distinct().take(10),
    )
}
