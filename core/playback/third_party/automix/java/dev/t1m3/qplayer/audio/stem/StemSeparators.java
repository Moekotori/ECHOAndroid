package dev.t1m3.qplayer.audio.stem;

import dev.t1m3.qplayer.audio.model.InferException;
import dev.t1m3.qplayer.audio.model.StemModels;
import dev.t1m3.qplayer.audio.model.StemSeparator;

public final class StemSeparators {

    public static final int OFF = 0;

    public static final int DSP = 1;

    public static final int AI = 2;

    public static final int DEFAULT_MODE = OFF;

    public interface Opener {

        StemSeparator open();
    }

    private static final Opener DEFAULT_DSP = new Opener() {
        @Override
        public StemSeparator open() {
            return new DspStemSeparator();
        }
    };

    private static volatile Opener dsp = DEFAULT_DSP;

    private StemSeparators() {
    }

    public static void installDsp(Opener value) {
        dsp = value == null ? DEFAULT_DSP : value;
    }

    public static void uninstallDsp() {
        dsp = DEFAULT_DSP;
    }

    public static boolean isKnownMode(int mode) {
        return mode == OFF || mode == DSP || mode == AI;
    }

    public static String label(int mode) {
        switch (mode) {
            case DSP:
                return "DSP";
            case AI:
                return StemModels.MODEL_NAME;
            default:
                return "no separation";
        }
    }

    public static boolean available(int mode) {
        switch (mode) {
            case DSP:
                return true;
            case AI:
                return StemModels.available();
            default:
                return false;
        }
    }

    public static String unavailableReason(int mode) {
        switch (mode) {
            case DSP:

                return "the DSP separator is unavailable";
            case AI:
                return StemModels.MODEL_NAME + " the model is not on disk (download it under 'Models')";
            default:
                return "mode " + mode + " is not a separator preset (" + label(mode) + "), this lane should not be wrapped";
        }
    }

    public static StemSeparator forMode(int mode) {
        switch (mode) {
            case DSP:
                return dsp.open();
            case AI:
                return StemModels.open();
            default:

                throw InferException.plan("mode " + mode + " is not a separator preset ("
                        + label(mode) + "）");
        }
    }
}
