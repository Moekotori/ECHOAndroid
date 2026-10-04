package app.echo.android

import app.echo.android.data.EchoLibrarySelectedSource
import app.echo.android.model.connect.EchoRemoteConnectionState
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkLocalFallbackTest {
    @Test
    fun lossOfConnectionRestoresPhoneControlsAndLocalLibrary() = runBlocking {
        for (lostState in listOf(
            EchoRemoteConnectionState.Disconnected,
            EchoRemoteConnectionState.Reconnecting,
            EchoRemoteConnectionState.Error,
        )) {
            val state = MutableStateFlow(EchoRemoteConnectionState.Connected)
            val source = MutableStateFlow(EchoLibrarySelectedSource.PcEcho)
            var remoteMode = true
            var writes = 0
            val observer = launch {
                observeEchoLinkLocalFallback(state, source, { remoteMode = false }) {
                    writes++
                    source.value = EchoLibrarySelectedSource.Local
                }
            }
            // Let the combine collector and its upstream collectors settle without timed waits.
            repeat(10) { yield() }
            assertTrue(remoteMode)
            assertEquals(0, writes)
            state.value = lostState
            repeat(10) { yield() }
            assertFalse(remoteMode)
            assertEquals(EchoLibrarySelectedSource.Local, source.value)
            assertEquals(1, writes)
            state.value = EchoRemoteConnectionState.Connected
            repeat(10) { yield() }
            assertFalse(remoteMode)
            assertEquals(EchoLibrarySelectedSource.Local, source.value)
            observer.cancelAndJoin()
        }
    }

    @Test
    fun disconnectedSessionPreservesOtherSourcesAndRejectsStalePcSelection() = runBlocking {
        val state = MutableStateFlow(EchoRemoteConnectionState.Disconnected)
        val source = MutableStateFlow(EchoLibrarySelectedSource.Cloud)
        var remoteMode = true
        var writes = 0
        val observer = launch {
            observeEchoLinkLocalFallback(state, source, { remoteMode = false }) {
                writes++
                source.value = EchoLibrarySelectedSource.Local
            }
        }
        repeat(10) { yield() }
        assertFalse(remoteMode)
        assertEquals(EchoLibrarySelectedSource.Cloud, source.value)
        assertEquals(0, writes)
        source.value = EchoLibrarySelectedSource.PcEcho
        repeat(10) { yield() }
        assertEquals(EchoLibrarySelectedSource.Local, source.value)
        assertEquals(1, writes)
        observer.cancelAndJoin()
    }
}
