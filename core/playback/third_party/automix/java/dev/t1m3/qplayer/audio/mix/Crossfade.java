package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class Crossfade implements Transition {

    public static final float DEFAULT_SECONDS = 6f;

    public final float seconds;

    public Crossfade() {
        this(DEFAULT_SECONDS);
    }

    public Crossfade(float seconds) {
        this.seconds = seconds;
    }

    public float secondsClamped() {
        return Math.max(seconds, 0f);
    }

    @Override
    public long windowFrames(int sampleRate) {
        return Math.round(secondsClamped() * (float) sampleRate);
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {
        Mixer.Gains g = Mixer.equalPowerGains(progress);
        return Mixer.mixTwo(out, from, g.from, to, g.to);
    }

    @Override
    public String name() {
        return "crossfade";
    }

    @Override
    public boolean rewritesSamples() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Crossfade)) return false;
        return Float.compare(((Crossfade) o).seconds, seconds) == 0;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(seconds);
    }

    @Override
    public String toString() {
        return "Crossfade{seconds=" + seconds + '}';
    }
}
