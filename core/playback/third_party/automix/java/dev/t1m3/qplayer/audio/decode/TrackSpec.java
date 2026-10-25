package dev.t1m3.qplayer.audio.decode;

import dev.t1m3.qplayer.audio.core.BitDepth;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.core.SourceFormat;

public final class TrackSpec {
    public final String codec;
    public final int sampleRate;
    public final ChannelLayout layout;
    public final Integer bitsPerSample;
    public final Long totalFrames;
    public final boolean lossless;

    public TrackSpec(
            String codec,
            int sampleRate,
            ChannelLayout layout,
            Integer bitsPerSample,
            Long totalFrames,
            boolean lossless) {
        this.codec = codec == null ? "unknown" : codec;
        this.sampleRate = sampleRate;
        this.layout = layout;
        this.bitsPerSample = bitsPerSample;
        this.totalFrames = totalFrames;
        this.lossless = lossless;
    }

    public Double durationSecs() {
        if (totalFrames == null) {
            return null;
        }
        return totalFrames.doubleValue() / Math.max(1, sampleRate);
    }

    public int samplesPerSecond() {
        return sampleRate * layout.count();
    }

    public SourceFormat sourceFormat() {
        BitDepth depth = bitsPerSample == null ? null : BitDepth.fromBits(bitsPerSample);
        return new SourceFormat(
                sampleRate,
                layout.count(),
                depth == null ? BitDepth.INT16 : depth,
                lossless,
                codec);
    }

    @Override
    public String toString() {
        return "TrackSpec{"
                + codec
                + " "
                + sampleRate
                + " Hz, "
                + layout
                + ", bits="
                + bitsPerSample
                + ", frames="
                + totalFrames
                + (lossless ? ", lossless" : ", lossy")
                + '}';
    }
}
