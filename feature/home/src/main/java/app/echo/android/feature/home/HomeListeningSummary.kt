package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.PlaybackHeatmapDay
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun HomeListeningSummary(days: List<PlaybackHeatmapDay>, onOpenLibrary: () -> Unit) {
    val today = LocalDate.now()
    val summary = remember(days, today) {
        val firstDay = today.with(DayOfWeek.MONDAY).minusWeeks(11).toEpochDay()
        val lastDay = today.toEpochDay()
        var plays = 0L
        val activeDays = mutableSetOf<Long>()
        for (day in days) {
            if (day.epochDay in firstDay..lastDay && day.playCount > 0) {
                plays += day.playCount.toLong()
                activeDays += day.epochDay
            }
        }
        plays to activeDays.size
    }
    val scheme = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    val playCount = remember(summary.first, locale) { NumberFormat.getIntegerInstance(locale).format(summary.first) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surface.copy(alpha = 0.62f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.home_listening_journal), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        }
        Text(
            text = if (summary.first > 0L) stringResource(R.string.home_listening_play_count, playCount)
                else stringResource(R.string.home_listening_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        )
        Text(
            text = if (summary.first > 0L) pluralStringResource(R.plurals.home_listening_days, summary.second, summary.second)
                else stringResource(R.string.home_listening_empty_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenLibrary, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.home_listening_open_library))
        }
    }
}
