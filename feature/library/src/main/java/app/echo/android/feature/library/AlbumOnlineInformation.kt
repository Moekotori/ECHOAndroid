package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.echoClickable
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.library.AlbumOnlineCredit
import app.echo.android.model.library.AlbumOnlineInfo
import app.echo.android.model.library.AlbumOnlineInfoLoader
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException

private val LocalAlbumOnlineInfoLoader = staticCompositionLocalOf<AlbumOnlineInfoLoader?> { null }

private const val CollapsedDescriptionLines = 4
private const val CollapsedCreditGroups = 3
private const val MaxCredits = 24

/** App wiring supplies the data implementation to local, remote and linked album routes. */
@Composable
fun AlbumOnlineInfoProvider(loader: AlbumOnlineInfoLoader, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAlbumOnlineInfoLoader provides loader, content = content)
}

@Composable
internal fun AlbumOnlineInformation(album: AlbumSummary) {
    val loader = LocalAlbumOnlineInfoLoader.current ?: return
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    var attempt by remember(album, language) { mutableIntStateOf(0) }
    var loading by remember(album, language) { mutableStateOf(true) }
    var failed by remember(album, language) { mutableStateOf(false) }
    var info by remember(album, language) { mutableStateOf<AlbumOnlineInfo?>(null) }
    LaunchedEffect(loader, album, language, attempt) {
        loading = true
        failed = false
        try {
            info = loader.load(album, language, refresh = attempt > 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("AlbumOnlineInfo", "Online album lookup failed", failure)
            EchoErrorLog.record(
                EchoErrorSource.Network,
                failure.message ?: "Online album lookup failed.",
                throwable = failure,
            )
            failed = true
        } finally {
            loading = false
        }
    }
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onSurface) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.album_online_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.album_online_refresh)) }
            }
            val result = info
            when {
                loading && result == null -> OnlineNotice(stringResource(R.string.album_online_loading))
                failed && result == null -> OnlineNotice(stringResource(R.string.album_online_error))
                result == null -> OnlineNotice(stringResource(R.string.album_online_no_match))
                else -> OnlineInfoBody(result, refreshFailed = failed)
            }
        }
    }
}

@Composable
private fun OnlineInfoBody(info: AlbumOnlineInfo, refreshFailed: Boolean) {
    val colors = MaterialTheme.colorScheme
    var serverExpanded by remember(info) { mutableStateOf(false) }
    var wikiExpanded by remember(info) { mutableStateOf(false) }
    if (info.stale || refreshFailed) {
        Text(stringResource(R.string.album_online_stale), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    } else if (info.partial) {
        Text(stringResource(R.string.album_online_partial), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    info.serverDescription?.let { serverDescription ->
        Text("Navidrome", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        ExpandableDescription(serverDescription, serverExpanded) { serverExpanded = !serverExpanded }
        info.serverUrl?.let { OnlineSourceLink("Last.fm", it) }
    }
    val description = info.description
    if (description != null) {
        ExpandableDescription(description, wikiExpanded) { wikiExpanded = !wikiExpanded }
        OnlineSourceRow {
            info.wikipediaUrl?.let { OnlineSourceLink("Wikipedia · ${info.wikipediaLanguage}", it) }
            OnlineSourceLink("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/")
        }
        Text(stringResource(R.string.album_online_attribution), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    } else if (!info.partial) {
        Text(stringResource(R.string.album_online_no_wiki), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }

    OnlineReleaseFacts(info)
    OnlineCredits(info.credits)
    if (info.releaseId.isNotBlank()) OnlineSourceLink(stringResource(R.string.album_online_mb_source), "https://musicbrainz.org/release/${info.releaseId}")
}

/** Text that clamps to a few lines with a fade, and only offers the toggle when something is actually cut off. */
@Composable
internal fun ExpandableDescription(
    text: String,
    expanded: Boolean,
    collapsedLines: Int = CollapsedDescriptionLines,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    onToggle: () -> Unit,
) {
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text,
            modifier = if (!expanded && clipped) Modifier.fadeOutBottom() else Modifier,
            style = style.copy(lineHeight = style.lineHeight * 1.12f),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Clip,
            onTextLayout = { if (!expanded) clipped = it.hasVisualOverflow },
        )
        if (clipped || expanded) {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (expanded) R.string.album_online_less else R.string.album_online_more))
            }
        }
    }
}

private fun Modifier.fadeOutBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** Date, region, label and catalogue number as soft tiles, two to a row. */
@Composable
private fun OnlineReleaseFacts(info: AlbumOnlineInfo) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(R.string.album_online_release), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    Text("${info.releaseTitle} · ${info.artist}", style = MaterialTheme.typography.bodyMedium)
    if (info.releaseId.isNotBlank()) {
        Text(stringResource(R.string.album_online_release_note), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    val facts = listOfNotNull(
        info.date?.let { stringResource(R.string.album_online_date) to it },
        info.country?.let { stringResource(R.string.album_online_country) to it },
        info.labels.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.album_online_label) to it.joinToString(" / ") },
        info.catalogNumbers.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.album_online_catalog) to it.joinToString(" / ") },
    )
    OnlineFactGrid(facts)
}

/** Credits grouped by role; each person gets an initial badge and, when known, the instrument or track. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineCredits(credits: List<AlbumOnlineCredit>) {
    if (credits.isEmpty()) return
    var showAll by remember(credits) { mutableStateOf(false) }
    val groups = remember(credits) {
        credits.take(MaxCredits).groupBy { it.role.substringBefore(" · ") }.entries.toList()
    }
    Text(stringResource(R.string.album_online_credits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    groups.take(if (showAll) groups.size else CollapsedCreditGroups).forEach { (role, people) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                creditRoleLabel(role),
                modifier = Modifier.width(84.dp).padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                people.forEach { person -> CreditChip(person, role) }
            }
        }
    }
    if (groups.size > CollapsedCreditGroups) {
        TextButton(onClick = { showAll = !showAll }) {
            Text(stringResource(if (showAll) R.string.album_online_less else R.string.album_online_more))
        }
    }
}

@Composable
private fun CreditChip(credit: AlbumOnlineCredit, role: String) {
    val detail = listOfNotNull(
        credit.role.removePrefix(role).removePrefix(" · ").takeIf { it.isNotBlank() },
        credit.track?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    PersonChip(credit.name, detail)
}

@Composable
private fun creditRoleLabel(role: String): String = when (role) {
    "composer" -> stringResource(R.string.album_online_composer)
    "lyricist", "writer" -> stringResource(R.string.album_online_lyricist)
    "producer" -> stringResource(R.string.album_online_producer)
    "arranger" -> stringResource(R.string.album_online_arranger)
    "vocal" -> stringResource(R.string.album_online_vocal)
    "instrument", "performer" -> stringResource(R.string.album_online_performer)
    else -> role
}

@Composable
private fun OnlineNotice(message: String) {
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnlineSourceRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        content()
    }
}

/** A plain text link, as before; no pill. */
@Composable
internal fun OnlineSourceLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(
        onClick = { failed = runCatching { uriHandler.openUri(url) }.isFailure },
        contentPadding = PaddingValues(0.dp),
    ) { Text(label) }
    if (failed) Text(stringResource(R.string.album_online_link_error), style = MaterialTheme.typography.bodySmall)
}
