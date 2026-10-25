package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode

/** Transport and toggles as bracketed commands; a filled cell is an active toggle. */
@Composable
internal fun TerminalButtons(
    status: EchoPlaybackStatus,
    isFavorite: Boolean,
    castActive: Boolean,
    actions: ExpressivePlayerActions,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Command("|< prev", stringResource(R.string.feature_player_previous_af0264), false, actions.previous)
            Command(
                if (status.isPlaying) "|| pause" else "> play",
                stringResource(R.string.feature_player_play_or_pause_37a70f), true, actions.playPause,
            )
            Command(">| next", stringResource(R.string.feature_player_next_d67904), false, actions.next)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Command("~ shuf", stringResource(R.string.feature_player_shuffle), status.shuffleEnabled, actions.shuffle)
            Command(
                if (status.repeatMode == EchoRepeatMode.One) "@1 rep" else "@ rep",
                stringResource(
                    when (status.repeatMode) {
                        EchoRepeatMode.Off -> R.string.feature_player_repeat_off_254ca2
                        EchoRepeatMode.One -> R.string.feature_player_repeat_one_3df94f
                        EchoRepeatMode.All -> R.string.feature_player_repeat_all_751078
                    },
                ),
                status.repeatMode != EchoRepeatMode.Off, actions.repeat,
            )
            Command("= queue", stringResource(R.string.feature_player_queue_37fa6a), false, actions.queue)
            Command(
                "* fav",
                stringResource(if (isFavorite) R.string.feature_player_unfavorite_3a27e4 else R.string.feature_player_favorite_b5d1f5),
                isFavorite, actions.favorite,
            )
            actions.cast?.let {
                Command(
                    "# cast",
                    stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast),
                    castActive, it,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Command(text: String, label: String, active: Boolean, onClick: () -> Unit) {
    val green = TerminalStyle.Green
    Box(
        Modifier.weight(1f).height(40.dp)
            .background(if (active) green else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, green)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; selected = active },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (active) TerminalStyle.Screen else green, fontFamily = TerminalStyle.Mono, fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}
