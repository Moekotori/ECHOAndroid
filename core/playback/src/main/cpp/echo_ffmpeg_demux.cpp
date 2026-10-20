#include <jni.h>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <new>

extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/error.h>
#include <libavutil/mem.h>
}

namespace {
constexpr int kIoBytes = 32 * 1024;
constexpr int kMaxPacketBytes = 8 * 1024 * 1024;
constexpr int kMaxIndexEntries = 16 * 1024;
constexpr int64_t kTimeUnset = INT64_MIN + 1;

struct Demuxer {
    JNIEnv* env = nullptr; // Set on every call; never shared across threads.
    jobject source = nullptr;
    jmethodID read = nullptr;
    jmethodID seek = nullptr;
    AVFormatContext* format = nullptr;
    AVIOContext* io = nullptr;
    AVPacket* packet = nullptr;
    int stream = -1;
    int64_t packet_time_us = 0;
    int64_t next_time_us = 0;
};

void fail(JNIEnv* env, const char* operation, int code) {
    if (env->ExceptionCheck()) return;
    char detail[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(code, detail, sizeof(detail));
    char message[256];
    snprintf(message, sizeof(message), "%s: %s", operation, detail);
    jclass type = env->FindClass("java/io/IOException");
    if (type) env->ThrowNew(type, message);
}

int read_bytes(void* opaque, uint8_t* buffer, int size) {
    auto* d = static_cast<Demuxer*>(opaque);
    JNIEnv* env = d->env;
    if (env->ExceptionCheck()) return AVERROR(EIO);
    // Some demuxer-specific indexes bypass max_index_size. Check at each
    // bounded AVIO refill as well, before another batch can be appended.
    if (d->format) {
        for (unsigned i = 0; i < d->format->nb_streams; ++i) {
            if (avformat_index_get_entries_count(d->format->streams[i]) > kMaxIndexEntries) {
                fail(env, "Audio seek index exceeds its budget", AVERROR_INVALIDDATA);
                return AVERROR_INVALIDDATA;
            }
        }
    }
    jobject target = env->NewDirectByteBuffer(buffer, size);
    if (!target) return AVERROR(ENOMEM);
    int result = env->CallIntMethod(d->source, d->read, target, size);
    env->DeleteLocalRef(target);
    if (env->ExceptionCheck()) return AVERROR(EIO);
    if (result < 0) return AVERROR_EOF;
    if (result == 0 || result > size) return AVERROR(EIO);
    return result;
}

int64_t seek_bytes(void* opaque, int64_t offset, int whence) {
    auto* d = static_cast<Demuxer*>(opaque);
    if (d->env->ExceptionCheck()) return AVERROR(EIO);
    int64_t result = d->env->CallLongMethod(d->source, d->seek, static_cast<jlong>(offset), whence);
    return d->env->ExceptionCheck() || result < 0 ? AVERROR(EIO) : result;
}

void destroy(JNIEnv* env, Demuxer* d) {
    if (!d) return;
    av_packet_free(&d->packet);
    avformat_close_input(&d->format);
    if (d->io) {
        av_freep(&d->io->buffer);
        avio_context_free(&d->io);
    }
    if (d->source) env->DeleteGlobalRef(d->source);
    delete d;
}

Demuxer* state(JNIEnv* env, jlong handle) {
    auto* d = reinterpret_cast<Demuxer*>(handle);
    d->env = env;
    return d;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeOpen(
    JNIEnv* env, jobject, jobject source, jstring container, jboolean seekable) {
    const char* name = env->GetStringUTFChars(container, nullptr);
    if (!name) return 0;
    const bool allowed = !strcmp(name, "aiff") || !strcmp(name, "dts") ||
                         !strcmp(name, "wv") || !strcmp(name, "asf") ||
                         !strcmp(name, "tta") || !strcmp(name, "tak") || !strcmp(name, "w64");
    const AVInputFormat* input_format = allowed ? av_find_input_format(name) : nullptr;
    env->ReleaseStringUTFChars(container, name);
    if (!input_format) { fail(env, "Unsupported audio container", AVERROR(EINVAL)); return 0; }
    auto* d = new (std::nothrow) Demuxer();
    if (!d) { fail(env, "Allocate audio demuxer", AVERROR(ENOMEM)); return 0; }
    d->env = env;
    d->source = env->NewGlobalRef(source);
    jclass type = env->GetObjectClass(source);
    d->read = env->GetMethodID(type, "read", "(Ljava/nio/ByteBuffer;I)I");
    d->seek = env->GetMethodID(type, "seek", "(JI)J");
    env->DeleteLocalRef(type);
    if (env->ExceptionCheck() || !d->source) { destroy(env, d); return 0; }
    d->format = avformat_alloc_context();
    d->packet = av_packet_alloc();
    auto* buffer = static_cast<uint8_t*>(av_malloc(kIoBytes));
    if (buffer) d->io = avio_alloc_context(buffer, kIoBytes, 0, d, read_bytes, nullptr, seek_bytes);
    if (!d->io) av_free(buffer);
    if (!d->format || !d->packet || !d->io) {
        fail(env, "Allocate audio input", AVERROR(ENOMEM)); destroy(env, d); return 0;
    }
    d->io->seekable = seekable ? AVIO_SEEKABLE_NORMAL : 0;
    d->format->pb = d->io;
    d->format->flags |= AVFMT_FLAG_CUSTOM_IO;
    d->format->probesize = 256 * 1024;
    d->format->max_analyze_duration = AV_TIME_BASE;
    d->format->max_index_size = 256 * 1024;
    d->format->max_streams = 16;
    int result = avformat_open_input(&d->format, nullptr, input_format, nullptr);
    if (result >= 0 && !env->ExceptionCheck()) result = avformat_find_stream_info(d->format, nullptr);
    if (result >= 0 && !env->ExceptionCheck()) {
        d->stream = av_find_best_stream(d->format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
        result = d->stream;
    }
    if (result < 0 || env->ExceptionCheck()) {
        fail(env, "Read audio header", result); destroy(env, d); return 0;
    }
    AVCodecParameters* p = d->format->streams[d->stream]->codecpar;
    if (p->sample_rate <= 0 || p->sample_rate > 768000 || p->ch_layout.nb_channels < 1 ||
        p->ch_layout.nb_channels > 8 || p->extradata_size > 1024 * 1024) {
        fail(env, "Invalid audio format", AVERROR_INVALIDDATA); destroy(env, d); return 0;
    }
    // The app has no DRM license/decryption integration for ASF.
    if (av_stream_get_side_data(d->format->streams[d->stream], AV_PKT_DATA_ENCRYPTION_INIT_INFO, nullptr)) {
        fail(env, "Encrypted audio is unsupported", AVERROR(ENOSYS)); destroy(env, d); return 0;
    }
    return reinterpret_cast<jlong>(d);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeCodec(JNIEnv* env, jobject, jlong handle) {
    auto* d = state(env, handle);
    return env->NewStringUTF(avcodec_get_name(d->format->streams[d->stream]->codecpar->codec_id));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeInfo(JNIEnv* env, jobject, jlong handle) {
    auto* d = state(env, handle);
    AVStream* s = d->format->streams[d->stream];
    auto* p = s->codecpar;
    int64_t duration = s->duration != AV_NOPTS_VALUE ? av_rescale_q(s->duration, s->time_base, AV_TIME_BASE_Q)
        : (d->format->duration != AV_NOPTS_VALUE ? d->format->duration : kTimeUnset);
    jlong values[] = {p->sample_rate, p->ch_layout.nb_channels, p->bits_per_coded_sample,
        p->block_align, p->codec_tag, p->bit_rate, duration};
    jlongArray result = env->NewLongArray(7);
    if (result) env->SetLongArrayRegion(result, 0, 7, values);
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeExtraData(JNIEnv* env, jobject, jlong handle) {
    auto* d = state(env, handle);
    auto* p = d->format->streams[d->stream]->codecpar;
    jbyteArray result = env->NewByteArray(p->extradata_size);
    if (result && p->extradata_size > 0) {
        env->SetByteArrayRegion(result, 0, p->extradata_size, reinterpret_cast<jbyte*>(p->extradata));
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeReadPacket(JNIEnv* env, jobject, jlong handle) {
    auto* d = state(env, handle);
    av_packet_unref(d->packet);
    for (int skipped = 0; skipped < 1024; ++skipped) {
        int result = av_read_frame(d->format, d->packet);
        if (env->ExceptionCheck()) return -1;
        if (result == AVERROR_EOF) { d->packet_time_us = d->next_time_us; return -1; }
        if (result < 0) { fail(env, "Read audio packet", result); return -1; }
        if (d->packet->size > kMaxPacketBytes) {
            fail(env, "Audio packet is too large", AVERROR_INVALIDDATA); return -1;
        }
        if (d->packet->stream_index != d->stream || d->packet->size == 0) {
            av_packet_unref(d->packet); continue;
        }
        if (av_packet_get_side_data(d->packet, AV_PKT_DATA_ENCRYPTION_INFO, nullptr)) {
            fail(env, "Encrypted audio is unsupported", AVERROR(ENOSYS)); return -1;
        }
        AVStream* s = d->format->streams[d->stream];
        int64_t pts = d->packet->pts != AV_NOPTS_VALUE ? d->packet->pts : d->packet->dts;
        const int64_t start = s->start_time == AV_NOPTS_VALUE ? 0 : s->start_time;
        d->packet_time_us = pts == AV_NOPTS_VALUE ? d->next_time_us : av_rescale_q(pts - start, s->time_base, AV_TIME_BASE_Q);
        d->next_time_us = d->packet_time_us + av_rescale_q(d->packet->duration, s->time_base, AV_TIME_BASE_Q);
        return d->packet->size;
    }
    fail(env, "Too many non-audio packets", AVERROR_INVALIDDATA);
    return -1;
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativePacketData(JNIEnv* env, jobject, jlong handle) {
    auto* d = state(env, handle);
    return env->NewDirectByteBuffer(d->packet->data, d->packet->size);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativePacketTimeUs(JNIEnv* env, jobject, jlong handle) {
    return state(env, handle)->packet_time_us;
}

extern "C" JNIEXPORT void JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeSeek(JNIEnv* env, jobject, jlong handle, jlong time_us) {
    auto* d = state(env, handle);
    AVStream* s = d->format->streams[d->stream];
    int64_t target = av_rescale_q(time_us, AV_TIME_BASE_Q, s->time_base);
    if (s->start_time != AV_NOPTS_VALUE) target += s->start_time;
    int result = av_seek_frame(d->format, d->stream, target, AVSEEK_FLAG_BACKWARD);
    if (result < 0 || env->ExceptionCheck()) { fail(env, "Seek audio", result); return; }
    av_packet_unref(d->packet);
    d->next_time_us = time_us;
}

extern "C" JNIEXPORT void JNICALL
Java_app_echo_android_playback_EchoFfmpegDemuxer_nativeClose(JNIEnv* env, jobject, jlong handle) {
    destroy(env, reinterpret_cast<Demuxer*>(handle));
}
