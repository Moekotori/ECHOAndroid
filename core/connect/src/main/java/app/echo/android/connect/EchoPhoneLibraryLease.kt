package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** A temporary status failure must not revoke the phone's active media URLs. */
fun shouldKeepEchoPhoneLibrarySharing(
    endpoint: EchoRemoteEndpoint?,
    target: EchoRemoteEndpoint,
    state: EchoRemoteConnectionState,
): Boolean = EchoLinkRequestPolicy.isSameEndpoint(endpoint, target) &&
    (state == EchoRemoteConnectionState.Connected || state == EchoRemoteConnectionState.Reconnecting)

private fun transientLeaseFailure(error: IOException): Boolean {
    val code = (error as? EchoLinkHttpException)?.statusCode
    return error !is EchoLinkHttpException || code == 408 || code == 429 ||
        (code != null && code in 500..599 && code != 501)
}

/** Keep explicit sharing intent alive across failed renewal rounds, using one cancellable job. */
suspend fun maintainEchoPhoneLibraryLease(
    register: suspend () -> Unit,
    onRenewed: () -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
) {
    var established = false
    while (currentCoroutineContext().isActive) {
        try {
            renewEchoPhoneLibraryLease(register, wait)
            established = true
            onRenewed()
            wait(60_000L)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (!established || !transientLeaseFailure(error)) throw error
            wait(30_000L)
        }
    }
}

/** Bound recovery so renewal cannot wait past the PC's three-minute lease. */
suspend fun renewEchoPhoneLibraryLease(
    register: suspend () -> Unit,
    waitBeforeRetry: suspend (Long) -> Unit = { delay(it) },
) {
    val renewed = withTimeoutOrNull(80_000L) {
        repeat(4) { attempt ->
            try {
                register()
                return@withTimeoutOrNull true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                if (!transientLeaseFailure(error) || attempt >= 3) throw error
                waitBeforeRetry(5_000L shl attempt)
            }
        }
        false
    }
    if (renewed != true) throw SocketTimeoutException("Phone library renewal timed out")
}
