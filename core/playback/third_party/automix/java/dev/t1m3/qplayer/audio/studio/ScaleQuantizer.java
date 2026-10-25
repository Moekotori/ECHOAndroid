package dev.t1m3.qplayer.audio.studio;

public final class ScaleQuantizer {
    public enum Scale {
        CHROMATIC("Chromatic", new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}),
        MAJOR("Major", new int[] {0, 2, 4, 5, 7, 9, 11}),
        MINOR("Minor", new int[] {0, 2, 3, 5, 7, 8, 10}),
        PENTATONIC("Pentatonic", new int[] {0, 2, 4, 7, 9}),
        BLUES("Blues", new int[] {0, 3, 5, 6, 7, 10});

        public final String label;
        public final int[] steps;

        Scale(String label, int[] steps) {
            this.label = label;
            this.steps = steps;
        }

        public static Scale fromIndex(int index) {
            Scale[] all = values();
            return index >= 0 && index < all.length ? all[index] : CHROMATIC;
        }

        public static String[] labels() {
            Scale[] all = values();
            String[] out = new String[all.length];
            for (int i = 0; i < all.length; i++) {
                out[i] = all[i].label;
            }
            return out;
        }
    }

    public static final double DEFAULT_A4 = 440.0;

    private ScaleQuantizer() {}

    public static double midiOf(double hz, double a4) {
        double ref = a4 > 1.0 ? a4 : DEFAULT_A4;
        return 69.0 + 12.0 * Math.log(hz / ref) / Math.log(2.0);
    }

    public static double hzOf(double midi, double a4) {
        double ref = a4 > 1.0 ? a4 : DEFAULT_A4;
        return ref * Math.pow(2.0, (midi - 69.0) / 12.0);
    }

    public static double targetHz(double hz, Scale scale, double a4) {
        return targetHz(hz, scale, a4, 0);
    }

    public static double targetHz(double hz, Scale scale, double a4, int tonic) {
        if (!(hz > 0.0) || Double.isNaN(hz) || Double.isInfinite(hz)) {
            return hz;
        }
        Scale s = scale == null ? Scale.CHROMATIC : scale;
        double midi = midiOf(hz, a4);
        int t = ((tonic % 12) + 12) % 12;
        int lo = (int) Math.floor(midi) - 12;
        int hi = (int) Math.ceil(midi) + 12;
        double bestMidi = midi;
        double bestDist = Double.MAX_VALUE;
        for (int m = lo; m <= hi; m++) {
            int pc = ((m - t) % 12 + 12) % 12;
            if (!inSteps(s, pc)) {
                continue;
            }
            double dist = Math.abs(m - midi);
            if (dist < bestDist - 1e-9) {
                bestDist = dist;
                bestMidi = m;
            }
        }
        return hzOf(bestMidi, a4);
    }

    private static boolean inSteps(Scale s, int pc) {
        for (int i = 0; i < s.steps.length; i++) {
            if (s.steps[i] == pc) {
                return true;
            }
        }
        return false;
    }

    public static double centsOff(double hz, Scale scale, double a4, int tonic) {
        if (!(hz > 0.0) || Double.isNaN(hz) || Double.isInfinite(hz)) {
            return 0.0;
        }
        double target = targetHz(hz, scale, a4, tonic);
        if (!(target > 0.0)) {
            return 0.0;
        }
        return 1200.0 * Math.log(hz / target) / Math.log(2.0);
    }
}
