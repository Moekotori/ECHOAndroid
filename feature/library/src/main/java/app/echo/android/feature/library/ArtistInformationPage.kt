package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.ArtistOnlineInfo
import app.echo.android.model.library.ArtistOnlineQuery

@Composable
internal fun ArtistInformationPage(query: ArtistOnlineQuery, active: Boolean) {
    val loader = LocalArtistOnlineInfoLoader.current
    val language = LocalConfiguration.current.locales[0].language
    val state = rememberArtistOnlineState<ArtistOnlineInfo>(Triple(query, language, loader), active) { refresh ->
        checkNotNull(loader).loadProfile(query, language, refresh)
    }
    var expanded by remember(query, language) { mutableStateOf(false) }
    val info = state.value
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = AlbumDetailBottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("heading") { ArtistOnlineHeading(stringResource(R.string.artist_about), state.loading) { state.attempt++ } }
        when {
            info == null -> item("status") {
                ArtistOnlineNotice(state.loading || state.completedAttempt < 0, state.failed,
                    stringResource(R.string.artist_info_no_match)) { state.attempt++ }
            }
            else -> {
                if (info.stale || state.failed || info.partial) item("partial") {
                    Text(stringResource(if (info.stale || state.failed) R.string.artist_online_stale else R.string.artist_online_partial),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item("biography") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(info.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(info.description ?: stringResource(R.string.artist_no_biography),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 9, overflow = TextOverflow.Ellipsis)
                        if (info.description != null) {
                            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(if (expanded) R.string.album_online_less else R.string.album_online_more))
                            }
                            info.wikipediaUrl?.let { ArtistSourceLink("Wikipedia · ${info.wikipediaLanguage}", it) }
                            ArtistSourceLink("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/")
                        }
                    }
                }
                item("facts") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                        info.area?.let { ArtistFact(stringResource(R.string.artist_fact_area), it) }
                        info.kind?.let {
                            val kind = when (it) { "Person" -> stringResource(R.string.artist_kind_person); "Group" -> stringResource(R.string.artist_kind_group); else -> it }
                            ArtistFact(stringResource(R.string.artist_fact_type), kind)
                        }
                        info.begin?.let { ArtistFact(stringResource(R.string.artist_fact_begin), it) }
                        info.end?.let { ArtistFact(stringResource(R.string.artist_fact_end), it) }
                        if (info.genres.isNotEmpty()) ArtistFact(stringResource(R.string.artist_fact_genres), info.genres.joinToString(" · "))
                        if (info.members.isNotEmpty()) ArtistFact(stringResource(R.string.artist_fact_members), info.members.joinToString(" / "))
                        ArtistSourceLink("MusicBrainz", "https://musicbrainz.org/artist/${info.musicBrainzId}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
    }
}

@Composable
internal fun ArtistOnlineHeading(title: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
        else IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.album_online_refresh)) }
    }
}

@Composable
internal fun ArtistOnlineNotice(loading: Boolean, failed: Boolean, empty: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (loading) stringResource(R.string.artist_online_loading) else if (failed) stringResource(R.string.artist_online_error) else empty,
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed && !loading) TextButton(onClick = onRetry) { Text(stringResource(R.string.artist_retry)) }
    }
}

@Composable
internal fun ArtistSourceLink(label: String, url: String) {
    val handler = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(onClick = { failed = runCatching { handler.openUri(url) }.isFailure }, contentPadding = PaddingValues(0.dp)) { Text(label) }
    if (failed) Text(stringResource(R.string.album_online_link_error), style = MaterialTheme.typography.bodySmall)
}
