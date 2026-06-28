package app.echo.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent

@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<SearchResult>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (SearchResult) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(homePanelColor(0.98f))
            .statusBarsPadding()
    ) {
        SearchTopBar(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onBack = onBack,
            focusRequester = focusRequester,
        )

        Spacer(Modifier.height(8.dp))

        SearchResultsList(
            searchResults = searchResults,
            onResultClick = onSearchResultClick,
        )
    }
}

@Composable
private fun SearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = homeBodyColor(),
            )
        }

        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text("搜索音乐、专辑、艺术家", color = homeBodyColor().copy(alpha = 0.4f))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = homeBodyColor().copy(alpha = 0.5f),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "清空",
                            tint = homeBodyColor().copy(alpha = 0.5f),
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = homePanelColor(0.94f),
                unfocusedContainerColor = homePanelColor(0.94f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = homeBodyColor(),
            ),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
        )
    }
}

@Composable
private fun SearchResultsList(
    searchResults: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
) {
    if (searchResults.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "输入关键词开始搜索",
                color = homeBodyColor().copy(alpha = 0.3f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        val trackResults = searchResults.filter { it.type == SearchResultType.Track }
        val albumResults = searchResults.filter { it.type == SearchResultType.Album }
        val artistResults = searchResults.filter { it.type == SearchResultType.Artist }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            if (trackResults.isNotEmpty()) {
                item {
                    Text(
                        text = "歌曲",
                        color = homeBodyColor().copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                items(trackResults) { result ->
                    SearchResultItemFull(result, onResultClick)
                }
            }

            if (albumResults.isNotEmpty()) {
                item {
                    if (trackResults.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        text = "专辑",
                        color = homeBodyColor().copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                items(albumResults) { result ->
                    SearchResultItemFull(result, onResultClick)
                }
            }

            if (artistResults.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "艺术家",
                        color = homeBodyColor().copy(alpha = 0.4f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                items(artistResults) { result ->
                    SearchResultItemFull(result, onResultClick)
                }
            }
        }
    }
}

@Composable
private fun SearchResultItemFull(result: SearchResult, onClick: (SearchResult) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(result) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (result.artworkUri.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (result.type == SearchResultType.Artist) CircleShape else RoundedCornerShape(8.dp))
                    .background(homeBodyColor().copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (result.type) {
                        SearchResultType.Track -> Icons.Rounded.MusicNote
                        SearchResultType.Album -> Icons.Rounded.Album
                        SearchResultType.Artist -> Icons.Rounded.Person
                    },
                    contentDescription = null,
                    tint = homeBodyColor().copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            ArtworkTile(
                artworkUri = result.artworkUri,
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (result.type == SearchResultType.Artist) CircleShape else RoundedCornerShape(8.dp)),
                accent = EchoAccent,
                showSignal = false,
                cornerRadius = if (result.type == SearchResultType.Artist) 24.dp else 8.dp,
                elevation = 0.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = homeBodyColor(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = result.subtitle,
                    color = homeBodyColor().copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
