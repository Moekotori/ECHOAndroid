package dev.t1m3.qplayer.audio.mix;

import dev.t1m3.qplayer.audio.core.AudioBuf;
import dev.t1m3.qplayer.audio.core.AudioException;
import dev.t1m3.qplayer.audio.core.ChannelLayout;
import dev.t1m3.qplayer.audio.core.SourceFormat;
import dev.t1m3.qplayer.audio.decode.Decoder;
import dev.t1m3.qplayer.audio.decode.TrackSpec;
import dev.t1m3.qplayer.audio.dsp.BinauralRenderer;
import dev.t1m3.qplayer.audio.dsp.Gain;
import dev.t1m3.qplayer.audio.dsp.PitchShifter;
import dev.t1m3.qplayer.audio.dsp.Resampler;

public final class Deck {

    static final int DECODE_BUF_FRAMES = 65_536;

    static final int PITCH_CHUNK_FACTOR = 4;

    public static final int RENDER_FRAMES = 4096;

    private static final float F32_EPSILON = 1.1920929E-7f;

    private final String source;

    private final Decoder decoder;

    private volatile MaterialHook materialHook;

    public void setMaterialHook(MaterialHook hook) {
        this.materialHook = hook;
    }

    public MaterialHook materialHook() {
        return materialHook;
    }
    private final Resampler resampler;
    private final Gain gain;

    private PitchShifter pitch;

    private BinauralRenderer spatial;

    private boolean spatialEnabled;

    private final int srcChannels;

    private final int dstChannels;

    private final AudioBuf decBuf;

    private final AudioBuf rsBuf;

    private final AudioBuf mapped;

    private final AudioBuf carry;

    private int carryFrames;

    private long outFrames;

    private boolean drained;

    private boolean tailDrained;

    private int pktFrames;

    private int pktFed;

    public static Deck open(String source, int outRate, int outChannels) {
        return open(source, java.util.Collections.<String, String>emptyMap(), outRate, outChannels);
    }

    public static Deck open(String source, java.util.Map<String, String> headers,
                            int outRate, int outChannels) {

        String sourceHandle = source;
        Decoder decoder = headers == null || headers.isEmpty()
                ? Decoder.open(sourceHandle)
                : Decoder.open(sourceHandle, headers);
        TrackSpec spec = decoder.spec();
        int srcChannels = spec.layout.count();

        Resampler resampler = new Resampler(spec.sampleRate, outRate, srcChannels);
        Gain gain = new Gain(outRate);

        int cap = RENDER_FRAMES;
        ChannelLayout dstLayout = ChannelLayout.of(outChannels);

        return new Deck(
                sourceHandle,
                decoder,
                resampler,
                gain,
                srcChannels,
                outChannels,
                new AudioBuf(spec.layout, DECODE_BUF_FRAMES),
                new AudioBuf(spec.layout, cap),
                new AudioBuf(dstLayout, cap),
                new AudioBuf(dstLayout, cap));
    }

    private Deck(String source, Decoder decoder, Resampler resampler, Gain gain,
                 int srcChannels, int dstChannels,
                 AudioBuf decBuf, AudioBuf rsBuf, AudioBuf mapped, AudioBuf carry) {
        this.source = source;
        this.decoder = decoder;
        this.resampler = resampler;
        this.gain = gain;
        this.srcChannels = srcChannels;
        this.dstChannels = dstChannels;
        this.decBuf = decBuf;
        this.rsBuf = rsBuf;
        this.mapped = mapped;
        this.carry = carry;
        this.carryFrames = 0;
        this.outFrames = 0L;
        this.drained = false;
        this.tailDrained = false;
        this.pktFrames = 0;
        this.pktFed = 0;
        this.pitch = null;
        this.spatial = null;
        this.spatialEnabled = false;
    }

    public String source() {
        return source;
    }

    public TrackSpec spec() {
        return decoder.spec();
    }

    public SourceFormat sourceFormat() {
        return decoder.spec().sourceFormat();
    }

    public int inRate() {
        return resampler.inRate();
    }

    public int outRate() {
        return resampler.outRate();
    }

    public boolean isPassthrough() {
        return resampler.isPassthrough() && pitch == null && !spatialIsActive();
    }

    public boolean spatialIsActive() {
        return spatialEnabled && spatial != null;
    }

    public long outFrames() {
        return outFrames;
    }

    public boolean isDrained() {
        return drained;
    }

    public float pitchSemitones() {
        PitchShifter ps = pitch;
        return ps == null ? 0.0f : PitchShifter.ratioToSemitones(ps.ratio());
    }

    public void setGainDb(float db) {
        gain.setTargetDb(db);
    }

    public void engagePitch() {
        if (pitch != null) {
            return;
        }
        pitch = idlePitchShifter(outRate(), dstChannels);
    }

    public static PitchShifter idlePitchShifter(int outRate, int outChannels) {
        int chunk = RENDER_FRAMES * PITCH_CHUNK_FACTOR;
        return new PitchShifter(outChannels, outRate, 0.0f, chunk);
    }

    public void engagePitchWith(PitchShifter shifter) {
        if (shifter == null) {

            throw new IllegalArgumentException("pitch shifter must not be null");
        }
        if (pitch != null) {
            return;
        }
        pitch = shifter;
    }

    public void setPitch(float semitones) {
        PitchShifter ps = pitch;
        if (ps != null) {
            ps.setSemitones(semitones);
            return;
        }

        if (Math.abs(semitones) < 1e-6f) {
            return;
        }
        int rate = outRate();
        int channels = dstChannels;
        int chunk = RENDER_FRAMES * PITCH_CHUNK_FACTOR;

        pitch = new PitchShifter(channels, rate, semitones, chunk);
    }

    public void setSpatial(boolean enabled) {
        spatialEnabled = enabled;
        if (!enabled || spatial != null) {
            return;
        }
        if (dstChannels != 2) {

            return;
        }

        ChannelLayout srcLayout = decoder.spec().layout;
        spatial = new BinauralRenderer(srcLayout, outRate());
    }

    public double seek(double secs) {

        double actual = decoder.seek(secs);

        resampler.reset();

        gain.snapTo(gain.target());

        pitch = null;

        if (spatial != null) {
            spatial.reset();
        }

        pktFrames = 0;
        pktFed = 0;

        carryFrames = 0;
        drained = false;

        tailDrained = false;

        outFrames = (long) Math.max(actual * (double) outRate(), 0.0);

        return actual;
    }

    public boolean gainIsUnity() {
        return Math.abs(gain.target() - 1.0f) < F32_EPSILON && !gain.isRamping();
    }

    public Long remainingFrames() {
        Long totalSrc = decoder.spec().totalFrames;
        if (totalSrc == null) {
            return null;
        }
        double ratio = (double) outRate() / (double) Math.max(inRate(), 1);
        long totalOut = (long) ((double) totalSrc.longValue() * ratio);

        return Long.valueOf(Math.max(totalOut - outFrames, 0L));
    }

    public int render(AudioBuf out) {

        if (out.channels() != dstChannels) {
            throw new IllegalArgumentException(
                    "output channel count differs from what the deck agreed on at open: " + out.channels() + " ≠ " + dstChannels);
        }

        if (out.capacityFrames() < RENDER_FRAMES) {
            throw AudioException.config("the output buffer must hold at least " + RENDER_FRAMES
                    + " frames, currently only " + out.capacityFrames());
        }
        int want = RENDER_FRAMES;

        int written = 0;
        if (carryFrames > 0) {
            int n = carryFrames;
            copyFramesInto(carry, out, 0, n);
            carryFrames = 0;
            written = n;
            outFrames += (long) n;

        }

        while (true) {

            if (pktFed < pktFrames) {
                int took = resampler.pushFrom(decBuf, pktFed);
                pktFed += took;
            }

            int produced = resampler.pull(rsBuf);

            if (produced == 0 && drained && !tailDrained && pktFed >= pktFrames) {
                tailDrained = true;
                produced = resampler.drain(rsBuf);
            }

            if (produced > 0) {

                if (spatialIsActive()) {

                    spatial.render(rsBuf, mapped);
                } else {
                    Mixer.mapChannels(rsBuf, mapped);
                }

                gain.process(mapped);

                PitchShifter ps = pitch;
                if (ps != null) {
                    int count = mapped.frames();
                    int fed = 0;
                    while (fed < count) {
                        int took = ps.pushFrom(mapped, fed);
                        if (took == 0) {
                            break;
                        }
                        fed += took;
                    }
                    if (fed != count) {
                        throw new IllegalStateException("the pitch shifter could not take the whole block (" + fed + "/" + count
                                + ") - the ring buffer is too small and frames are being dropped silently");
                    }
                    int got = ps.pull(out, written);
                    written += got;
                    outFrames += (long) got;
                    if (written >= want) {
                        break;
                    }

                    continue;
                }

                int room = want - written;
                int take = Math.min(mapped.frames(), room);
                copyFramesInto(mapped, out, written, take);
                int extra = mapped.frames() - take;
                if (extra > 0) {
                    copyFromOffset(mapped, take, carry, extra);
                    carryFrames = extra;
                }
                written += take;
                outFrames += (long) take;
                if (written >= want) {
                    break;
                }

                continue;
            }

            if (pktFed < pktFrames) {
                break;
            }

            if (drained) {

                break;
            }

            int got = decoder.decodeInto(decBuf);

            MaterialHook hook = materialHook;
            if (hook != null && got > 0) {
                hook.onDecoded(decBuf, got, Double.NaN);
            }
            if (got == 0) {
                drained = true;

                continue;
            }
            pktFrames = got;
            pktFed = 0;
        }

        PitchShifter tail = pitch;
        if (tail != null && drained && written < want) {
            int got = tail.drain(out, written);
            written += got;
            outFrames += (long) got;
        }

        out.setFrames(written);
        return written;
    }

    public void close() {
        decoder.close();
    }

    static void copyFramesInto(AudioBuf src, AudioBuf dst, int offset, int n) {
        if (n == 0) {
            return;
        }

        float[] s = src.backingArray();
        float[] d = dst.backingArray();
        for (int c = 0; c < dst.channels(); c++) {
            System.arraycopy(s, src.channelBase(c), d, dst.channelBase(c) + offset, n);
        }
    }

    static void copyFromOffset(AudioBuf src, int from, AudioBuf dst, int n) {
        if (n == 0) {
            dst.setFrames(0);
            return;
        }
        float[] s = src.backingArray();
        float[] d = dst.backingArray();
        for (int c = 0; c < dst.channels(); c++) {
            System.arraycopy(s, src.channelBase(c) + from, d, dst.channelBase(c), n);
        }
        dst.setFrames(n);
    }

    @Override
    public String toString() {
        PitchShifter ps = pitch;
        return "Deck{codec=" + decoder.spec().codec
                + ", inRate=" + resampler.inRate()
                + ", outRate=" + resampler.outRate()
                + ", channels=(" + srcChannels + ", " + dstChannels + ")"
                + ", pitchSemitones=" + (ps == null ? "null" : Float.toString(pitchSemitones()))
                + ", spatial=" + spatialIsActive()
                + ", outFrames=" + outFrames
                + ", drained=" + drained + '}';
    }
}
