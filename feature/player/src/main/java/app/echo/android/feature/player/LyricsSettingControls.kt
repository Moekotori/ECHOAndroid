package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.echo.android.design.echoAccentColor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSlider
import app.echo.android.design.EchoSwitch
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keep drag feedback local; persist only the committed value, never every pointer event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsSettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(value.coerceIn(range)) }
    val displayed = if (dragging) draft else value.coerceIn(range)
    val valueText = "${(displayed * 100f).roundToInt()}%"
    val resetLabel = stringResource(R.string.lyrics_setting_reset)
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText, Modifier.widthIn(min = 48.dp), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            TextButton(
                onClick = { dragging = false; onCommit(defaultValue) },
                enabled = abs(displayed - defaultValue) > 0.005f,
                modifier = Modifier.semantics { contentDescription = "$label · $resetLabel" },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(resetLabel, style = MaterialTheme.typography.labelMedium) }
        }
        EchoSlider(
            value = displayed,
            onValueChange = { dragging = true; draft = (it * 100f).roundToInt() / 100f },
            onValueChangeFinished = { onCommit(draft.coerceIn(range)); dragging = false },
            valueRange = range,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/** One quiet selection rail replaces a row of individually framed buttons. */
@Composable
internal fun LyricsSettingChoices(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val accent = echoAccentColor()
    Row(
        Modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (selected == value) accent.copy(alpha = if (dark) 0.20f else 0.12f) else Color.Transparent)
                    .selectable(selected = selected == value, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected == value) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun LyricsSettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Column(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            EchoSwitch(checked, onCheckedChange = null)
        }
        if (!hint.isNullOrBlank()) Text(hint, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
            color = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.65f) else echoTheme().muted)
    }
}

@Composable
internal fun LyricsSettingsHandle(onDismiss: () -> Unit) {
    val description = stringResource(R.string.feature_player_close_lyrics_settings_752454)
    PlayerSettingsDragHandle(description, onDismiss)
}
