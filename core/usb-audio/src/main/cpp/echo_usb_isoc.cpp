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

#define URB_COUNT 16
#define FEEDBACK_URB_COUNT 4
#define KEEPALIVE_URB_COUNT 4
#define USBDEVFS_URB_TYPE_ISO 0
#define WRITE_FATAL -2

#ifndef USBDEVFS_GET_SPEED
#define USBDEVFS_GET_SPEED _IO('U', 31)
#endif

struct UrbSlot {
    struct usbdevfs_urb* urb;
    uint8_t* buffer;
    int in_flight;
    int frames;
    int is_feedback;
    int is_silence;
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
    int high_speed;
    int fatal;
    int64_t acc_q16;
    int64_t feedback_q16;
    int64_t nominal_q16;
    uint8_t feedback_ep;
    int feedback_max;
    struct UrbSlot slots[URB_COUNT];
    struct UrbSlot feedback_slots[FEEDBACK_URB_COUNT];
    int64_t submitted_frames;
    int64_t completed_frames;
    int64_t audio_completed_frames;
};

static int bytes_per_frame(const Writer* writer) {
    return writer->channels * writer->bytes_per_sample;
}

static int next_packet_samples(Writer* writer) {
    writer->acc_q16 += writer->feedback_q16;
    int samples = (int)(writer->acc_q16 >> 16);
    writer->acc_q16 &= 0xffff;
    return samples > 0 ? samples : 1;
}

static size_t urb_alloc_size(int packets_per_urb) {
    return sizeof(struct usbdevfs_urb) +
           sizeof(struct usbdevfs_iso_packet_desc) * (size_t)packets_per_urb;
}

static int is_fatal_errno(int err) {
    return err == ENODEV || err == ESHUTDOWN || err == ENOENT || err == EPERM;
}

static int is_fatal_urb_status(int status) {
    return status == -ENODEV || status == -ESHUTDOWN || status == -ENOENT || status == -EPERM;
}

static void apply_feedback(Writer* writer, const uint8_t* data, int length) {
    if (data == nullptr || length < 3) return;
    int64_t measured;
    if (writer->high_speed && length >= 4) {
        measured = (int64_t)data[0] |
                   ((int64_t)data[1] << 8) |
                   ((int64_t)data[2] << 16) |
                   ((int64_t)data[3] << 24);
    } else {
        measured = ((int64_t)data[0] |
                    ((int64_t)data[1] << 8) |
                    ((int64_t)data[2] << 16)) << 2;
    }
    if (measured <= 0) return;
    const int64_t slack = 1LL << 16;
    int64_t minv = writer->nominal_q16 - slack;
    if (minv < 1) minv = 1;
    int64_t maxv = writer->nominal_q16 + slack;
    if (measured < minv) measured = minv;
    if (measured > maxv) measured = maxv;
    writer->feedback_q16 = (writer->feedback_q16 * 3 + measured) / 4;
}

static int submit_urb(int fd, struct usbdevfs_urb* urb) {
    int rc = ioctl(fd, USBDEVFS_SUBMITURB, urb);
    if (rc < 0) {
        LOGE("SUBMITURB failed: %s", strerror(errno));
        return -1;
    }
    return 0;
}

static int submit_feedback(Writer* writer, int index) {
    if (writer->feedback_ep == 0 || writer->fatal) return -1;
    UrbSlot* slot = &writer->feedback_slots[index];
    struct usbdevfs_urb* urb = slot->urb;
    memset(urb, 0, urb_alloc_size(1));
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = writer->feedback_ep;
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    urb->buffer = slot->buffer;
    urb->buffer_length = writer->feedback_max;
    urb->number_of_packets = 1;
    urb->iso_frame_desc[0].length = (unsigned int)writer->feedback_max;
    if (submit_urb(writer->fd, urb) != 0) return -1;
    slot->in_flight = 1;
    slot->is_feedback = 1;
    return 0;
}

static void mark_completed(Writer* writer, UrbSlot* slot) {
    writer->completed_frames += slot->frames;
    if (!slot->is_silence) {
        writer->audio_completed_frames += slot->frames;
    }
    slot->in_flight = 0;
    slot->frames = 0;
    slot->is_silence = 0;
}

static void reap(Writer* writer) {
    while (true) {
        struct usbdevfs_urb* urb = nullptr;
        int rc = ioctl(writer->fd, USBDEVFS_REAPURBNDELAY, &urb);
        if (rc < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) return;
            if (is_fatal_errno(errno)) writer->fatal = 1;
            LOGE("REAPURB failed: %s", strerror(errno));
            return;
        }
        for (int i = 0; i < URB_COUNT; ++i) {
            if (writer->slots[i].urb == urb) {
                if (is_fatal_urb_status(urb->status)) writer->fatal = 1;
                mark_completed(writer, &writer->slots[i]);
                urb = nullptr;
                break;
            }
        }
        if (urb == nullptr) continue;
        for (int i = 0; i < FEEDBACK_URB_COUNT; ++i) {
            if (writer->feedback_slots[i].urb == urb) {
                writer->feedback_slots[i].in_flight = 0;
                if (is_fatal_urb_status(urb->status)) {
                    writer->fatal = 1;
                    break;
                }
                int actual = (int)urb->iso_frame_desc[0].actual_length;
                if (actual <= 0) actual = urb->actual_length;
                if (actual > 0 && urb->iso_frame_desc[0].status == 0) {
                    apply_feedback(writer, writer->feedback_slots[i].buffer, actual);
                }
                submit_feedback(writer, i);
                break;
            }
        }
    }
}

static void discard_inflight(Writer* writer) {
    for (int i = 0; i < URB_COUNT; ++i) {
        if (writer->slots[i].in_flight && writer->slots[i].urb != nullptr) {
            ioctl(writer->fd, USBDEVFS_DISCARDURB, writer->slots[i].urb);
        }
    }
    for (int i = 0; i < FEEDBACK_URB_COUNT; ++i) {
        if (writer->feedback_slots[i].in_flight && writer->feedback_slots[i].urb != nullptr) {
            ioctl(writer->fd, USBDEVFS_DISCARDURB, writer->feedback_slots[i].urb);
        }
    }
    reap(writer);
    for (int i = 0; i < URB_COUNT; ++i) {
        writer->slots[i].in_flight = 0;
        writer->slots[i].frames = 0;
        writer->slots[i].is_silence = 0;
    }
    for (int i = 0; i < FEEDBACK_URB_COUNT; ++i) {
        writer->feedback_slots[i].in_flight = 0;
    }
}

static void free_writer(Writer* writer) {
    if (writer == nullptr) return;
    discard_inflight(writer);
    for (int i = 0; i < URB_COUNT; ++i) {
        free(writer->slots[i].urb);
        free(writer->slots[i].buffer);
    }
    for (int i = 0; i < FEEDBACK_URB_COUNT; ++i) {
        free(writer->feedback_slots[i].urb);
        free(writer->feedback_slots[i].buffer);
    }
    free(writer);
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
    if (submit_urb(writer->fd, urb) != 0) {
        if (is_fatal_errno(errno)) writer->fatal = 1;
        return -1;
    }
    slot->in_flight = 1;
    return 0;
}

static int fill_and_submit_silence(Writer* writer, int slot_index) {
    int used = 0;
    int packets = 0;
    int frames_this_urb = 0;
    const int frame_bytes = bytes_per_frame(writer);
    int64_t saved_acc = writer->acc_q16;
    for (int p = 0; p < writer->packets_per_urb; ++p) {
        const int samples = next_packet_samples(writer);
        const int packet_bytes = samples * frame_bytes;
        if (packet_bytes <= 0 || packet_bytes > writer->max_packet ||
            used + packet_bytes > writer->max_packet * writer->packets_per_urb) {
            writer->acc_q16 = saved_acc;
            return -1;
        }
        memset(writer->slots[slot_index].buffer + used, 0, (size_t)packet_bytes);
        writer->slots[slot_index].urb->iso_frame_desc[p].length = (unsigned int)packet_bytes;
        writer->slots[slot_index].urb->iso_frame_desc[p].actual_length = 0;
        writer->slots[slot_index].urb->iso_frame_desc[p].status = 0;
        used += packet_bytes;
        frames_this_urb += samples;
        packets += 1;
        saved_acc = writer->acc_q16;
    }
    writer->slots[slot_index].frames = frames_this_urb;
    writer->slots[slot_index].is_silence = 1;
    if (submit_filled_urb(writer, slot_index, used, packets) != 0) {
        writer->slots[slot_index].frames = 0;
        writer->slots[slot_index].is_silence = 0;
        return -1;
    }
    writer->submitted_frames += frames_this_urb;
    return 0;
}

static int inflight_out_count(const Writer* writer) {
    int count = 0;
    for (int i = 0; i < URB_COUNT; ++i) {
        if (writer->slots[i].in_flight) count += 1;
    }
    return count;
}

static void fill_keepalive(Writer* writer) {
    if (writer->fatal) return;
    int needed = KEEPALIVE_URB_COUNT - inflight_out_count(writer);
    for (int i = 0; i < URB_COUNT && needed > 0; ++i) {
        if (writer->slots[i].in_flight) continue;
        if (fill_and_submit_silence(writer, i) == 0) {
            needed -= 1;
        }
    }
}

static void apply_bus_speed(Writer* writer, int requested_pps) {
    writer->pps = requested_pps >= 8000 ? 8000 : 1000;
    int speed = ioctl(writer->fd, USBDEVFS_GET_SPEED);
    if (speed >= 3) {
        writer->pps = 8000;
    } else if (speed == 1 || speed == 2) {
        writer->pps = 1000;
    }
    writer->high_speed = writer->pps >= 8000 ? 1 : 0;
    writer->packets_per_urb = writer->high_speed ? 8 : 1;
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
        jint packets_per_second,
        jint feedback_endpoint,
        jint feedback_max_packet) {
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
    apply_bus_speed(writer, packets_per_second);
    writer->nominal_q16 = ((int64_t)sample_rate_hz << 16) / writer->pps;
    writer->feedback_q16 = writer->nominal_q16;
    writer->feedback_ep = (uint8_t)feedback_endpoint;
    writer->feedback_max = feedback_max_packet > 0 ? feedback_max_packet : (writer->high_speed ? 4 : 3);
    const int buffer_bytes = writer->max_packet * writer->packets_per_urb;
    for (int i = 0; i < URB_COUNT; ++i) {
        writer->slots[i].urb = (struct usbdevfs_urb*)calloc(1, urb_alloc_size(writer->packets_per_urb));
        writer->slots[i].buffer = (uint8_t*)malloc((size_t)buffer_bytes);
        if (writer->slots[i].urb == nullptr || writer->slots[i].buffer == nullptr) {
            free_writer(writer);
            return 0;
        }
    }
    if (writer->feedback_ep != 0) {
        for (int i = 0; i < FEEDBACK_URB_COUNT; ++i) {
            writer->feedback_slots[i].urb = (struct usbdevfs_urb*)calloc(1, urb_alloc_size(1));
            writer->feedback_slots[i].buffer = (uint8_t*)malloc((size_t)writer->feedback_max);
            writer->feedback_slots[i].is_feedback = 1;
            if (writer->feedback_slots[i].urb == nullptr || writer->feedback_slots[i].buffer == nullptr) {
                free_writer(writer);
                return 0;
            }
            submit_feedback(writer, i);
        }
    }
    LOGI("iso writer fd=%d ep=0x%x rate=%d ch=%d bps=%d pps=%d fb=0x%x",
         fd, endpoint_address, sample_rate_hz, channel_count, bytes_per_sample, writer->pps, feedback_endpoint);
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
    if (writer->fatal) return WRITE_FATAL;
    reap(writer);
    if (writer->fatal) return WRITE_FATAL;
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
        const int64_t urb_start_acc = writer->acc_q16;
        int used = 0;
        int packets = 0;
        int frames_this_urb = 0;
        int64_t saved_acc = writer->acc_q16;
        for (int p = 0; p < writer->packets_per_urb; ++p) {
            const int samples = next_packet_samples(writer);
            const int packet_bytes = samples * frame_bytes;
            if (packet_bytes <= 0 || packet_bytes > writer->max_packet) {
                LOGE("iso packet %d exceeds max %d", packet_bytes, writer->max_packet);
                writer->acc_q16 = urb_start_acc;
                env->ReleaseByteArrayElements(packed, src, JNI_ABORT);
                return -1;
            }
            if (used + packet_bytes > writer->max_packet * writer->packets_per_urb ||
                consumed + packet_bytes > length) {
                writer->acc_q16 = saved_acc;
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
            saved_acc = writer->acc_q16;
        }
        if (packets == 0) {
            consumed = urb_start_consumed;
            break;
        }
        writer->slots[free_slot].frames = frames_this_urb;
        writer->slots[free_slot].is_silence = 0;
        if (submit_filled_urb(writer, free_slot, used, packets) != 0) {
            writer->slots[free_slot].frames = 0;
            writer->acc_q16 = urb_start_acc;
            consumed = urb_start_consumed;
            env->ReleaseByteArrayElements(packed, src, JNI_ABORT);
            if (writer->fatal) return WRITE_FATAL;
            return consumed > 0 ? consumed : -1;
        }
        writer->submitted_frames += frames_this_urb;
    }
    env->ReleaseByteArrayElements(packed, src, JNI_ABORT);
    return consumed;
}

extern "C" JNIEXPORT void JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativePrime(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr || writer->fatal) return;
    reap(writer);
    fill_keepalive(writer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeCompletedFrames(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr) return 0;
    reap(writer);
    return writer->audio_completed_frames;
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
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeFlush(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr) return;
    discard_inflight(writer);
    writer->acc_q16 = 0;
    writer->feedback_q16 = writer->nominal_q16;
    writer->submitted_frames = 0;
    writer->completed_frames = 0;
    writer->audio_completed_frames = 0;
    if (writer->feedback_ep != 0 && !writer->fatal) {
        for (int i = 0; i < FEEDBACK_URB_COUNT; ++i) {
            submit_feedback(writer, i);
        }
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeCapacityFrames(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr || writer->pps <= 0) return 1;
    const int samples = writer->sample_rate / writer->pps;
    const int per_urb = (samples > 0 ? samples : 1) * writer->packets_per_urb;
    return (jlong)per_urb * URB_COUNT;
}

extern "C" JNIEXPORT void JNICALL
Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeClose(JNIEnv*, jclass, jlong handle) {
    auto* writer = reinterpret_cast<Writer*>(handle);
    if (writer == nullptr) return;
    free_writer(writer);
}
