package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun SettingsStartupBackgroundCard(
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val hasImage = !uri.isNullOrBlank()
    var imageFailed by remember(uri) { mutableStateOf(false) }
    SettingsSectionCard(title = stringResource(R.string.settings_startup_background)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.settings_startup_background_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasImage && !imageFailed) {
                val context = LocalContext.current
                val request = remember(context, uri) {
                    ImageRequest.Builder(context).data(uri).size(768, 432).crossfade(false).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.settings_startup_background_preview),
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Text(
                    stringResource(
                        if (imageFailed) R.string.settings_startup_background_unavailable
                        else R.string.settings_startup_background_default,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_startup_background_choose),
                    selected = hasImage,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPick,
                )
                BackgroundSourceAction(
                    label = stringResource(R.string.settings_bg_default_label),
                    selected = !hasImage,
                    enabled = hasImage,
                    modifier = Modifier.weight(1f),
                    onClick = onClear,
                )
            }
        }
    }
}
