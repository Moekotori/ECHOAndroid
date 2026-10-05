package app.echo.android.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

/** Anchor occasional lyric actions in the header instead of the playback row. */
@Composable
internal fun LyricsPageActions(
    onOpenSettings: () -> Unit,
    onCast: (() -> Unit)?,
    castActive: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onMenuExpanded: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val menuExpanded by rememberUpdatedState(onMenuExpanded)
    LaunchedEffect(expanded) { menuExpanded(expanded) }
    DisposableEffect(Unit) { onDispose { menuExpanded(false) } }
    val settingsLabel = stringResource(R.string.feature_player_lyrics_settings_843bc9)
    val castLabel = stringResource(
        if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast,
    )
    Box(modifier) {
        GlyphButton(
            icon = Icons.Rounded.MoreHoriz,
            description = if (onCast == null) settingsLabel else "$settingsLabel / $castLabel",
            touchSize = 48.dp, iconSize = 22.dp, tint = tint,
            background = Color.Transparent,
            onClick = { if (onCast == null) onOpenSettings() else expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            DropdownMenuItem(
                text = { Text(settingsLabel) },
                leadingIcon = { EchoIcon(PlayerControlIcons.Settings, null, Modifier.size(20.dp)) },
                onClick = { expanded = false; onOpenSettings() },
            )
            if (onCast != null) DropdownMenuItem(
                text = { Text(castLabel) },
                leadingIcon = { EchoIcon(PlayerControlIcons.Cast, null, Modifier.size(20.dp)) },
                onClick = { expanded = false; onCast() },
            )
        }
    }
}
