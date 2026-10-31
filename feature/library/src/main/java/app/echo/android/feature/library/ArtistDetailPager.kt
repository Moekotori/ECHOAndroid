package app.echo.android.feature.library

import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoContentMaxWidth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.design.animateSilkToPage
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.design.rememberContentPagerNestedScroll
import app.echo.android.model.library.ArtistOnlineQuery
import app.echo.android.model.library.ArtistSummary
import kotlinx.coroutines.launch

/** Paging owns no player state; each page retains its own vertical scroll position. */
@Composable
internal fun ArtistDetailPager(
    artist: ArtistSummary,
    query: ArtistOnlineQuery,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    playbackEnabled: Boolean,
    onPlayOnPc: (() -> Unit)? = null,
    music: @Composable () -> Unit,
) = key(artist.artistKey) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val fling = rememberSilkPagerFlingBehavior(pager)
    val nestedScroll = rememberContentPagerNestedScroll(pager, fling)
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    var showActions by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(modifier.fillMaxSize().background(echoPageBackgroundColor()).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = EchoContentMaxWidth).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_library_back_49093c))
            }
            Text(if (pager.currentPage == 0) stringResource(R.string.artist_page_artist) else artist.name,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Box {
                IconButton(onClick = { showActions = true }, enabled = playbackEnabled) {
                    EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.artist_playback_actions))
                }
                EchoDropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    EchoDropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_library_play_all_55c80e)) },
                        onClick = { showActions = false; onPlayAll() },
                        selected = false,
                        enabled = playbackEnabled,
                    )
                    EchoDropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_library_shuffle_34e7ce)) },
                        onClick = { showActions = false; onShuffle() },
                        selected = false,
                        enabled = playbackEnabled,
                    )
                    if (onPlayOnPc != null) EchoDropdownMenuItem(
                        text = { Text(stringResource(R.string.artist_play_on_pc)) },
                        onClick = { showActions = false; onPlayOnPc() },
                        selected = false,
                        enabled = playbackEnabled,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = EchoContentMaxWidth).padding(horizontal = 24.dp)) {
            LibraryTextTabs(listOf(stringResource(R.string.artist_page_music), stringResource(R.string.artist_page_information),
                stringResource(R.string.artist_page_concerts)), pager.currentPage, pager = pager, onSelect = { page ->
                scope.launch { pager.animateSilkToPage(page, lightweight) }
            })
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
            flingBehavior = fling,
            pageNestedScrollConnection = nestedScroll,
        ) { page ->
            when (page) {
                0 -> music()
                1 -> ArtistInformationPage(query, active = pager.settledPage == 1 && !pager.isScrollInProgress)
                2 -> ArtistConcertsPage(query, active = pager.settledPage == 2 && !pager.isScrollInProgress)
            }
        }
    }
    }
}
