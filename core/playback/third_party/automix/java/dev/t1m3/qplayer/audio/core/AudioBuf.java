package dev.t1m3.qplayer.audio.core;

import java.nio.FloatBuffer;

public final class AudioBuf {

    private final float[] data;
    private final int channels;
    private final int capacityFrames;
    private int frames;

    public AudioBuf(ChannelLayout layout, int capacityFrames) {
        int ch = layout.count();
        if (ch <= 0) {
            throw new IllegalArgumentException("channel count must be > 0");
        }
        if (capacityFrames < 0) {
            throw new IllegalArgumentException("frame capacity must not be negative: " + capacityFrames);
        }
        if ((long) ch * capacityFrames > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("channel count x frame capacity exceeds array limit: "
                    + ch + " × " + capacityFrames);
        }
        this.channels = ch;
        this.capacityFrames = capacityFrames;
        this.data = new float[ch * capacityFrames];
        this.frames = 0;
    }

    public int channels() {
        return channels;
    }

    public int capacityFrames() {
        return capacityFrames;
    }

    public int frames() {
        return frames;
    }

    public void setFrames(int n) {
        this.frames = n < 0 ? 0 : Math.min(n, capacityFrames);
    }

    public void clear() {
        this.frames = 0;
    }

    public boolean isEmpty() {
        return frames == 0;
    }

    public FloatBuffer channel(int ch) {
        checkChannel(ch);
        int base = ch * capacityFrames;
        return FloatBuffer.wrap(data, base, frames).slice();
    }

    public FloatBuffer channelWriter(int ch) {
        checkChannel(ch);
        int base = ch * capacityFrames;
        return FloatBuffer.wrap(data, base, capacityFrames).slice();
    }

    public float[] backingArray() {
        return data;
    }

    public int channelBase(int ch) {
        checkChannel(ch);
        return ch * capacityFrames;
    }

    public void zero() {
        java.util.Arrays.fill(data, 0f);
    }

    public int interleaveInto(float[] out) {
        int ch = channels;
        int n = Math.min(frames, out.length / ch);
        for (int i = 0; i < n; i++) {
            int row = i * ch;
            for (int c = 0; c < ch; c++) {
                out[row + c] = data[c * capacityFrames + i];
            }
        }
        return n * ch;
    }

    public float peak() {
        float peak = 0f;
        for (int c = 0; c < channels; c++) {
            int base = c * capacityFrames;
            for (int i = 0; i < frames; i++) {
                float a = Math.abs(data[base + i]);
                if (a > peak) {
                    peak = a;
                }
            }
        }
        return peak;
    }

    public boolean isClipping() {
        return peak() > 1f;
    }

    public int append(AudioBuf other) {
        if (other == null || channels != other.channels) {
            return 0;
        }
        int room = capacityFrames - frames;
        int n = Math.min(other.frames, room);
        if (n == 0) {
            return 0;
        }
        int start = frames;
        int srcStride = other.capacityFrames;
        for (int c = 0; c < channels; c++) {
            System.arraycopy(other.data, c * srcStride, data, c * capacityFrames + start, n);
        }
        frames += n;
        return n;
    }

    private void checkChannel(int ch) {
        if (ch < 0 || ch >= channels) {
            throw new IndexOutOfBoundsException("channel index out of range: " + ch + " (total " + channels + " channels)");
        }
    }

    @Override
    public String toString() {
        return "AudioBuf{" + channels + "ch, frames=" + frames + "/" + capacityFrames
                + ", peak=" + peak() + '}';
    }
}
