package app.echo.android

import app.echo.android.data.OpraSearchResult
import app.echo.android.model.playback.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OpraSearchControllerTest {
    @Test fun switchingCatalogKeepsQueryAndReturningFromBrandRestoresRootResults() = runBlocking {
        val calls = mutableListOf<String>()
        val controller = OpraSearchController(this, { catalog, query, _, brand ->
            calls += "$catalog/$query/$brand"
            Result.success(result("$catalog/$query/$brand"))
        }, Int::toString)
        controller.setQuery("HD650")
        controller.search(false)
        yield()
        val root = controller.state.value.results
        controller.browse("measurement")
        yield()
        val callCount = calls.size
        controller.setQuery("oratory")
        assertEquals(callCount, calls.size) // Scoped typing filters the loaded catalog locally.
        controller.search(true)
        yield()
        assertEquals("AutoEq//measurement", calls.last())
        controller.browse(null)
        assertEquals("HD650", controller.state.value.query)
        assertSame(root, controller.state.value.results)
        yield()
        controller.browse("catalog:opra")
        assertEquals("HD650", controller.state.value.query)
        yield()
        assertEquals(HeadphoneCorrectionCatalog.Opra, controller.state.value.catalog)
        assertEquals("Opra/HD650/null", calls.last())
    }

    @Test fun failedRefreshKeepsUsableResultsButFailedNewQueryDoesNotExposeStaleMatches() = runBlocking {
        var fail = false
        val controller = OpraSearchController(this, { _, query, _, _ ->
            if (fail) Result.failure(IllegalStateException("fixture")) else Result.success(result(query))
        }, Int::toString)
        controller.setQuery("HD650")
        controller.search(false)
        yield()
        val results = controller.state.value.results
        fail = true
        controller.search(true)
        assertSame(results, controller.state.value.results)
        yield()
        assertSame(results, controller.state.value.results)
        assertFalse(controller.state.value.loading)
        assertNotNull(controller.state.value.message)
        controller.setQuery("AirPods")
        assertSame(results, controller.state.value.results) // No empty flash during the replacement.
        controller.search(false)
        yield()
        assertTrue(controller.state.value.results.isEmpty())
    }

    @Test fun lateReplacedRequestCannotOverwriteNewSearch() = runBlocking {
        val finishOld = CompletableDeferred<Unit>()
        val controller = OpraSearchController(this, { _, query, _, _ ->
            if (query == "old") withContext(NonCancellable) { finishOld.await() }
            Result.success(result(query))
        }, Int::toString)
        controller.setQuery("old")
        controller.search(false)
        yield()
        controller.setQuery("new")
        controller.search(false)
        yield()
        finishOld.complete(Unit)
        yield()
        yield()
        assertEquals("new", controller.state.value.query)
        assertEquals("new", controller.state.value.results.single().productName)
        assertFalse(controller.state.value.loading)
    }

    private fun result(name: String) = OpraSearchResult(
        listOf(OpraHeadphoneCorrectionProduct(name, name, "Fixture", null, emptyList())),
        OpraDatabaseStatus(source = "fixture", productCount = 1, eqCount = 1),
        listOf(OpraHeadphoneBrand("measurement", "Measurement", 1)),
    )
}
