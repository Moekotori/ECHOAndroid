package dev.t1m3.qplayer.audio.mix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public final class FlowSorter {

    static final int CACHE_MAX = 400;

    public static final class Request {

        public final String key;

        public final String source;

        public final Map<String, String> headers;

        public final String label;

        public Request(String key, String source, Map<String, String> headers, String label) {
            this.key = key;
            this.source = source;
            this.headers = headers;
            this.label = label;
        }
    }

    public static final class Failure {

        public final String label;

        public final String detail;

        public Failure(String label, String detail) {
            this.label = label;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static final class Result {

        public final List<FlowFeatures> features;

        public final List<Failure> failures;

        public final boolean cancelled;

        Result(List<FlowFeatures> features, List<Failure> failures, boolean cancelled) {
            this.features = features;
            this.failures = failures;
            this.cancelled = cancelled;
        }
    }

    public interface Listener {

        void onProgress(int done, int total);

        void onFinished(Result result);

        void onFailed(String message);
    }

    public interface Resolver {

        Resolution resolve(int index, Request request);
    }

    public static final class Resolution {

        public final String source;

        public final Map<String, String> headers;

        public final String reason;

        public Resolution(String source, Map<String, String> headers, String reason) {
            this.source = source;
            this.headers = headers;
            this.reason = reason == null ? "" : reason;
        }

        public static Resolution ok(String source, Map<String, String> headers) {
            return new Resolution(source, headers, "");
        }

        public static Resolution failed(String reason) {
            return new Resolution(null, null, reason);
        }
    }

    private final Executor executor;
    private final FlowAnalyzer analyzer;

    private volatile Resolver resolver;

    public void setResolver(Resolver value) {
        this.resolver = value;
    }

    private final Map<String, FlowFeatures> cache =
            Collections.synchronizedMap(new LinkedHashMap<String, FlowFeatures>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, FlowFeatures> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    private final java.util.concurrent.atomic.AtomicLong generation =
            new java.util.concurrent.atomic.AtomicLong();

    public FlowSorter(Executor executor, FlowAnalyzer analyzer) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (analyzer == null) {
            throw new IllegalArgumentException("analyzer must not be null");
        }
        this.executor = executor;
        this.analyzer = analyzer;
    }

    public void start(final List<Request> requests, final Listener listener) {
        final List<Request> input = requests == null
                ? Collections.<Request>emptyList() : new ArrayList<>(requests);
        final long myGeneration = generation.incrementAndGet();
        executor.execute(new Runnable() {
            @Override
            public void run() {

                runBatch(input, myGeneration, listener);
            }
        });
    }

    public void cancel() {
        generation.incrementAndGet();
    }

    public int cachedCount() {
        return cache.size();
    }

    private void runBatch(List<Request> requests, long myGeneration, Listener listener) {
        int total = requests.size();
        List<FlowFeatures> features = new ArrayList<>(total);
        List<Failure> failures = new ArrayList<>();
        int done = 0;
        boolean cancelled = false;
        try {
            for (int i = 0; i < total; i++) {
                if (generation.get() != myGeneration) {

                    cancelled = true;
                    break;
                }
                Request request = requests.get(i);
                FlowFeatures measured = null;

                measured = cache.get(request.key);
                if (measured == null) {
                    String source = request.source;
                    Map<String, String> headers = request.headers;
                    String missing = null;
                    if (source == null || source.isEmpty()) {

                        Resolver current = resolver;
                        Resolution resolution = null;
                        if (current != null) {
                            try {
                                resolution = current.resolve(i, request);
                            } catch (Throwable error) {
                                resolution = Resolution.failed(message(error));
                            }
                        }
                        if (resolution == null || resolution.source == null
                                || resolution.source.isEmpty()) {
                            missing = resolution != null && !resolution.reason.isEmpty()
                                    ? resolution.reason : "no usable source handle";
                        } else {
                            source = resolution.source;
                            headers = resolution.headers;
                        }
                    }
                    if (missing != null) {
                        failures.add(new Failure(request.label, missing));
                    } else {
                        try {
                            measured = analyzer.analyze(source, headers);
                            if (measured != null) {
                                cache.put(request.key, measured);
                            }
                        } catch (Throwable error) {
                            measured = null;
                            failures.add(new Failure(request.label, message(error)));
                        }
                    }
                }
                features.add(measured);
                done++;
                listener.onProgress(done, total);
            }
            while (features.size() < total) {
                features.add(null);
            }
            listener.onFinished(new Result(features, failures, cancelled));
        } catch (Throwable fatal) {

            listener.onFailed(message(fatal));
        }
    }

    private static String message(Throwable error) {
        String m = error == null ? null : error.getMessage();
        return m == null || m.isEmpty() ? String.valueOf(error) : m;
    }

    public static String cacheKey(String identity, long sizeBytes, long modifiedMs) {
        return (identity == null ? "" : identity) + '\u0000' + sizeBytes + '\u0000' + modifiedMs;
    }
}
