#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define LOG_TAG "EchoUsbIsoc"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define URB_COUNT 8
#define USBDEVFS_URB_TYPE_ISO 0

struct UrbSlot {
    struct usbdevfs_urb* urb;
    uint8_t* buffer;
    int in_flight;
};

struct Writer {
    int fd;
    uint8_t endpoint;
    int packets_per_urb;
    int max_packet;
    int sample_rate;
    int channels;
    int bytes_per_sample;
    int pps;
    int remainder;
    struct UrbSlot slots[URB_COUNT];
    int64_t submitted_frames;
    int64_t completed_frames;
};

static int bytes_per_frame(const Writer* writer) {
    return writer->channels * writer->bytes_per_sample;
}

static int next_packet_samples(Writer* writer) {
    const int samples = (writer->remainder + writer->sample_rate) / writer->pps;
    writer->remainder = (writer->remainder + writer->sample_rate) % writer->pps;
    return samples;
}

static size_t urb_alloc_size(int packets_per_urb) {
    return sizeof(struct usbdevfs_urb) +
           sizeof(struct usbdevfs_iso_packet_desc) * (size_t)packets_per_urb;
}

static void free_writer(Writer* writer) {
    if (writer == nullptr) return;
    for (int i = 0; i < URB_COUNT; ++i) {
        if (writer->slots[i].in_flight && writer->slots[i].urb != nullptr) {
            ioctl(writer->fd, USBDEVFS_DISCARDURB, writer->slots[i].urb);
        }
        free(writer->slots[i].urb);
        free(writer->slots[i].buffer);
    }
    free(writer);
}

static void reap(Writer* writer) {
    while (true) {
        struct usbdevfs_urb* urb = nullptr;
        int rc = ioctl(writer->fd, USBDEVFS_REAPURBNDELAY, &urb);
        if (rc < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) return;
            LOGE("REAPURB failed: %s", strerror(errno));
            return;
        }
        for (int i = 0; i < URB_COUNT; ++i) {
            if (writer->slots[i].urb == urb) {
                writer->slots[i].in_flight = 0;
                const int frame_bytes = bytes_per_frame(writer);
                if (frame_bytes > 0) {
                    writer->completed_frames += urb->actual_length / frame_bytes;
                }
                break;
            }
        }
    }
}

static int submit_filled_urb(Writer* writer, int slot_index, int used_bytes, int packet_count) {
    UrbSlot* slot = &writer->slots[slot_index];
    struct usbdevfs_urb* urb = slot->urb;
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = writer->endpoint;
    urb->status = 0;
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    urb->buffer = slot->buffer;
    urb->buffer_length = used_bytes;
    urb->actual_length = 0;
    urb->start_frame = 0;
    urb->number_of_packets = packet_count;
    urb->error_count = 0;
    urb->signr = 0;
    urb->usercontext = writer;
    int rc = ioctl(writer->fd, USBDEVFS_SUBMITURB, urb);
    if (rc < 0) {
        LOGE("SUBMITURB failed: %s", strerror(errno));
        return -1;
    }
    slot->in_flight = 1;
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeCreate(
        JNIEnv*,
        jclass,
        jint fd,
        jint endpoint_address,
        jint max_packet_size,
        jint sample_rate_hz,
        jint channel_count,
        jint bytes_per_sample,
        jint packets_per_second) {
    if (fd < 0 || sample_rate_hz <= 0 || channel_count <= 0 || bytes_per_sample <= 0) {
        return 0;
    }
    auto* writer = (Writer*)calloc(1, sizeof(Writer));
    if (writer == nullptr) return 0;
    writer->fd = fd;
    writer->endpoint = (uint8_t)endpoint_address;
    writer->max_packet = max_packet_size > 0 ? max_packet_size : 192;
    writer->sample_rate = sample_rate_hz;
    writer->channels = channel_count;
    writer->bytes_per_sample = bytes_per_sample;
    writer->pps = packets_per_second >= 8000 ? 8000 : 1000;
    writer->packets_per_urb = writer->pps >= 8000 ? 8 : 1;
    const int buffer_bytes = writer->max_packet * writer->packets_per_urb;
    for (int i = 0; i < URB_COUNT; ++i) {
        writer->slots[i].urb = (struct usbdevfs_urb*)calloc(1, urb_alloc_size(writer->packets_per_urb));
        writer->slots[i].buffer = (uint8_t*)malloc((size_t)buffer_bytes);
        if (writer->slots[i].urb == nullptr || writer->slots[i].buffer == nullptr) {
            free_writer(writer);
            return 0;
        }
    }
    LOGI("iso writer fd=%d ep=0x%x rate=%d ch=%d bps=%d pps=%d",
         fd, endpoint_address, sample_rate_hz, channel_count, bytes_per_sample, writer->pps);
    return reinterpret_cast<jlong>(writer);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeWrite(
        JNIEnv* env,
        jclass,
        jlong handle,
        jbyteArray packed,
        jint offset,
        jint length) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr || packed == nullptr || length <= 0) return 0;
    reap(writer);
    jbyte* src = env->GetByteArrayElements(packed, nullptr);
    if (src == nullptr) return -1;
    int consumed = 0;
    const int frame_bytes = bytes_per_frame(writer);
    while (consumed < length) {
        int free_slot = -1;
        for (int i = 0; i < URB_COUNT; ++i) {
            if (!writer->slots[i].in_flight) {
                free_slot = i;
                break;
            }
        }
        if (free_slot < 0) break;
        const int urb_start_consumed = consumed;
        const int urb_start_remainder = writer->remainder;
        int used = 0;
        int packets = 0;
        int frames_this_urb = 0;
        bool incomplete = false;
        for (int p = 0; p < writer->packets_per_urb; ++p) {
            const int samples = next_packet_samples(writer);
            const int packet_bytes = samples * frame_bytes;
            if (packet_bytes <= 0 || used + packet_bytes > writer->max_packet * writer->packets_per_urb) {
                incomplete = true;
                break;
            }
            if (consumed + packet_bytes > length) {
                incomplete = true;
                break;
            }
            memcpy(writer->slots[free_slot].buffer + used, src + offset + consumed, (size_t)packet_bytes);
            writer->slots[free_slot].urb->iso_frame_desc[p].length = (unsigned int)packet_bytes;
            writer->slots[free_slot].urb->iso_frame_desc[p].actual_length = 0;
            writer->slots[free_slot].urb->iso_frame_desc[p].status = 0;
            used += packet_bytes;
            consumed += packet_bytes;
            frames_this_urb += samples;
            packets += 1;
        }
        if (incomplete || packets != writer->packets_per_urb) {
            writer->remainder = urb_start_remainder;
            consumed = urb_start_consumed;
            break;
        }
        if (submit_filled_urb(writer, free_slot, used, packets) != 0) {
            writer->remainder = urb_start_remainder;
            consumed = urb_start_consumed;
            env->ReleaseByteArrayElements(packed, src, JNI_ABORT);
            return consumed > 0 ? consumed : -1;
        }
        writer->submitted_frames += frames_this_urb;
    }
    env->ReleaseByteArrayElements(packed, src, JNI_ABORT);
    return consumed;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeCompletedFrames(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr) return 0;
    reap(writer);
    return writer->completed_frames;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeQueuedFrames(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr) return 0;
    reap(writer);
    const int64_t queued = writer->submitted_frames - writer->completed_frames;
    return queued > 0 ? queued : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeClose(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr) return;
    reap(writer);
    free_writer(writer);
}
