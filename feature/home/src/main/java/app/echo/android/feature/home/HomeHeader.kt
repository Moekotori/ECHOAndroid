package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun HomeHeader(onOpenSearch: () -> Unit, onEditLayout: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null, onOpenSettings: (() -> Unit)? = null) {
    val compact = app.echo.android.design.LocalEchoLayout.current.heightDp < 600f
    val title: @Composable () -> Unit = {
        Text(stringResource(R.string.home_page_title),
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() })
    }
    val actions: @Composable () -> Unit = {
        if (onOpenHistory != null || onEditLayout != null || onOpenSettings != null) HomeHeaderActions {
            if (onOpenHistory != null) HomeHeaderAction(Icons.Rounded.History,
                stringResource(R.string.playback_history_title), onOpenHistory)
            if (onEditLayout != null) HomeHeaderAction(Icons.Rounded.Tune,
                stringResource(R.string.home_layout_title), onEditLayout)
            if (onOpenSettings != null) HomeHeaderAction(Icons.Rounded.Settings,
                stringResource(R.string.home_open_settings), onOpenSettings)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        if (compact && maxWidth >= 560.dp * fontScale) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.widthIn(max = 160.dp)) { title() }
                Box(Modifier.weight(1f)) {
                    HomeSearchBar(stringResource(R.string.feature_home_search_songs_albums_and_artists_c46634), onOpenSearch)
                }
                actions()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { title() }
                    actions()
                }
                HomeSearchBar(stringResource(R.string.feature_home_search_songs_albums_and_artists_c46634), onOpenSearch)
            }
        }
    }
}

/** Page-level actions: plain icons, no container. */
@Composable
private fun HomeHeaderActions(content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun HomeHeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        EchoIcon(icon, description, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSearchBar(placeholder: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        color = scheme.surfaceContainer) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoIcon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = scheme.primary)
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
