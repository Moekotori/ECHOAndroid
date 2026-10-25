package dev.t1m3.qplayer.audio.core;

public enum SignalPath {

    PROCESSED,

    BIT_PERFECT;

    public boolean isBitPerfect() {
        return this == BIT_PERFECT;
    }

    public boolean allowsDsp() {
        return this == PROCESSED;
    }
}
