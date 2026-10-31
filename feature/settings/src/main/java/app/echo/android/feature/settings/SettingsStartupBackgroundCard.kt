package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** The launch image is a separate choice from the in-app wallpaper, so it sits under its own heading. */
@Composable
internal fun SettingsStartupBackgroundCard(
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasImage = !uri.isNullOrBlank()
    var imageFailed by remember(uri) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().settingsSearchAnchor(stringResource(R.string.settings_startup_background)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(96.dp).height(60.dp).clip(SettingsShape).background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImage && !imageFailed) {
                    val context = LocalContext.current
                    val request = remember(context, uri) {
                        ImageRequest.Builder(context).data(uri).size(384, 240).crossfade(false).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = stringResource(R.string.settings_startup_background_preview),
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    )
                } else {
                    EchoIcon(Icons.Rounded.Image, null, Modifier.size(24.dp), tint = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.settings_startup_background),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(
                        when {
                            imageFailed -> R.string.settings_startup_background_unavailable
                            hasImage -> R.string.settings_startup_background_detail
                            else -> R.string.settings_startup_background_default
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        SettingsSegmentedControl(
            listOf(
                SettingsSegment(stringResource(R.string.settings_startup_background_choose), selected = hasImage, onClick = onPick),
                SettingsSegment(stringResource(R.string.settings_bg_default_label), selected = !hasImage, enabled = hasImage, onClick = onClear),
            ),
        )
    }
}
