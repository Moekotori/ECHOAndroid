package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import app.echo.android.design.LocalEchoLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun SongAlphabetIndex(
    itemCount: Int,
    listState: LazyListState,
    findIndex: suspend (Char) -> Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var jumpJob by remember { mutableStateOf<Job?>(null) }
    val colors = MaterialTheme.colorScheme
    val layout = LocalEchoLayout.current
    val bottomPadding = if (layout.heightDp < 600f) {
        minOf(LibraryBottomControlsPadding, (layout.heightDp * 0.25f).dp)
    } else LibraryBottomControlsPadding
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = bottomPadding),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(0.92f).align(Alignment.CenterEnd)) {
            // Keep every letter readable in short windows; scroll rather than overlap labels.
            val letterHeight = maxOf(maxHeight / 26, 20.dp * fontScale)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                for (letter in 'A'..'Z') {
                    val label = stringResource(R.string.library_jump_to_letter, letter.toString())
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(letterHeight)
                            .clickable(onClickLabel = label) {
                                jumpJob?.cancel()
                                jumpJob = scope.launch {
                                    val index = findIndex(letter)
                                    if (itemCount > 0) {
                                        listState.scrollToItem(index.coerceIn(0, itemCount - 1))
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = letter.toString(),
                            modifier = Modifier.padding(end = 4.dp),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 1.em),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
