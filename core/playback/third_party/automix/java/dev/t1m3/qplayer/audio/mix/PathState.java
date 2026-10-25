package dev.t1m3.qplayer.audio.mix;

public enum PathState {

    BIT_PERFECT("passthrough"),

    PROCESSED("processed"),

    TRANSITIONING("in transition");

    private final String label;

    PathState(String label) {
        this.label = label;
    }

    public boolean isBitPerfect() {
        return this == BIT_PERFECT;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
