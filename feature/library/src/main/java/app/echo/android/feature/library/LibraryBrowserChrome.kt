package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.echoPageBackgroundColor

@Composable
internal fun LibraryBrowserFrame(
    query: String,
    onQueryChange: (String) -> Unit,
    sources: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    searchPlaceholder: String = stringResource(R.string.feature_library_search_songs_artists_albums_14dc2c),
    content: @Composable ColumnScope.() -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searching = searchExpanded || query.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocus.requestFocus()
    }

    Surface(Modifier.fillMaxSize(), color = echoPageBackgroundColor()) {
        Box(Modifier.statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize().padding(start = 24.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onQueryChange("")
                            searchExpanded = false
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                                stringResource(R.string.feature_library_close_search_50a720))
                        }
                        LibraryInlineSearch(query, onQueryChange,
                            Modifier.weight(1f).focusRequester(searchFocus), searchPlaceholder)
                    } else {
                        Text(stringResource(R.string.feature_library_library_848e9b), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.feature_library_search_library_80ef90))
                        }
                        actions()
                    }
                }
                Box(Modifier.padding(end = 24.dp)) { sources() }
                content()
            }
        }
    }
}

@Composable
internal fun LibraryInlineSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.feature_library_search_songs_artists_albums_14dc2c),
) {
    app.echo.android.design.EchoSearchField(query, onQueryChange, placeholder,
        stringResource(R.string.library_clear_search), modifier)
}

@Composable
internal fun LibraryCollectionEmpty(title: String, detail: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (onAction != null && actionLabel != null) Button(onClick = onAction, shape = RoundedCornerShape(4.dp)) { Text(actionLabel) }
    }
}

@Composable
internal fun LibraryDetailFrame(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = echoPageBackgroundColor()) {
        Box(Modifier.statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                content()
            }
        }
    }
}
