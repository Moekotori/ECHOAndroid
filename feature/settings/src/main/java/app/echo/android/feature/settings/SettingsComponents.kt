package app.echo.android.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoPlaybackDiagnostics
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import kotlin.math.roundToInt

private val SignalBarHeights = listOf(18.dp, 30.dp, 24.dp, 42.dp, 28.dp, 48.dp, 34.dp, 22.dp, 38.dp, 26.dp, 44.dp, 20.dp)

@Composable
internal fun signalPanelColor(lightAlpha: Float = 0.64f): Color {
    val scheme = MaterialTheme.colorScheme
    return if (LocalEchoDarkTheme.current) {
        scheme.surface.copy(alpha = (lightAlpha * 0.84f).coerceIn(0.38f, 0.64f))
    } else {
        scheme.surface.copy(alpha = lightAlpha)
    }
}

@Composable
internal fun signalPanelBorder(lightAlpha: Float = 0.84f): BorderStroke {
    return BorderStroke(1.dp, signalPanelBorderColor(lightAlpha))
}

@Composable
private fun signalPanelBorderColor(lightAlpha: Float = 0.84f): Color {
    val scheme = MaterialTheme.colorScheme
    return if (LocalEchoDarkTheme.current) {
        scheme.outline.copy(alpha = (lightAlpha * 0.32f).coerceIn(0.20f, 0.34f))
    } else {
        scheme.outlineVariant.copy(alpha = lightAlpha)
    }
}

@Composable
private fun signalAccentColor(): Color {
    return MaterialTheme.colorScheme.primary
}

@Composable
private fun signalHeroBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        if (LocalEchoDarkTheme.current) {
            listOf(
                scheme.surface.copy(alpha = 0.72f),
                scheme.surfaceVariant.copy(alpha = 0.48f),
                scheme.primary.copy(alpha = 0.10f),
                scheme.surface.copy(alpha = 0.82f),
            )
        } else {
            listOf(
                scheme.surface.copy(alpha = 0.78f),
                scheme.surfaceVariant.copy(alpha = 0.58f),
                scheme.primary.copy(alpha = 0.08f),
            )
        },
    )
}

@Composable
internal fun SignalHeroCard(
    status: EchoPlaybackStatus,
    output: String,
    codec: String,
    buffer: String,
    lastCommand: String,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = signalAccentColor()
    val track = status.track
    val diagnostics = status.diagnostics
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                signalHeroBrush(),
            )
            .border(signalPanelBorder(0.86f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (track != null) "实时信号" else "信号待命",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        track?.let { "${it.title} · ${it.artist}" }
                            ?: if (status.isPlaying) "正在输出音频流" else "选择曲目后显示完整链路",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatePill(label = signalReadinessLabel(status), active = status.isPlaying || track != null)
            }
            SignalBars(active = status.isPlaying)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("文件", diagnostics.fileFormatLabel(), accent, Modifier.weight(1f))
                SignalStatTile("解码", codec, accent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("缓冲", buffer, accent, Modifier.weight(1f))
                SignalStatTile("输出", output, accent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("处理", diagnostics.processingLabel(), accent, Modifier.weight(1f))
                SignalStatTile("命令", lastCommand, accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun StatePill(label: String, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (active) scheme.primary else scheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) accent.copy(alpha = 0.16f) else signalPanelColor(0.48f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) 0.32f else 0.22f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SignalStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(signalPanelColor(0.62f))
            .border(signalPanelBorder(0.80f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                value,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SignalFlowPanel(
    codec: String,
    output: String,
    dspActive: Boolean,
    diagnostics: EchoPlaybackDiagnostics,
) {
    val outputDetail = diagnostics.signalOutputStageDetail(output)
    val usbExclusiveActive = diagnostics.usbExclusiveEnabled || diagnostics.usbBitPerfectActive
    val accent = signalAccentColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EchoSectionTitle("链路路径", "源文件 -> 解码 -> DSP -> 输出")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalFlowStep(
                    index = "01",
                    title = "源文件",
                    detail = diagnostics.fileFormatLabel(),
                    note = "本机曲库",
                    active = true,
                )
                SignalFlowStep(
                    index = "02",
                    title = "解码器",
                    detail = codec,
                    note = diagnostics.decodedFormatLabel(),
                    active = true,
                )
                SignalFlowStep(
                    index = "03",
                    title = "处理层",
                    detail = diagnostics.processingLabel(),
                    note = if (dspActive) "会改变原始采样" else "保持轻处理链路",
                    active = dspActive,
                )
                SignalFlowStep(
                    index = "04",
                    title = "输出端",
                    detail = outputDetail,
                    note = diagnostics.usbOutputSupportLabel(),
                    active = usbExclusiveActive || outputDetail != output,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FlowChip("本机优先", selected = true, Modifier.weight(1f))
                FlowChip("DSP ${if (dspActive) "开启" else "关闭"}", selected = dspActive, Modifier.weight(1f))
                FlowChip(diagnostics.usbExclusiveFlowChipLabel(), selected = usbExclusiveActive, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SignalFlowStep(
    index: String,
    title: String,
    detail: String,
    note: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = signalAccentColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) accent.copy(alpha = 0.10f) else signalPanelColor(0.52f))
            .border(
                BorderStroke(1.dp, if (active) accent.copy(alpha = 0.24f) else signalPanelBorderColor(0.72f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            index,
            color = if (active) accent else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(
                detail,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            note,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun EchoPlaybackDiagnostics.signalOutputStageDetail(fallback: String): String =
    when {
        usbBitPerfectActive -> "USB 独占 · bit-perfect"
        usbExclusiveEnabled && usbHostPermissionPending -> "等待 USB 授权"
        usbExclusiveEnabled && usbHostPermissionGranted && usbAudioHasIsochronousOut -> "USB 已授权 · ISO 待驱动"
        usbExclusiveEnabled && usbHostPermissionGranted -> "USB 已授权 · 待接管"
        usbExclusiveEnabled && usbConnected -> "USB 未授权 · 未独占"
        usbConnected -> "USB mixer"
        else -> fallback
    }

private fun EchoPlaybackDiagnostics.usbExclusiveFlowChipLabel(): String =
    when {
        usbBitPerfectActive -> "独占已接管"
        usbExclusiveEnabled && usbHostPermissionPending -> "等待授权"
        usbExclusiveEnabled && usbHostPermissionGranted -> "已授权未接管"
        usbExclusiveEnabled -> "未授权"
        usbConnected -> "独占关闭"
        else -> "USB 未连接"
    }

@Composable
internal fun AudioFormatPanel(
    status: EchoPlaybackStatus,
    equalizerState: EchoEqualizerState,
) {
    val diagnostics = status.diagnostics
    val accent = signalAccentColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoSectionTitle("格式矩阵", diagnostics.signalIntegrityLabel(equalizerState))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("采样率", diagnostics.sampleRateLabel(), accent, Modifier.weight(1f))
                SignalStatTile("解码输出", diagnostics.decodedSampleRateLabel(), accent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("位深", diagnostics.bitDepthLabel(), accent, Modifier.weight(1f))
                SignalStatTile("声道", diagnostics.channelLabel(), accent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("码率", diagnostics.bitrateLabel(), accent, Modifier.weight(1f))
                SignalStatTile("直通", diagnostics.bitPerfectReadout(equalizerState), accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun EqualizerPanel(
    state: EchoEqualizerState,
    opraState: OpraHeadphoneCorrectionState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
    onOpraQueryChange: (String) -> Unit,
    onOpraSearch: () -> Unit,
    onOpraRefresh: () -> Unit,
    onOpraPresetSelected: (String) -> Unit,
    onOpraApplySelected: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(24.dp))
            .animateContentSize()
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    EchoSectionTitle("EQ", equalizerDetail(state))
                    state.warning?.let { warning ->
                        Text(
                            warning,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "折叠 EQ" else "展开 EQ",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { expanded = !expanded }
                            .padding(2.dp)
                            .size(24.dp),
                    )
                    EchoComponentSwitch(
                        checked = state.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EchoEqualizerPresets.presets.forEach { preset ->
                        EqualizerPresetChip(
                            label = preset.name,
                            selected = state.presetId == preset.id,
                            enabled = true,
                            onClick = { onPresetSelected(preset.id) },
                        )
                    }
                    EqualizerPresetChip(
                        label = "Reset",
                        selected = false,
                        enabled = true,
                        onClick = onReset,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.bands.forEach { band ->
                        EqualizerBandSlider(
                            label = formatEqFrequency(band.frequencyHz),
                            gainDb = band.gainDb,
                            range = band.minGainDb..band.maxGainDb,
                            enabled = state.enabled,
                            dark = dark,
                            onGainChange = { onBandGainChange(band.index, it) },
                        )
                    }
                }
                EchoPlaceholderLine(
                    if (state.active) {
                        "EQ 正在处理当前音频；bit-perfect 不再成立"
                    } else {
                        "关闭或 Flat 时保持原始输出路径"
                    },
                )
                OpraCorrectionPanel(
                    state = opraState,
                    onQueryChange = onOpraQueryChange,
                    onSearch = onOpraSearch,
                    onRefresh = onOpraRefresh,
                    onPresetSelected = onOpraPresetSelected,
                    onApplySelected = onOpraApplySelected,
                )
            }
        }
    }
}

@Composable
private fun EchoComponentSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = scheme.primary.copy(alpha = if (dark) 0.86f else 0.76f),
            checkedBorderColor = Color.White.copy(alpha = if (dark) 0.28f else 0.52f),
            uncheckedThumbColor = if (dark) Color.White.copy(alpha = 0.58f) else scheme.onSurfaceVariant.copy(alpha = 0.72f),
            uncheckedTrackColor = if (dark) Color.White.copy(alpha = 0.14f) else scheme.outlineVariant.copy(alpha = 0.55f),
            uncheckedBorderColor = if (dark) Color.White.copy(alpha = 0.22f) else scheme.outlineVariant.copy(alpha = 0.76f),
        ),
    )
}

@Composable
private fun OpraCorrectionPanel(
    state: OpraHeadphoneCorrectionState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onApplySelected: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EchoSectionTitle("OPRA", "耳机校正 · 近似映射到系统 EQ")
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("耳机型号") },
            placeholder = { Text("HD 650 / IER-M9 / AirPods Max") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EqualizerPresetChip(
                label = if (state.loading) "搜索中" else "搜索",
                selected = false,
                enabled = !state.loading,
                onClick = onSearch,
            )
            EqualizerPresetChip(
                label = "刷新库",
                selected = false,
                enabled = !state.loading,
                onClick = onRefresh,
            )
            Button(
                onClick = onApplySelected,
                enabled = state.selectedPreset != null && !state.loading,
                modifier = Modifier.weight(1f),
            ) {
                Text("近似应用")
            }
        }
        if (state.loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在读取 OPRA 数据库", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.message?.let { message ->
            Text(
                message,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.status.eqCount > 0) {
            Text(
                "${state.status.vendorCount} 品牌 · ${state.status.productCount} 型号 · ${state.status.eqCount} 曲线 · ${state.status.source}",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.results.take(5).forEach { product ->
            OpraProductResult(
                product = product,
                selectedEqId = state.selectedEqId,
                onPresetSelected = onPresetSelected,
            )
        }
    }
}

@Composable
private fun OpraProductResult(
    product: OpraHeadphoneCorrectionProduct,
    selectedEqId: String?,
    onPresetSelected: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                product.productName,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(product.vendorName, product.subtype ?: "${product.presets.size} preset").joinToString(" · "),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            product.presets.take(6).forEach { preset ->
                EqualizerPresetChip(
                    label = preset.details ?: preset.author,
                    selected = preset.eqId == selectedEqId,
                    enabled = true,
                    onClick = { onPresetSelected(preset.eqId) },
                )
            }
        }
    }
}

@Composable
private fun EqualizerPresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (selected) signalAccentColor() else scheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else signalPanelColor(0.50f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.38f else 0.22f)),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun EqualizerBandSlider(
    label: String,
    gainDb: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    dark: Boolean,
    onGainChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                formatEqGain(gainDb),
                color = if (gainDb == 0f) scheme.onSurfaceVariant else scheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = gainDb.coerceIn(range.start, range.endInclusive),
            onValueChange = { onGainChange((it * 10f).roundToInt() / 10f) },
            valueRange = range,
            enabled = enabled,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary.copy(alpha = 0.84f),
                inactiveTrackColor = scheme.outlineVariant.copy(alpha = if (dark) 0.66f else 0.72f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun SignalFlowStage(
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.08f)),
                ),
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.32f)), RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun FlowArrow() {
    Text(
        "→",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
internal fun FlowChip(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val accent = signalAccentColor()
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else signalPanelColor(0.54f))
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) accent.copy(alpha = 0.28f) else signalPanelBorderColor(0.74f),
                ),
                RoundedCornerShape(20.dp),
            )
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CurrentStreamPanel(
    status: EchoPlaybackStatus,
    lastCommand: String,
    requestToken: Long,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle("当前流", status.track?.album ?: "暂无曲目")
            DiagnosticLine("曲目", status.track?.title ?: "无")
            DiagnosticLine("艺人", status.track?.artist ?: "无")
            DiagnosticLine("进度", "${formatDuration(status.positionMs)} / ${formatDuration(status.durationMs)}")
            DiagnosticLine("模式", "${repeatModeLabel(status.repeatMode)} · ${if (status.shuffleEnabled) "随机开启" else "顺序播放"}")
            DiagnosticLine("命令", lastCommand)
            DiagnosticLine("令牌", requestToken.toString())
        }
    }
}

@Composable
internal fun HealthPanel(status: EchoPlaybackStatus) {
    val diagnostics = status.diagnostics
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoSectionTitle("健康", diagnostics.lastError?.message ?: "播放链路没有记录错误")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FlowChip("稳定", selected = diagnostics.lastError == null, Modifier.weight(1f))
                FlowChip(if (diagnostics.bufferedMs > 0L) "有缓冲" else "无缓冲", selected = diagnostics.bufferedMs > 0L, Modifier.weight(1f))
                FlowChip(if (diagnostics.lastCommand == null) "无命令" else commandLabel(diagnostics.lastCommand), selected = diagnostics.lastCommand != null, Modifier.weight(1f))
            }
            DiagnosticLine("输出路由", diagnostics.outputRoute)
            DiagnosticLine("缓冲余量", "${diagnostics.bufferedMs / 1000}s")
            DiagnosticLine("解码错误", diagnostics.lastError?.message ?: "无")
            DiagnosticLine("USB 回退", diagnostics.usbLastRequestError?.message ?: "无")
        }
    }
}

@Composable
internal fun SignalBars(active: Boolean) {
    val accent = signalAccentColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(signalPanelColor(0.46f))
            .border(signalPanelBorder(0.62f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBarHeights.forEachIndexed { index, height ->
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(if (active || index % 2 == 0) height else height * 0.5f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    accent.copy(alpha = if (active) 0.95f else 0.38f),
                                    accent.copy(alpha = if (active) 0.66f else 0.24f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
internal fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun playbackStateLabel(state: EchoPlaybackState): String =
    when (state) {
        EchoPlaybackState.Idle -> "空闲"
        EchoPlaybackState.Loading -> "加载中"
        EchoPlaybackState.Playing -> "播放中"
        EchoPlaybackState.Paused -> "已暂停"
        EchoPlaybackState.Seeking -> "定位中"
        EchoPlaybackState.Buffering -> "缓冲中"
        EchoPlaybackState.Ended -> "已结束"
        EchoPlaybackState.Stopped -> "已停止"
        EchoPlaybackState.Error -> "错误"
    }

internal fun repeatModeLabel(mode: EchoRepeatMode): String =
    when (mode) {
        EchoRepeatMode.Off -> "循环关闭"
        EchoRepeatMode.All -> "列表循环"
        EchoRepeatMode.One -> "单曲循环"
    }

internal fun commandLabel(command: String?): String =
    when (command?.lowercase()) {
        null, "idle" -> "空闲"
        "play", "playpause" -> "播放"
        "pause" -> "暂停"
        "next" -> "下一首"
        "previous" -> "上一首"
        "seek" -> "跳转"
        "stop" -> "停止"
        else -> command
    }

private fun signalReadinessLabel(status: EchoPlaybackStatus): String =
    when {
        status.isPlaying -> "实时"
        status.track != null -> playbackStateLabel(status.state)
        else -> "待命"
    }

private fun EchoPlaybackDiagnostics.fileFormatLabel(): String {
    val parts = listOfNotNull(
        codec ?: "未知编码",
        sampleRateHz?.let(::formatSampleRate),
        bitDepth?.let { "${it}bit" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "等待播放"
}

private fun EchoPlaybackDiagnostics.decodedFormatLabel(): String {
    val parts = listOfNotNull(
        decodedSampleRateHz?.let(::formatSampleRate) ?: sampleRateHz?.let(::formatSampleRate),
        channelCount?.let(::formatChannels),
        bitDepth?.let { "${it}bit PCM" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "等待解码"
}

private fun EchoPlaybackDiagnostics.processingLabel(): String =
    when {
        offloadActive -> "硬件 offload"
        usbBitPerfectActive -> "USB bit-perfect"
        else -> "系统链路"
    }

private fun EchoPlaybackDiagnostics.signalIntegrityLabel(equalizerState: EchoEqualizerState): String =
    when {
        equalizerState.active -> "EQ 正在改变频响，bit-perfect 不成立"
        usbBitPerfectActive -> "USB 独占已接管，链路保持 bit-perfect"
        offloadActive -> "硬件 offload 生效，Android mixer 介入更少"
        usbExclusiveEnabled && usbConnected -> "USB 独占已开启，等待授权或接管"
        else -> "系统输出链路，适合常规播放"
    }

private fun EchoPlaybackDiagnostics.bitPerfectReadout(equalizerState: EchoEqualizerState): String =
    when {
        equalizerState.active -> "否 · EQ"
        usbBitPerfectActive -> "是 · USB"
        offloadActive -> "接近 · offload"
        usbExclusiveEnabled -> "等待 USB"
        else -> "否 · mixer"
    }

private fun EchoPlaybackDiagnostics.usbOutputSupportLabel(): String =
    when {
        usbBitPerfectActive -> "bit-perfect active"
        usbBitPerfectSupported -> "支持 bit-perfect"
        usbAudioHasIsochronousOut && usbAudioHasFeedbackEndpoint -> "iso OUT + feedback"
        usbAudioHasIsochronousOut -> "iso OUT"
        usbConnected -> "USB 已识别"
        else -> "系统输出"
    }

private fun EchoPlaybackDiagnostics.sampleRateLabel(): String =
    sampleRateHz?.let(::formatSampleRate) ?: "未知"

private fun EchoPlaybackDiagnostics.decodedSampleRateLabel(): String =
    decodedSampleRateHz?.let(::formatSampleRate)
        ?: sampleRateHz?.let { "同源 ${formatSampleRate(it)}" }
        ?: "未知"

private fun EchoPlaybackDiagnostics.bitDepthLabel(): String =
    bitDepth?.let { "${it}bit" } ?: "未知"

private fun EchoPlaybackDiagnostics.channelLabel(): String =
    channelCount?.let(::formatChannels) ?: "未知"

private fun EchoPlaybackDiagnostics.bitrateLabel(): String =
    bitrate?.let(::formatBitrate) ?: "未知"

private fun formatSampleRate(sampleRateHz: Int): String =
    if (sampleRateHz >= 1000) {
        "${formatEqNumber(sampleRateHz / 1000f)} kHz"
    } else {
        "$sampleRateHz Hz"
    }

private fun formatChannels(channelCount: Int): String =
    when (channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channelCount}ch"
    }

private fun formatBitrate(bitrate: Int): String =
    if (bitrate >= 1_000_000) {
        "${formatEqNumber((bitrate / 100_000f).roundToInt() / 10f)} Mbps"
    } else {
        "${bitrate / 1000} kbps"
    }

private fun equalizerDetail(state: EchoEqualizerState): String =
    when {
        state.enabled && state.supported -> "${state.presetName} · ${state.bands.size} bands"
        state.enabled && !state.available -> "${state.presetName} · 等待播放"
        state.enabled -> "${state.presetName} · 等待系统 EQ"
        else -> "关闭 · ${state.bands.size} bands"
    }

private fun formatEqFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1000) {
        "${formatEqNumber((frequencyHz / 100f).roundToInt() / 10f)} kHz"
    } else {
        "$frequencyHz Hz"
    }

private fun formatEqGain(gainDb: Float): String {
    val rounded = (gainDb * 10f).roundToInt() / 10f
    val prefix = if (rounded > 0f) "+" else ""
    return "$prefix${formatEqNumber(rounded)} dB"
}

private fun formatEqNumber(value: Float): String =
    if (value == value.roundToInt().toFloat()) {
        value.roundToInt().toString()
    } else {
        value.toString()
    }

