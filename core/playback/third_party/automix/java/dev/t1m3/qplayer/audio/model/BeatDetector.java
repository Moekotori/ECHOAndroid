package dev.t1m3.qplayer.audio.model;

public interface BeatDetector extends AutoCloseable {
    String label();

    MelParams melParams();

    PeakParams peakParams();

    float[] beats(float[] pcm);

    @Override
    void close();
}
