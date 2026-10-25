package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EqProcessor {
    private static final double[] Q1 = {0.7071067811865476};
    private static final double[] Q2 = {0.541196100146197, 1.306562964876377};
    private static final double[] Q4 = {
        0.509795579104159, 0.601344886935045, 0.899976223136416, 2.562915447741505
    };

    private final int rate;
    private final int channels;

    private volatile Compiled compiled = Compiled.EMPTY;

    public EqProcessor(int rate, int channels) {
        this.rate = Math.max(rate, 1);
        this.channels = Math.max(channels, 1);
    }

    public int rate() {
        return rate;
    }

    public void setBands(List<EqBand> bands) {
        Compiled old = compiled;
        List<Plan> built = new ArrayList<Plan>();
        boolean any = false;
        int index = 0;
        for (EqBand band : bands == null ? Collections.<EqBand>emptyList() : bands) {
            if (index >= StudioRack.MAX_BANDS) {
                break;
            }
            Plan previous = index < old.plans.length ? old.plans[index] : null;
            Plan plan = Plan.compile(rate, channels, band, previous);
            built.add(plan);
            any = any || plan.active;
            index++;
        }
        compiled = new Compiled(built.toArray(new Plan[built.size()]), any);
    }

    public boolean active() {
        return compiled.any;
    }

    public int activeBands() {
        int n = 0;
        Plan[] plans = compiled.plans;
        for (int i = 0; i < plans.length; i++) {
            if (plans[i].active) {
                n++;
            }
        }
        return n;
    }

    public void reset() {
        Plan[] plans = compiled.plans;
        for (int i = 0; i < plans.length; i++) {
            plans[i].reset();
        }
    }

    public void process(AudioBuf buf) {
        Compiled c = compiled;
        if (!c.any) {
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        float[] backing = buf.backingArray();
        int baseL = buf.channelBase(0);
        int baseR = buf.channelBase(buf.channels() > 1 ? 1 : 0);
        boolean stereo = buf.channels() > 1;
        Plan[] plans = c.plans;
        for (int pi = 0; pi < plans.length; pi++) {
            Plan p = plans[pi];
            if (!p.active) {
                continue;
            }
            if (p.target == ChannelTarget.MID || p.target == ChannelTarget.SIDE) {
                if (!stereo) {
                    if (p.target == ChannelTarget.SIDE) {
                        continue;
                    }
                    for (int i = 0; i < frames; i++) {
                        backing[baseL + i] = (float) p.filter(0, 0, backing[baseL + i]);
                    }
                    continue;
                }
                boolean mid = p.target == ChannelTarget.MID;
                for (int i = 0; i < frames; i++) {
                    double l = backing[baseL + i];
                    double r = backing[baseR + i];
                    double m = l + r;
                    double side = l - r;
                    if (mid) {
                        m = p.filter(0, 0, m);
                    } else {
                        side = p.filter(0, 0, side);
                    }
                    backing[baseL + i] = (float) ((m + side) * 0.5);
                    backing[baseR + i] = (float) ((m - side) * 0.5);
                }
                continue;
            }
            int first = p.target == ChannelTarget.RIGHT ? 1 : 0;
            int last = (p.target == ChannelTarget.STEREO && stereo) ? 2 : first + 1;
            if (stereo) {
                for (int ch = first; ch < last; ch++) {
                    int base = buf.channelBase(ch);
                    for (int i = 0; i < frames; i++) {
                        backing[base + i] = (float) p.filter(ch, 0, backing[base + i]);
                    }
                }
            } else if (first == 0) {
                for (int i = 0; i < frames; i++) {
                    backing[baseL + i] = (float) p.filter(0, 0, backing[baseL + i]);
                }
            }
        }
    }

    private static final class Compiled {
        static final Compiled EMPTY = new Compiled(new Plan[0], false);

        final Plan[] plans;
        final boolean any;

        Compiled(Plan[] plans, boolean any) {
            this.plans = plans;
            this.any = any;
        }
    }

    private static final class Plan {
        final BandType type;
        final ChannelTarget target;
        final int stages;
        final boolean active;
        final StudioBiquad[][] filters;

        private Plan(
                BandType type,
                ChannelTarget target,
                int stages,
                boolean active,
                StudioBiquad[][] filters) {
            this.type = type;
            this.target = target;
            this.stages = stages;
            this.active = active;
            this.filters = filters;
        }

        static Plan compile(int rate, int channels, EqBand band, Plan previous) {
            ChannelTarget target = band.channel;
            boolean usesRight = target == ChannelTarget.STEREO || target == ChannelTarget.RIGHT;
            int voices = usesRight && channels > 1 ? 2 : 1;
            int stages = band.stages();
            boolean active = !band.isTransparent();
            StudioBiquad[][] filters = new StudioBiquad[stages][voices];
            double[] qs = qsFor(band, stages);
            for (int s = 0; s < stages; s++) {
                for (int v = 0; v < voices; v++) {
                    StudioBiquad reuse = reuseOf(previous, band, s, v);
                    StudioBiquad f = design(rate, band, qs[s]);
                    if (reuse != null) {
                        reuse.updateFrom(f);
                        f = reuse;
                    }
                    filters[s][v] = f;
                }
            }
            return new Plan(band.type, target, stages, active, filters);
        }

        private static StudioBiquad reuseOf(Plan previous, EqBand band, int stage, int voice) {
            if (previous == null
                    || previous.type != band.type
                    || previous.target != band.channel
                    || previous.stages != band.stages()) {
                return null;
            }
            if (stage >= previous.filters.length || voice >= previous.filters[stage].length) {
                return null;
            }
            return previous.filters[stage][voice];
        }

        double filter(int voice, int stage, double x) {
            double y = x;
            for (int s = 0; s < stages; s++) {
                y = filters[s][voice].process(y);
            }
            return y;
        }

        void reset() {
            for (StudioBiquad[] row : filters) {
                for (StudioBiquad f : row) {
                    f.reset();
                }
            }
        }

        private static double[] qsFor(EqBand band, int stages) {
            if (!band.type.usesSlope()) {
                return new double[] {band.q};
            }
            if (stages >= 4) {
                return Q4;
            }
            return stages == 2 ? Q2 : Q1;
        }

        private static StudioBiquad design(int rate, EqBand band, double q) {
            float f = (float) band.freqHz;
            float qq = (float) q;
            float g = (float) band.gainDb;
            switch (band.type) {
                case LOW_SHELF:
                    return StudioBiquad.lowShelf(rate, f, qq, g);
                case HIGH_SHELF:
                    return StudioBiquad.highShelf(rate, f, qq, g);
                case HIGH_PASS:
                    return StudioBiquad.highPass(rate, f, qq);
                case LOW_PASS:
                    return StudioBiquad.lowPass(rate, f, qq);
                case BAND_PASS:
                    return StudioBiquad.bandPass(rate, f, qq);
                case NOTCH:
                    return StudioBiquad.notch(rate, f, qq);
                case ALL_PASS:
                    return StudioBiquad.allPass(rate, f, qq);
                case PEAK:
                default:
                    return StudioBiquad.peaking(rate, f, qq, g);
            }
        }
    }
}
