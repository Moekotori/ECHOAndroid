package app.echo.android.feature.home

import app.echo.android.design.EchoSwitch
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.home_layout_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_layout_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(draft.order, key = { _, section -> section.id }) { index, section ->
                    val label = stringResource(section.titleResource())
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f).padding(end = 4.dp),
                            style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { draft = draft.move(section, -1) }, enabled = index > 0) {
                            EchoIcon(Icons.Rounded.KeyboardArrowUp,
                                stringResource(R.string.home_layout_move_up, label))
                        }
                        IconButton(onClick = { draft = draft.move(section, 1) }, enabled = index < draft.order.lastIndex) {
                            EchoIcon(Icons.Rounded.KeyboardArrowDown,
                                stringResource(R.string.home_layout_move_down, label))
                        }
                        EchoSwitch(checked = section !in draft.hidden,
                            onCheckedChange = { draft = draft.withVisibility(section, it) },
                            modifier = Modifier.semantics { contentDescription = label })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft = EchoHomeLayout() }) {
                    Text(stringResource(R.string.home_layout_reset))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_layout_cancel)) }
                TextButton(onClick = { onApply(draft); onDismiss() }) {
                    Text(stringResource(R.string.home_layout_apply))
                }
            }
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
