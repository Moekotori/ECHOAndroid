package dev.t1m3.qplayer.audio.mix;

public class TuningException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public final String key;

    public final String why;

    public TuningException(String key, String why) {
        super("tuning key " + key + " is invalid: " + why);
        this.key = key;
        this.why = why;
    }
}
