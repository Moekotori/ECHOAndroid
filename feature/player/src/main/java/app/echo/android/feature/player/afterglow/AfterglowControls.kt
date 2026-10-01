package app.echo.android.feature.player.afterglow

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.feature.player.R
import app.echo.android.model.settings.EchoLyricsPageStyle

@Composable
internal fun AfterglowControls(
    index: Int, plan: AfterglowPlan?, offsetMs: Long, style: EchoLyricsPageStyle,
    scene: AfterglowScene, chosenScene: String?, onScene: (String?) -> Unit,
    onPalette: () -> Unit, onRandomize: () -> Unit, onSeek: (Long) -> Unit,
    onInteraction: () -> Unit = {}, onMenu: (Boolean) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(menuOpen) { onMenu(menuOpen) }
    DisposableEffect(Unit) { onDispose { onMenu(false) } }
    FlowRow(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            IconButton(onClick = { onInteraction(); plan?.lines?.getOrNull(index - 1)?.let { onSeek((it.startMs - offsetMs).coerceAtLeast(0L)) } }, enabled = index > 0) {
                EchoIcon(Icons.AutoMirrored.Rounded.NavigateBefore, stringResource(R.string.afterglow_previous_line))
            }
            IconButton(onClick = { onInteraction(); plan?.lines?.getOrNull(index + 1)?.let { onSeek((it.startMs - offsetMs).coerceAtLeast(0L)) } },
                enabled = plan != null && index + 1 < plan.lines.size) {
                EchoIcon(Icons.AutoMirrored.Rounded.NavigateNext, stringResource(R.string.afterglow_next_line))
            }
        }
        Row {
            Box {
                IconButton(onClick = { onInteraction(); menuOpen = true }) {
                    EchoIcon(Icons.Rounded.Landscape, stringResource(R.string.afterglow_scene) + " · " + stringResource(scene.title))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.afterglow_scene_auto)) },
                        modifier = Modifier.semantics { selected = chosenScene == null },
                        trailingIcon = { if (chosenScene == null) EchoIcon(Icons.Rounded.Check, null) },
                        onClick = { onInteraction(); onScene(null); menuOpen = false })
                    AfterglowScene.forStyle(style).forEach { choice ->
                        DropdownMenuItem(text = { Text(stringResource(choice.title)) },
                            modifier = Modifier.semantics { selected = chosenScene == choice.pcId },
                            trailingIcon = { if (chosenScene == choice.pcId) EchoIcon(Icons.Rounded.Check, null) },
                            onClick = { onInteraction(); onScene(choice.pcId); menuOpen = false })
                    }
                }
            }
            TextButton(onClick = { onInteraction(); onPalette() }) { Text(stringResource(R.string.afterglow_palette)) }
            IconButton(onClick = { onInteraction(); onRandomize() }) { EchoIcon(Icons.Rounded.Shuffle, stringResource(R.string.afterglow_randomize)) }
        }
    }
}
