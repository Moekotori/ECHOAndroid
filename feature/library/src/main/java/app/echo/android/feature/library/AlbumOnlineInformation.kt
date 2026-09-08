package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.AlbumOnlineInfo
import app.echo.android.model.library.AlbumOnlineInfoLoader
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException

private val LocalAlbumOnlineInfoLoader = staticCompositionLocalOf<AlbumOnlineInfoLoader?> { null }

/** App wiring supplies the data implementation to local, remote and linked album routes. */
@Composable
fun AlbumOnlineInfoProvider(loader: AlbumOnlineInfoLoader, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAlbumOnlineInfoLoader provides loader, content = content)
}

@Composable
internal fun AlbumOnlineInformation(album: AlbumSummary) {
    val loader = LocalAlbumOnlineInfoLoader.current ?: return
    val language = LocalConfiguration.current.locales[0].language
    var attempt by remember(album, language) { mutableIntStateOf(0) }
    var loading by remember(album, language) { mutableStateOf(true) }
    var failed by remember(album, language) { mutableStateOf(false) }
    var info by remember(album, language) { mutableStateOf<AlbumOnlineInfo?>(null) }
    var expanded by remember(album, language) { mutableStateOf(false) }
    var showCredits by remember(album, language) { mutableStateOf(false) }
    LaunchedEffect(loader, album, language, attempt) {
        loading = true
        failed = false
        try {
            info = loader.load(album, language, refresh = attempt > 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("AlbumOnlineInfo", "Online album lookup failed", failure)
            failed = true
        } finally {
            loading = false
        }
    }
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onSurface) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.album_online_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.album_online_refresh)) }
            }
            val result = info
            when {
                loading && result == null -> Text(stringResource(R.string.album_online_loading), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                failed && result == null -> Text(stringResource(R.string.album_online_error), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                result == null -> Text(stringResource(R.string.album_online_no_match), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                else -> {
                    if (result.stale || failed) Text(stringResource(R.string.album_online_stale), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    else if (result.partial) Text(stringResource(R.string.album_online_partial), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    val description = result.description
                    if (description != null) {
                        Text(description, style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(stringResource(if (expanded) R.string.album_online_less else R.string.album_online_more))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.wikipediaUrl?.let { OnlineSourceLink("Wikipedia · ${result.wikipediaLanguage}", it) }
                            OnlineSourceLink("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/")
                        }
                        Text(stringResource(R.string.album_online_attribution), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    } else if (!result.partial) {
                        Text(stringResource(R.string.album_online_no_wiki), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.album_online_release), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${result.releaseTitle} · ${result.artist}", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.album_online_release_note), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    result.date?.let { OnlineFact(stringResource(R.string.album_online_date), it) }
                    result.country?.let { OnlineFact(stringResource(R.string.album_online_country), it) }
                    if (result.labels.isNotEmpty()) OnlineFact(stringResource(R.string.album_online_label), result.labels.joinToString(" / "))
                    if (result.catalogNumbers.isNotEmpty()) OnlineFact(stringResource(R.string.album_online_catalog), result.catalogNumbers.joinToString(" / "))
                    if (result.credits.isNotEmpty()) {
                        Text(stringResource(R.string.album_online_credits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        result.credits.take(if (showCredits) 24 else 4).forEach { credit ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(credit.name, style = MaterialTheme.typography.bodyMedium)
                                val role = credit.role.substringBefore(" · ")
                                val label = when (role) {
                                    "composer" -> stringResource(R.string.album_online_composer)
                                    "lyricist", "writer" -> stringResource(R.string.album_online_lyricist)
                                    "producer" -> stringResource(R.string.album_online_producer)
                                    "arranger" -> stringResource(R.string.album_online_arranger)
                                    "vocal" -> stringResource(R.string.album_online_vocal)
                                    "instrument", "performer" -> stringResource(R.string.album_online_performer)
                                    else -> role
                                }
                                Text(listOfNotNull(label + credit.role.removePrefix(role), credit.track).joinToString(" · "),
                                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (result.credits.size > 4) TextButton(onClick = { showCredits = !showCredits }) {
                            Text(stringResource(if (showCredits) R.string.album_online_less else R.string.album_online_more))
                        }
                    }
                    OnlineSourceLink(stringResource(R.string.album_online_mb_source), "https://musicbrainz.org/release/${result.releaseId}")
                }
            }
        }
    }
}

@Composable
private fun OnlineFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OnlineSourceLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(onClick = { failed = runCatching { uriHandler.openUri(url) }.isFailure }) { Text(label) }
    if (failed) Text(stringResource(R.string.album_online_link_error), style = MaterialTheme.typography.bodySmall)
}
