package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.*

/** Curve details scroll independently; apply and save stay within reach on short screens. */
@Composable
internal fun OpraCorrectionPreview(
    state: OpraHeadphoneCorrectionState,
    equalizer: EchoEqualizerState,
    bypassed: Boolean,
    userPresets: List<EchoEqualizerUserPreset>,
    onApplySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    val preset = state.selectedPreset ?: return
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val current = !bypassed && equalizer.supported && equalizer.matchesCorrection(preset)
    val starred = userPresets.any { it.opraEqId == preset.eqId }
    val canStar = starred || userPresets.size < EchoEqualizerUserPresets.MaxCount
    val starAction = stringResource(if (starred) R.string.opra_unfavorite else R.string.opra_favorite)
    val logo = state.brands.firstOrNull { it.name == preset.vendorName }?.logoUrl
    Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.opra_preview_title), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose) {
                EchoIcon(Icons.Default.Close, stringResource(R.string.opra_close_preview), tint = scheme.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalEqWell(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OpraBrandLogo(preset.vendorName, logo, 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preset.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(preset.author, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            preset.details?.let { SignalNote(it) }
                            SignalNote(stringResource(R.string.opra_filter_summary, preset.bands.size, formatEqGain(preset.preampDb)))
                        }
                    }
                    SignalEqCurve(state.previewCurve, live = !bypassed, showFrequencyLabels = true)
                }
            }
            if (!current) SignalNote(stringResource(R.string.opra_preview_note))
            if (!equalizer.supported) SignalNote(stringResource(R.string.opra_unavailable_output), error = true)
            if (bypassed) SignalNote(stringResource(R.string.signal_bypass_detail), error = true)
            if (!canStar) SignalNote(stringResource(R.string.correction_favorites_full, EchoEqualizerUserPresets.MaxCount))
            state.message?.let { message ->
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { SignalNote(message) }
            }
            preset.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { link ->
                TextButton(onClick = { runCatching { uriHandler.openUri(link) } }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.opra_source))
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApplySelected,
                enabled = !state.loading && !current && !bypassed && equalizer.supported,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(if (current) R.string.correction_applied_short else R.string.correction_apply))
            }
            OutlinedButton(onClick = onToggleFavorite, enabled = !state.loading && canStar,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = starAction },
                shape = RoundedCornerShape(16.dp)) {
                EchoIcon(if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (starred) R.string.correction_saved_short else R.string.correction_save_short))
            }
        }
    }
}
