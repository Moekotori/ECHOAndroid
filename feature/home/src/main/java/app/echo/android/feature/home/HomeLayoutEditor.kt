package app.echo.android.feature.home

import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetActionBar
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.EchoSheetOrderRow
import app.echo.android.design.echoItemMotion
import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoHomeLayout
import app.echo.android.model.settings.EchoHomeSection

private val HomeLayoutDraftSaver = listSaver<EchoHomeLayout, String>(
    save = { layout -> layout.normalized().order.map { section ->
        if (section in layout.hidden) "-${section.id}" else section.id
    } },
    restore = { saved -> EchoHomeLayout(
        order = saved.mapNotNull { EchoHomeSection.fromId(it.removePrefix("-")) },
        hidden = saved.filter { it.startsWith("-") }
            .mapNotNull { EchoHomeSection.fromId(it.removePrefix("-")) }.toSet(),
    ).normalized() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeLayoutEditor(
    layout: EchoHomeLayout,
    onDismiss: () -> Unit,
    onApply: (EchoHomeLayout) -> Unit,
) {
    // The sheet owns a single editing session; cancel never modifies persisted settings.
    var draft by rememberSaveable(stateSaver = HomeLayoutDraftSaver) { mutableStateOf(layout.normalized()) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    EchoModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Offset-dependent top padding would resize the sheet and move its expanded anchor.
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        },
    ) {
        // Keep the anchor stable while the lazy list scrolls or sections are reordered.
        Column(Modifier.fillMaxWidth().height(maxHeight)) {
            EchoSheetHeader(
                title = stringResource(R.string.home_layout_title),
                subtitle = stringResource(R.string.home_layout_detail),
            )
            LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(draft.order, key = { _, section -> section.id }) { index, section ->
                    val label = stringResource(section.titleResource())
                    EchoSheetOrderRow(
                        position = index + 1,
                        label = label,
                        visible = section !in draft.hidden,
                        canMoveUp = index > 0,
                        canMoveDown = index < draft.order.lastIndex,
                        moveUpDescription = stringResource(R.string.home_layout_move_up, label),
                        moveDownDescription = stringResource(R.string.home_layout_move_down, label),
                        onMoveUp = { draft = draft.move(section, -1) },
                        onMoveDown = { draft = draft.move(section, 1) },
                        onVisibleChange = { draft = draft.withVisibility(section, it) },
                        modifier = echoItemMotion(),
                    )
                }
            }
            EchoSheetActionBar(
                applyLabel = stringResource(R.string.home_layout_apply),
                onApply = { onApply(draft); onDismiss() },
                cancelLabel = stringResource(R.string.home_layout_cancel),
                onCancel = onDismiss,
                resetLabel = stringResource(R.string.home_layout_reset),
                onReset = { draft = EchoHomeLayout() },
            )
        }
    }
}

internal fun EchoHomeSection.titleResource(): Int = when (this) {
    EchoHomeSection.Resume -> R.string.home_resume
    EchoHomeSection.Recent -> R.string.feature_home_recent_activity_581ef8
    EchoHomeSection.DailyAlbum -> R.string.home_daily_album
    EchoHomeSection.Recommended -> R.string.feature_home_recommended_for_you_8335d9
    EchoHomeSection.Favorites -> R.string.feature_home_albums_you_like_95a2b9
    EchoHomeSection.Rediscover -> R.string.home_rediscover
    EchoHomeSection.Artists -> R.string.feature_home_artist_ranking_80100b
    EchoHomeSection.ListeningSummary -> R.string.home_listening_journal
    EchoHomeSection.Overview -> R.string.home_layout_overview
}

@Composable
internal fun HomeLayoutHiddenNotice(onRestore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(stringResource(R.string.home_layout_hidden), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRestore) { Text(stringResource(R.string.home_layout_reset)) }
    }
}
