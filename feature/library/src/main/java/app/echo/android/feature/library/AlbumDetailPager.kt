package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.launch

/** Both album routes share paging while keeping their existing lazy track lists. */
@Composable
internal fun AlbumDetailPager(
    album: AlbumSummary,
    tracks: List<EchoTrack>,
    onBack: () -> Unit,
    trackContent: LazyListScope.() -> Unit,
) = key(album.albumKey) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    var informationVisited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage == 1) informationVisited = true
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            AlbumDetailTopBar(onBack)
        }
        TabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent) {
            listOf(R.string.album_page_tracks, R.string.album_page_information).forEachIndexed { index, label ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = {
                        scope.launch {
                            if (lightweight) pager.scrollToPage(index)
                            else pager.animateScrollToPage(index)
                        }
                    },
                    text = { Text(stringResource(label)) },
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            if (page == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
                    content = trackContent,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 24.dp, end = 24.dp, top = 16.dp,
                        bottom = AlbumDetailBottomPadding,
                    ),
                ) {
                    item(key = "album-title") {
                        Text(album.title, style = MaterialTheme.typography.headlineSmall)
                    }
                    item(key = "album-online-information") {
                        // Pager precomposition must not start an online lookup.
                        if (informationVisited) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                                AlbumOnlineInformation(album)
                            }
                        }
                    }
                    item(key = "album-details") {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                            AlbumInformation(album, tracks)
                        }
                    }
                }
            }
        }
    }
}
