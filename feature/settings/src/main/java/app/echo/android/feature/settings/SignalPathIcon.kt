package app.echo.android.feature.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus

internal enum class SignalPathTone {
    Idle, Live, Routed, Converted, Direct;

    fun tint(neutral: Color): Color {
        val onDark = neutral.luminance() > 0.5f
        return when (this) {
            Idle, Live -> neutral
            Routed -> if (onDark) Color(0xFF8BC6AC) else Color(0xFF34735B)
            Converted -> if (onDark) Color(0xFF89AFFF) else Color(0xFF365FAD)
            Direct -> if (onDark) Color(0xFFB394EE) else Color(0xFF7950C7)
        }
    }
}

internal fun EchoPlaybackStatus.signalPathTone(): SignalPathTone = when {
    track == null || (state != EchoPlaybackState.Playing && state != EchoPlaybackState.Paused) -> SignalPathTone.Idle
    hasVerifiedUsbDirect() -> SignalPathTone.Direct
    hasDecodedRateChange() -> SignalPathTone.Converted
    diagnostics.outputRouteVerified -> SignalPathTone.Routed
    else -> SignalPathTone.Live
}

@Composable
fun SignalPathButton(status: EchoPlaybackStatus, tint: Color, onClick: () -> Unit) {
    val label = stringResource(R.string.feature_settings_signal_path_2fed34)
    val tone = status.signalPathTone()
    IconButton(onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        if (LocalEchoEffectivePerformanceMode.current.isLightweight) {
            SignalPathGlyph(tone, tint)
        } else {
            Crossfade(tone, animationSpec = tween(200, easing = EchoMotion.Silk), label = "signal-path-state") {
                SignalPathGlyph(it, tint)
            }
        }
    }
}

@Composable
internal fun SignalPathGlyph(tone: SignalPathTone, neutral: Color) {
    EchoIcon(
        painterResource(if (tone == SignalPathTone.Idle) R.drawable.ic_signal_path_idle else R.drawable.ic_signal_path_live),
        contentDescription = null,
        modifier = Modifier.size(26.dp),
        tint = tone.tint(neutral),
    )
}
