package app.echo.android

import app.echo.android.data.OpraHeadphoneCorrectionRepository
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.playback.OpraHeadphoneCorrectionState
import app.echo.android.playback.EchoEqualizerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns request replacement and selection; the ViewModel only wires application settings. */
internal class OpraSearchController(
    private val scope: CoroutineScope,
    private val repository: OpraHeadphoneCorrectionRepository,
    private val text: (String, String, String) -> String,
) {
    private val mutableState = MutableStateFlow(OpraHeadphoneCorrectionState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0L

    fun setQuery(query: String) {
        searchJob?.cancel()
        generation++
        mutableState.update { it.copy(query = query, loading = false, results = emptyList(),
            selectedEqId = null, previewCurve = emptyList(), message = null) }
    }

    fun search(refresh: Boolean) {
        val query = state.value.query.trim()
        if (query.isBlank() && !refresh) {
            message(text("Enter a headphone model", "请输入耳机型号", "ヘッドホンの機種を入力してください"))
            return
        }
        searchJob?.cancel()
        val request = ++generation
        mutableState.update { it.copy(loading = true, message = null) }
        searchJob = scope.launch {
            val result = repository.search(query, refresh)
            if (request != generation) return@launch
            result.onSuccess { found ->
                val selected = found.products.firstOrNull()?.presets?.firstOrNull()
                mutableState.update { it.copy(loading = false, results = found.products, status = found.status,
                    selectedEqId = selected?.eqId,
                    previewCurve = selected?.let { preset -> EchoEqualizerEngine.responseCurve(preset.bands, preset.preampDb) }.orEmpty(),
                    message = when {
                        found.status.source == "cache-fallback" -> text("Update failed; using the saved database", "更新失败，正在使用已缓存的数据库", "更新できないため保存済みデータを使用しています")
                        query.isBlank() -> text("Database ready. Enter a model to search.", "数据库已就绪，请输入型号搜索", "データを準備しました。機種を検索してください")
                        found.products.isEmpty() -> text("No matching model", "未找到匹配型号", "一致する機種がありません")
                        else -> null
                    }) }
            }.onFailure { error ->
                val message = text(
                    "Could not load OPRA. Check the connection and retry.",
                    "无法读取 OPRA，请检查网络后重试",
                    "OPRA を読み込めません。接続を確認して再試行してください",
                )
                mutableState.update { state -> state.copy(loading = false, message = message) }
                EchoErrorLog.record(EchoErrorSource.Network, message, throwable = error)
            }
        }
    }

    fun select(eqId: String) {
        val preset = state.value.results.asSequence().flatMap { it.presets.asSequence() }.firstOrNull { it.eqId == eqId } ?: return
        mutableState.update { it.copy(selectedEqId = eqId,
            previewCurve = EchoEqualizerEngine.responseCurve(preset.bands, preset.preampDb), message = null) }
    }

    fun message(value: String) { mutableState.update { it.copy(message = value) } }
}
