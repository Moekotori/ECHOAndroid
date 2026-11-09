package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.rememberEchoWindowLayout
import app.echo.android.model.playback.EchoTrackTransitionOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTransitionQuickControl(
    active: Boolean,
    options: EchoTrackTransitionOptions,
    usbExclusive: Boolean,
    bitPerfect: Boolean,
    onChange: (EchoTrackTransitionOptions) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) expanded = false }
    val title = stringResource(R.string.settings_section_transition)
    IconButton(enabled = active, onClick = { expanded = true }) {
        EchoIcon(Icons.Rounded.SyncAlt, title, Modifier.size(22.dp))
    }
    if (active && expanded) {
        val maxHeight = minOf((rememberEchoWindowLayout().heightDp * 0.70f).dp, 520.dp)
        val performanceMode = LocalEchoEffectivePerformanceMode.current.id
        EchoModalSheet(
            onDismissRequest = { expanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
                EchoSheetHeader(
                    title = title,
                    trailing = {
                        TextButton(onClick = { expanded = false }) {
                            Text(stringResource(R.string.update_done))
                        }
                    },
                )
                CompositionLocalProvider(LocalSettingsCompactMode provides true) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    ) {
                        val gaplessTitle = stringResource(R.string.settings_gapless)
                        val gaplessDetail = stringResource(R.string.settings_gapless_detail)
                        if (options.fadeEnabled || options.smartEnabled) {
                            SettingsActionRow(
                                title = gaplessTitle,
                                detail = gaplessDetail,
                                actionLabel = stringResource(R.string.feature_settings_reset_1106f5),
                                onClick = { onChange(options.copy(fadeEnabled = false, smartEnabled = false)) },
                            )
                        } else {
                            SettingsInfoRow(title = gaplessTitle, detail = gaplessDetail)
                        }
                        AudioTransitionControls(options, usbExclusive, bitPerfect, performanceMode, onChange)
                    }
                }
            }
        }
    }
}
