# DefaultRenderersFactory discovers this renderer by name; JNI uses these class/method names.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { public <init>(...); }
-keepclasseswithmembernames,includedescriptorclasses class androidx.media3.decoder.ffmpeg.** {
    native <methods>;
}
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
    private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}
