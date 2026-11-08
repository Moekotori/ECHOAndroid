package app.echo.android.feature.library

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.ArtistOnlineInfo
import app.echo.android.model.library.ArtistOnlineQuery

private const val BiographyCollapsedLines = 8

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtistInformationPage(query: ArtistOnlineQuery, active: Boolean) {
    val loader = LocalArtistOnlineInfoLoader.current
    val language = LocalConfiguration.current.locales[0].language
    val state = rememberArtistOnlineState<ArtistOnlineInfo>(Triple(query, language, loader), active) { refresh ->
        checkNotNull(loader).loadProfile(query, language, refresh)
    }
    var expanded by remember(query, language) { mutableStateOf(false) }
    val info = state.value
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = AlbumDetailBottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(info.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        if (info.description != null || info.serverDescription == null) {
                            ExpandableDescription(
                                text = info.description ?: stringResource(R.string.artist_no_biography),
                                expanded = expanded,
                                onToggle = { expanded = !expanded },
                                collapsedLines = BiographyCollapsedLines,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (info.description != null) {
                            OnlineSourceRow {
                                info.wikipediaUrl?.let { ArtistSourceLink("Wikipedia · ${info.wikipediaLanguage}", it) }
                                ArtistSourceLink("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/")
                            }
                        }
                    }
                }
                info.serverDescription?.let { description -> item("server-biography") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Navidrome", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(description, style = MaterialTheme.typography.bodyLarge)
                        info.serverUrl?.let { ArtistSourceLink("Last.fm", it) }
                    }
                } }
                item("facts") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val kindPerson = stringResource(R.string.artist_kind_person)
                        val kindGroup = stringResource(R.string.artist_kind_group)
                        val facts = listOfNotNull(
                            info.area?.let { stringResource(R.string.artist_fact_area) to it },
                            info.kind?.let { stringResource(R.string.artist_fact_type) to when (it) { "Person" -> kindPerson; "Group" -> kindGroup; else -> it } },
                            info.begin?.let { stringResource(R.string.artist_fact_begin) to it },
                            info.end?.let { stringResource(R.string.artist_fact_end) to it },
                        )
                        OnlineFactGrid(facts)
                        if (info.genres.isNotEmpty()) {
                            Text(stringResource(R.string.artist_fact_genres), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                info.genres.forEach { OnlineTag(it) }
                            }
                        }
                        if (info.members.isNotEmpty()) {
                            Text(stringResource(R.string.artist_fact_members), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                info.members.forEach { PersonChip(it) }
                            }
                        }
                        if (info.musicBrainzId.isNotBlank()) ArtistSourceLink("MusicBrainz", "https://musicbrainz.org/artist/${info.musicBrainzId}")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistOnlineHeading(title: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
        else IconButton(onClick = onRefresh) { EchoIcon(Icons.Rounded.Refresh, stringResource(R.string.album_online_refresh)) }
    }
}

@Composable
internal fun ArtistOnlineNotice(loading: Boolean, failed: Boolean, empty: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (loading) stringResource(R.string.artist_online_loading) else if (failed) stringResource(R.string.artist_online_error) else empty,
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed && !loading) TextButton(onClick = onRetry) { Text(stringResource(R.string.artist_retry)) }
    }
}

/** Source links share the album page's pill style. */
@Composable
internal fun ArtistSourceLink(label: String, url: String) = OnlineSourceLink(label, url)
