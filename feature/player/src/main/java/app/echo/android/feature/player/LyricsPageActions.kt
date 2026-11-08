package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Cast lives in the header; lyric settings sit bottom-right in [LyricsTransportControls]. */
@Composable
internal fun LyricsCastAction(
    onCast: (() -> Unit)?,
    castActive: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (onCast == null) return
    GlyphButton(
        icon = PlayerControlIcons.Cast,
        description = stringResource(if (castActive) R.string.feature_player_cast_active else R.string.feature_player_cast),
        touchSize = 48.dp, iconSize = 24.dp, tint = tint,
        background = Color.Transparent,
        onClick = onCast,
        modifier = modifier.castSemantics(castActive),
    )
}

/** The header cast button is a toggle for screen readers, not just a colour change. */
internal fun Modifier.castSemantics(active: Boolean): Modifier =
    semantics { role = Role.Button; selected = active }
