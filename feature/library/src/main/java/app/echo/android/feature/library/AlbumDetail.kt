package app.echo.android.feature.library

import app.echo.android.design.EchoMotion
import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import app.echo.android.design.echoPageSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import app.echo.android.design.EchoIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.design.rememberArtworkPalette
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import kotlinx.coroutines.flow.Flow

internal val AlbumDetailBottomPadding = 168.dp
private val LocalAlbumDetail = staticCompositionLocalOf { false }
private val AlbumTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.28f),
    offset = Offset(0f, 1.5f),
    blurRadius = 8f,
)

internal data class DetailGlassColors(
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
internal fun rememberDetailGlassColors(): DetailGlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    // 列表行会高频调用,真正 remember 避免每次重组都分配
    return remember(scheme, dark) {
        DetailGlassColors(
            surface = if (dark) scheme.surface.copy(alpha = 0.62f) else scheme.surface.copy(alpha = 0.78f),
            elevatedSurface = if (dark) scheme.surfaceVariant.copy(alpha = 0.34f) else scheme.surface.copy(alpha = 0.82f),
            border = if (dark) scheme.outlineVariant.copy(alpha = 0.34f) else scheme.outlineVariant.copy(alpha = 0.46f),
            content = scheme.onSurface.copy(alpha = if (dark) 0.94f else 0.90f),
            muted = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.72f else 0.76f),
        )
    }
}

@Composable
internal fun AlbumDetailPage(
    album: AlbumSummary,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onWriteNfc: (() -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onAddNextUp: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenTrackArtist: ((EchoTrack) -> Unit)? = null,
    artistAlbums: (() -> Flow<PagingData<AlbumSummary>>?)? = null,
    onOpenAlbum: ((AlbumSummary) -> Unit)? = null,
    playingTrackId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    liked: Boolean? = null,
    onLikedChange: (Boolean) -> Unit = {},
) {
    val rangeSource = rememberLibraryRangeSource(tracks)
    val palette = rememberArtworkPalette(album.artworkUri, seedKey = album.albumKey)
    val loadedTracks = tracks.itemSnapshotList.items
    val facts = remember(loadedTracks) { albumTrackFacts(loadedTracks) }
    val discSummaries = if (loadedTracks.size == album.trackCount) facts.discs else emptyMap()
    val artistName = displayMetadataOrUnknown(
        album.albumArtist,
        displayMetadataOrUnknown(album.artist, unknownArtistLabel()),
    )
    var artistAlbumsOpen by rememberSaveable(album.albumKey) { mutableStateOf(false) }
    CompositionLocalProvider(LocalAlbumDetail provides true) {
        Box(
            modifier = modifier
                .fillMaxSize(),
        ) {
            AlbumDetailLightBackground(
                artworkUri = album.artworkUri,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
            AlbumDetailLayout(
                album = album,
                onWriteNfc = onWriteNfc,
                liked = liked,
                onLikedChange = onLikedChange,
                tracks = loadedTracks,
                accent = palette.vibrant,
                onBack = onBack,
                onOpenArtist = onOpenArtist,
                footerContent = {
                    if (artistAlbums != null && onOpenAlbum != null) {
                        item(key = "artist-albums") {
                            ArtistAlbumsRail(
                                artistName = artistName,
                                currentAlbumKey = album.albumKey,
                                albums = artistAlbums,
                                accent = palette.vibrant,
                                onOpenAlbum = onOpenAlbum,
                                onShowAll = { artistAlbumsOpen = true },
                            )
                        }
                    }
                },
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        AlbumHero(album = album, palette = palette, facts = facts, onOpenArtist = onOpenArtist)
                        if (facts.hasCue) {
                            Spacer(Modifier.height(14.dp))
                            AlbumCueBanner()
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }
                item(key = "tracks-header") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 4.dp),
                    ) {
                        AlbumTracksHeader(count = album.trackCount)
                    }
                }

                when {
                    tracks.isInitialPagingLoad() -> item(key = "loading") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_loading_tracks_8e2147))
                    }
                    tracks.isInitialPagingError() -> item(key = "error") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_failed_to_load_tracks_f65c9b))
                    }
                    tracks.itemCount == 0 -> item(key = "empty") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                    }
                    else -> items(
                        count = tracks.itemCount,
                        key = { index -> tracks.peek(index)?.id ?: "track-$index" },
                    ) { index ->
                        tracks[index]?.let { track ->
                            Box(
                                modifier = libraryTrackItemMotion()
                                    .fillMaxWidth()
                                    .widthIn(max = EchoContentMaxWidth)
                                    .padding(horizontal = 16.dp),
                            ) {
                                AlbumDiscTrackRow(
                                    rangeSource = rangeSource,
                                    index = index,
                                    track = track,
                                    previous = if (index > 0) tracks.peek(index - 1) else null,
                                    isFirst = index == 0,
                                    multiDisc = facts.multiDisc,
                                    discTotals = discSummaries[track.albumDiscNumber()],
                                    specBadge = trackSpecOutlier(track, facts.spec.dominant),
                                    accent = palette.vibrant,
                                    playing = track.id == playingTrackId,
                                    isPlaying = isPlaying && track.id == playingTrackId,
                                    onClick = { onPlayTrack(track) },
                                    onMoveUp = onMoveTrack.albumMoveAction(
                                        index, index - 1, track,
                                        if (index > 0) tracks.peek(index - 1) else null,
                                    ),
                                    onMoveDown = onMoveTrack.albumMoveAction(
                                        index, index + 1, track,
                                        if (index + 1 < tracks.itemCount) tracks.peek(index + 1) else null,
                                    ),
                                    onUpdateTrackMetadata = onUpdateTrackMetadata,
                                    onImportLyrics = onImportLyrics,
                                    onPickArtwork = onPickArtwork,
                                    onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                    onAddToPlaylist = onAddToPlaylist,
                                    onPlayNext = onPlayNext,
                                    onAddNextUp = onAddNextUp,
                                    onEnqueue = onEnqueue,
                                    onOpenArtist = onOpenTrackArtist,
                                )
                            }
                        }
                    }
                }
            }
            if (artistAlbumsOpen && artistAlbums != null && onOpenAlbum != null) {
                // Built once per open sheet; the pager flow is not created while the sheet is closed.
                val artistAlbumFlow = remember(album.albumKey) { artistAlbums() }
                if (artistAlbumFlow != null) {
                    ArtistAlbumsSheet(
                        artistName = artistName,
                        albums = artistAlbumFlow,
                        accent = palette.vibrant,
                        onOpenAlbum = { target ->
                            artistAlbumsOpen = false
                            if (target.albumKey != album.albumKey) onOpenAlbum(target)
                        },
                        onDismiss = { artistAlbumsOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlbumDetailListPage(
    album: AlbumSummary,
    tracks: List<EchoTrack>,
    onBack: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
    onPlayTrack: (EchoTrack) -> Unit,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenTrackArtist: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rangeSource = rememberLibraryRangeSource(tracks)
    val palette = rememberArtworkPalette(album.artworkUri, seedKey = album.albumKey)
    val facts = remember(tracks) { albumTrackFacts(tracks) }
    val discSummaries = if (tracks.size == album.trackCount) facts.discs else emptyMap()
    CompositionLocalProvider(LocalAlbumDetail provides true) {
        Box(
            modifier = modifier
                .fillMaxSize(),
        ) {
            AlbumDetailLightBackground(
                artworkUri = album.artworkUri,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
            AlbumDetailLayout(
                album = album,
                tracks = tracks,
                accent = palette.vibrant,
                onBack = onBack,
                onOpenArtist = onOpenArtist,
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        AlbumHero(album = album, palette = palette, facts = facts, onOpenArtist = onOpenArtist)
                        if (onPlayOnPc != null) {
                            Spacer(Modifier.height(14.dp))
                            AlbumPlayOnPcAction(onPlayOnPc)
                        }
                        if (facts.hasCue) {
                            Spacer(Modifier.height(14.dp))
                            AlbumCueBanner()
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }
                item(key = "tracks-header") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = EchoContentMaxWidth)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 4.dp),
                    ) {
                        AlbumTracksHeader(count = album.trackCount)
                    }
                }

                if (tracks.isEmpty()) {
                    item(key = "empty") {
                        AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                    }
                } else {
                    itemsIndexed(
                        items = tracks,
                        key = { _, track -> track.id },
                    ) { index, track ->
                        Box(
                            modifier = libraryTrackItemMotion()
                                .fillMaxWidth()
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 16.dp),
                        ) {
                            AlbumDiscTrackRow(
                                rangeSource = rangeSource,
                                index = index,
                                track = track,
                                previous = tracks.getOrNull(index - 1),
                                isFirst = index == 0,
                                multiDisc = facts.multiDisc,
                                discTotals = discSummaries[track.albumDiscNumber()],
                                specBadge = trackSpecOutlier(track, facts.spec.dominant),
                                accent = palette.vibrant,
                                onClick = { onPlayTrack(track) },
                                onMoveUp = onMoveTrack.albumMoveAction(index, index - 1, track, tracks.getOrNull(index - 1)),
                                onMoveDown = onMoveTrack.albumMoveAction(index, index + 1, track, tracks.getOrNull(index + 1)),
                                onUpdateTrackMetadata = onUpdateTrackMetadata,
                                onImportLyrics = onImportLyrics,
                                onPickArtwork = onPickArtwork,
                                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                onOpenArtist = onOpenTrackArtist,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun GenreTrackDetailPage(
    artist: ArtistSummary,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onAddNextUp: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rangeSource = rememberLibraryRangeSource(tracks)
    val palette = rememberArtworkPalette(artist.artworkUri, seedKey = artist.artistKey)
    val loadedTracks = tracks.itemSnapshotList.items
    Box(
        modifier = modifier
            .fillMaxSize()
            .detailBackSwipe(onBack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.vibrant.copy(alpha = 0.34f),
                        0.45f to palette.deep.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = AlbumDetailBottomPadding),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = EchoContentMaxWidth)
                        .padding(horizontal = 20.dp),
                ) {
                    AlbumDetailTopBar(onBack = onBack)
                    Spacer(Modifier.height(8.dp))
                    ArtistHero(artist = artist, palette = palette)
                    Spacer(Modifier.height(18.dp))
                    AlbumActionBar(onPlayAll = onPlayAll, onShuffle = onShuffle)
                    Spacer(Modifier.height(18.dp))
                    AlbumDetailInsights(
                        source = sourceInsight(loadedTracks),
                        info = formatInsight(loadedTracks),
                        palette = palette,
                    )
                    Spacer(Modifier.height(22.dp))
                    AlbumTracksHeader(count = artist.trackCount)
                    Spacer(Modifier.height(10.dp))
                }
            }

            when {
                tracks.isInitialPagingLoad() -> item(key = "loading") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_loading_tracks_8e2147))
                }
                tracks.isInitialPagingError() -> item(key = "error") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_failed_to_load_tracks_f65c9b))
                }
                tracks.itemCount == 0 -> item(key = "empty") {
                    AlbumDetailNotice(stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a))
                }
                else -> items(
                    count = tracks.itemCount,
                    key = { index -> tracks.peek(index)?.id ?: "track-$index" },
                ) { index ->
                    tracks[index]?.let { track ->
                        Box(
                            modifier = libraryTrackItemMotion()
                                .fillMaxWidth()
                                .widthIn(max = EchoContentMaxWidth)
                                .padding(horizontal = 20.dp),
                        ) {
                            AlbumTrackRow(
                                rangeSource = rangeSource,
                                index = index,
                                track = track,
                                accent = palette.vibrant,
                                onClick = { onPlayTrack(track) },
                                onUpdateTrackMetadata = onUpdateTrackMetadata,
                                onImportLyrics = onImportLyrics,
                                onPickArtwork = onPickArtwork,
                                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                                onAddToPlaylist = onAddToPlaylist,
                                onPlayNext = onPlayNext,
                                onAddNextUp = onAddNextUp,
                                onEnqueue = onEnqueue,
                                onOpenArtist = onOpenArtist,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun AlbumDetailLightBackground(
    artworkUri: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val base = echoPageBackgroundColor()
    // The palette resolves after the cover loads; fade to it instead of snapping (lightweight mode snaps).
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val tint by animateColorAsState(
        targetValue = palette.vibrant,
        animationSpec = if (lightweight) snap() else tween(durationMillis = 400, easing = EchoMotion.Silk),
        label = "albumTint",
    )
    // Missing artwork must not turn a hash-generated placeholder color into a full-screen wash.
    Box(modifier = modifier.background(base)) {
        if (!artworkUri.isNullOrBlank()) {
            Box(
                Modifier.fillMaxWidth().height(420.dp).background(
                    Brush.verticalGradient(
                        0f to tint.copy(alpha = if (dark) 0.26f else 0.30f),
                        0.55f to tint.copy(alpha = if (dark) 0.12f else 0.10f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            AlbumHeroAtmosphere(
                artworkUri = artworkUri,
                accent = tint,
                modifier = Modifier.fillMaxWidth().height(380.dp),
            )
        }
    }
}

@Composable
private fun ArtistHero(artist: ArtistSummary, palette: ArtworkPalette) {
    val colors = rememberDetailGlassColors()
    DetailHeroLayout(
        artworkSize = 200.dp,
        centered = true,
        artwork = { modifier ->
            ArtworkTile(
                artworkUri = artist.artworkUri,
                modifier = modifier,
                accent = palette.vibrant,
                showSignal = artist.artworkUri == null,
                cornerRadius = 0.dp,
                elevation = 22.dp,
            )
        },
    ) {
        Text(
            displayMetadataOrUnknown(artist.name, unknownArtistLabel()),
            color = colors.content,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = AlbumTextShadow),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artistMetaLine(artist),
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AlbumDetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumDetailBackButton(onBack)
    }
}

/** [bordered] draws the circular chip; without it the icon sits directly on the page. */
@Composable
internal fun AlbumDetailBackButton(onBack: () -> Unit, bordered: Boolean = true) {
    val colors = rememberDetailGlassColors()
    val chip = if (bordered) {
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.elevatedSurface)
            .border(BorderStroke(1.dp, colors.border), CircleShape)
    } else {
        Modifier.size(48.dp).clip(CircleShape)
    }
    Box(
        modifier = chip.echoClickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        EchoIcon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(L10nR.string.feature_library_back_49093c),
            tint = colors.content,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun AlbumActionBar(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayOnPc: (() -> Unit)? = null,
) {
    val refined = LocalAlbumDetail.current
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val actionContent = scheme.primary
    val actionBorder = scheme.primary.copy(alpha = if (dark) 0.36f else 0.30f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumDetailActionButton(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(L10nR.string.feature_library_play_all_55c80e),
            iconSize = 24.dp,
            contentColor = if (refined) scheme.onPrimary else actionContent,
            containerColor = if (refined) scheme.primary else colors.elevatedSurface,
            borderColor = if (refined) Color.Transparent else actionBorder,
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
        )
        AlbumDetailActionButton(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(L10nR.string.feature_library_shuffle_34e7ce),
            iconSize = 22.dp,
            contentColor = if (refined) colors.content else actionContent,
            containerColor = colors.elevatedSurface,
            borderColor = if (refined) colors.border else actionBorder,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
    }
    if (onPlayOnPc != null) {
        AlbumDetailActionButton(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(L10nR.string.feature_library_play_on_pc_7c21a4),
            iconSize = 22.dp,
            contentColor = if (refined) colors.content else actionContent,
            containerColor = colors.elevatedSurface,
            borderColor = if (refined) colors.border else actionBorder,
            onClick = onPlayOnPc,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }
}

@Composable
private fun AlbumDetailActionButton(
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    contentColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (LocalAlbumDetail.current) 14.dp else 26.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .echoClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIcon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Facts describe the catalog files, not the active audio output route. */
@Composable
internal fun AlbumInformation(
    album: AlbumSummary,
    tracks: List<EchoTrack>,
    onOpenArtist: (() -> Unit)? = null,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val colors = rememberDetailGlassColors()
    val facts = remember(album, tracks) {
        AlbumFileFacts(
            artists = tracks.map { it.artist.trim() }.filter { it.isNotEmpty() }.distinct(),
            discs = tracks.mapNotNull { it.discNumber?.takeIf { number -> number > 0 } }.distinct().size,
            sources = tracks.map { it.source.id }.distinct(),
            formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct(),
            sampleRates = tracks.mapNotNull { it.sampleRateHz?.takeIf { hz -> hz > 0 } }.distinct(),
            sizeBytes = tracks.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            knownSizes = tracks.count { it.sizeBytes > 0L },
            knownSampleRates = tracks.count { (it.sampleRateHz ?: 0) > 0 },
        )
    }
    val addedDate = remember(album.addedAtSeconds, locale) {
        album.addedAtSeconds.takeIf { it > 0L }?.let {
            java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale)
                .format(java.util.Date(it * 1000L))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(L10nR.string.album_information_title), color = colors.content,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (tracks.size < album.trackCount) {
            Text(stringResource(L10nR.string.album_information_partial, tracks.size, album.trackCount),
                color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        AlbumInformationRow(
            stringResource(L10nR.string.album_information_artist),
            displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel()),
            onClick = onOpenArtist,
            onClickLabel = stringResource(L10nR.string.feature_library_go_to_artist),
        )
        album.year?.takeIf { it > 0 }?.let {
            AlbumInformationRow(stringResource(L10nR.string.album_information_year), it.toString())
        }
        AlbumInformationRow(stringResource(L10nR.string.feature_library_tracks_2d80e8), libraryTrackCountLabel(album.trackCount))
        if (album.durationMs > 0L) {
            AlbumInformationRow(stringResource(L10nR.string.album_information_duration), formatDuration(album.durationMs))
        }
        addedDate?.let { AlbumInformationRow(stringResource(L10nR.string.album_information_added), it) }
        if (facts.discs > 1 && tracks.size == album.trackCount) {
            AlbumInformationRow(stringResource(L10nR.string.album_information_discs), facts.discs.toString())
        }
        if (facts.artists.size > 1) {
            AlbumInformationRow(stringResource(L10nR.string.album_information_track_artists), facts.artists.joinToString(" / ", limit = 8, truncated = "…"))
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(L10nR.string.album_information_audio), color = colors.content,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        AlbumSpecBar(groups = remember(tracks) { albumSpec(tracks).groups }, accent = MaterialTheme.colorScheme.primary)
        if (facts.sources.isNotEmpty()) {
            AlbumInformationRow(stringResource(L10nR.string.feature_library_source_4aff16), facts.sources.map { sourceLabel(it) }.joinToString(" / "))
        }
        if (facts.formats.isNotEmpty()) {
            AlbumInformationRow(stringResource(L10nR.string.feature_library_format_a7775b), facts.formats.joinToString(" / "))
        }
        formatSampleRates(facts.sampleRates)?.let {
            AlbumInformationRow(stringResource(L10nR.string.album_information_sample_rate), it)
        }
        if (facts.sizeBytes > 0L) {
            AlbumInformationRow(stringResource(if (facts.knownSizes == album.trackCount) L10nR.string.album_information_size else L10nR.string.album_information_known_size), formatFileSize(facts.sizeBytes))
        }
        if (tracks.isNotEmpty() && (facts.knownSampleRates < tracks.size || facts.knownSizes < tracks.size)) {
            Text(stringResource(L10nR.string.album_information_missing_specs), color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(L10nR.string.album_information_file_specs), color = colors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

private data class AlbumFileFacts(
    val artists: List<String>,
    val discs: Int,
    val sources: List<String>,
    val formats: List<String>,
    val sampleRates: List<Int>,
    val sizeBytes: Long,
    val knownSizes: Int,
    val knownSampleRates: Int,
)

@Composable
private fun AlbumInformationRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val colors = rememberDetailGlassColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, modifier = Modifier.weight(0.36f), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            modifier = Modifier
                .weight(0.64f)
                .then(
                    if (onClick != null) {
                        Modifier.echoClickable(
                            onClickLabel = onClickLabel,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            color = colors.content,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AlbumDetailInsights(
    source: DetailInsight,
    info: DetailInsight,
    palette: ArtworkPalette,
) {
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val shape = RoundedCornerShape(20.dp)
    val refined = LocalAlbumDetail.current
    if (refined) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EchoIcon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(source.primary, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                Text("·", color = colors.muted, style = MaterialTheme.typography.bodySmall)
                Text(info.primary, color = colors.muted, style = MaterialTheme.typography.labelMedium)
            }
            Text(info.secondary, color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val containerBrush = Brush.linearGradient(
        if (dark) {
            listOf(
                scheme.surface.copy(alpha = 0.54f),
                scheme.surfaceVariant.copy(alpha = 0.30f),
                palette.deep.copy(alpha = 0.12f),
            )
        } else {
            listOf(
                scheme.surface.copy(alpha = 0.84f),
                scheme.surfaceVariant.copy(alpha = 0.50f),
                palette.vibrant.copy(alpha = 0.08f),
            )
        },
    )
    val borderColor = if (refined) {
        colors.border
    } else if (dark) {
        palette.vibrant.copy(alpha = 0.22f)
    } else {
        colors.border
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .then(if (refined) Modifier.background(scheme.surface) else Modifier.background(containerBrush))
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(horizontal = if (refined) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailInsightCell(
            insight = source,
            icon = Icons.Rounded.MusicNote,
            accent = palette.vibrant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(42.dp)
                .background(borderColor),
        )
        DetailInsightCell(
            insight = info,
            icon = Icons.Rounded.GraphicEq,
            accent = palette.deep,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailInsightCell(
    insight: DetailInsight,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = rememberDetailGlassColors()
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val iconTint = if (LocalAlbumDetail.current) colors.muted else if (dark) accent.copy(alpha = 0.82f) else scheme.primary.copy(alpha = 0.74f)
    val titleColor = if (LocalAlbumDetail.current) colors.muted else if (dark) accent.copy(alpha = 0.86f) else scheme.primary
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIcon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(if (LocalAlbumDetail.current) 20.dp else 26.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                insight.title,
                color = titleColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                insight.primary,
                color = colors.content,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                insight.secondary,
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AlbumTracksHeader(
    count: Int,
    titleColor: Color? = null,
    metaColor: Color? = null,
) {
    val colors = rememberDetailGlassColors()
    val resolvedTitleColor = titleColor ?: colors.content
    val resolvedMetaColor = metaColor ?: colors.muted
    Row(
        modifier = Modifier.fillMaxWidth().echoPageSection(3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(L10nR.string.feature_library_tracks_2d80e8),
            color = resolvedTitleColor,
            style = if (LocalAlbumDetail.current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = if (LocalAlbumDetail.current) FontWeight.Medium else FontWeight.SemiBold,
        )
        Text(
            libraryTrackCountLabel(count),
            color = resolvedMetaColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private data class DetailInsight(
    val title: String,
    val primary: String,
    val secondary: String,
)

@Composable
private fun sourceInsight(tracks: List<EchoTrack>): DetailInsight {
    val resolvedSource = tracks
        .map { it.source.id }
        .distinct()
        .singleOrNull()
        ?.let { sourceLabel(it) }
        ?: if (tracks.isEmpty()) {
            stringResource(L10nR.string.feature_library_local_library_ddf4b1)
        } else {
            stringResource(L10nR.string.feature_library_multiple_sources_0f53d5)
        }
    val secondary = if (tracks.isEmpty()) {
        stringResource(L10nR.string.feature_library_waiting_for_track_info_f2add7)
    } else {
        libraryTrackCountLabel(tracks.size)
    }
    return DetailInsight(stringResource(L10nR.string.feature_library_source_4aff16), resolvedSource, secondary)
}

@Composable
private fun albumInfoInsight(album: AlbumSummary, tracks: List<EchoTrack>): DetailInsight {
    val primary = displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel())
    val year = album.year?.takeIf { it > 0 }?.toString()
    val discs = tracks.mapNotNull { it.discNumber?.takeIf { disc -> disc > 0 } }.distinct().size
    val secondary = buildList {
        year?.let { add(it) }
        add(libraryTrackCountLabel(album.trackCount))
        if (discs > 1) {
            add(stringResource(L10nR.string.feature_library_discs_discs_902194, (discs).toString()))
        }
        if (album.durationMs > 0L) add(readableDuration(album.durationMs))
    }.joinToString(" · ")
    return DetailInsight(stringResource(L10nR.string.feature_library_info_41ae25), primary, secondary)
}

@Composable
private fun artistInfoInsight(artist: ArtistSummary, tracks: List<EchoTrack>): DetailInsight {
    val formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct().take(2)
    val primary = libraryAlbumCountLabel(artist.albumCount.coerceAtLeast(0))
    val secondary = buildList {
        add(libraryTrackCountLabel(artist.trackCount))
        if (artist.durationMs > 0L) add(readableDuration(artist.durationMs))
        if (formats.isNotEmpty()) add(formats.joinToString(" / "))
    }.joinToString(" · ")
    return DetailInsight(stringResource(L10nR.string.feature_library_info_41ae25), primary, secondary)
}

@Composable
private fun formatInsight(tracks: List<EchoTrack>): DetailInsight {
    val formats = tracks.mapNotNull { formatMimeType(it.mimeType) }.distinct().take(3)
    val primary = formats.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        ?: stringResource(L10nR.string.feature_library_format_pending_ce16a1)
    val size = tracks.sumOf { it.sizeBytes }.takeIf { it > 0L }?.let(::formatFileSize)
    val sampleRate = formatSampleRates(tracks.mapNotNull { it.sampleRateHz?.takeIf { hz -> hz > 0 } }.distinct())
    val secondary = buildList {
        size?.let { add(it) }
        add(sampleRate ?: stringResource(L10nR.string.feature_library_sample_rate_pending_bd6e6d))
    }.joinToString(" · ")
    return DetailInsight(stringResource(L10nR.string.feature_library_format_a7775b), primary, secondary)
}

@Composable
private fun sourceLabel(sourceId: String): String = when (sourceId.lowercase()) {
    "mediastore" -> stringResource(L10nR.string.feature_library_local_library_ddf4b1)
    "subsonic" -> "Subsonic / Navidrome"
    "webdav" -> "WebDAV"
    "smb" -> "SMB"
    "jellyfin" -> "Jellyfin / Emby"
    "unknown" -> stringResource(L10nR.string.feature_library_unknown_source_d8c291)
    else -> when {
        sourceId.startsWith("subsonic:", ignoreCase = true) -> "Subsonic / Navidrome"
        sourceId.startsWith("webdav:", ignoreCase = true) -> "WebDAV"
        sourceId.startsWith("smb:", ignoreCase = true) -> "SMB"
        sourceId.startsWith("jellyfin:", ignoreCase = true) -> "Jellyfin / Emby"
        else -> sourceId
    }
}

@Composable
private fun readableDuration(durationMs: Long): String {
    val minutes = (durationMs / 60000L).toInt()
    return if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(durationMs)
}

@Composable
internal fun AlbumTrackRow(
    index: Int,
    track: EchoTrack,
    rangeSource: LibraryRangeSource? = null,
    accent: Color,
    palette: ArtworkPalette? = null,
    playing: Boolean = false,
    isPlaying: Boolean = false,
    /** Short spec such as "MP3 44.1" for a file that differs from the rest of the album. */
    specBadge: String? = null,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onAddNextUp: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
    showCard: Boolean = true,
) {
    val colors = rememberDetailGlassColors()
    TrackContextMenu(
        rangeSource = rangeSource,
        track = track,
        onPlay = onClick,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onUpdateTrackMetadata = onUpdateTrackMetadata,
        onImportLyrics = onImportLyrics,
        onPickArtwork = onPickArtwork,
        onMatchNeteaseMetadata = onMatchNeteaseMetadata,
        onAddToPlaylist = onAddToPlaylist,
        onPlayNext = onPlayNext,
        onAddNextUp = onAddNextUp,
        onEnqueue = onEnqueue,
        onOpenArtist = onOpenArtist?.let { open -> { open(track) } },
        onOpenAlbum = onOpenAlbum?.let { open -> { open(track) } },
        modifier = Modifier.fillMaxWidth(),
    ) { pressModifier ->
        val flat = !showCard || LocalAlbumDetail.current
        val rowShape = RoundedCornerShape(16.dp)
        val cardModifier = if (flat) {
            Modifier
        } else {
            val dark = LocalEchoDarkTheme.current
            val rowBrush = remember(dark, colors.surface, accent) {
                Brush.linearGradient(
                    listOf(
                        if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.76f),
                        colors.surface,
                        accent.copy(alpha = if (dark) 0.10f else 0.05f),
                    ),
                )
            }
            Modifier.background(rowBrush).border(BorderStroke(1.dp, colors.border), rowShape)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(rowShape)
                .then(cardModifier)
                .then(pressModifier)
                .padding(horizontal = if (flat) 8.dp else 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (playing) {
                Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                    AlbumPlayingIndicator(isPlaying = isPlaying)
                }
            } else {
                Text(
                    text = (track.trackNumber ?: (index + 1)).toString().padStart(2, '0'),
                    color = if (flat) colors.muted else accent,
                    style = if (flat) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(26.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    displayMetadataOrUnknown(track.title, unknownTrackLabel()),
                    color = colors.content,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    displayMetadataOrUnknown(track.artist, unknownArtistLabel()),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                cueClipRangeLabel(track)?.let { clip ->
                    Text(
                        clip,
                        color = colors.muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            if (specBadge != null) {
                Text(
                    specBadge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.muted.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = colors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                formatDuration(track.durationMs),
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AlbumDiscTrackRow(
    index: Int,
    track: EchoTrack,
    rangeSource: LibraryRangeSource? = null,
    previous: EchoTrack?,
    isFirst: Boolean,
    multiDisc: Boolean,
    discTotals: AlbumDiscTotals? = null,
    specBadge: String? = null,
    accent: Color,
    playing: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onUpdateTrackMetadata: (suspend (EchoTrackMetadataUpdate) -> Boolean)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onAddNextUp: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onOpenArtist: ((EchoTrack) -> Unit)? = null,
    onOpenAlbum: ((EchoTrack) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        if (shouldShowAlbumDiscHeader(track, previous, isFirst, multiDisc)) {
            AlbumDiscHeader(discNumber = track.albumDiscNumber(), isFirst = isFirst, totals = discTotals)
        }
        AlbumTrackRow(
        rangeSource = rangeSource,
            index = index,
            track = track,
            accent = accent,
            playing = playing,
            isPlaying = isPlaying,
            specBadge = specBadge,
            onClick = onClick,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onUpdateTrackMetadata = onUpdateTrackMetadata,
            onImportLyrics = onImportLyrics,
            onPickArtwork = onPickArtwork,
            onMatchNeteaseMetadata = onMatchNeteaseMetadata,
            onAddToPlaylist = onAddToPlaylist,
            onPlayNext = onPlayNext,
            onAddNextUp = onAddNextUp,
            onEnqueue = onEnqueue,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
        )
    }
}

@Composable
private fun AlbumCueBanner() {
    val colors = rememberDetailGlassColors()
    Text(
        stringResource(L10nR.string.album_cue_banner),
        color = colors.muted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.elevatedSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun AlbumDiscHeader(discNumber: Int, isFirst: Boolean, totals: AlbumDiscTotals?) {
    val colors = rememberDetailGlassColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = if (isFirst) 8.dp else 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(L10nR.string.album_disc_number, discNumber),
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
        // Totals are only known once every track is loaded, so a half-loaded album shows no partial numbers.
        if (totals != null) {
            Text(
                listOf(libraryTrackCountLabel(totals.trackCount), readableDuration(totals.durationMs)).joinToString(" · "),
                color = colors.muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun ((fromIndex: Int, toIndex: Int) -> Unit)?.albumMoveAction(
    fromIndex: Int,
    toIndex: Int,
    track: EchoTrack,
    neighbor: EchoTrack?,
): (() -> Unit)? {
    val move = this ?: return null
    if (neighbor == null) return null
    if (track.albumDiscNumber() != neighbor.albumDiscNumber()) return null
    return { move(fromIndex, toIndex) }
}

@Composable
internal fun AlbumDetailNotice(message: String) {
    val colors = rememberDetailGlassColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = EchoContentMaxWidth)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(message, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun artistMetaLine(artist: ArtistSummary): String {
    val parts = mutableListOf<String>()
    if (artist.albumCount > 0) parts.add(libraryAlbumCountLabel(artist.albumCount))
    parts.add(libraryTrackCountLabel(artist.trackCount))
    if (artist.durationMs > 0L) {
        val minutes = (artist.durationMs / 60000L).toInt()
        parts.add(if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(artist.durationMs))
    }
    return parts.joinToString(" · ")
}
