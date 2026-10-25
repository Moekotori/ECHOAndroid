package dev.t1m3.qplayer.audio.stem;

import dev.t1m3.qplayer.audio.model.StemSeparator;

public final class DspStemSeparator implements StemSeparator {

    private static final int N = 2048;

    private static final int HOP = 512;

    private static final int HALF = N / 2 + 1;

    private static final double LO_OUTER = 100.0;
    private static final double LO_INNER = 280.0;
    private static final double HI_INNER = 4000.0;
    private static final double HI_OUTER = 5200.0;

    private static final double MIN_COVERAGE = 0.25;

    private final double vocalK;

    private static final ThreadLocal<Scratch> SCRATCH = new ThreadLocal<Scratch>() {
        @Override
        protected Scratch initialValue() {
            return new Scratch();
        }
    };

    private static final class Scratch {
        int frames;
        double[] accInsL = new double[0];
        double[] accInsR = new double[0];
        double[] accVoc = new double[0];
        double[] winSum = new double[0];

        final double[] specA = new double[N];
        final double[] specB = new double[N];
        final double[] specC = new double[N];
        final double[] specD = new double[N];
        final double[] specE = new double[N];
        final double[] specF = new double[N];
        final double[] specG = new double[N];
        final double[] specH = new double[N];
        final double[] specI = new double[N];
        final double[] specJ = new double[N];
    }

    private final double[] winCache = new double[N];
    private boolean winReady;

    private int gainRate = -1;
    private double[] gainCache;

    private double[] window() {
        if (!winReady) {
            for (int i = 0; i < N; i++) {
                winCache[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1));
            }
            winReady = true;
        }
        return winCache;
    }

    private double[] centerGain(int sampleRate) {
        if (gainCache != null && gainRate == sampleRate) {
            return gainCache;
        }
        double[] out = new double[HALF];
        for (int b = 0; b < HALF; b++) {
            double f = (double) b * sampleRate / N;
            double g;
            if (f < LO_OUTER) {
                g = 1.0;
            } else if (f < LO_INNER) {
                g = 1.0 + (vocalK - 1.0) * 0.5
                        * (1 - Math.cos(Math.PI * (f - LO_OUTER) / (LO_INNER - LO_OUTER)));
            } else if (f <= HI_INNER) {
                g = vocalK;
            } else if (f < HI_OUTER) {
                g = vocalK + (1.0 - vocalK) * 0.5
                        * (1 - Math.cos(Math.PI * (f - HI_INNER) / (HI_OUTER - HI_INNER)));
            } else {
                g = 1.0;
            }
            out[b] = g;
        }
        gainCache = out;
        gainRate = sampleRate;
        return out;
    }

    public DspStemSeparator() {
        this(0.0);
    }

    public DspStemSeparator(double vocalK) {
        if (Double.isNaN(vocalK) || vocalK < 0.0 || vocalK > 1.0) {
            throw new IllegalArgumentException(
                    "vocalK is the vocal retention amount and must be within [0,1] (0 = fully removed, i.e. instrumental; 1 = passed through), got " + vocalK);
        }
        this.vocalK = vocalK;
    }

    public double vocalK() {
        return vocalK;
    }

    @Override
    public StemSet separate(float[] left, float[] right, int sampleRate) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("neither channel may be null (for mono material pass the same buffer twice)");
        }
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "both channels must have the same length (the seam mixes them back time-aligned), got "
                            + left.length + " ≠ " + right.length);
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sample rate must be positive, got " + sampleRate);
        }
        int frames = left.length;
        if (frames < N) {
            return passThrough(left, right);
        }

        double[] win = window();

        double[] centerGain = centerGain(sampleRate);

        float[] insL = new float[frames];
        float[] insR = new float[frames];
        float[] vocL = new float[frames];
        float[] vocR = new float[frames];

        int stftFrames = 0;
        for (int off = 0; off + N <= frames; off += HOP) {
            stftFrames++;
        }
        int threads = separationThreads(stftFrames);
        if (threads > 1) {

            int per = (frames + threads - 1) / threads;
            java.util.List<java.util.concurrent.Future<?>> tasks =
                    new java.util.ArrayList<java.util.concurrent.Future<?>>(threads);
            for (int t = 0; t < threads; t++) {
                final int from = t * per;
                final int to = Math.min(frames, from + per);
                if (from >= to) {
                    break;
                }
                tasks.add(SEPARATION_POOL.submit(new Runnable() {
                    @Override
                    public void run() {
                        processRange(left, right, win, centerGain, frames, from, to,
                                insL, insR, vocL, vocR);
                    }
                }));
            }
            for (java.util.concurrent.Future<?> task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IllegalStateException(e.getCause());
                }
            }
            return new StemSet(vocL, vocR, insL, insR);
        }
        processRange(left, right, win, centerGain, frames, 0, frames, insL, insR, vocL, vocR);
        return new StemSet(vocL, vocR, insL, insR);
    }

    private static int separationThreads(int stftFrames) {
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(1, Math.min(MAX_THREADS, cores - 1));
        return stftFrames < MIN_FRAMES_TO_SPLIT ? 1 : threads;
    }

    private static final int MAX_THREADS = 4;

    private static final int MIN_FRAMES_TO_SPLIT = 8;

    private static final java.util.concurrent.ExecutorService SEPARATION_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(
                    Math.max(1, Math.min(MAX_THREADS,
                            Runtime.getRuntime().availableProcessors() - 1)),
                    new java.util.concurrent.ThreadFactory() {
                        private final java.util.concurrent.atomic.AtomicInteger n =
                                new java.util.concurrent.atomic.AtomicInteger();
                        @Override
                        public Thread newThread(Runnable r) {
                            Thread th = new Thread(r, "qplayer-stem-" + n.getAndIncrement());
                            th.setDaemon(true);
                            return th;
                        }
                    });

    private static void processRange(float[] left, float[] right, double[] win,
                                     double[] gain, int frames, int from, int to,
                                     float[] insL, float[] insR, float[] vocL, float[] vocR) {
        Scratch scratch = SCRATCH.get();
        if (scratch.frames < frames) {
            scratch.frames = frames;
            scratch.accInsL = new double[frames];
            scratch.accInsR = new double[frames];
            scratch.accVoc = new double[frames];
            scratch.winSum = new double[frames];
        }
        double[] accInsL = scratch.accInsL;
        double[] accInsR = scratch.accInsR;
        double[] accVoc = scratch.accVoc;
        double[] winSum = scratch.winSum;

        java.util.Arrays.fill(accInsL, from, to, 0.0);
        java.util.Arrays.fill(accInsR, from, to, 0.0);
        java.util.Arrays.fill(accVoc, from, to, 0.0);
        java.util.Arrays.fill(winSum, from, to, 0.0);
        double[] lRe = scratch.specA;
        double[] lIm = scratch.specB;
        double[] rRe = scratch.specC;
        double[] rIm = scratch.specD;
        double[] iLRe = scratch.specE;
        double[] iLIm = scratch.specF;
        double[] iRRe = scratch.specG;
        double[] iRIm = scratch.specH;
        double[] vRe = scratch.specI;
        double[] vIm = scratch.specJ;

        int firstOff = 0;
        if (from > N) {
            firstOff = ((from - N) / HOP + 1) * HOP;
        }
        int lastOff = frames - N;
        for (int off = firstOff; off <= lastOff && off < to; off += HOP) {
            for (int i = 0; i < N; i++) {
                lRe[i] = left[off + i] * win[i];
                lIm[i] = 0;
                rRe[i] = right[off + i] * win[i];
                rIm[i] = 0;
            }
            fft(lRe, lIm, false);
            fft(rRe, rIm, false);

            for (int b = 0; b < HALF; b++) {
                double g = gain[b];
                double cRe = (lRe[b] + rRe[b]) * 0.5;
                double cIm = (lIm[b] + rIm[b]) * 0.5;
                double sRe = (lRe[b] - rRe[b]) * 0.5;
                double sIm = (lIm[b] - rIm[b]) * 0.5;
                iLRe[b] = sRe + cRe * g;
                iLIm[b] = sIm + cIm * g;
                iRRe[b] = -sRe + cRe * g;
                iRIm[b] = -sIm + cIm * g;
                vRe[b] = cRe * (1.0 - g);
                vIm[b] = cIm * (1.0 - g);
            }

            for (int b = 1; b < HALF - 1; b++) {
                iLRe[N - b] = iLRe[b];
                iLIm[N - b] = -iLIm[b];
                iRRe[N - b] = iRRe[b];
                iRIm[N - b] = -iRIm[b];
                vRe[N - b] = vRe[b];
                vIm[N - b] = -vIm[b];
            }
            fft(iLRe, iLIm, true);
            fft(iRRe, iRIm, true);
            fft(vRe, vIm, true);
            double scale = 1.0 / N;

            for (int i = 0; i < N; i++) {
                double w = win[i];
                accInsL[off + i] += iLRe[i] * scale * w;
                accInsR[off + i] += iRRe[i] * scale * w;
                accVoc[off + i] += vRe[i] * scale * w;
                winSum[off + i] += w * w;
            }
        }

        for (int i = from; i < to; i++) {
            double ws = winSum[i];
            if (ws >= MIN_COVERAGE) {

                insL[i] = clampUnit(accInsL[i] / ws);
                insR[i] = clampUnit(accInsR[i] / ws);
                float v = clampUnit(accVoc[i] / ws);
                vocL[i] = v;
                vocR[i] = v;
                continue;
            }

            double cover = ws / MIN_COVERAGE;
            double processedL = 0;
            double processedR = 0;
            double processedV = 0;
            if (cover > 0.0) {
                processedL = accInsL[i] / ws;
                processedR = accInsR[i] / ws;
                processedV = accVoc[i] / ws;
            }

            insL[i] = clampUnit(left[i] + cover * (processedL - left[i]));
            insR[i] = clampUnit(right[i] + cover * (processedR - right[i]));
            float v = clampUnit(cover * processedV);
            vocL[i] = v;
            vocR[i] = v;
        }
    }

    @Override
    public void close() {

    }

    private static StemSet passThrough(float[] left, float[] right) {
        int frames = left.length;
        return new StemSet(new float[frames], new float[frames],
                left.clone(), right.clone());
    }

    private static float clampUnit(double v) {
        return (float) Math.max(-1.0, Math.min(1.0, v));
    }

    private static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? 1 : -1);
            double wRe = Math.cos(ang);
            double wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double curRe = 1;
                double curIm = 0;
                for (int j = 0; j < len / 2; j++) {
                    double uRe = re[i + j];
                    double uIm = im[i + j];
                    double vRe = re[i + j + len / 2] * curRe - im[i + j + len / 2] * curIm;
                    double vIm = re[i + j + len / 2] * curIm + im[i + j + len / 2] * curRe;
                    re[i + j] = uRe + vRe;
                    im[i + j] = uIm + vIm;
                    re[i + j + len / 2] = uRe - vRe;
                    im[i + j + len / 2] = uIm - vIm;
                    double newRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = newRe;
                }
            }
        }
    }
}
