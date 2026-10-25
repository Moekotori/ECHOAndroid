package dev.t1m3.qplayer.audio.core;

public enum RateFamily {

    BASE_44,

    BASE_48;

    public static RateFamily of(int rate) {
        return rate % 44_100 == 0 ? BASE_44 : BASE_48;
    }
}
