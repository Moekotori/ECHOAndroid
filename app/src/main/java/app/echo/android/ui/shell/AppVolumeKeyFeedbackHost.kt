package app.echo.android.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.AppVolumeKeyFeedback
import app.echo.android.R
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import kotlinx.coroutines.flow.StateFlow
import app.echo.android.feature.settings.R as SettingsR

@Composable
internal fun AppVolumeKeyFeedbackHost(
    feedback: StateFlow<AppVolumeKeyFeedback?>,
    modifier: Modifier = Modifier,
) {
    val current by feedback.collectAsStateWithLifecycle()
    val notice = current ?: return
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shape = RoundedCornerShape(20.dp)
    val elevation = if (LocalEchoEffectivePerformanceMode.current.isLightweight) 0.dp else 6.dp
    val icon = when (notice) {
        AppVolumeKeyFeedback.Locked -> Icons.Rounded.Lock
        is AppVolumeKeyFeedback.Level -> if (notice.muted) Icons.AutoMirrored.Rounded.VolumeOff
            else Icons.AutoMirrored.Rounded.VolumeUp
    }

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .width(216.dp)
            .shadow(elevation, shape)
            .background(scheme.surfaceContainerHigh, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(scheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                EchoIcon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = scheme.onSurface)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(SettingsR.string.volume_level),
                    style = typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                when (notice) {
                    AppVolumeKeyFeedback.Locked -> Text(
                        stringResource(R.string.usb_key_volume_locked),
                        style = typography.bodySmall,
                        color = scheme.onSurface,
                    )
                    is AppVolumeKeyFeedback.Level -> Text(
                        if (notice.muted) stringResource(SettingsR.string.volume_muted)
                        else String.format(LocalConfiguration.current.locales[0], "%.1f dB", notice.db),
                        style = typography.titleLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                    )
                }
            }
        }

        if (notice is AppVolumeKeyFeedback.Level) {
            // Match the app volume control's -60 to 0 dB scale.
            val fraction = if (notice.muted) 0f else ((notice.db + 60f) / 60f).coerceIn(0f, 1f)
            VolumeScale(fraction)
        }
    }
}

@Composable
private fun VolumeScale(fraction: Float) {
    val scheme = MaterialTheme.colorScheme
    val inactive = scheme.onSurface.copy(alpha = 0.14f)
    val active = scheme.primary
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        val segments = 12
        val gap = 3.dp.toPx()
        val segmentWidth = ((size.width - (segments - 1) * gap) / segments).coerceAtLeast(0f)
        val corner = CornerRadius(size.height / 2f)
        repeat(segments) { index ->
            val start = Offset(index * (segmentWidth + gap), 0f)
            val fill = (fraction * segments - index).coerceIn(0f, 1f)
            drawRoundRect(inactive, start, Size(segmentWidth, size.height), corner)
            if (fill > 0f) drawRoundRect(active, start, Size(segmentWidth * fill, size.height), corner)
        }
    }
}
