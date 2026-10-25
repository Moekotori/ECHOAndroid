#pragma once
#include <stdint.h>

// Owned by the session's existing lock. No allocation, logging or additional locking.
struct UsbTransferMetrics {
    int64_t min_queued_frames = -1;
    int64_t audio_urbs = 0;
    int64_t feedback_urbs = 0;
    int64_t audio_packet_errors = 0;
    int64_t feedback_packet_errors = 0;
    int64_t submit_failures = 0;
    int64_t reap_failures = 0;
    int64_t supply_gap_count = 0;
    int64_t supply_gap_frames = 0;
    int64_t invalid_feedback = 0;
    int64_t last_error_source = 0;
    int64_t last_error_code = 0;
    int64_t audio_deadline_ns = 0;
    bool active = false;
    bool discarding = false;

    void error(int source, int code) {
        last_error_source = source;
        last_error_code = code;
    }
    void set_active(bool value) {
        if (active != value) audio_deadline_ns = 0;
        active = value;
    }
    void observe_queue(int64_t frames) {
        if (active && audio_deadline_ns > 0 && (min_queued_frames < 0 || frames < min_queued_frames))
            min_queued_frames = frames;
    }
    // This is an estimate of host supply gaps, not proof of DAC-side underruns.
    // Call only when a new real-audio write resumes; pause/EOS/flush disarm it.
    void before_audio(int64_t now_ns, int64_t queued_frames, int sample_rate) {
        observe_queue(queued_frames);
        if (active && audio_deadline_ns > 0 && queued_frames == 0 &&
                now_ns > audio_deadline_ns + 1000000LL) {
            ++supply_gap_count;
            const int64_t gap_ns = now_ns - audio_deadline_ns;
            supply_gap_frames += (gap_ns / 1000000000LL) * sample_rate +
                (gap_ns % 1000000000LL) * sample_rate / 1000000000LL;
            audio_deadline_ns = 0;
        }
    }
    void submitted_audio(int64_t now_ns, int frames, int sample_rate) {
        if (!active) return;
        if (audio_deadline_ns < now_ns) audio_deadline_ns = now_ns;
        audio_deadline_ns += (int64_t)frames * 1000000000LL / sample_rate;
    }
};
