package dev.t1m3.qplayer.audio.mix;

import java.util.Map;

public interface MaterialHook {

    float[][] transform(String source, Map<String, String> headers,
                        double startSecs, int frames, int rate);

    default void onDecoded(dev.t1m3.qplayer.audio.core.AudioBuf buf, int frames,
                           double startSecs) {

    }
}
