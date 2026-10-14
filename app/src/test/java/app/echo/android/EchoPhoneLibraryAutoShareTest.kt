package app.echo.android

import app.echo.android.data.EchoAppSettings
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteStatus
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoPhoneLibraryAutoShareTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "192.168.1.20", 26789, "paired-token")
    private val connected = EchoRemoteStatus(EchoRemoteConnectionState.Connected, endpoint)
    private suspend fun settle() { repeat(12) { yield() } }

    @Test
    fun defaultStartsAfterConnectIgnoresTicksAndKeepsRunningWhenHidden() = runBlocking {
        val enabled = MutableStateFlow(EchoAppSettings().echoLinkSharePhoneLibraryEnabled)
        val connection = MutableStateFlow(EchoRemoteStatus())
        val visible = MutableStateFlow(true)
        var starts = 0
        var stops = 0
        val observer = launch {
            observeEchoPhoneLibraryAutoShare(enabled, connection, visible, { starts++ }, { stops++ })
        }
        try {
            settle()
            assertEquals(0, starts)
            connection.value = connected
            settle()
            assertEquals(1, starts)
            connection.value = connected.copy(playback = connected.playback.copy(volume = 0.7f))
            settle()
            assertEquals(1, starts)
            visible.value = false
            settle()
            assertEquals(0, stops)
            connection.value = connected.copy(endpoint = endpoint.copy(host = "192.168.1.21"))
            settle()
            assertEquals(1, starts)
            visible.value = true
            settle()
            assertEquals(2, starts)
        } finally { observer.cancelAndJoin() }
    }

    @Test
    fun restoredBackgroundConnectionWaitsForVisibleAppAndRemembersDisabledPreference() = runBlocking {
        val enabled = MutableStateFlow(true)
        val connection = MutableStateFlow(connected)
        val visible = MutableStateFlow(false)
        var starts = 0
        var stops = 0
        val observer = launch {
            observeEchoPhoneLibraryAutoShare(enabled, connection, visible, { starts++ }, { stops++ })
        }
        try {
            settle()
            assertEquals(0, starts)
            visible.value = true
            settle()
            assertEquals(1, starts)
            enabled.value = false
            settle()
            assertEquals(1, stops)
            connection.value = EchoRemoteStatus()
            settle()
            connection.value = connected
            visible.value = false
            settle()
            visible.value = true
            settle()
            assertEquals(1, starts)
            assertEquals(1, stops)
            enabled.value = true
            settle()
            assertEquals(2, starts)
        } finally { observer.cancelAndJoin() }
    }
}
