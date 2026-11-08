package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoPageBackHandler

@Composable
internal fun PlayerAppearanceScreen(
    appearance: PlayerAppearance,
    onPreview: (PlayerAppearance) -> Unit,
    onCommit: () -> Unit,
    lyricsBackdrop: String,
    onLyricsBackdropChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    EchoPageBackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.player_appearance_back),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.player_appearance_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = { onPreview(PlayerAppearance()); onCommit() }) {
                Text(stringResource(R.string.player_appearance_reset))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PlayerAppearanceSettings(
            appearance, onPreview, onCommit, lyricsBackdrop, onLyricsBackdropChange,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f),
        )
    }
}
