package dev.t1m3.qplayer.audio.dsp;

public enum SpatialMode {

    OFF,

    BINAURAL,

    PASSTHROUGH;

    public String label() {
        switch (this) {
            case OFF:
                return "off";
            case BINAURAL:
                return "binaural render";
            case PASSTHROUGH:
                return "multichannel passthrough";
            default:

                throw new IllegalStateException("unknown spatial audio mode: " + this);
        }
    }

    public boolean rewritesSamples() {
        return this == BINAURAL;
    }

    public static SpatialMode defaultValue() {
        return OFF;
    }
}
