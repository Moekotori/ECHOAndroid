package app.echo.android.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.model.library.EchoSavedMoment

@Composable
fun MomentsScreen(query: String, moments: LazyPagingItems<EchoSavedMoment>, onQuery: (String) -> Unit,
    onPlay: (EchoSavedMoment) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    var deleting by remember { mutableStateOf<EchoSavedMoment?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.moments_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text(stringResource(R.string.search_back)) }
        }
        OutlinedTextField(query, onQuery, singleLine = true, label = { Text(stringResource(R.string.moments_search)) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (moments.loadState.refresh is androidx.paging.LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (moments.loadState.refresh is androidx.paging.LoadState.Error) item { Text(stringResource(R.string.search_partial)); TextButton(onClick = moments::retry) { Text(stringResource(R.string.search_retry)) } }
            if (moments.itemCount == 0 && moments.loadState.refresh is androidx.paging.LoadState.NotLoading) item { Text(stringResource(R.string.moments_empty)) }
            items(moments.itemCount, key = { moments.peek(it)?.bookmark?.id ?: "placeholder:$it" }) { index -> moments[index]?.let { moment ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(enabled = moment.track.uri.isNotBlank()) { onPlay(moment) }.padding(vertical = 8.dp)) {
                        Text(moment.bookmark.label, style = MaterialTheme.typography.titleMedium)
                        Text("${moment.track.title} · ${moment.track.artist}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val ms = moment.bookmark.positionMs
                        Text("%d:%02d.%03d".format(ms / 60000, ms / 1000 % 60, ms % 1000), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { deleting = moment }) { Text(stringResource(R.string.moments_delete)) }
                }
                HorizontalDivider()
            } }
        }
    }
    deleting?.let { moment -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.moments_delete)) },
        text = { Text(moment.bookmark.label) }, confirmButton = { TextButton(onClick = { onDelete(moment.bookmark.id); deleting = null }) { Text(stringResource(R.string.moments_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.search_back)) } }) }
}
