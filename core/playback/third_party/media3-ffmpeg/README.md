# Media3 FFmpeg audio bridge

Unmodified audio Java sources and `ffmpeg_jni.cc` from AndroidX Media **1.10.1**:
https://github.com/androidx/media/tree/1.10.1/libraries/decoder_ffmpeg

The upstream Apache-2.0 license is in `LICENSE`. Only the four audio Java classes
are included; the experimental video renderer is not included. ECHO's CMake,
Gradle wiring and FFmpeg build script are maintained separately. `VERSION` must
match the version catalog; review and refresh these sources on Media3 upgrades.

## Build

Normal Gradle native builds run `scripts/build-ffmpeg.py` automatically. Requirements:
macOS or Linux, Python 3.9+, Make, NDK 27.2.12479018 and CMake 3.22.1. Windows builds
use WSL with a Linux JDK/SDK/NDK. The first build downloads the pinned FFmpeg 6.1.6
release from ffmpeg.org and verifies SHA-256 before extracting it. Later builds
reuse the native outputs until the script or NDK changes. No prebuilt third-party
AAR or silently optional backend is used; native build failures fail the build.

Build outputs stay in `core/playback/build/ffmpeg/`; CMake staging also stays under
`build/`. ABIs match USB audio: armeabi-v7a, arm64-v8a and x86_64, API 26 minimum,
with 16 KB ELF segment alignment for the JNI shared library.

FFmpeg is built audio-only, without networking, demuxers, encoders, programs,
auto-detected external libraries, GPL, version3 or nonfree options. Decoder list
and source checksum are in the build script. Original source, configuration,
objects and logs remain under `build/ffmpeg` for inspection and relinking.

## Playback policy and boundaries

`EchoRenderersFactory` enables extensions in ON mode: the platform renderer is
preferred and FFmpeg is selected when the platform renderer cannot support the
format. Both use ECHO's existing AudioSink, EQ and system/USB output provider.
`setEnableDecoderFallback(true)` permits alternative MediaCodec decoders on
initialization failure; it does **not** implement a retry through FFmpeg after a
platform decoder fails mid-track.

The software backend includes AAC, MP3, AC-3/E-AC-3, TrueHD, DTS, Vorbis, Opus,
AMR, FLAC, ALAC and PCM A-law/mu-law decoders. A file still requires a compatible
Media3 extractor. In particular APE, DSF/DFF, native DSD and DoP are not added by
this change, even though scanners may already recognize some of their suffixes.

The upstream renderer negotiates float or 16-bit PCM with the sink. This change
does not enable float output globally or change EQ processing. It does not claim
bit-perfect, lossless high-bit-depth output, or automatic recovery from runtime
decoder failures. Those require separate signal-path work and device validation.

## Distribution

FFmpeg is a separate LGPL-2.1-or-later dependency in this build configuration.
Its license and the bridge license are packaged as app assets. For binary
distribution retain the exact FFmpeg source, build instructions and the
relinkable objects needed for this statically linked JNI library, and fulfill
the applicable LGPL source/relinking requirements. Shipping license text alone
does not replace those requirements. Do not enable GPL/nonfree build options
without separately reviewing the distribution implications.

## Focused verification

`./gradlew checkModules :core:playback:testDebugUnitTest :core:playback:assembleDebug --no-configuration-cache`

For packaging changes also assemble the app and check that `libffmpegJNI.so` is
present for all three ABIs. Native loading and decoding must be checked on an
Android runtime; JVM unit tests cannot establish that. Instrumentation tests
are not added to default CI.
