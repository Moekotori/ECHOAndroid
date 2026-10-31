package app.echo.android.feature.settings

/** Paths are relative to the generated settings assets, sourced from canonical notices. */
internal data class OpenSourceNotice(
    val name: String,
    val license: String,
    val files: List<String>,
    val source: String? = null,
)

internal val echoProjectLicense = OpenSourceNotice(
    "ECHO Android", "AGPL-3.0", listOf("ECHO-AGPL-3.0.txt"),
    "https://github.com/moekotori/echoandroid",
)

internal val bundledOpenSourceNotices = listOf(
    OpenSourceNotice("FFmpeg", "LGPL-2.1-or-later",
        listOf("audio/FFmpeg-NOTICE.txt", "audio/FFmpeg-LGPL-2.1.txt"),
        "https://ffmpeg.org/releases/ffmpeg-6.1.6.tar.xz"),
    OpenSourceNotice("AndroidX Media3 FFmpeg", "Apache-2.0",
        listOf("audio/FFmpeg-NOTICE.txt", "audio/Media3-FFmpeg-Apache-2.0.txt"),
        "https://github.com/androidx/media/tree/1.10.1/libraries/decoder_ffmpeg"),
    OpenSourceNotice("AutoMix", "Apache-2.0",
        listOf("audio/automix-NOTICE.txt", "audio/automix-Apache-2.0.txt")),
    OpenSourceNotice("AutoEq", "MIT", listOf("autoeq/NOTICE.txt", "autoeq/LICENSE.txt"),
        "https://github.com/jaakkopasanen/AutoEq/tree/7ae0f56d53074872b028649617a22bbb4232feb7"),
    OpenSourceNotice("Pixelarticons", "MIT", listOf("fonts/Pixelarticons-LICENSE.txt")),
    *listOf("Outfit", "MaShanZheng", "CormorantGaramond", "Anton", "PixelifySans", "VT323")
        .map { OpenSourceNotice(it, "OFL-1.1", listOf("fonts/$it-OFL.txt")) }.toTypedArray(),
)
