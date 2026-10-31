package app.echo.android

import android.content.Context
import app.echo.android.data.AutoEqHeadphoneCorrectionRepository
import app.echo.android.model.playback.HeadphoneCorrectionCatalog
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
import kotlinx.coroutines.delay

/** Owns request replacement and selection; the ViewModel only wires application settings. */
internal class OpraSearchController(
    private val scope: CoroutineScope,
    private val repository: OpraHeadphoneCorrectionRepository,
    private val context: Context,
) {
    private val mutableState = MutableStateFlow(OpraHeadphoneCorrectionState())
    val state = mutableState.asStateFlow()
    private val autoEqRepository = AutoEqHeadphoneCorrectionRepository(context)
    private var searchJob: Job? = null
    private var generation = 0L
    private var parentQuery = ""

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
                results = if (keepBrandResults) current.results else emptyList(),
                selectedEqId = if (keepBrandResults) current.selectedEqId else null,
                previewCurve = if (keepBrandResults) current.previewCurve else emptyList(),
                message = if (keepBrandResults) current.message else null,
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
        val returningToCatalog = brandId == null && state.value.selectedBrandId != null
        if (brandId != null && !brandId.startsWith("catalog:") && state.value.selectedBrandId == null) {
            parentQuery = state.value.query
        }
        searchJob?.cancel()
        generation++
        val catalog = when (brandId) {
            "catalog:autoeq" -> HeadphoneCorrectionCatalog.AutoEq
            "catalog:opra" -> HeadphoneCorrectionCatalog.Opra
            else -> state.value.catalog
        }
        val sourceChanged = catalog != state.value.catalog
        if (sourceChanged) parentQuery = ""
        mutableState.update {
            it.copy(
                catalog = catalog,
                brands = if (sourceChanged) emptyList() else it.brands,
                status = if (sourceChanged) app.echo.android.model.playback.OpraDatabaseStatus() else it.status,
                selectedBrandId = brandId?.takeUnless { id -> id.startsWith("catalog:") },
                query = if (returningToCatalog) parentQuery else "",
                loading = false,
                results = emptyList(),
                selectedEqId = null,
                previewCurve = emptyList(),
                message = null,
            )
        }
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
            val result = if (catalog == HeadphoneCorrectionCatalog.AutoEq)
                autoEqRepository.search(query, brandId)
            else repository.search(query, refresh, vendorId = brandId)
            if (request != generation) return@launch
            result.onSuccess { found ->
                val selected = found.products.asSequence().flatMap { it.presets.asSequence() }
                    .firstOrNull { it.eqId == state.value.selectedEqId }
                mutableState.update { it.copy(loading = false, results = found.products, status = found.status, brands = found.brands,
                    selectedEqId = selected?.eqId,
                    previewCurve = selected?.let { preset -> EchoEqualizerEngine.responseCurve(preset.bands, preset.preampDb) }.orEmpty(),
                    message = when {
                        found.status.source == "cache-fallback" -> context.getString(R.string.opra_cache_fallback)
                        query.isBlank() && brandId == null -> null
                        found.products.isEmpty() -> context.getString(R.string.opra_no_match)
                        else -> null
                    }) }
            }.onFailure { error ->
                val message = context.getString(if (catalog == HeadphoneCorrectionCatalog.AutoEq) R.string.autoeq_load_failed else R.string.opra_load_failed)
                mutableState.update { state -> state.copy(loading = false, message = message) }
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
