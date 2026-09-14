package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
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
    music: @Composable () -> Unit,
) = key(artist.artistKey) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_library_back_49093c))
            }
            Text(if (pager.currentPage == 0) stringResource(R.string.artist_page_artist) else artist.name,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
        Box(Modifier.padding(horizontal = 24.dp)) {
            LibraryTextTabs(listOf(stringResource(R.string.artist_page_music), stringResource(R.string.artist_page_information),
                stringResource(R.string.artist_page_concerts)), pager.currentPage, onSelect = { page ->
                scope.launch { if (lightweight) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
            })
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> music()
                1 -> ArtistInformationPage(query, active = pager.settledPage == 1 && !pager.isScrollInProgress)
                2 -> ArtistConcertsPage(query, active = pager.settledPage == 2 && !pager.isScrollInProgress)
            }
        }
    }
    }
}
