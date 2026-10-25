package app.echo.android.feature.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.podcast.EchoPodcastFeed

@Composable
internal fun PodcastShowHeader(feed: EchoPodcastFeed, busy: Boolean, revision: Int, actions: PodcastActions, onUnfollow: () -> Unit) {
    var expanded by rememberSaveable(feed.id) { mutableStateOf(false) }
    var menu by remember(feed.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PodcastCover(feed, Modifier.size(64.dp), revision)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(feed.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (feed.author.isNotBlank()) Text(feed.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = actions.listen, enabled = !busy, modifier = Modifier.weight(1f)) {
                    EchoIcon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.podcast_listen))
                }
                IconButton(onClick = actions.refresh, enabled = !busy) { EchoIcon(Icons.Rounded.Refresh, stringResource(R.string.podcast_refresh)) }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy) { EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.podcast_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (feed.description.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.podcast_expand)) }, onClick = { menu = false; expanded = !expanded })
                        DropdownMenuItem(text = { Text(stringResource(R.string.podcast_source)) }, onClick = { menu = false; actions.openUrl(feed.website ?: feed.url) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.podcast_unfollow)) }, onClick = { menu = false; onUnfollow() })
                    }
                }
            }
            if (expanded && feed.description.isNotBlank()) {
                Text(feed.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.podcast_collapse)) }
            }
        }
    }
}
