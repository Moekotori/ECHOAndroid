package app.echo.android.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size

enum class EchoArtworkSize {
    Tiny,
    Thumbnail,
    Card,
    Hero,
}

@Composable
fun EchoArtworkImage(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    sizeClass: EchoArtworkSize = EchoArtworkSize.Thumbnail,
) {
    EchoArtworkImage(
        artworkUri = artworkUri,
        contentDescription = contentDescription,
        modifier = modifier,
        shape = shape,
        sizeClass = sizeClass,
        accent = EchoAccent,
        showSignal = false,
        placeholderIconSize = null,
    )
}

@Composable
internal fun EchoArtworkImage(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    sizeClass: EchoArtworkSize = EchoArtworkSize.Thumbnail,
    accent: Color = EchoAccent,
    showSignal: Boolean = false,
    placeholderIconSize: Dp? = null,
) {
    val context = LocalContext.current
    val model = remember(context, artworkUri, sizeClass) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .apply {
                EchoArtworkRequestHeadersRegistry.headersFor(artworkUri).forEach { (name, value) ->
                    setHeader(name, value)
                }
            }
            .crossfade(false)
            .apply {
                if (sizeClass == EchoArtworkSize.Hero) {
                    size(Size.ORIGINAL)
                }
            }
            .build()
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNullOrBlank()) {
            EchoArtworkPlaceholder(
                sizeClass = sizeClass,
                accent = accent,
                showSignal = showSignal,
                iconSize = placeholderIconSize,
            )
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    EchoArtworkPlaceholder(
                        sizeClass = sizeClass,
                        accent = accent,
                        showSignal = showSignal,
                        iconSize = placeholderIconSize,
                    )
                },
                error = {
                    EchoArtworkPlaceholder(
                        sizeClass = sizeClass,
                        accent = accent,
                        showSignal = showSignal,
                        iconSize = placeholderIconSize,
                    )
                },
            )
        }
    }
}

@Composable
private fun EchoArtworkPlaceholder(
    sizeClass: EchoArtworkSize,
    accent: Color,
    showSignal: Boolean,
    iconSize: Dp?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Headphones,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.88f),
            modifier = Modifier.size(iconSize ?: sizeClass.defaultIconSize(showSignal)),
        )
        if (showSignal) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
            EchoArtworkSignalStrip(accent)
        }
    }
}

@Composable
private fun EchoArtworkSignalStrip(accent: Color) {
    val heights = listOf(12.dp, 24.dp, 16.dp, 34.dp, 22.dp, 42.dp, 28.dp, 18.dp, 30.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, height ->
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(height),
                shape = RoundedCornerShape(8.dp),
                color = if (index % 3 == 0) {
                    accent.copy(alpha = 0.92f)
                } else {
                    Color.White.copy(alpha = 0.64f)
                },
                content = {},
            )
        }
    }
}

private fun EchoArtworkSize.defaultIconSize(showSignal: Boolean): Dp =
    when (this) {
        EchoArtworkSize.Tiny -> 22.dp
        EchoArtworkSize.Thumbnail -> if (showSignal) 38.dp else 32.dp
        EchoArtworkSize.Card -> if (showSignal) 42.dp else 36.dp
        EchoArtworkSize.Hero -> if (showSignal) 48.dp else 42.dp
    }
