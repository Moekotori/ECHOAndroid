package dev.t1m3.qplayer.audio.mix;

public final class SampleBoundary {

    public static final long MAX_MAP_ERROR = 1L;

    private SampleBoundary() {
    }

    public static long floorFrame(double secs, int rate) {
        if (rate <= 0 || Double.isNaN(secs)) {
            return 0L;
        }
        return (long) Math.floor(secs * (double) rate);
    }

    public static long[] halfOpen(double startSecs, double endSecs, int rate) {
        long a = floorFrame(startSecs, rate);
        long b = floorFrame(endSecs, rate);
        return new long[] {a, Math.max(a, b)};
    }

    public static long clampTo(double ideal, long lowest, long highest) {
        long rounded = Math.round(ideal);
        long clamped = Math.max(lowest, Math.min(highest, rounded));

        return clamped;
    }

    public static long mapError(double ideal, long mapped) {
        return Math.abs(Math.round(ideal) - mapped);
    }
}
