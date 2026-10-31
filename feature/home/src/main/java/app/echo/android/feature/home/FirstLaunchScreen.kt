package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

/** Three bounded, scrollable steps; the app owns navigation and completion persistence. */
@Composable
fun FirstLaunchScreen(
    step: Int,
    saving: Boolean,
    saveFailed: Boolean,
    onStepChange: (Int) -> Unit,
    onFinish: (openLibrary: Boolean) -> Unit,
) {
    val page = step.coerceIn(0, 2)
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ECHO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { onFinish(false) }, enabled = !saving) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            // Body can scroll on small screens / large system fonts; actions stay within the safe area.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text(stringResource(R.string.onboarding_step, page + 1, 3),
                    style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                FirstLaunchIllustration(page)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(when (page) {
                        0 -> R.string.onboarding_music_title
                        1 -> R.string.onboarding_player_title
                        else -> R.string.onboarding_link_title
                    }), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(when (page) {
                        0 -> R.string.onboarding_music_body
                        1 -> R.string.onboarding_player_body
                        else -> R.string.onboarding_link_body
                    }), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                HorizontalDivider(color = colors.outlineVariant)
                when (page) {
                    0 -> {
                        FirstLaunchTip(Icons.Rounded.FolderOpen, R.string.onboarding_import_title, R.string.onboarding_import_body)
                        FirstLaunchTip(Icons.Rounded.Wifi, R.string.onboarding_sources_title, R.string.onboarding_sources_body)
                    }
                    1 -> {
                        FirstLaunchTip(Icons.Rounded.TouchApp, R.string.onboarding_open_player_title, R.string.onboarding_open_player_body)
                        FirstLaunchTip(Icons.Rounded.Lyrics, R.string.onboarding_lyrics_title, R.string.onboarding_lyrics_body)
                    }
                    else -> {
                        FirstLaunchTip(Icons.Rounded.Wifi, R.string.onboarding_pair_title, R.string.onboarding_pair_body)
                        FirstLaunchTip(Icons.Rounded.FolderOpen, R.string.onboarding_later_title, R.string.onboarding_later_body)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saveFailed) {
                    Text(stringResource(R.string.onboarding_save_failed), color = colors.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page > 0) {
                        IconButton(onClick = { onStepChange(page - 1) }, enabled = !saving) {
                            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.onboarding_back))
                        }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { index ->
                            Box(Modifier.size(if (index == page) 18.dp else 6.dp, 6.dp)
                                .background(if (index == page) colors.onSurface else colors.outlineVariant, CircleShape))
                        }
                    }
                    Button(
                        onClick = { if (page < 2) onStepChange(page + 1) else onFinish(true) },
                        enabled = !saving,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(if (saving) R.string.onboarding_saving
                            else if (page < 2) R.string.onboarding_next else R.string.onboarding_add_music))
                    }
                }
                if (page == 2) {
                    TextButton(onClick = { onFinish(false) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_explore))
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstLaunchTip(icon: ImageVector, title: Int, body: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        EchoIcon(icon, null, Modifier.padding(top = 2.dp).size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
