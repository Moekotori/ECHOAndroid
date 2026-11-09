package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.echoPageBackgroundColor

internal val LibraryToolbarActionSize = 48.dp

@Composable
internal fun LibraryBrowserFrame(
    query: String,
    onQueryChange: (String) -> Unit,
    sourcePicker: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    navigationActions: @Composable RowScope.() -> Unit = {},
    sortAction: @Composable () -> Unit = {},
    searchEnabled: Boolean = true,
    searchPlaceholder: String = stringResource(R.string.feature_library_search_songs_artists_albums_14dc2c),
    content: @Composable ColumnScope.() -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searching = searchEnabled && (searchExpanded || query.isNotEmpty())
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchExpanded, searchEnabled) {
        if (!searchEnabled) {
            if (searchExpanded) {
                keyboard?.hide()
                searchExpanded = false
            }
        } else if (searchExpanded) {
            searchFocus.requestFocus()
        }
    }

    Surface(Modifier.fillMaxSize(), color = echoPageBackgroundColor()) {
        Box(Modifier.statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = LibraryToolbarActionSize).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searching) {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onQueryChange("")
                            searchExpanded = false
                        }) {
                            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack,
                                stringResource(R.string.feature_library_close_search_50a720))
                        }
                        LibraryInlineSearch(
                            query = query,
                            onQueryChange = onQueryChange,
                            placeholder = searchPlaceholder,
                            modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        )
                    } else {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.feature_library_library_848e9b), Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(6.dp))
                            sourcePicker()
                        }
                        navigationActions()
                        if (searchEnabled) {
                            IconButton(onClick = { searchExpanded = true }) {
                                EchoIcon(Icons.Rounded.Search, stringResource(R.string.feature_library_search_library_80ef90),
                                    Modifier.size(22.dp))
                            }
                        }
                        actions()
                    }
                    sortAction()
                }
                content()
            }
        }
    }
}

internal val LibraryCategoryBarHeight = 42.dp

@Composable
internal fun LibraryCategoryBar(tabs: @Composable () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) { tabs() }
        Box(Modifier.width(LibraryToolbarActionSize), contentAlignment = Alignment.Center) { actions() }
    }
}

@Composable
private fun LibraryInlineSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyMedium
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        textStyle = textStyle.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { innerTextField ->
            Row(
                Modifier.heightIn(min = LibraryToolbarActionSize)
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, end = if (query.isEmpty()) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoIcon(Icons.Rounded.Search, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            color = scheme.onSurfaceVariant,
                            style = textStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.library_clear_search), Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
internal fun LibraryCollectionEmpty(title: String, detail: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(Icons.Rounded.LibraryMusic, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
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
