package app.echo.android.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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

/** Existing library aggregates only: no file scan or per-frame work. */
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
    val palette = libraryPalette()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val localBytes = localSizeBytes.coerceAtLeast(0L)
    val offlineBytes = offlineSizeBytes.coerceIn(0L, Long.MAX_VALUE - localBytes)
    val totalBytes = localBytes + offlineBytes
    val minutes = durationMs.coerceAtLeast(0L) / 60_000L

    Column(
        Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_library_overview_title),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface)
            TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.settings_library_overview_manage)) }
        }
        LibraryTintPanel(palette.teal) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 280.dp || configuration.fontScale > 1.3f
                val ring: @Composable () -> Unit = {
                    LibraryStorageRing(localBytes, offlineBytes, palette.teal, palette.amber)
                }
                val storage: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.settings_library_overview_storage),
                            style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        Text(Formatter.formatFileSize(context, totalBytes),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = scheme.onSurface)
                        Text(stringResource(R.string.settings_library_overview_hours_minutes,
                            numbers.format(minutes / 60L), numbers.format(minutes % 60L)),
                            style = MaterialTheme.typography.bodySmall, color = palette.teal)
                    }
                }
                if (compact) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { ring(); storage() }
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ring()
                    Box(Modifier.weight(1f)) { storage() }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = palette.teal.copy(alpha = 0.14f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryOverviewMetric(numbers.format(trackCount.coerceAtLeast(0)),
                    stringResource(R.string.settings_library_overview_tracks), palette.teal, Modifier.weight(1f))
                LibraryOverviewMetric(numbers.format(albumCount.coerceAtLeast(0)),
                    stringResource(R.string.settings_albums), palette.blue, Modifier.weight(1f))
                LibraryOverviewMetric(numbers.format(artistCount.coerceAtLeast(0)),
                    stringResource(R.string.settings_artists), palette.violet, Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryOverviewDetail(stringResource(R.string.settings_library_overview_local),
                Formatter.formatFileSize(context, localBytes), palette.teal)
            LibraryOverviewDetail(stringResource(R.string.settings_library_overview_offline),
                Formatter.formatFileSize(context, offlineBytes), palette.amber)
        }
        Text(stringResource(if (trackCount <= 0) R.string.settings_library_overview_empty
            else R.string.settings_library_overview_scope),
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LibraryStorageRing(localBytes: Long, offlineBytes: Long, localColor: Color, offlineColor: Color) {
    val total = localBytes + offlineBytes
    val localShare = if (total > 0L) (localBytes.toDouble() / total).toFloat() else 0f
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 10.dp.toPx()
            val origin = Offset(width / 2f, width / 2f)
            val arcSize = Size(size.width - width, size.height - width)
            val stroke = Stroke(width)
            drawArc(trackColor, -90f, 360f, false, origin, arcSize, style = stroke)
            if (localBytes > 0L) drawArc(localColor, -90f, 360f * localShare, false, origin, arcSize, style = stroke)
            if (offlineBytes > 0L) drawArc(offlineColor, -90f + 360f * localShare,
                360f * (1f - localShare), false, origin, arcSize, style = stroke)
            drawCircle(localColor.copy(alpha = 0.10f), radius = size.minDimension / 2f - 20.dp.toPx(),
                style = Stroke(1.dp.toPx()))
        }
        Text(stringResource(R.string.settings_library_overview_local_short),
            Modifier.padding(22.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryOverviewMetric(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = color, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LibraryOverviewDetail(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    }
}
