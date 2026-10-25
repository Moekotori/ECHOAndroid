package app.echo.android.feature.player

import app.echo.android.design.EchoSlider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.echo.android.model.settings.EchoLyricsBackdrop
import app.echo.android.model.settings.EchoPlayerPageStyle

@Composable
internal fun PlayerAppearanceSettings(
    appearance: PlayerAppearance,
    onPreview: (PlayerAppearance) -> Unit,
    onCommit: () -> Unit,
    lyricsBackdrop: String,
    onLyricsBackdropChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val pageStyle = EchoPlayerPageStyle.fromId(appearance.style)
    val backdrop = EchoLyricsBackdrop.fromId(lyricsBackdrop)
    PlaybackSettingsSection(
        icon = Icons.Rounded.Palette,
        title = stringResource(R.string.player_appearance_title),
        detail = stringResource(playerPageStyleLabel(pageStyle)) +
            if (appearance.usesFlatSurface) "" else " · " + stringResource(lyricsBackdropLabel(backdrop)),
        expanded = expanded,
        onToggleExpanded = { expanded = !expanded },
        framed = false,
        trailing = {
            if (expanded) TextButton(onClick = { onPreview(PlayerAppearance()); onCommit() }) {
                Text(stringResource(R.string.player_appearance_reset))
            }
        },
    ) {
        PlayerPageStyleSelector(EchoPlayerPageStyle.fromId(appearance.style)) { id ->
            onPreview(appearance.copy(style = id)); onCommit()
        }
        SettingsGroupLabel(stringResource(R.string.lyrics_backdrop_title))
        PlayerBackdropChoices(
            selected = backdrop,
            flatSurface = appearance.usesFlatSurface,
            onSelect = onLyricsBackdropChange,
            onUseClassic = { onPreview(appearance.copy(style = EchoPlayerPageStyle.Classic.id)); onCommit() },
        )
        AppearanceScaleSlider(
            label = stringResource(R.string.player_appearance_text_size),
            value = appearance.textScale,
            range = 0.8f..1.2f,
            steps = 7,
            onPreview = { onPreview(appearance.copy(textScale = it)) },
            onCommit = onCommit,
        )
        AppearanceScaleSlider(
            label = stringResource(R.string.player_appearance_cover_size),
            value = appearance.artworkScale,
            range = 0.7f..1f,
            steps = 5,
            onPreview = { onPreview(appearance.copy(artworkScale = it)) },
            onCommit = onCommit,
        )
    }
}

internal fun playerPageStyleLabel(style: EchoPlayerPageStyle): Int = when (style) {
    EchoPlayerPageStyle.Classic -> R.string.player_appearance_classic
    EchoPlayerPageStyle.RecordSleeve -> R.string.player_appearance_record_sleeve
    EchoPlayerPageStyle.PixelHandheld -> R.string.player_appearance_pixel_handheld
    EchoPlayerPageStyle.TypePoster -> R.string.player_appearance_type_poster
    EchoPlayerPageStyle.AfterglowMist -> R.string.afterglow_mist
    EchoPlayerPageStyle.AfterglowNight -> R.string.afterglow_night
    EchoPlayerPageStyle.HoloPrism -> R.string.player_appearance_holo_prism
    EchoPlayerPageStyle.StarlightOrbit -> R.string.player_appearance_starlight_orbit
    EchoPlayerPageStyle.SakuraLetter -> R.string.player_appearance_sakura_letter
    EchoPlayerPageStyle.NeonRain -> R.string.player_appearance_neon_rain
    EchoPlayerPageStyle.SodaPop -> R.string.player_appearance_soda_pop
    EchoPlayerPageStyle.AmberFilm -> R.string.player_appearance_amber_film
}

@Composable
internal fun PlayerPageStyleSelector(style: EchoPlayerPageStyle, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.player_appearance_detail),
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsOptionGrid(options = playerPageStyleOptions(), selectedId = style.id, onSelect = onSelect)
    }
}

@Composable
private fun AppearanceScaleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onPreview: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.player_appearance_percent, (value * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge)
        }
        EchoSlider(
            value = value,
            onValueChange = onPreview,
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
