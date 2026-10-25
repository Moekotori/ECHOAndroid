package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class Gapless implements Transition {

    public static final Gapless INSTANCE = new Gapless();

    private Gapless() {
    }

    @Override
    public long windowFrames(int sampleRate) {
        return 0;
    }

    @Override
    public int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress) {

        if (progress < 0.5f) {
            return Mixer.gainInto(out, from, 1f);
        }
        return Mixer.gainInto(out, to, 1f);
    }

    @Override
    public String name() {
        return "gapless join";
    }

    @Override
    public boolean rewritesSamples() {
        return false;
    }
}
