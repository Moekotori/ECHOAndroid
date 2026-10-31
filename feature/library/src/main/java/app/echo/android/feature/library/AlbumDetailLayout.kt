package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoTrack

private val AlbumTopBarHeight = 56.dp
private val AlbumTopBarRevealDistance = 150.dp

/**
 * Both album routes share one scrolling page: hero, tracks, then the album information.
 * A pinned bar keeps the back button reachable and fades the title in once the hero scrolls away.
 * The information items are lazy, so the online lookup only starts once they approach the viewport.
 */
@Composable
internal fun AlbumDetailLayout(
    album: AlbumSummary,
    tracks: List<EchoTrack>,
    accent: Color,
    onBack: () -> Unit,
    onOpenArtist: (() -> Unit)? = null,
    trackContent: LazyListScope.() -> Unit,
) = key(album.albumKey) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val revealPx = with(density) { AlbumTopBarRevealDistance.toPx() }
    // 0 at rest, 1 once the hero has scrolled away; read only in graphicsLayer so scrolling never recomposes.
    val barProgress = remember(listState, revealPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / revealPx).coerceIn(0f, 1f)
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barColor = lerp(echoPageBackgroundColor(), accent, 0.12f)

    Box(Modifier.fillMaxSize().detailBackSwipe(onBack)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset + AlbumTopBarHeight, bottom = AlbumDetailBottomPadding),
        ) {
            trackContent()
            item(key = "album-information") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    AlbumOnlineInformation(album)
                }
            }
            item(key = "album-details") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    AlbumInformation(album, tracks, onOpenArtist = onOpenArtist)
                }
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = barProgress.value * 0.96f }
                    .background(barColor),
            )
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .height(AlbumTopBarHeight)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumDetailBackButton(onBack, bordered = false)
                Text(
                    displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .graphicsLayer { alpha = barProgress.value },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
