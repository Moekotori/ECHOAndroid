package dev.t1m3.qplayer.audio.model;

public final class StemModels {
    public static final String MODEL_NAME = "htdemucs";

    public interface Opener {
        StemSeparator open();
    }

    private static volatile Opener opener;

    private StemModels() {}

    public static void install(Opener value) {
        opener = value;
    }

    public static void uninstall() {
        opener = null;
    }

    public static boolean isInstalled() {
        return opener != null;
    }

    public static boolean available() {
        return false;
    }

    public static StemSeparator open() {
        Opener value = opener;
        if (value == null) {
            throw InferException.load(
                    "no stem model implementation installed (the host must call StemModels.install"
                        + " at startup)");
        }
        return value.open();
    }
}
