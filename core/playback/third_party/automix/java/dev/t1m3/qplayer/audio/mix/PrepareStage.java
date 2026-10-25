package dev.t1m3.qplayer.audio.mix;

public final class PrepareStage {

    public static final String IDLE = " (no prepare running)";

    private static volatile String stage = IDLE;

    private PrepareStage() {
    }

    public static void mark(String what) {
        stage = what == null ? IDLE : what;
    }

    public static void clear() {
        stage = IDLE;
    }

    public static String current() {
        return stage;
    }
}
