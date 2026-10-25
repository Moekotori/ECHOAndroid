package dev.t1m3.qplayer.audio.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public abstract class PathVerdict {

    private PathVerdict() {
    }

    public static final class Direct extends PathVerdict {

        public final int sampleRate;

        public final BitDepth bitDepth;

        public Direct(int sampleRate, BitDepth bitDepth) {
            this.sampleRate = sampleRate;
            this.bitDepth = bitDepth;
        }

        @Override
        public String summary() {
            return "passthrough " + formatRate(sampleRate) + " / " + bitDepth.label();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Direct)) return false;
            Direct d = (Direct) o;
            return sampleRate == d.sampleRate && bitDepth == d.bitDepth;
        }

        @Override
        public int hashCode() {
            return 31 * sampleRate + bitDepth.hashCode();
        }
    }

    public static final class Resampled extends PathVerdict {

        public final int from;

        public final int to;

        public final boolean sameFamily;

        public Resampled(int from, int to, boolean sameFamily) {
            this.from = from;
            this.to = to;
            this.sameFamily = sameFamily;
        }

        @Override
        public String summary() {
            String note = sameFamily ? "same-family integer multiple" : "cross clock family";
            return "resampled " + formatRate(from) + " → " + formatRate(to) + "（" + note + "）";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Resampled)) return false;
            Resampled r = (Resampled) o;
            return from == r.from && to == r.to && sameFamily == r.sameFamily;
        }

        @Override
        public int hashCode() {
            return (31 * from + to) * 31 + (sameFamily ? 1 : 0);
        }
    }

    public static final class Requantized extends PathVerdict {

        public final BitDepth from;

        public final BitDepth to;

        public final boolean upconverted;

        public Requantized(BitDepth from, BitDepth to, boolean upconverted) {
            this.from = from;
            this.to = to;
            this.upconverted = upconverted;
        }

        @Override
        public String summary() {
            String note = upconverted ? "zero-padded, lossless" : "reduced significant bits";
            return "bit depth " + from + " → " + to + "（" + note + "）";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Requantized)) return false;
            Requantized r = (Requantized) o;
            return from == r.from && to == r.to && upconverted == r.upconverted;
        }

        @Override
        public int hashCode() {
            return (31 * from.hashCode() + to.hashCode()) * 31 + (upconverted ? 1 : 0);
        }
    }

    public boolean isBitPerfect() {
        return this instanceof Direct;
    }

    public boolean losesInformation() {
        if (this instanceof Direct) return false;
        if (this instanceof Resampled) return true;
        return !((Requantized) this).upconverted;
    }

    public abstract String summary();

    public static String formatRate(int rate) {
        if (rate % 1000 == 0) {
            return (rate / 1000) + " kHz";
        }
        return String.format(Locale.ROOT, "%.1f kHz", rate / 1000.0);
    }

    public static PathVerdict classify(SourceFormat src, int[] deviceRates,
                                       BitDepth[] deviceDepths) {
        boolean rateExact = containsRate(deviceRates, src.sampleRate());
        boolean depthExact = containsDepth(deviceDepths, src.bitDepth());

        if (rateExact && depthExact) {
            return new Direct(src.sampleRate(), src.bitDepth());
        }

        if (!rateExact) {
            int to = pickRate(src.sampleRate(), deviceRates);
            return new Resampled(src.sampleRate(), to,
                    RateFamily.of(src.sampleRate()) == RateFamily.of(to));
        }

        BitDepth to = pickDepth(src.bitDepth(), deviceDepths);
        return new Requantized(src.bitDepth(), to, to.bits() > src.bitDepth().bits());
    }

    static int pickRate(int src, int[] avail) {
        if (avail == null || avail.length == 0) {
            return src;
        }
        TreeSet<Integer> sorted = new TreeSet<Integer>();
        for (int rate : avail) {
            if (rate > 0) sorted.add(rate);
        }
        if (sorted.isEmpty()) {
            return src;
        }

        RateFamily family = RateFamily.of(src);
        for (Integer rate : sorted) {
            if (RateFamily.of(rate) == family && rate >= src) {
                return rate;
            }
        }
        for (Integer rate : sorted) {
            if (rate >= src) {
                return rate;
            }
        }
        return sorted.last();
    }

    static BitDepth pickDepth(BitDepth src, BitDepth[] avail) {
        if (avail == null || avail.length == 0) {
            return src;
        }
        for (BitDepth depth : avail) {
            if (depth == src) return depth;
        }

        List<BitDepth> sorted = new ArrayList<BitDepth>(new TreeSet<BitDepth>(Arrays.asList(avail)));
        if (sorted.isEmpty()) {
            return src;
        }
        for (BitDepth depth : sorted) {
            if (depth.bits() >= src.bits()) {
                return depth;
            }
        }
        return sorted.get(sorted.size() - 1);
    }

    private static boolean containsRate(int[] rates, int rate) {
        if (rates == null) return false;
        for (int candidate : rates) {
            if (candidate == rate) return true;
        }
        return false;
    }

    private static boolean containsDepth(BitDepth[] depths, BitDepth depth) {
        if (depths == null) return false;
        for (BitDepth candidate : depths) {
            if (candidate == depth) return true;
        }
        return false;
    }
}
