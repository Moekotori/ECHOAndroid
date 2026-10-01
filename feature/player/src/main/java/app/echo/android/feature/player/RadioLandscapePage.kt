package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoSleepTimerMode

/** Station identity and tuning stay visible while the transport pane can scroll independently. */
@Composable
internal fun RadioLandscapePage(
    status: EchoPlaybackStatus,
    presentation: RadioPlayerPresentation,
    bitrateKbps: Int?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    val colors = radioPlayerColors()
    PlayerCoverLayout(
        minimumPortraitHeight = 672.dp,
        modifier = Modifier.fillMaxSize(),
        minimumDetailsWidth = 300.dp,
        artwork = {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                Text(presentation.station, color = colors.ink, fontFamily = RecordSleeveStyle.TitleFont,
                    fontSize = 30.sp, lineHeight = 34.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(presentation.host, color = colors.muted, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                RadioTuningScale(colors)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    RadioFrequencyDisplay(colors, maxOf(80.dp, maxWidth - 105.dp))
                }
            }
        },
        details = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(presentation.liveTitle ?: stringResource(R.string.radio_demo_program),
                    color = colors.ink, fontFamily = RecordSleeveStyle.TitleFont,
                    fontSize = 22.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(radioStatusLabel(status)), color = colors.accent, fontSize = 14.sp)
                Text(if (bitrateKbps != null) stringResource(R.string.radio_bitrate, bitrateKbps)
                    else stringResource(R.string.radio_demo_bitrate), color = colors.muted, fontSize = 12.sp)
                RadioListeningActions(colors, status.sleepTimerMode != EchoSleepTimerMode.Off,
                    onSetSleepTimer, onCancelSleepTimer)
                RadioTransport(colors, status.isPlaying, onPrevious, onPlayPause, onNext)
                RadioUtilities(colors, onOpenQueue, onCast, castActive)
            }
        },
    )
}
