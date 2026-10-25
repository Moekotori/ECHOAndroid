package dev.t1m3.qplayer.audio.studio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StudioRack {
    public static final int MAX_BANDS = 16;

    public static final StudioRack EMPTY =
            new StudioRack(
                    false,
                    0.0,
                    0.0,
                    false,
                    false,
                    0.0,
                    Collections.<EqBand>emptyList(),
                    PitchParams.off(),
                    DynamicsParams.off(),
                    StereoParams.neutral(),
                    SaturationParams.off());

    public final boolean enabled;

    public final double preampDb;

    public final double balance;

    public final boolean polarityInvert;

    public final boolean loudness;

    public final double loudnessAmount;

    public final List<EqBand> bands;

    public final PitchParams pitch;

    public final DynamicsParams dynamics;

    public final StereoParams stereo;

    public final SaturationParams saturation;

    private StudioRack(
            boolean enabled,
            double preampDb,
            double balance,
            boolean polarityInvert,
            boolean loudness,
            double loudnessAmount,
            List<EqBand> bands,
            PitchParams pitch,
            DynamicsParams dynamics,
            StereoParams stereo,
            SaturationParams saturation) {
        this.enabled = enabled;
        this.preampDb = clamp(preampDb, -24.0, 24.0);
        this.balance = clamp(balance, -1.0, 1.0);
        this.polarityInvert = polarityInvert;
        this.loudness = loudness;
        this.loudnessAmount = clamp(loudnessAmount, 0.0, 1.0);
        List<EqBand> copy = new ArrayList<EqBand>();
        for (EqBand b : bands == null ? Collections.<EqBand>emptyList() : bands) {
            if (b != null && copy.size() < MAX_BANDS) {
                copy.add(b);
            }
        }
        this.bands = Collections.unmodifiableList(copy);
        this.pitch = pitch == null ? PitchParams.off() : pitch;
        this.dynamics = dynamics == null ? DynamicsParams.off() : dynamics;
        this.stereo = stereo == null ? StereoParams.neutral() : stereo;
        this.saturation = saturation == null ? SaturationParams.off() : saturation;
    }

    public static StudioRack of(
            boolean enabled,
            double preampDb,
            double balance,
            boolean polarityInvert,
            boolean loudness,
            double loudnessAmount,
            List<EqBand> bands) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                PitchParams.off(),
                DynamicsParams.off(),
                StereoParams.neutral(),
                SaturationParams.off());
    }

    public static StudioRack of(
            boolean enabled,
            double preampDb,
            double balance,
            boolean polarityInvert,
            boolean loudness,
            double loudnessAmount,
            List<EqBand> bands,
            PitchParams pitchValue) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitchValue,
                DynamicsParams.off(),
                StereoParams.neutral(),
                SaturationParams.off());
    }

    public static StudioRack full(
            boolean enabled,
            double preampDb,
            double balance,
            boolean polarityInvert,
            boolean loudness,
            double loudnessAmount,
            List<EqBand> bands,
            PitchParams pitchValue,
            DynamicsParams dynamicsValue,
            StereoParams stereoValue,
            SaturationParams saturationValue) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitchValue,
                dynamicsValue,
                stereoValue,
                saturationValue);
    }

    public static StudioRack eq(List<EqBand> bands) {
        return new StudioRack(
                true,
                0.0,
                0.0,
                false,
                false,
                0.0,
                bands,
                PitchParams.off(),
                DynamicsParams.off(),
                StereoParams.neutral(),
                SaturationParams.off());
    }

    public StudioRack withEnabled(boolean value) {
        return new StudioRack(
                value,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withPreamp(double db) {
        return new StudioRack(
                enabled,
                db,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withBalance(double value) {
        return new StudioRack(
                enabled,
                preampDb,
                value,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withPolarityInvert(boolean value) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                value,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withLoudness(boolean on, double amount) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                on,
                amount,
                bands,
                pitch,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withBands(List<EqBand> value) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                value,
                pitch,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withDynamics(DynamicsParams value) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                value,
                stereo,
                saturation);
    }

    public StudioRack withStereo(StereoParams value) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                dynamics,
                value,
                saturation);
    }

    public StudioRack withSaturation(SaturationParams value) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                pitch,
                dynamics,
                stereo,
                value);
    }

    public StudioRack withPitch(PitchParams value) {
        return new StudioRack(
                enabled,
                preampDb,
                balance,
                polarityInvert,
                loudness,
                loudnessAmount,
                bands,
                value,
                dynamics,
                stereo,
                saturation);
    }

    public StudioRack withBand(int index, EqBand band) {
        if (index < 0 || index >= bands.size() || band == null) {
            return this;
        }
        List<EqBand> next = new ArrayList<EqBand>(bands);
        next.set(index, band);
        return withBands(next);
    }

    public boolean active() {
        if (!enabled) {
            return false;
        }
        if (Math.abs(preampDb) > 1e-6 || Math.abs(balance) > 1e-6 || polarityInvert) {
            return true;
        }
        if (loudness && loudnessAmount > 1e-6) {
            return true;
        }
        if (pitch.active()) {
            return true;
        }
        if (dynamics.active() || stereo.active() || saturation.active()) {
            return true;
        }
        for (int i = 0; i < bands.size(); i++) {
            if (!bands.get(i).isTransparent()) {
                return true;
            }
        }
        return false;
    }

    public int activeBands() {
        int n = 0;
        for (int i = 0; i < bands.size(); i++) {
            if (!bands.get(i).isTransparent()) {
                n++;
            }
        }
        return n;
    }

    public int latencyFrames() {
        return latencyFrames(48_000);
    }

    public int latencyFrames(int rate) {
        int frames = 0;
        if (pitch.active()) {
            frames += PitchModule.LATENCY_FRAMES;
        }
        frames += stereo.latencyFrames(rate);
        if (dynamics.active() && dynamics.limiterOn) {
            frames += dynamics.lookaheadFrames(rate);
        }
        return frames;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String toString() {
        return "StudioRack{"
                + (enabled ? "on" : "off")
                + ", preamp "
                + preampDb
                + "dB, balance "
                + balance
                + ", "
                + activeBands()
                + " bands, loudness comp "
                + (loudness ? "on" : "off")
                + ", pitch "
                + pitch
                + ", dynamics "
                + dynamics
                + ", stereo "
                + stereo
                + ", saturation "
                + saturation
                + "}";
    }
}
