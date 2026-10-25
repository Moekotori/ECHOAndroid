package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public interface StudioModule {
    void process(AudioBuf buf);

    boolean active();

    void reset();

    int latencyFrames();
}
