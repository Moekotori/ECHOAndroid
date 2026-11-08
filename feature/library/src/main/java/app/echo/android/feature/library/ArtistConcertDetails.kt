package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.ArtistConcert
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun concertDisplayStamp(value: String, zone: String?, locale: Locale = Locale.getDefault()): String = runCatching {
    if (value.length == 10) LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    else {
        val date = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zone?.let(ZoneId::of) ?: ZoneId.systemDefault()).toLocalDateTime() }
            .getOrElse { LocalDateTime.parse(value) }
        date.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }
}.getOrDefault(value)

@Composable
internal fun ArtistConcertDetails(event: ArtistConcert) {
    val detail = event.details
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        @Composable fun fact(label: Int, value: String?) {
            if (!value.isNullOrBlank()) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
        fact(R.string.artist_concert_opens, detail.opensAt?.let { concertDisplayStamp(it, event.timezone) })
        fact(R.string.artist_concert_ends, detail.endsAt?.let { concertDisplayStamp(it, event.timezone) })
        val lineup = remember(detail.lineup) { detail.lineup.joinToString(" · ") { it.name + (it.role?.let { role -> " ($role)" } ?: "") } }
        fact(R.string.artist_concert_lineup, lineup)
        fact(R.string.artist_concert_address, detail.address)
        if (detail.latitude != null && detail.longitude != null) fact(R.string.artist_concert_coordinates,
            "${detail.latitude}, ${detail.longitude}")
        detail.ticketed?.let { fact(R.string.artist_concert_admission,
            stringResource(if (it) R.string.artist_concert_ticketed else R.string.artist_concert_free)) }
        fact(R.string.artist_concert_price, detail.priceText)
        fact(R.string.artist_concert_sale_start, detail.salesStartAt?.let { concertDisplayStamp(it, event.timezone) })
        fact(R.string.artist_concert_sale_end, detail.salesEndAt?.let { concertDisplayStamp(it, event.timezone) })
        fact(R.string.artist_concert_notes, detail.notes)
        detail.sources.forEach { source ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(source.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                source.updatedAt?.let { Text(stringResource(R.string.artist_concert_source_updated, concertDisplayStamp(it, event.timezone)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                source.fetchedAt?.let { Text(stringResource(R.string.artist_concert_checked, concertDisplayStamp(it, null)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (detail.sources.isEmpty()) Text(event.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
