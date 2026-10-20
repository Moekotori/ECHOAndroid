package app.echo.android.feature.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import app.echo.android.design.echoPressFeedback

/** Small, quiet corners: covers and press targets stay close to square instead of turning into pills. */
internal val HomeCoverCorner = 6.dp
internal val HomeTouchCorner = 4.dp

/**
 * A flat, container-less block for home content. It draws no fill, border or shadow; it only adds
 * vertical breathing room and, when clickable, a press target, so sections align to the page edge.
 */
@Composable
internal fun HomeBlock(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(PaddingValues(vertical = verticalPadding))) { content() }
    }
    val shape = RoundedCornerShape(HomeTouchCorner)
    if (onClick == null) {
        Surface(modifier.fillMaxWidth(), shape = shape, color = Color.Transparent, content = inner)
    } else {
        val source = remember { MutableInteractionSource() }
        Surface(onClick, modifier.fillMaxWidth().echoPressFeedback(source), shape = shape,
            color = Color.Transparent, interactionSource = source, content = inner)
    }
}
