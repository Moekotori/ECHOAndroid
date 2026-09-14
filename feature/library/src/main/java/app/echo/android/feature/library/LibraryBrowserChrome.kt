package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoContentMaxWidth

@Composable
internal fun LibraryBrowserFrame(
    query: String,
    onQueryChange: (String) -> Unit,
    sources: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    searchPlaceholder: String = stringResource(R.string.feature_library_search_songs_artists_albums_14dc2c),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.feature_library_library_848e9b), Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                    actions()
                }
                sources()
                LibraryInlineSearch(query, onQueryChange, Modifier.padding(vertical = 10.dp), searchPlaceholder)
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
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = query, onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
        shape = RoundedCornerShape(4.dp),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, Modifier.size(20.dp)) },
        trailingIcon = if (query.isNotEmpty()) { {
            IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.library_clear_search)) }
        } } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
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
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                content()
            }
        }
    }
}
