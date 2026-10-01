package app.echo.android.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/** Displays the existing library aggregates; opening settings never scans files. */
@Composable
internal fun SettingsLibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    durationMs: Long,
    localSizeBytes: Long,
    offlineSizeBytes: Long,
    onOpenLibrary: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val localBytes = localSizeBytes.coerceAtLeast(0L)
    val offlineBytes = offlineSizeBytes.coerceIn(0L, Long.MAX_VALUE - localBytes)
    val totalBytes = localBytes + offlineBytes
    val localShare = if (totalBytes > 0L) (localBytes.toDouble() / totalBytes).toFloat() else 0f
    val localColor = scheme.primary
    val offlineColor = scheme.tertiary
    val trackColor = scheme.surfaceVariant
    val minutes = durationMs.coerceAtLeast(0L) / 60_000L

    Column(
        Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_library_overview_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            TextButton(onClick = onOpenLibrary) {
                Text(stringResource(R.string.settings_library_overview_manage))
            }
        }
        Box(Modifier.size(184.dp), contentAlignment = Alignment.Center) {
            // The ring represents the two music sizes, not a fictitious device capacity.
            // No per-frame state or animation is needed for these event-driven aggregates.
            Canvas(Modifier.fillMaxSize()) {
                val width = 14.dp.toPx()
                val inset = width / 2f
                val arcSize = Size(size.width - width, size.height - width)
                val origin = Offset(inset, inset)
                val stroke = Stroke(width)
                drawArc(trackColor, -90f, 360f, false, origin, arcSize, style = stroke)
                if (totalBytes > 0L) {
                    if (localBytes > 0L) drawArc(
                        localColor, -90f, 360f * localShare, false, origin, arcSize, style = stroke,
                    )
                    if (offlineBytes > 0L) drawArc(
                        offlineColor, -90f + 360f * localShare, 360f * (1f - localShare),
                        false, origin, arcSize, style = stroke,
                    )
                }
            }
            Column(
                Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    Formatter.formatFileSize(context, totalBytes),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.settings_library_overview_storage),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryOverviewMetric(
                numbers.format(trackCount.coerceAtLeast(0)),
                stringResource(R.string.settings_library_overview_tracks), Modifier.weight(1f),
            )
            LibraryOverviewMetric(
                numbers.format(albumCount.coerceAtLeast(0)),
                stringResource(R.string.settings_albums), Modifier.weight(1f),
            )
            LibraryOverviewMetric(
                numbers.format(artistCount.coerceAtLeast(0)),
                stringResource(R.string.settings_artists), Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LibraryOverviewDetail(
                label = stringResource(R.string.settings_library_overview_duration),
                value = stringResource(
                    R.string.settings_library_overview_hours_minutes,
                    numbers.format(minutes / 60L), numbers.format(minutes % 60L),
                ),
            )
            LibraryOverviewDetail(
                label = stringResource(R.string.settings_library_overview_local),
                value = Formatter.formatFileSize(context, localBytes),
                color = localColor,
            )
            LibraryOverviewDetail(
                label = stringResource(R.string.settings_library_overview_offline),
                value = Formatter.formatFileSize(context, offlineBytes),
                color = offlineColor,
            )
        }
        Text(
            stringResource(
                if (trackCount <= 0) R.string.settings_library_overview_empty
                else R.string.settings_library_overview_scope,
            ),
            modifier = Modifier.fillMaxWidth(),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LibraryOverviewMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LibraryOverviewDetail(label: String, value: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (color != null) Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End)
    }
}
