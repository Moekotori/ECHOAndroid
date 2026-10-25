package app.echo.android.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.echoClickable
import app.echo.android.design.echoExpandIndicator
import app.echo.android.design.echoItemMotion
import app.echo.android.model.playback.OpraHeadphoneCorrectionState

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct

@Composable
internal fun OpraModelResults(
    state: OpraHeadphoneCorrectionState,
    visibleResults: List<OpraHeadphoneCorrectionProduct>,
    onPresetSelected: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expandedProduct by remember(state.results) { mutableStateOf<String?>(null) }
    val logosByVendorName = remember(state.brands) { state.brands.associate { it.name to it.logoUrl } }
    if (visibleResults.isNotEmpty()) {
        SignalEqWell(Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visibleResults, key = { it.productId }) { product ->
                    val expanded = expandedProduct == product.productId
                    Column(echoItemMotion()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .echoClickable(enabled = !state.loading, role = Role.Button) {
                                    if (product.presets.size == 1) onPresetSelected(product.presets.first().eqId)
                                    else expandedProduct = if (expanded) null else product.productId
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OpraBrandLogo(
                                name = product.vendorName,
                                logoUrl = logosByVendorName[product.vendorName],
                                size = 36.dp,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(product.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                val summary = if (state.catalog == app.echo.android.model.playback.HeadphoneCorrectionCatalog.AutoEq && product.presets.size == 1) {
                                    product.presets.first().author
                                } else {
                                    pluralStringResource(L10nR.plurals.opra_model_curves, product.presets.size, product.presets.size)
                                }
                                SignalNote(product.vendorName + " · " + summary)
                            }
                            EchoIcon(
                                if (product.presets.size == 1) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.echoExpandIndicator(expanded),
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                        EchoExpand(expanded) {
                            Column {
                                product.presets.forEach { preset ->
                                    val selected = preset.eqId == state.selectedEqId
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                            .selectable(
                                                selected = selected,
                                                enabled = !state.loading,
                                                role = Role.RadioButton,
                                                onClick = { onPresetSelected(preset.eqId) },
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .width(2.dp)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(if (selected) scheme.primary else Color.Transparent),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                preset.author,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = if (selected) scheme.primary else scheme.onSurface,
                                            )
                                            preset.details?.let { SignalNote(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (!state.loading && state.selectedBrandId != null) {
        SignalNote(stringResource(L10nR.string.opra_no_matching_models))
    }
}
