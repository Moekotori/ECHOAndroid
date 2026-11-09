package app.echo.android.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun LibrarySortButton(
    contentDescription: String,
    onClick: () -> Unit,
    filterCount: Int = 0,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(width = LibraryToolbarActionSize, height = LibraryCategoryBarHeight),
    ) {
        BadgedBox(
            badge = { if (filterCount > 0) Badge { Text(filterCount.toString()) } },
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                EchoIcon(
                    LibrarySortIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val LibrarySortIcon = ImageVector.Builder(
    name = "LibrarySort",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6f, 5.5f)
        lineTo(6f, 18.5f)
        moveTo(2.5f, 15f)
        lineTo(6f, 18.5f)
        lineTo(9.5f, 15f)
        moveTo(12.5f, 6f)
        lineTo(21.5f, 6f)
        moveTo(12.5f, 11.5f)
        lineTo(19f, 11.5f)
        moveTo(12.5f, 17f)
        lineTo(16.5f, 17f)
    }
}.build()
