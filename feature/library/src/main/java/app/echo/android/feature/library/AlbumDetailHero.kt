package app.echo.android.feature.library

import android.os.Build
import app.echo.android.feature.library.R as L10nR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.echoClickable
import app.echo.android.design.echoPageSection
import app.echo.android.design.echoSharedCover
import app.echo.android.design.formatDuration
import app.echo.android.model.library.AlbumSummary

private val AlbumHeroArtworkSize = 224.dp
private val HiResGoldLight = Color(0xFF9A6F0B)
private val HiResGoldDark = Color(0xFFF2D27A)

/** Centered cover, title, artist and two plain text lines: facts, then file spec. No cards, no chips. */
@Composable
internal fun AlbumHero(
    album: AlbumSummary,
    palette: ArtworkPalette,
    facts: AlbumTrackFacts,
    onOpenArtist: (() -> Unit)? = null,
) {
    val colors = rememberDetailGlassColors()
    val dark = LocalEchoDarkTheme.current
    // The artist line borrows the cover colour; the deep swatch keeps contrast on light pages.
    val artistColor = if (dark) palette.vibrant else palette.deep
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier.echoSharedCover("album", album.albumKey).size(AlbumHeroArtworkSize),
            accent = palette.vibrant,
            showSignal = album.artworkUri == null,
            cornerRadius = 18.dp,
            elevation = 18.dp,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
            color = colors.content,
            modifier = Modifier.echoPageSection(0),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = (if (onOpenArtist != null) {
                Modifier.echoClickable(
                    onClickLabel = stringResource(L10nR.string.feature_library_go_to_artist),
                    role = Role.Button,
                    onClick = onOpenArtist,
                )
            } else {
                Modifier
            }).echoPageSection(1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel()),
                color = artistColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onOpenArtist != null) {
                EchoIcon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = artistColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            albumFactsLine(album, facts.genre),
            modifier = Modifier.echoPageSection(2),
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        AlbumSpecLine(facts.spec, colors.muted)
    }
}

@Composable
private fun AlbumSpecLine(spec: AlbumSpec, color: Color) {
    val rest = listOfNotNull(
        spec.formats.take(2).joinToString(" / ").takeIf { it.isNotEmpty() },
        formatSampleRates(spec.sampleRatesHz),
        spec.sizeBytes?.let(::formatFileSize),
    ).joinToString(" · ")
    if (rest.isEmpty() && !spec.hiRes) return
    val gold = if (LocalEchoDarkTheme.current) HiResGoldDark else HiResGoldLight
    Text(
        buildAnnotatedString {
            if (spec.hiRes) {
                withStyle(SpanStyle(color = gold, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)) {
                    append("Hi-Res")
                }
                if (rest.isNotEmpty()) append("  ")
            }
            append(rest)
        },
        modifier = Modifier.fillMaxWidth().echoPageSection(2),
        color = color,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

/** Year · genre · track count · length. Genre comes from the loaded tracks, since the album summary has none. */
@Composable
private fun albumFactsLine(album: AlbumSummary, genre: String?): String {
    val parts = mutableListOf<String>()
    album.year?.takeIf { it > 0 }?.let { parts.add(it.toString()) }
    genre?.let { parts.add(it) }
    parts.add(libraryTrackCountLabel(album.trackCount))
    if (album.durationMs > 0L) {
        val minutes = (album.durationMs / 60000L).toInt()
        parts.add(if (minutes >= 1) libraryMinutesLabel(minutes) else formatDuration(album.durationMs))
    }
    return parts.joinToString(" · ")
}

/**
 * The cover, blurred and faded out downwards, behind the hero. It needs RenderEffect blur (API 31) and is skipped
 * in lightweight mode, where the plain colour wash drawn by the page background stays.
 */
@Composable
internal fun AlbumHeroAtmosphere(
    artworkUri: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (LocalEchoEffectivePerformanceMode.current.isLightweight || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        0.3f to Color.Black,
                        0.75f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // A small tile scaled up before blurring keeps the decode and the blur layer cheap.
        ArtworkTile(
            artworkUri = artworkUri,
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = 8f
                    scaleY = 3.6f
                    alpha = if (dark) 0.42f else 0.5f
                }
                .blur(22.dp, BlurredEdgeTreatment.Unbounded),
            accent = accent,
            cornerRadius = 0.dp,
        )
    }
}
