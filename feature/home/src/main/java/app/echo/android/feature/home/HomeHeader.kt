package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import app.echo.android.design.EchoSearchButton

@Composable
internal fun HomeHeader(onOpenSearch: () -> Unit, onEditLayout: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_page_title), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onOpenHistory != null) IconButton(onClick = onOpenHistory) {
                EchoIcon(Icons.Rounded.History, stringResource(R.string.playback_history_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEditLayout != null) IconButton(onClick = onEditLayout) {
                EchoIcon(Icons.Rounded.Tune, stringResource(R.string.home_layout_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EchoSearchButton(stringResource(R.string.feature_home_search_songs_albums_and_artists_c46634), onOpenSearch)
    }
}
