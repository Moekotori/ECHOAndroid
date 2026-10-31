package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import kotlin.math.abs

import app.echo.android.model.playback.EchoEqualizerUserPreset

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
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val favorites = remember(userPresets) { EchoEqualizerUserPresets.opraFavorites(userPresets) }
    val starredEqId = state.selectedEqId?.let { eqId -> favorites.firstOrNull { it.opraEqId == eqId }?.opraEqId }
    val logosByVendorName = remember(state.brands) { state.brands.associate { it.name to it.logoUrl } }
    val preset = state.selectedPreset
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(L10nR.string.opra_preview_title), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose) {
                EchoIcon(Icons.Default.Close, contentDescription = stringResource(L10nR.string.opra_close_preview), tint = scheme.onSurfaceVariant)
            }
        }
        if (preset != null) {
            val current = !bypassed && equalizer.parametric && equalizer.enabled && equalizer.sourceLabel == preset.displayName &&
                equalizer.filters == preset.bands && abs(equalizer.preampDb - preset.preampDb) < 0.05f
            SignalEqWell(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OpraBrandLogo(
                            name = preset.vendorName,
                            logoUrl = logosByVendorName[preset.vendorName],
                            size = 44.dp,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preset.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(preset.author, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                            preset.details?.let { SignalNote(it) }
                            SignalNote(stringResource(L10nR.string.opra_filter_summary, preset.bands.size, formatEqGain(preset.preampDb)))
                        }
                    }
                    SignalEqCurve(state.previewCurve, live = !bypassed, showFrequencyLabels = true)
                    if (!current) SignalNote(stringResource(L10nR.string.opra_preview_note))
                    if (!equalizer.supported) SignalNote(stringResource(L10nR.string.opra_unavailable_output), error = true)
                    if (bypassed) SignalNote(stringResource(L10nR.string.signal_bypass_detail), error = true)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onApplySelected,
                            enabled = !state.loading && !current && !bypassed && equalizer.supported,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(stringResource(if (current) L10nR.string.opra_applied else L10nR.string.diag_apply_approx))
                        }
                        val starred = starredEqId != null
                        val canStar = starred || userPresets.size < EchoEqualizerUserPresets.MaxCount
                        val starLabel = stringResource(if (starred) L10nR.string.opra_unfavorite else L10nR.string.opra_favorite)
                        IconButton(
                            onClick = onToggleFavorite,
                            enabled = !state.loading && canStar,
                            modifier = Modifier.semantics { contentDescription = starLabel },
                        ) {
                            EchoIcon(
                                if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.message?.let { SignalNote(it) }
                    preset.sourceUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { link ->
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(link) } },
                            modifier = Modifier.padding(start = 4.dp, end = 16.dp, bottom = 8.dp),
                        ) { Text(stringResource(L10nR.string.opra_source)) }
                    }
                }
            }
        }
    }
}
