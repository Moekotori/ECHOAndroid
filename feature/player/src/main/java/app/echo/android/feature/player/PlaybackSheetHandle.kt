package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoClickable
import app.echo.android.design.echoTheme

/** Drag only the header handle so scrolling settings never accidentally dismisses the sheet. */
@Composable
internal fun PlaybackSheetHandle(onDismiss: () -> Unit) {
    PlayerSettingsDragHandle(stringResource(R.string.feature_player_collapse_playback_settings_79e2cd), onDismiss)
}

@Composable
internal fun PlayerSettingsDragHandle(label: String, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label }
            .pointerInput(threshold) {
                var distance = 0f
                detectVerticalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragCancel = { distance = 0f },
                    onDragEnd = { if (distance >= threshold) dismiss() },
                ) { change, delta ->
                    change.consume()
                    distance = (distance + delta).coerceAtLeast(0f)
                }
            }
            .echoClickable(role = Role.Button, onClickLabel = label, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(echoTheme().muted.copy(alpha = 0.35f)))
    }
}
