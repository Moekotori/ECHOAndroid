package app.echo.android.playback

import androidx.media3.common.PlaybackException
import app.echo.android.model.playback.EchoAudioErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackErrorMapperTest {
    @Test
    fun classifiesHttp403AsAuthenticationFailure() {
        val error = classifyPlaybackError(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            httpStatusCode = 403,
        )

        assertEquals(EchoAudioErrorKind.AuthenticationFailed, error.kind)
        assertTrue(error.recoverable)
        assertTrue(error.message.contains("HTTP 403"))
    }

    @Test
    fun classifiesHttp404AsMissingFile() {
        val error = classifyPlaybackError(
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            httpStatusCode = 404,
        )

        assertEquals(EchoAudioErrorKind.FileMissing, error.kind)
        assertFalse(error.recoverable)
    }

    @Test
    fun classifiesNetworkFailureAndRedactsSensitiveDetails() {
        val error = classifyPlaybackError(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            rawMessage = "Failed https://moe:secret@example.test/song.flac?token=abc&u=moe&p=secret Authorization: Bearer abc",
        )

        assertEquals(EchoAudioErrorKind.NetworkFailure, error.kind)
        assertTrue(error.recoverable)
        assertFalse(error.message.contains("moe:secret"))
        assertFalse(error.message.contains("token=abc"))
        assertFalse(error.message.contains("p=secret"))
        assertFalse(error.message.contains("Bearer abc"))
        assertTrue(error.message.contains("<redacted>"))
    }

    @Test
    fun classifiesUnsupportedAndDecodeFailuresSeparately() {
        val unsupported = classifyPlaybackError(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        val decode = classifyPlaybackError(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
        )

        assertEquals(EchoAudioErrorKind.UnsupportedFormat, unsupported.kind)
        assertEquals(EchoAudioErrorKind.DecodeFailure, decode.kind)
    }
}
