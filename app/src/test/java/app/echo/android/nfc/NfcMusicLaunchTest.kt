package app.echo.android.nfc

import app.echo.android.EchoLaunchActions
import app.echo.android.library.EchoLibraryShortcutRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NfcMusicLaunchTest {
    @Test fun slowCatalogLookupKeepsLaunchPendingUntilResolved() = runBlocking {
        val request = EchoLibraryShortcutRequest("echo.album.album-one", true)
        val result = CompletableDeferred<String>()
        EchoLaunchActions.requestLibraryShortcut(request)
        val lookup = async(start = CoroutineStart.UNDISPATCHED) {
            EchoLaunchActions.resolveLibraryShortcut(request) { result.await() }
        }
        // Clearing this key early cancels the Compose LaunchedEffect while catalog IO is suspended.
        assertEquals(request, EchoLaunchActions.libraryShortcut.value)
        result.complete("album ready")
        assertEquals("album ready", lookup.await())
        assertNull(EchoLaunchActions.libraryShortcut.value)
    }

    @Test fun cancelledOldLookupDoesNotConsumeNewCardRequest() = runBlocking {
        val old = EchoLibraryShortcutRequest("echo.album.album-one", true)
        val next = EchoLibraryShortcutRequest("echo.playlist.playlist-two", true)
        val result = CompletableDeferred<Unit>()
        EchoLaunchActions.requestLibraryShortcut(old)
        val lookup = async(start = CoroutineStart.UNDISPATCHED) {
            EchoLaunchActions.resolveLibraryShortcut(old) { result.await() }
        }
        EchoLaunchActions.requestLibraryShortcut(next)
        lookup.cancelAndJoin()
        assertEquals(next, EchoLaunchActions.libraryShortcut.value)
        assertEquals("playlist ready", EchoLaunchActions.resolveLibraryShortcut(next) { "playlist ready" })
        assertNull(EchoLaunchActions.libraryShortcut.value)
    }

    @Test fun recreationCanRetryCancelledLookupButLateOldResultCannotPlay() = runBlocking {
        val request = EchoLibraryShortcutRequest("echo.album.album-one", true)
        EchoLaunchActions.requestLibraryShortcut(request)
        val suspended = CompletableDeferred<Unit>()
        val interrupted = async(start = CoroutineStart.UNDISPATCHED) {
            EchoLaunchActions.resolveLibraryShortcut(request) { suspended.await() }
        }
        interrupted.cancelAndJoin()
        assertEquals(request, EchoLaunchActions.libraryShortcut.value)

        val result = CompletableDeferred<Unit>()
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            EchoLaunchActions.resolveLibraryShortcut(request) { result.await() }
        }
        val next = EchoLibraryShortcutRequest("echo.playlist.playlist-two", true)
        EchoLaunchActions.requestLibraryShortcut(next)
        result.complete(Unit)
        try { old.await(); fail("An old lookup must not trigger playback") }
        catch (_: CancellationException) { }
        assertEquals(next, EchoLaunchActions.libraryShortcut.value)
        EchoLaunchActions.resolveLibraryShortcut(next) { Unit }
    }
}
