package app.echo.android.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackError
import java.io.FileNotFoundException
import java.io.IOException

@UnstableApi
fun PlaybackException.toEchoPlaybackError(): EchoPlaybackError {
    val httpStatusCode = causeChain()
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
    val rawMessage = listOfNotNull(message, cause?.message)
        .joinToString(" / ")
        .takeIf { it.isNotBlank() }

    return classifyPlaybackError(
        errorCode = errorCode,
        httpStatusCode = httpStatusCode,
        rawMessage = rawMessage,
        cause = cause,
    )
}

internal fun classifyPlaybackError(
    errorCode: Int,
    httpStatusCode: Int? = null,
    rawMessage: String? = null,
    cause: Throwable? = null,
): EchoPlaybackError {
    val causeChain = cause?.causeChain().orEmpty()
    return when {
        httpStatusCode == 401 || httpStatusCode == 403 ||
            errorCode == PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.AuthenticationFailed,
                message = "Remote source rejected authentication${httpStatusSuffix(httpStatusCode)}.",
                recoverable = true,
            )

        httpStatusCode == 404 || httpStatusCode == 410 ||
            errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            causeChain.any { it is FileNotFoundException } ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.FileMissing,
                message = "Audio file was not found${httpStatusSuffix(httpStatusCode)}.",
                recoverable = false,
            )

        httpStatusCode != null && httpStatusCode >= 500 ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.NetworkFailure,
                message = "Remote source returned a temporary server error${httpStatusSuffix(httpStatusCode)}.",
                recoverable = true,
            )

        errorCode in networkErrorCodes ||
            (errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED && causeChain.any { it is IOException }) ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.NetworkFailure,
                message = playbackMessage("Network playback failed", errorCode, rawMessage),
                recoverable = true,
            )

        errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
            errorCode == PlaybackException.ERROR_CODE_PERMISSION_DENIED ||
            causeChain.any { it is SecurityException } ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.PermissionDenied,
                message = playbackMessage("Playback permission was denied", errorCode, rawMessage),
                recoverable = true,
            )

        errorCode in unsupportedFormatErrorCodes ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.UnsupportedFormat,
                message = playbackMessage("Audio format is not supported", errorCode, rawMessage),
                recoverable = false,
            )

        errorCode in decodeErrorCodes ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.DecodeFailure,
                message = playbackMessage("Audio decoder failed", errorCode, rawMessage),
                recoverable = true,
            )

        else ->
            EchoPlaybackError(
                kind = EchoAudioErrorKind.Unknown,
                message = playbackMessage("Playback failed", errorCode, rawMessage),
                recoverable = true,
            )
    }
}

internal fun sanitizePlaybackDiagnosticText(raw: String?): String =
    raw.orEmpty()
        .replace(authorizationHeaderRegex, "\$1<redacted>")
        .replace(urlUserInfoRegex, "\$1<redacted>@")
        .replace(sensitiveQueryParameterRegex, "\$1<redacted>")

private fun playbackMessage(prefix: String, errorCode: Int, rawMessage: String?): String {
    val codeName = PlaybackException.getErrorCodeName(errorCode)
    val detail = sanitizePlaybackDiagnosticText(rawMessage)
        .takeIf { it.isNotBlank() && !it.equals(prefix, ignoreCase = true) }
    return if (detail == null) {
        "$prefix ($codeName)."
    } else {
        "$prefix ($codeName; $detail)."
    }
}

private fun httpStatusSuffix(statusCode: Int?): String =
    statusCode?.let { " (HTTP $it)" }.orEmpty()

private fun Throwable.causeChain(): Sequence<Throwable> = sequence {
    var current: Throwable? = this@causeChain
    while (current != null) {
        yield(current)
        current = current.cause
    }
}

private val networkErrorCodes = setOf(
    PlaybackException.ERROR_CODE_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
)

private val unsupportedFormatErrorCodes = setOf(
    PlaybackException.ERROR_CODE_NOT_SUPPORTED,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)

private val decodeErrorCodes = setOf(
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
)

private val authorizationHeaderRegex = Regex(
    pattern = """(?i)(\bauthorization\s*[:=]\s*)(?:basic|bearer)\s+[^\s,;]+""",
)
private val urlUserInfoRegex = Regex(
    pattern = """([a-zA-Z][a-zA-Z0-9+.-]*://)([^/@\s]+)@""",
)
private val sensitiveQueryParameterRegex = Regex(
    pattern = """(?i)([?&](?:access_token|api_key|apikey|auth|authorization|key|p|pass|passwd|password|pwd|s|salt|t|token|u|user|username)=)[^&#\s]+""",
)
