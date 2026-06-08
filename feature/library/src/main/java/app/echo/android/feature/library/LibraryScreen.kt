package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EmptyState
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.formatDuration
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibraryScanProgress
import kotlinx.coroutines.launch

internal enum class LibraryViewMode(
    val label: String,
    val icon: ImageVector,
) {
    Songs("歌曲", Icons.AutoMirrored.Rounded.QueueMusic),
    Folders("文件夹", Icons.Rounded.LibraryMusic),
    Albums("专辑", Icons.Rounded.LibraryMusic),
    Artists("艺术家", Icons.Rounded.Person),
    Cloud("网盘", Icons.Rounded.CloudQueue),
}

@Composable
fun LibraryScreen(
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    tracks: LazyPagingItems<EchoTrack>,
    albums: LazyPagingItems<AlbumSummary>,
    artists: LazyPagingItems<ArtistSummary>,
    selectedAlbum: AlbumSummary?,
    selectedArtist: ArtistSummary?,
    albumDetailTracks: LazyPagingItems<EchoTrack>?,
    artistDetailTracks: LazyPagingItems<EchoTrack>?,
    onRequestPermission: () -> Unit,
    onScanFolder: () -> Unit,
    onScanAll: () -> Unit,
    onCancelScan: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onPlayAlbum: (AlbumSummary) -> Unit,
    onShuffleAlbum: (AlbumSummary) -> Unit,
    onPlayArtist: (ArtistSummary) -> Unit,
    onShuffleArtist: (ArtistSummary) -> Unit,
    onOpenAlbum: (AlbumSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onCloseDetail: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { LibraryViewMode.entries.size })
    val scope = rememberCoroutineScope()

    // 专辑详情走全屏沉浸式页面，不套用曲库的 PageChrome
    val activeAlbumDetail = selectedAlbum
    if (hasPermission && activeAlbumDetail != null && albumDetailTracks != null) {
        AlbumDetailPage(
            album = activeAlbumDetail,
            tracks = albumDetailTracks,
            onBack = onCloseDetail,
            onPlayAll = { onPlayAlbum(activeAlbumDetail) },
            onShuffle = { onShuffleAlbum(activeAlbumDetail) },
            onPlayTrack = onPlayTrack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // 艺术家详情同样走全屏沉浸式页面
    val activeArtistDetail = selectedArtist
    if (hasPermission && activeArtistDetail != null && artistDetailTracks != null) {
        ArtistDetailPage(
            artist = activeArtistDetail,
            tracks = artistDetailTracks,
            onBack = onCloseDetail,
            onPlayAll = { onPlayArtist(activeArtistDetail) },
            onShuffle = { onShuffleArtist(activeArtistDetail) },
            onPlayTrack = onPlayTrack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    PageChrome(
        title = "曲库",
        subtitle = null,
        badge = "本地",
        showBrand = false,
        compactHeader = true,
        actions = {
            LibraryScanAction(
                hasPermission = hasPermission,
                scanState = scanState,
                onRequestPermission = onRequestPermission,
                onScanFolder = onScanFolder,
                onScanAll = onScanAll,
                onCancelScan = onCancelScan,
            )
        },
    ) {
        when {
            !hasPermission -> EmptyState("授权后即可索引本机音乐。")
            scanState.isScanning -> LibraryScanStatus(scanState = scanState, onCancelScan = onCancelScan)
            tracks.loadState.refresh is LoadState.Loading -> {
                LibraryBrowserHeader(
                    scanState = scanState,
                    selectedMode = LibraryViewMode.entries[pagerState.currentPage],
                    onSelectMode = { mode ->
                        scope.launch { pagerState.animateScrollToPage(mode.ordinal) }
                    },
                )
                EmptyState("正在加载曲库...")
            }
            tracks.loadState.refresh is LoadState.Error -> {
                LibraryBrowserHeader(
                    scanState = scanState,
                    selectedMode = LibraryViewMode.entries[pagerState.currentPage],
                    onSelectMode = { mode ->
                        scope.launch { pagerState.animateScrollToPage(mode.ordinal) }
                    },
                )
                EmptyState("曲库查询失败。")
            }
            else -> {
                LibraryBrowserHeader(
                    scanState = scanState,
                    selectedMode = LibraryViewMode.entries[pagerState.currentPage],
                    onSelectMode = { mode ->
                        scope.launch { pagerState.animateScrollToPage(mode.ordinal) }
                    },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (LibraryViewMode.entries[page]) {
                        LibraryViewMode.Songs -> {
                            if (tracks.itemCount == 0) {
                                LibraryBootstrapState()
                            } else {
                                TrackList(
                                    tracks = tracks,
                                    onPlayTrack = onPlayTrack,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        LibraryViewMode.Folders -> LibraryPlaceholderPage(
                            title = "文件夹视图",
                            subtitle = "按存储路径浏览本机音乐会放在这里。",
                        )

                        LibraryViewMode.Albums -> AlbumWall(
                            albums = albums,
                            onOpenAlbum = onOpenAlbum,
                            modifier = Modifier.fillMaxSize(),
                        )

                        LibraryViewMode.Artists -> ArtistWall(
                            artists = artists,
                            onOpenArtist = onOpenArtist,
                            modifier = Modifier.fillMaxSize(),
                        )

                        LibraryViewMode.Cloud -> LibraryPlaceholderPage(
                            title = "网盘音乐",
                            subtitle = "之后可以接入云端曲库和同步播放列表。",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBrowserHeader(
    scanState: LibraryScanProgress,
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    LibraryScanResultBanner(scanState)
    LibraryPagerTabs(
        selectedMode = selectedMode,
        onSelectMode = onSelectMode,
    )
}
