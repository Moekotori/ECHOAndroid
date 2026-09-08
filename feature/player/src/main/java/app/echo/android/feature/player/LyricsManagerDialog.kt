package app.echo.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.model.i18n.echoText
import app.echo.android.model.lyrics.EchoLyricsCandidate

@Composable
fun LyricsManagerDialog(
    trackTitle: String,
    candidates: List<EchoLyricsCandidate>,
    searching: Boolean,
    selectedId: String? = null,
    error: String?,
    onSearch: () -> Unit,
    onChoose: (String) -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewId by remember(trackTitle) { mutableStateOf<String?>(null) }
    var searched by remember(trackTitle) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(echoText("Lyrics · $trackTitle", "歌词 · $trackTitle", "歌詞 · $trackTitle")) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(echoText("Choose a result to preview. Selected lyrics are saved offline.",
                        "点击候选预览，选用后自动保存到本地。", "候補をプレビューし、選択するとオフライン保存されます。"))
                    TextButton(onClick = { searched = true; onSearch() }, enabled = !searching) {
                        Text(echoText("Search / refresh online", "在线搜索 / 重新获取", "オンライン検索 / 更新"))
                    }
                    TextButton(onClick = onImport) { Text(echoText("Import lyrics file", "导入歌词文件", "歌詞ファイルを読み込む")) }
                    TextButton(onClick = onRemove) { Text(echoText("Clear selection and use automatic lyrics", "解除选择 / 导入绑定，恢复自动歌词", "選択を解除して自動歌詞に戻す")) }
                    Row {
                        TextButton(onClick = { onAdjustOffset(-50L) }) { Text("−50 ms") }
                        TextButton(onClick = { onAdjustOffset(50L) }) { Text("+50 ms") }
                    }
                    Text(echoText("Long-press a lyric line to align it to the current position.",
                        "长按歌词行，可将“这句现在开始”对齐到播放位置。", "歌詞行を長押しすると現在の再生位置に合わせられます。"),
                        style = MaterialTheme.typography.bodySmall)
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (searched && !searching && candidates.isEmpty() && error == null) {
                        Text(echoText("No candidates available. Check the connection or import a file.",
                            "暂未找到候选，请检查网络或导入文件。", "候補がありません。接続を確認するかファイルを読み込んでください。"))
                    }
                }
                items(candidates, key = { it.id }) { candidate ->
                    Column(Modifier.fillMaxWidth().clickable { previewId = candidate.id }.padding(vertical = 8.dp)) {
                        Text("${candidate.title} · ${candidate.artist}", style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(candidate.lyrics.sourceLabel, candidate.album,
                            candidate.durationMs.takeIf { it > 0 }?.let { "${it / 1000}s" },
                            if (candidate.lyrics.lines.any { it.words.isNotEmpty() }) echoText("Word timed", "逐字", "単語同期") else null
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (previewId == candidate.id) {
                            candidate.lyrics.lines.filter { it.text.isNotBlank() }.take(6).forEach { line ->
                                Text(line.text, Modifier.padding(top = 6.dp))
                                line.translation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                line.romanization?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            TextButton(onClick = { onChoose(candidate.id) }) {
                                Text(if (candidate.id == selectedId) echoText("Saved and selected", "已保存并选用", "保存・選択済み")
                                    else echoText("Use and save offline", "选用并离线保存", "選択してオフライン保存"))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(echoText("Done", "完成", "完了")) } },
    )
}
