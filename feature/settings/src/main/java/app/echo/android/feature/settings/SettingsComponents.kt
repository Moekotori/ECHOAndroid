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
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassContainerBrush
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

@Composable
private fun signalPanelColor(lightAlpha: Float = 0.64f): Color {
    return if (LocalEchoDarkTheme.current) EchoGlassPanel.copy(alpha = 0.48f) else Color.White.copy(alpha = lightAlpha)
}

@Composable
private fun signalPanelBorder(lightAlpha: Float = 0.84f): BorderStroke {
    return if (LocalEchoDarkTheme.current) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = lightAlpha))
}

@Composable
private fun signalHeroBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        if (LocalEchoDarkTheme.current) {
            return echoGlassContainerBrush(1.06f, accent = EchoGlassCyan)
        } else {
            listOf(
                Color.White.copy(alpha = 0.72f),
                EchoHomeMist.copy(alpha = 0.64f),
                EchoHomeBlue.copy(alpha = 0.12f),
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
                        "链路总览",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (status.isPlaying) "正在输出稳定音频流" else "等待播放，链路保持就绪",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatePill(label = playbackStateLabel(status.state), active = status.isPlaying)
            }
            SignalBars(active = status.isPlaying)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("输出", output, EchoAccent, Modifier.weight(1f))
                SignalStatTile("解码", codec, EchoAccent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SignalStatTile("缓冲", buffer, Color(0xFF35C28E), Modifier.weight(1f))
                SignalStatTile("命令", lastCommand, EchoAccent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun StatePill(label: String, active: Boolean) {
    val accent = if (active) Color(0xFF1A9B68) else EchoAccentText
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
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
    val outputAccent = if (usbExclusiveActive) Color(0xFF35C28E) else EchoAccent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(signalPanelColor(0.64f))
            .border(signalPanelBorder(0.84f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EchoSectionTitle("链路路径", "从曲库到输出设备")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalFlowStage("曲库", "本机", EchoAccent, Modifier.weight(1f))
                FlowArrow()
                SignalFlowStage("解码", codec, EchoAccent, Modifier.weight(1f))
                FlowArrow()
                SignalFlowStage("输出", outputDetail, outputAccent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FlowChip("本机优先", selected = true, Modifier.weight(1f))
                FlowChip("DSP ${if (dspActive) "开启" else "关闭"}", selected = dspActive, Modifier.weight(1f))
                FlowChip(diagnostics.usbExclusiveFlowChipLabel(), selected = usbExclusiveActive, Modifier.weight(1f))
            }
        }
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
    val accent = if (selected) EchoAccent else scheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) EchoAccent.copy(alpha = 0.16f) else signalPanelColor(0.50f),
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
                color = if (gainDb == 0f) scheme.onSurfaceVariant else EchoAccent,
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
                inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.16f) else scheme.outlineVariant.copy(alpha = 0.72f),
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
    val accent = EchoAccent
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else signalPanelColor(0.54f))
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) accent.copy(alpha = 0.28f) else if (dark) EchoDarkGlassBorder else EchoGlassBorder.copy(alpha = 0.74f),
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
            DiagnosticLine("进度", "${formatDuration(status.positionMs)} / ${formatDuration(status.durationMs)}")
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
            EchoSectionTitle("健康", diagnostics.lastError?.message ?: "未记录掉音")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FlowChip("稳定", selected = diagnostics.lastError == null, Modifier.weight(1f))
                FlowChip("本机", selected = true, Modifier.weight(1f))
                FlowChip("接力", selected = false, Modifier.weight(1f))
            }
            EchoPlaceholderLine(if (diagnostics.lastError == null) "解码回退记录为空" else "查看解码回退")
            EchoPlaceholderLine("PC 链路质量待测")
        }
    }
}

@Composable
internal fun SignalBars(active: Boolean) {
    val heights = listOf(18.dp, 30.dp, 24.dp, 42.dp, 28.dp, 48.dp, 34.dp, 22.dp, 38.dp, 26.dp, 44.dp, 20.dp)
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
            heights.forEachIndexed { index, height ->
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(if (active || index % 2 == 0) height else height * 0.5f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    EchoAccent.copy(alpha = if (active) 0.95f else 0.4f),
                                    EchoAccent.copy(alpha = if (active) 0.7f else 0.3f),
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

