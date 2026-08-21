package app.echo.android

import android.content.Intent
import android.net.Uri

object EchoIncomingAudio {
    const val IncomingIdPrefix = "incoming:"

    fun urisFromIntent(intent: Intent): List<Uri> =
        collect(
            action = intent.action,
            data = intent.data,
            extraStreams = extraStreamUris(intent),
            clipUris = clipUris(intent),
        )

    fun collect(
        action: String?,
        data: Uri?,
        extraStreams: List<Uri> = emptyList(),
        clipUris: List<Uri> = emptyList(),
    ): List<Uri> {
        if (!isIncomingAudioAction(action)) return emptyList()
        return (listOfNotNull(data) + extraStreams + clipUris)
            .distinct()
            .filter { uri -> uri.scheme.equals("content", ignoreCase = true) || uri.scheme.equals("file", ignoreCase = true) }
    }

    fun isIncomingAudioAction(action: String?): Boolean =
        action == Intent.ACTION_VIEW ||
            action == Intent.ACTION_SEND ||
            action == Intent.ACTION_SEND_MULTIPLE

    fun libraryTrackIdForMediaStoreUri(uri: Uri): String? =
        libraryTrackIdForMediaStoreUri(uri.toString())

    fun libraryTrackIdForMediaStoreUri(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        mediaPathId.find(value)?.groupValues?.getOrNull(1)?.let { return "mediastore:$it" }
        audioDocumentId.find(value)?.groupValues?.getOrNull(1)?.let { return "mediastore:$it" }
        return null
    }

    fun incomingTrackId(uri: Uri): String = IncomingIdPrefix + uri.toString()

    private fun extraStreamUris(intent: Intent): List<Uri> {
        val multiple = intent.parcelableUriList(Intent.EXTRA_STREAM)
        if (multiple.isNotEmpty()) return multiple
        return listOfNotNull(intent.parcelableUri(Intent.EXTRA_STREAM))
    }

    private fun clipUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return buildList {
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
    }

    private val mediaPathId = Regex("""/audio/media/(\d+)(?:\?.*)?$""")
    private val audioDocumentId = Regex("""(?:document|audio)(?::|%3A)(\d+)""", RegexOption.IGNORE_CASE)
}

private fun Intent.parcelableUri(key: String): Uri? =
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

private fun Intent.parcelableUriList(key: String): List<Uri> =
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(key).orEmpty()
    }
