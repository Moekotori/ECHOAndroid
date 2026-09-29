package app.echo.android.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.ListeningStatsEntry

/** 24 根柱子的时段分布。静态绘制，没有动画或计时器。 */
@Composable
internal fun ListeningStatsHourChart(playsByHour: List<Int>, modifier: Modifier = Modifier) {
    val accent = echoAccentColor()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val max = remember(playsByHour) { playsByHour.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    val peak = remember(playsByHour) { playsByHour.indices.maxByOrNull { playsByHour[it] } ?: 0 }
    val peakText = stringResource(R.string.listening_stats_peak_hour, peak)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.listening_stats_by_hour),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(peakText, style = MaterialTheme.typography.bodyMedium, color = homeBodyColor())
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = peakText },
        ) {
            val slots = 24
            val gap = 3.dp.toPx()
            val barWidth = ((size.width - gap * (slots - 1)) / slots).coerceAtLeast(1f)
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            for (hour in 0 until slots) {
                val value = playsByHour.getOrElse(hour) { 0 }
                val left = hour * (barWidth + gap)
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                if (value > 0) {
                    val barHeight = (size.height * value / max).coerceAtLeast(barWidth)
                    drawRoundRect(accent, Offset(left, size.height - barHeight), Size(barWidth, barHeight), radius)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = homeBodyColor())
            }
        }
    }
}

@Composable
internal fun ListeningStatsRankRow(
    rank: Int,
    entry: ListeningStatsEntry,
    circle: Boolean,
    onClick: (() -> Unit)?,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .let { if (onClick != null) it.echoClickable(onClick = onClick) else it }
        .padding(vertical = 6.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = homeBodyColor(),
            modifier = Modifier.width(24.dp),
        )
        ArtworkTile(
            artworkUri = entry.artworkUri,
            modifier = Modifier.size(48.dp).clip(if (circle) CircleShape else RoundedCornerShape(8.dp)),
            accent = echoAccentColor(),
            cornerRadius = if (circle) 24.dp else 8.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let { subtitle ->
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = homeBodyColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(listeningPlayCount(entry.playCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(formatListeningDuration(entry.listenedMs), style = MaterialTheme.typography.labelSmall, color = homeBodyColor())
        }
    }
}
