package app.echo.android.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoStateContent
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoPageSection
import app.echo.android.design.echoSheetSurface
import app.echo.android.design.echoTheme
import app.echo.android.design.rememberEchoContentMotion

/** Shared look of the player's bottom settings sheets: one surface, an accent wash, no nested cards. */
@Composable
internal fun Modifier.settingsSheetSurface(): Modifier = echoSheetSurface()

/** Neutral plate behind round header buttons. */
@Composable
internal fun settingsPlateColor(): Color =
    if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.09f) else echoTheme().muted.copy(alpha = 0.13f)

/**
 * Header shared by the sheets. [deeper] swaps the default leading item for a back button;
 * title and subtitle cross-fade whenever either changes.
 */
@Composable
internal fun SettingsSheetHeader(
    deeper: Boolean,
    title: String,
    subtitle: String,
    backDescription: String,
    closeDescription: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted
    val plate = settingsPlateColor()
    Row(
        modifier = Modifier
            .echoPageSection(0)
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EchoStateContent(deeper) { more ->
            if (more) {
                GlyphButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    description = backDescription,
                    touchSize = 52.dp, iconSize = 22.dp, tint = titleColor,
                    background = plate, onClick = onBack,
                )
            } else {
                leading()
            }
        }
        EchoStateContent(title to subtitle, Modifier.weight(1f)) { (t, s) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    t,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.isNotBlank()) Text(
                    s,
                    color = mutedColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GlyphButton(
            icon = Icons.Rounded.Close,
            description = closeDescription,
            touchSize = 40.dp,
            iconSize = 20.dp,
            tint = titleColor,
            background = plate,
            onClick = onClose,
        )
    }
}

/**
 * Menu ↔ detail page swap: the incoming page slides in from the side it belongs to, and the sheet
 * height follows with a spring instead of jumping. [isDeeper] is evaluated on the page keys, never on live state.
 */
@Composable
internal fun <T> SettingsPageSwitcher(
    state: T,
    isDeeper: (T) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val motion = rememberEchoContentMotion()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            motion.tabSwitch(forward = isDeeper(targetState)).using(
                if (lightweight) null else SizeTransform(clip = true) { _, _ ->
                    EchoMotion.silkSize(EchoMotion.ExpandMs)
                },
            )
        },
        label = "settings-sheet-page",
        content = { content(it) },
    )
}

/** Small caption above a group of controls. */
@Composable
internal fun SettingsGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp),
        color = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.6f) else echoTheme().muted,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
    )
}
