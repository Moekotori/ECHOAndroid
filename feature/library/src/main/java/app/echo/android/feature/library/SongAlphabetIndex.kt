package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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

    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = LibraryBottomControlsPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (letter in 'A'..'Z') {
            val label = stringResource(R.string.library_jump_to_letter, letter.toString())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(onClickLabel = label) {
                        jumpJob?.cancel()
                        jumpJob = scope.launch {
                            val index = findIndex(letter)
                            if (itemCount > 0) {
                                listState.scrollToItem(index.coerceIn(0, itemCount - 1))
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 1.em),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
