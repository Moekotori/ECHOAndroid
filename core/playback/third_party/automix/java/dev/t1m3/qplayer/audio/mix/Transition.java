package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public interface Transition {

    long windowFrames(int sampleRate);

    int render(AudioBuf out, AudioBuf from, AudioBuf to, float progress);

    String name();

    boolean rewritesSamples();

    default TransitionPlan prepare(String outro, String intro, int rate) {
        return TransitionPlan.NONE;
    }

    default TransitionPlan prepare(String outro, String intro, int rate,
                                   java.util.Map<String, String> outroHeaders,
                                   java.util.Map<String, String> introHeaders) {
        return prepare(outro, intro, rate);
    }

    default double outroLoopLeadInSecs() {
        return 0.0;
    }

    default PitchPlan pitchAt(float progress) {
        return PitchPlan.ZERO;
    }

    default long contentFramesFor(long outFrames) {
        return outFrames;
    }
}
