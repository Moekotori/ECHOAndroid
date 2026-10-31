package app.echo.android

import app.echo.android.model.playback.EchoVolumeSettings
import app.echo.android.data.EchoVolumeSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Test

class AppVolumeControllerTest {
    @Test fun backupRestoreCancelsPendingSaveAndLaterEditsStillPersist() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val disk = MutableStateFlow(EchoVolumeSnapshot(EchoVolumeSettings(levelDb = -10f)))
            var saves = 0
            val controller = AppVolumeController(scope, disk, MutableStateFlow("speaker"), { value, version ->
                saves++
                assertEquals(disk.value.restoreVersion, version)
                disk.value = EchoVolumeSnapshot(value, version)
            }, {}, { throw it })
            yield()
            controller.adjust(-1)
            val restored = EchoVolumeSettings(levelDb = -30f)
            disk.value = EchoVolumeSnapshot(restored, 1L)
            yield()
            assertEquals(restored, controller.state.value)
            delay(250)
            assertEquals(0, saves)
            assertEquals(restored, disk.value.volume)
            controller.adjust(-1)
            delay(250)
            assertEquals(1, saves)
            assertEquals(restored.adjusted(-1), disk.value.volume)
        } finally { scope.cancel() }
    }

    @Test fun backupRestoreAlsoWinsAgainstASaveAlreadyInFlight() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val disk = MutableStateFlow(EchoVolumeSnapshot(EchoVolumeSettings(levelDb = -10f)))
            val saveStarted = CompletableDeferred<Unit>()
            val finishSave = CompletableDeferred<Unit>()
            val saveFinished = CompletableDeferred<Unit>()
            var rejected = false
            val controller = AppVolumeController(scope, disk, MutableStateFlow("speaker"), { value, version ->
                saveStarted.complete(Unit)
                finishSave.await()
                if (disk.value.restoreVersion == version) disk.value = EchoVolumeSnapshot(value, version)
                else rejected = true
                saveFinished.complete(Unit)
            }, {}, { throw it })
            yield()
            controller.adjust(-1)
            withTimeout(2000) { saveStarted.await() }
            val restored = EchoVolumeSnapshot(EchoVolumeSettings(levelDb = -30f), 1L)
            disk.value = restored
            yield()
            assertEquals(restored.volume, controller.state.value)
            finishSave.complete(Unit)
            withTimeout(2000) { saveFinished.await() }
            assertTrue(rejected)
            assertEquals(restored, disk.value)
        } finally { scope.cancel() }
    }

    @Test fun toggleKeepsTheAudibleLevelAndDisabledModeUsesOneSharedVolume() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val remembered = EchoVolumeSettings(-18f, 6f, rememberPerDevice = true).forDevice("speaker")
                .forDevice("usb:1:2").copy(levelDb = -22f, gainDb = -3f).rememberDevice()
            val disk = MutableStateFlow(remembered.copy(rememberPerDevice = false).normalized)
            val routes = MutableStateFlow<String?>("speaker")
            val controller = AppVolumeController(scope, disk.map { EchoVolumeSnapshot(it) }, routes, { value, _ -> disk.value = value }, {}, { throw it })
            yield()
            assertFalse(controller.state.value.rememberPerDevice)
            assertEquals(-22f, controller.state.value.levelDb, 0f)
            controller.adjust(-1)
            routes.value = "bluetooth:address:aa"
            yield()
            assertNull(controller.state.value.deviceKey)
            assertEquals(-22.5f, controller.state.value.levelDb, 0f)
            assertEquals(-3f, controller.state.value.gainDb, 0f)
            assertEquals(remembered.deviceLevels, controller.state.value.deviceLevels)
            controller.set(controller.state.value.copy(rememberPerDevice = true))
            assertEquals(-22.5f, controller.state.value.levelDb, 0f)
            assertEquals("bluetooth:address:aa", controller.state.value.deviceKey)
            routes.value = "speaker"
            yield()
            assertEquals(-18f, controller.state.value.levelDb, 0f)
            assertEquals(6f, controller.state.value.gainDb, 0f)
            controller.set(controller.state.value.copy(rememberPerDevice = false))
            assertNull(controller.state.value.deviceKey)
            routes.value = "usb:1:2"
            yield()
            assertEquals(-18f, controller.state.value.levelDb, 0f)
            assertEquals(6f, controller.state.value.gainDb, 0f)
            delay(250)
            assertFalse(disk.value.rememberPerDevice)
            assertEquals(controller.state.value, disk.value)
            controller.set(controller.state.value.copy(rememberPerDevice = true))
            assertEquals(-18f, controller.state.value.levelDb, 0f)
            assertEquals(6f, controller.state.value.gainDb, 0f)
            assertEquals("usb:1:2", controller.state.value.deviceKey)
        } finally { scope.cancel() }
    }

    @Test fun repeatedKeysUseTheConfiguredStepAndCoalescePersistence() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val disk = MutableStateFlow(EchoVolumeSettings(levelDb = -10f, gainDb = 3f, stepDb = 0.1f, muted = true))
            val routes = MutableStateFlow<String?>("speaker")
            var saves = 0
            val controller = AppVolumeController(scope, disk.map { EchoVolumeSnapshot(it) }, routes,
                { value, _ -> saves++; disk.value = value }, {}, { throw it })
            yield()
            repeat(75) { assertTrue(controller.adjust(-1)) }
            assertEquals(-17.5f, controller.state.value.levelDb, 0f)
            assertEquals(3f, controller.state.value.gainDb, 0f)
            assertTrue(controller.state.value.muted)
            assertTrue(controller.adjust(1))
            assertFalse(controller.state.value.muted)
            assertEquals(-17.4f, controller.state.value.levelDb, 0.00001f)
            assertEquals(0, saves)
            delay(250)
            assertEquals(1, saves)
            assertEquals(controller.state.value, disk.value)
        } finally { scope.cancel() }
    }

    @Test fun routeSwitchDuringPendingSaveRetainsBothDevicesAndRejectsStaleSnapshots() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            val disk = MutableStateFlow(EchoVolumeSettings(levelDb = -18f, gainDb = 6f, stepDb = 1f, rememberPerDevice = true))
            val routes = MutableStateFlow<String?>("bluetooth:address:aa")
            val controller = AppVolumeController(scope, disk.map { EchoVolumeSnapshot(it) }, routes, { value, _ -> disk.value = value }, {}, { throw it })
            yield()
            repeat(10) { controller.adjust(-1) }
            val oldPage = controller.state.value
            routes.value = "usb:1:2"
            yield()
            assertEquals(6f, controller.state.value.gainDb, 0f)
            controller.set(controller.state.value.copy(levelDb = -12f, gainDb = -3f))
            disk.value = oldPage
            yield()
            assertEquals("usb:1:2", controller.state.value.deviceKey)
            controller.set(oldPage.copy(gainDb = 12f))
            assertEquals(-3f, controller.state.value.gainDb, 0f)
            routes.value = "bluetooth:address:aa"
            yield()
            assertEquals(-28f, controller.state.value.levelDb, 0f)
            assertEquals(6f, controller.state.value.gainDb, 0f)
            routes.value = "usb:1:2"
            yield()
            assertEquals(-12f, controller.state.value.levelDb, 0f)
            assertEquals(-3f, controller.state.value.gainDb, 0f)
            delay(250)
            assertEquals(controller.state.value, disk.value)
            assertEquals(-28f, disk.value.forDevice("bluetooth:address:aa").levelDb, 0f)
        } finally { scope.cancel() }
    }
}
