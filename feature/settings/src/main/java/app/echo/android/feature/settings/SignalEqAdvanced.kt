package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.echo.android.model.playback.*
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
internal fun SignalEqAdvancedOptions(options: EchoEqOptions, enabled: Boolean,
    onChange: (EchoEqOptions) -> Unit, onPreview: (EchoEqOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DspValueSlider(stringResource(R.string.eq_pc_strength), options.strengthPercent, 0f..100f, enabled,
            { "${signalNumber(it, 0)}%" }, { onChange(options.copy(strengthPercent = it)) },
            onPreview = { onPreview(options.copy(strengthPercent = it)) }, unit = "%", decimals = 0)
        DspValueSlider(stringResource(R.string.eq_pc_tilt), options.tiltDb, -6f..6f, enabled, ::formatEqGain,
            { onChange(options.copy(tiltDb = it)) }, onPreview = { onPreview(options.copy(tiltDb = it)) }, unit = "dB")
    }
}

@Composable
internal fun SignalEqBandAdvanced(band: OpraEqBand, enabled: Boolean, dynamicAvailable: Boolean,
    onCommit: (OpraEqBand) -> Unit, onPreview: (OpraEqBand) -> Unit = onCommit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.eq_pc_scope), style = MaterialTheme.typography.labelLarge)
        val scopes = EchoEqBandScope.entries
        DspChoices(listOf(stringResource(R.string.eq_pc_stereo), stringResource(R.string.eq_pc_left),
            stringResource(R.string.eq_pc_right), stringResource(R.string.eq_pc_mid), stringResource(R.string.eq_pc_side)),
            scopes.indexOf(band.scope), enabled) { onCommit(band.copy(scope = scopes[it])) }
        val pcRange = band.gainDb in -24f..12f && (band.q ?: 1f) in .1f..12f && band.frequencyHz >= 20f
        val gainType = band.normalizedType() in EchoEqOptions.GainTypes
        Text(stringResource(R.string.eq_pc_dynamic), style = MaterialTheme.typography.labelLarge)
        DspChoices(listOf(stringResource(R.string.eq_pc_dynamic_off), stringResource(R.string.eq_pc_dynamic_down), stringResource(R.string.eq_pc_dynamic_up)),
            band.dynamicMode.ordinal, enabled && gainType && pcRange && (dynamicAvailable || band.dynamicMode != EchoEqDynamicMode.Off)) {
            onCommit(band.copy(dynamicMode = EchoEqDynamicMode.entries[it]))
        }
        if (!dynamicAvailable) SignalNote(stringResource(R.string.eq_pc_dynamic_limit))
        if (band.dynamicMode != EchoEqDynamicMode.Off) {
            DspValueSlider(stringResource(R.string.eq_pc_threshold), band.dynamicThresholdDb, -48f..0f, enabled, ::formatEqGain,
                { onCommit(band.copy(dynamicThresholdDb = it)) }, onPreview = { onPreview(band.copy(dynamicThresholdDb = it)) }, unit = "dB")
            DspValueSlider(stringResource(R.string.eq_pc_ratio), band.dynamicRatio, 1f..20f, enabled,
                { "${signalNumber(it)}:1" }, { onCommit(band.copy(dynamicRatio = it)) }, onPreview = { onPreview(band.copy(dynamicRatio = it)) }, unit = ":1")
            DspValueSlider(stringResource(R.string.eq_pc_attack), band.dynamicAttackMs, .5f..80f, enabled,
                { "${signalNumber(it)} ms" }, { onCommit(band.copy(dynamicAttackMs = it)) }, onPreview = { onPreview(band.copy(dynamicAttackMs = it)) }, unit = "ms")
            DspValueSlider(stringResource(R.string.eq_pc_release), band.dynamicReleaseMs, 10f..400f, enabled,
                { "${signalNumber(it)} ms" }, { onCommit(band.copy(dynamicReleaseMs = it)) }, onPreview = { onPreview(band.copy(dynamicReleaseMs = it)) }, unit = "ms")
        }
    }
}

/** The native 750 ms lease is renewed only while the finger is held on this control. */
@Composable
internal fun SignalEqAudition(band: OpraEqBand?, enabled: Boolean, onAudition: (Boolean, Float, Float) -> Unit) {
    var held by remember { mutableStateOf(false) }
    val callback = rememberUpdatedState(onAudition)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { held = false; callback.value(false, 1000f, 1f) } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); callback.value(false, 1000f, 1f) }
    }
    LaunchedEffect(held, band?.frequencyHz, band?.q, enabled) {
        if (!held || !enabled || band == null) return@LaunchedEffect
        try { while (true) { callback.value(true, band.frequencyHz, band.q ?: 1f); delay(250) } }
        finally { callback.value(false, 1000f, 1f) }
    }
    DisposableEffect(Unit) { onDispose { callback.value(false, 1000f, 1f) } }
    Box(Modifier.fillMaxWidth().semantics { role = Role.Button; onClick { if (enabled) { held = !held; true } else false } }
        .background(if (held) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow)
        .pointerInput(enabled, band) { awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            if (enabled && band != null) held = true
            try { waitForUpOrCancellation() } finally { held = false }
        } }.padding(16.dp)) {
        Text(stringResource(if (held) R.string.eq_pc_audition_active else R.string.eq_pc_audition_hold), color = MaterialTheme.colorScheme.onSurface)
    }
}
