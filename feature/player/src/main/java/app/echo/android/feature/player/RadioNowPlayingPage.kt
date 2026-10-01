package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoSleepTimerMode

/** Radio-only presentation; sample dial/program artwork never changes playback or metadata. */
@Composable
internal fun RadioNowPlayingPage(
    status: EchoPlaybackStatus,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = radioPlayerColors()
    val presentation = remember(status.track) { radioPlayerPresentation(status.track) }
    val bitrateKbps = status.diagnostics.bitrate?.takeIf { it > 0 }?.div(1000)
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (maxWidth >= 540.dp && maxWidth > maxHeight && maxHeight < 600.dp) {
            RadioLandscapePage(status, presentation, bitrateKbps, onPlayPause, onNext, onPrevious,
                onOpenQueue, onCast, castActive, onSetSleepTimer, onCancelSleepTimer)
            return@BoxWithConstraints
        }
        val frequencyWidth = maxOf(80.dp, maxWidth - 105.dp * fontScale)
        // Let short windows / large text scroll, without shrinking transport touch areas.
        val pageHeight = maxOf(maxHeight, (672f * fontScale).dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .height(pageHeight).padding(top = 12.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.radio_demo_channel), color = colors.accent,
                fontFamily = RecordSleeveStyle.BodyFont, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 3.5.sp)
            BasicText(presentation.station,
                style = TextStyle(color = colors.ink, fontFamily = RecordSleeveStyle.TitleFont,
                    fontWeight = FontWeight.Medium, lineHeight = 0.98.em, letterSpacing = (-1).sp),
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 70.sp, stepSize = 1.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Text(presentation.host, color = colors.muted, fontFamily = RecordSleeveStyle.TitleFont,
                fontSize = 25.sp, lineHeight = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(0.65f))
            RadioTuningScale(colors)
            Spacer(Modifier.height(8.dp))
            RadioFrequencyDisplay(colors, frequencyWidth)
            Spacer(Modifier.height(8.dp))
            RadioRule(colors)
            Spacer(Modifier.height(16.dp))
            Text(presentation.liveTitle ?: stringResource(R.string.radio_demo_program),
                color = colors.ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
                fontSize = 23.sp, lineHeight = 29.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.radio_demo_schedule), color = colors.muted,
                fontFamily = RecordSleeveStyle.BodyFont, fontSize = 16.sp, lineHeight = 23.sp)
            Spacer(Modifier.height(10.dp))
            RadioDecorativeWaveform(colors)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(11.dp).background(colors.accent, CircleShape))
                Text(stringResource(radioStatusLabel(status)), color = colors.accent,
                    fontFamily = RecordSleeveStyle.BodyFont, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                Text(if (bitrateKbps != null)
                    stringResource(R.string.radio_bitrate, bitrateKbps)
                    else stringResource(R.string.radio_demo_bitrate),
                    color = colors.muted, fontFamily = RecordSleeveStyle.BodyFont, fontSize = 14.sp, lineHeight = 20.sp)
            }
            RadioRule(colors)
            RadioListeningActions(colors, status.sleepTimerMode != EchoSleepTimerMode.Off,
                onSetSleepTimer, onCancelSleepTimer)
            Spacer(Modifier.weight(0.4f))
            RadioTransport(colors, status.isPlaying, onPrevious, onPlayPause, onNext)
            Spacer(Modifier.weight(0.4f))
            RadioUtilities(colors, onOpenQueue, onCast, castActive)
        }
    }
}

@Composable
private fun RadioRule(colors: RadioPlayerColors) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.rule))
}

internal fun radioStatusLabel(status: EchoPlaybackStatus): Int = when {
    status.state == EchoPlaybackState.Error -> R.string.radio_state_error
    status.state == EchoPlaybackState.Buffering -> R.string.radio_state_buffering
    status.state == EchoPlaybackState.Loading || status.state == EchoPlaybackState.Seeking -> R.string.radio_state_connecting
    status.isPlaying -> R.string.radio_state_playing
    status.state == EchoPlaybackState.Paused -> R.string.radio_state_paused
    else -> R.string.radio_state_stopped
}
