package dev.t1m3.qplayer.audio.studio;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public final class DynamicsModule implements StudioModule {
    private static final double MAX_LOOKAHEAD_MS = 5.0;

    private final int rate;
    private final int channels;

    private volatile DynamicsParams params = DynamicsParams.off();

    private double compEnv = 1.0;
    private double gateEnv = 1.0;
    private int gateHold;
    private double rmsState;
    private double detEnv;

    static final double DETECTOR_RELEASE_MS = 50.0;
    private double limEnv = 1.0;

    private final float[][] lookahead;
    private final int lookaheadCapacity;
    private int lookaheadAt;

    private volatile float gainReductionDb;

    public DynamicsModule(int rate, int channels) {
        this.rate = Math.max(1, rate);
        this.channels = Math.max(1, channels);
        this.lookaheadCapacity =
                Math.max(1, (int) Math.ceil(MAX_LOOKAHEAD_MS / 1000.0 * this.rate)) + 1;
        this.lookahead = new float[this.channels][lookaheadCapacity];
    }

    public void setParams(DynamicsParams value) {
        this.params = value == null ? DynamicsParams.off() : value;
    }

    public DynamicsParams params() {
        return params;
    }

    @Override
    public boolean active() {
        return params.active();
    }

    @Override
    public int latencyFrames() {
        DynamicsParams p = params;
        return p.active() ? p.lookaheadFrames(rate) : 0;
    }

    public float gainReductionDb() {
        return gainReductionDb;
    }

    @Override
    public void reset() {
        compEnv = 1.0;
        detEnv = 0.0;
        gateEnv = 1.0;
        gateHold = 0;
        rmsState = 0.0;
        limEnv = 1.0;
        lookaheadAt = 0;
        for (float[] line : lookahead) {
            java.util.Arrays.fill(line, 0f);
        }
        gainReductionDb = 0f;
    }

    @Override
    public void process(AudioBuf buf) {
        DynamicsParams p = params;
        if (!p.active()) {
            gainReductionDb = 0f;
            return;
        }
        int frames = buf.frames();
        if (frames <= 0) {
            return;
        }
        int chs = Math.min(buf.channels(), channels);
        float[] backing = buf.backingArray();
        double maxGrDb = 0.0;

        double compAttack = alpha(p.compAttackMs);
        double compRelease = alpha(p.compReleaseMs);
        double rmsAlpha = alpha(p.compRmsWindowMs);
        double detectorRelease = Math.exp(-1.0 / (DETECTOR_RELEASE_MS / 1000.0 * rate));
        double gateAttack = alpha(2.0);
        double gateRelease = alpha(80.0);
        int gateHoldFrames = (int) Math.round(p.gateHoldMs / 1000.0 * rate);
        double ceiling = Math.pow(10.0, p.limiterCeilingDb / 20.0);
        double limRelease = alpha(p.limiterReleaseMs);
        int look = p.lookaheadFrames(rate);
        double makeup = Math.pow(10.0, p.compMakeupDb / 20.0);
        double gateFloor = Math.pow(10.0, p.gateRangeDb / 20.0);

        for (int i = 0; i < frames; i++) {
            double peak = 0.0;
            for (int ch = 0; ch < chs; ch++) {
                double v = Math.abs(backing[buf.channelBase(ch) + i]);
                if (v > peak) {
                    peak = v;
                }
            }
            detEnv = Math.max(peak, detEnv * detectorRelease);
            double level = detEnv;
            if (p.compRms) {
                rmsState += (peak * peak - rmsState) * rmsAlpha;
                level = Math.sqrt(Math.max(rmsState, 0.0));
            }
            double levelDb = level > 1e-9 ? 20.0 * Math.log10(level) : -120.0;

            double gateTarget = 1.0;
            if (p.gateOn && p.gateRangeDb < -1e-6) {
                if (levelDb < p.gateThresholdDb) {
                    if (gateHold > 0) {
                        gateHold--;
                    } else {
                        gateTarget = gateFloor;
                    }
                } else {
                    gateHold = gateHoldFrames;
                }
            }
            gateEnv += (gateTarget - gateEnv) * (gateTarget < gateEnv ? gateAttack : gateRelease);

            double compTarget = 1.0;
            if (p.compOn) {
                double over = levelDb - p.compThresholdDb;
                double knee = p.compKneeDb;
                double grDb;
                if (knee > 1e-9 && over > -knee / 2.0 && over < knee / 2.0) {
                    double x = over + knee / 2.0;
                    grDb = (1.0 - 1.0 / p.compRatio) * x * x / (2.0 * knee);
                } else if (over > 0.0) {
                    grDb = over * (1.0 - 1.0 / p.compRatio);
                } else {
                    grDb = 0.0;
                }
                compTarget = Math.pow(10.0, -Math.max(grDb, 0.0) / 20.0);
            }
            compEnv += (compTarget - compEnv) * (compTarget < compEnv ? compAttack : compRelease);

            double limTarget = 1.0;
            if (p.limiterOn && peak > ceiling) {
                limTarget = ceiling / peak;
            }
            limEnv = limTarget < limEnv ? limTarget : limEnv + (limTarget - limEnv) * limRelease;

            double grDb = -20.0 * Math.log10(Math.max(Math.min(gateEnv * compEnv, 1.0), 1e-9));
            if (grDb > maxGrDb) {
                maxGrDb = grDb;
            }

            for (int ch = 0; ch < chs; ch++) {
                int at = buf.channelBase(ch) + i;
                double v = backing[at] * gateEnv * compEnv * makeup;
                if (p.limiterOn && look > 0) {
                    float[] line = lookahead[ch];
                    int readAt =
                            ((lookaheadAt - look) % lookaheadCapacity + lookaheadCapacity)
                                    % lookaheadCapacity;
                    double delayed = line[readAt];
                    line[lookaheadAt] = (float) v;
                    v = delayed * limEnv;
                } else if (p.limiterOn) {
                    v *= limEnv;
                }
                backing[at] = (float) v;
            }
            lookaheadAt = (lookaheadAt + 1) % lookaheadCapacity;
        }
        if (maxGrDb > 0.0) {
            gainReductionDb = (float) Math.max(maxGrDb, gainReductionDb * 0.7);
        } else {
            gainReductionDb = (float) (gainReductionDb * 0.7);
        }
    }

    private double alpha(double ms) {
        double secs = Math.max(ms, 0.01) / 1000.0;
        return 1.0 - Math.exp(-1.0 / (secs * rate));
    }

    @Override
    public String toString() {
        return "DynamicsModule{" + params + ", reduction " + gainReductionDb + " dB}";
    }
}
