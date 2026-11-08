package app.echo.android.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.PlaybackHeatmapDay
import app.echo.android.model.playback.PlaybackHistorySummary
import java.time.LocalDate
import java.time.ZoneId

private const val TREND_DAYS = 14

/**
 * Totals for the current filter plus a 14-day strip. The strip ignores the filter on purpose:
 * it answers "how have I been listening lately", like the dashboard on ECHO PC.
 */
@Composable
internal fun PlaybackHistoryOverview(
    summary: PlaybackHistorySummary?,
    trend: List<PlaybackHeatmapDay>,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        shape = shape,
        color = scheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.16f), scheme.primary.copy(alpha = 0f))))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.listening_stats_total_time),
                        style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                    )
                    Text(
                        if (summary == null) "—" else formatListeningDuration(summary.listenedMs),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                InsightsLink(onStats)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric(
                    stringResource(R.string.listening_stats_total_plays),
                    summary?.plays?.toString() ?: "—", Modifier.weight(1f),
                )
                OverviewMetric(
                    stringResource(R.string.playback_history_overview_tracks),
                    summary?.tracks?.toString() ?: "—", Modifier.weight(1f),
                )
                OverviewMetric(
                    stringResource(R.string.playback_history_overview_latest),
                    summary?.latestPlayedAtEpochMs?.let(::relativeTime) ?: "—", Modifier.weight(1.3f),
                )
            }
            PlaybackHistoryTrend(trend)
        }
    }
}

@Composable
private fun InsightsLink(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(50)).echoClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.playback_history_insights),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = scheme.primary,
        )
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.width(20.dp), tint = scheme.primary)
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(scheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Static bars, one per day, today last. No animation or timer: it redraws only when data changes. */
@Composable
private fun PlaybackHistoryTrend(trend: List<PlaybackHeatmapDay>) {
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now(ZoneId.systemDefault()).toEpochDay() }
    val counts = remember(trend, today) {
        val byDay = trend.associate { it.epochDay to it.playCount }
        IntArray(TREND_DAYS) { byDay[today - (TREND_DAYS - 1) + it] ?: 0 }
    }
    val max = remember(counts) { counts.max().coerceAtLeast(1) }
    val total = remember(counts) { counts.sum() }
    val description = pluralStringResource(R.plurals.playback_history_trend_description, total, total)
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.playback_history_trend), style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { },
        )
        val accent = scheme.primary
        val rest = scheme.onSurface.copy(alpha = 0.08f)
        Canvas(Modifier.fillMaxWidth().height(52.dp).clearAndSetSemantics { }) {
            val gap = 5.dp.toPx()
            val bar = ((size.width - gap * (TREND_DAYS - 1)) / TREND_DAYS).coerceAtLeast(1f)
            val radius = CornerRadius(bar / 2f, bar / 2f)
            for (i in 0 until TREND_DAYS) {
                val left = i * (bar + gap)
                val value = counts[i]
                drawRoundRect(rest, Offset(left, 0f), Size(bar, size.height), radius)
                if (value > 0) {
                    val h = (size.height * value / max).coerceAtLeast(bar)
                    drawRoundRect(
                        accent.copy(alpha = if (i == TREND_DAYS - 1) 1f else 0.5f),
                        Offset(left, size.height - h), Size(bar, h), radius,
                    )
                }
            }
        }
    }
}

internal fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
