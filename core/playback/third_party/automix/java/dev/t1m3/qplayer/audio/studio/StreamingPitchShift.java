package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.dsp.PitchShifter;

public final class StreamingPitchShift {
    public static final int CHUNK = 1024;

    public static final int LATENCY_FRAMES = CHUNK;

    private final int channels;
    private final PitchShifter shifter;
    private final AudioBuf wet;
    private final float[][] carry;
    private int carryCount;
    private final float[][] holdBuf;
    private int holdAt;

    private double appliedSemitones;

    private boolean draining;

    private int flushed;

    public StreamingPitchShift(int rate, int channels) {
        int r = Math.max(rate, 1);
        this.channels = Math.max(channels, 1);
        int shifterChannels = this.channels > 1 ? 2 : 1;
        this.shifter = new PitchShifter(shifterChannels, r, 0f, CHUNK);
        this.wet = new AudioBuf(ChannelLayout.of(shifterChannels), CHUNK);
        this.carry = new float[this.channels][CHUNK * 2];
        this.holdBuf = new float[this.channels][CHUNK];
    }

    public double semitones() {
        return appliedSemitones;
    }

    public void setSemitones(double semitones) {
        if (semitones != appliedSemitones) {
            appliedSemitones = semitones;
            shifter.setSemitones((float) semitones);
        }
    }

    public boolean active() {
        return draining;
    }

    public int latencyFrames() {
        return CHUNK;
    }

    public void reset() {
        shifter.reset();
        carryCount = 0;
        for (float[] h : holdBuf) {
            java.util.Arrays.fill(h, 0f);
        }
        holdAt = 0;
        appliedSemitones = 0;
        draining = false;
        flushed = 0;
    }

    public void process(AudioBuf buf, float mix) {
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        if (flushed > 0) {
            for (float[] h : holdBuf) {
                java.util.Arrays.fill(h, 0f);
            }
            holdAt = 0;
            shifter.reset();
            flushed = 0;
        }
        draining = true;
        int chs = Math.min(buf.channels(), channels);
        int done = 0;
        while (done < frames) {
            int n = Math.min(CHUNK, frames - done);
            int pushed = 0;
            while (pushed < n && shifter.freeFrames() > 0) {
                int k = shifter.pushFrom(buf, done + pushed);
                if (k <= 0) {
                    break;
                }
                pushed += k;
            }
            drain();
            mixBack(buf, done, n, mix, chs);
            done += n;
        }
    }

    private void drain() {
        int guard = 0;
        while (carry[0].length - carryCount >= CHUNK && guard++ < 64) {
            int got = Math.max(shifter.pull(wet, 0), 0);
            if (got <= 0) {
                return;
            }
            int room = carry[0].length - carryCount;
            int take = Math.min(got, room);
            for (int ch = 0; ch < channels; ch++) {
                int from = wet.channelBase(Math.min(ch, wet.channels() - 1));
                System.arraycopy(wet.backingArray(), from, carry[ch], carryCount, take);
            }
            carryCount += take;
        }
    }

    private void mixBack(AudioBuf buf, int from, int n, float mixValue, int chs) {
        float[] backing = buf.backingArray();
        int usable = Math.min(n, carryCount);
        int at = holdAt;
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < chs; ch++) {
                int base = buf.channelBase(ch);
                float[] wetCarry = carry[ch];
                float[] hold = holdBuf[ch];
                float dry = backing[base + from + i];
                float wetV = i < usable ? wetCarry[i] : 0f;
                float mixed = dry * (1.0f - mixValue) + wetV * mixValue;
                backing[base + from + i] = hold[at];
                hold[at] = mixed;
            }
            at = at + 1 == holdBuf[0].length ? 0 : at + 1;
        }
        holdAt = at;
        if (usable > 0) {
            int left = carryCount - usable;
            for (int ch = 0; ch < chs; ch++) {
                if (left > 0) {
                    System.arraycopy(carry[ch], usable, carry[ch], 0, left);
                }
            }
            carryCount = left;
        }
    }

    void flushHold(AudioBuf buf) {
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        int chs = Math.min(buf.channels(), channels);
        float[] backing = buf.backingArray();
        int at = holdAt;
        float inv = 1f / (float) CHUNK;
        for (int i = 0; i < frames && flushed < CHUNK; i++) {
            float w = (flushed + 1) * inv;
            if (w > 1f) {
                w = 1f;
            }
            for (int ch = 0; ch < chs; ch++) {
                int base = buf.channelBase(ch);
                float tail = holdBuf[ch][at];
                holdBuf[ch][at] = 0f;
                backing[base + i] = tail * (1f - w) + backing[base + i] * w;
            }
            at = at + 1 == holdBuf[0].length ? 0 : at + 1;
            flushed++;
        }
        holdAt = at;
        if (flushed < CHUNK) {
            return;
        }
        draining = false;
        flushed = 0;
    }
}
