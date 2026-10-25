package dev.t1m3.qplayer.audio.core;

public final class SourceFormat {

    private final int sampleRate;
    private final int channels;
    private final BitDepth bitDepth;
    private final boolean lossless;
    private final String codec;

    public SourceFormat(int sampleRate, int channels, BitDepth bitDepth,
                        boolean lossless, String codec) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitDepth = bitDepth;
        this.lossless = lossless;
        this.codec = codec == null ? "" : codec;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

    public BitDepth bitDepth() {
        return bitDepth;
    }

    public boolean lossless() {
        return lossless;
    }

    public String codec() {
        return codec;
    }

    public RateFamily family() {
        return RateFamily.of(sampleRate);
    }

    public boolean isHires() {
        return lossless && (sampleRate > 48_000 || bitDepth.bits() > 16);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceFormat)) return false;
        SourceFormat other = (SourceFormat) o;
        return sampleRate == other.sampleRate
                && channels == other.channels
                && bitDepth == other.bitDepth
                && lossless == other.lossless
                && codec.equals(other.codec);
    }

    @Override
    public int hashCode() {
        int h = sampleRate;
        h = 31 * h + channels;
        h = 31 * h + bitDepth.hashCode();
        h = 31 * h + (lossless ? 1 : 0);
        h = 31 * h + codec.hashCode();
        return h;
    }

    @Override
    public String toString() {
        return "SourceFormat{" + sampleRate + " Hz, " + channels + "ch, " + bitDepth
                + (lossless ? ", lossless" : ", lossy") + ", " + codec + '}';
    }
}
