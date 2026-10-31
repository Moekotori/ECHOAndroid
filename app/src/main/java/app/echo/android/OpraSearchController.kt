package app.echo.android

import android.content.Context
import app.echo.android.data.AutoEqHeadphoneCorrectionRepository
import app.echo.android.model.playback.HeadphoneCorrectionCatalog
import app.echo.android.data.OpraHeadphoneCorrectionRepository
import app.echo.android.data.OpraSearchResult
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
import kotlinx.coroutines.delay
import java.util.EnumMap

/** Owns request replacement and selection; the ViewModel only wires application settings. */
internal class OpraSearchController(
    private val scope: CoroutineScope,
    private val loadCatalog: suspend (HeadphoneCorrectionCatalog, String, Boolean, String?) -> Result<OpraSearchResult>,
    private val getString: (Int) -> String,
) {
    constructor(scope: CoroutineScope, repository: OpraHeadphoneCorrectionRepository, context: Context) : this(
        scope, catalogLoader(context.applicationContext, repository), context::getString,
    )

    private val mutableState = MutableStateFlow(OpraHeadphoneCorrectionState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0L
    // At most two root snapshots, each with the bounded global search results.
    private val roots = EnumMap<HeadphoneCorrectionCatalog, OpraHeadphoneCorrectionState>(HeadphoneCorrectionCatalog::class.java)

    fun setQuery(query: String) {
        if (query == state.value.query) return
        val keepBrandResults = state.value.selectedBrandId != null
        if (!keepBrandResults) {
            searchJob?.cancel()
            generation++
        }
        mutableState.update { current ->
            current.copy(
                query = query,
                loading = !keepBrandResults || current.loading,
                selectedEqId = null,
                previewCurve = emptyList(),
                message = null,
            )
        }
        if (!keepBrandResults) searchJob = scope.launch {
            delay(300)
            searchJob = null
            search(false)
        }
    }

    fun restoreQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        mutableState.update { current ->
            if (current.query.isNotBlank()) current else current.copy(query = trimmed)
        }
    }

    fun browse(brandId: String?) {
        val current = state.value
        searchJob?.cancel()
        generation++
        val catalog = when (brandId) {
            "catalog:autoeq" -> HeadphoneCorrectionCatalog.AutoEq
            "catalog:opra" -> HeadphoneCorrectionCatalog.Opra
            else -> current.catalog
        }
        val sourceChanged = catalog != current.catalog
        val returningToCatalog = brandId == null && current.selectedBrandId != null
        val base = when {
            sourceChanged -> (roots[catalog] ?: OpraHeadphoneCorrectionState(catalog = catalog)).let {
                it.copy(query = current.query, results = if (it.query == current.query) it.results else emptyList())
            }
            returningToCatalog -> roots[catalog] ?: current.copy(query = "", results = emptyList())
            else -> current.copy(query = "", results = emptyList())
        }
        mutableState.value = base.copy(
            selectedBrandId = brandId?.takeUnless { it.startsWith("catalog:") },
            loading = false, selectedEqId = null, previewCurve = emptyList(), message = null,
        )
        search(false)
    }

    fun search(refresh: Boolean) {
        val query = state.value.query.trim()
        val brandId = state.value.selectedBrandId
        val catalog = state.value.catalog
        searchJob?.cancel()
        val request = ++generation
        mutableState.update { it.copy(loading = true, message = null) }
        searchJob = scope.launch {
            // Scoped typing is local. Refresh must still load the whole brand/source,
            // otherwise clearing the query would leave only the previous subset.
            val result = loadCatalog(catalog, if (brandId == null) query else "", refresh, brandId)
            if (request != generation) return@launch
            result.onSuccess { found ->
                val selected = found.products.asSequence().flatMap { it.presets.asSequence() }
                    .firstOrNull { it.eqId == state.value.selectedEqId }
                mutableState.update { it.copy(loading = false, results = found.products, status = found.status, brands = found.brands,
                    selectedEqId = selected?.eqId,
                    previewCurve = selected?.let { preset -> EchoEqualizerEngine.responseCurve(preset.bands, preset.preampDb) }.orEmpty(),
                    message = if (found.status.source == "cache-fallback") getString(R.string.opra_cache_fallback) else null) }
                if (brandId == null) roots[catalog] = state.value
            }.onFailure { error ->
                val message = getString(if (catalog == HeadphoneCorrectionCatalog.AutoEq) R.string.autoeq_load_failed else R.string.opra_load_failed)
                mutableState.update { state -> state.copy(loading = false, message = message,
                    results = if (brandId == null && roots[catalog]?.query != state.query) emptyList() else state.results) }
                EchoErrorLog.record(if (catalog == HeadphoneCorrectionCatalog.AutoEq) EchoErrorSource.Other else EchoErrorSource.Network, message, throwable = error)
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

private fun catalogLoader(context: Context, opra: OpraHeadphoneCorrectionRepository):
    suspend (HeadphoneCorrectionCatalog, String, Boolean, String?) -> Result<OpraSearchResult> {
    val autoEq = AutoEqHeadphoneCorrectionRepository(context)
    return { catalog, query, refresh, brand ->
        if (catalog == HeadphoneCorrectionCatalog.AutoEq) autoEq.search(query, brand)
        else opra.search(query, refresh, vendorId = brand)
    }
}
