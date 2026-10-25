package dev.t1m3.qplayer.audio.decode;

import dev.t1m3.qplayer.audio.core.AudioBuf;

public interface Decoder extends AutoCloseable {
    TrackSpec spec();

    long framesDecoded();

    boolean isDrained();

    double seek(double secs);

    int decodeInto(AudioBuf out);

    @Override
    void close();

    interface Opener {
        Decoder open(String source, java.util.Map<String, String> headers);
    }

    static void install(Opener opener) {
        Holder.opener = opener;
    }

    static Decoder open(String source) {
        return open(source, java.util.Collections.<String, String>emptyMap());
    }

    static Decoder open(String source, java.util.Map<String, String> headers) {
        Opener opener = Holder.scoped.get();
        if (opener == null) opener = Holder.opener;
        if (opener == null) {
            throw new IllegalStateException(
                    "no decoder factory installed: the host must call Decoder.install(...) at"
                        + " startup");
        }
        return opener.open(
                source,
                headers == null ? java.util.Collections.<String, String>emptyMap() : headers);
    }

    // Android host: bind decoding and cancellation to one preparation worker.
    static <T> T withOpener(Opener opener, java.util.function.Supplier<T> work) {
        return withOpener(opener, () -> {}, work);
    }

    static <T> T withOpener(Opener opener, Runnable budget, java.util.function.Supplier<T> work) {
        Opener previous = Holder.scoped.get();
        Runnable previousBudget = Holder.budget.get();
        java.util.Map<String, Object> previousCache = Holder.cache.get();
        Holder.scoped.set(opener);
        Holder.budget.set(budget);
        Holder.cache.set(new java.util.LinkedHashMap<String, Object>(5, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Object> entry) {
                return size() > 4;
            }
        });
        try { return work.get(); }
        finally {
            if (previous == null) Holder.scoped.remove(); else Holder.scoped.set(previous);
            if (previousBudget == null) Holder.budget.remove(); else Holder.budget.set(previousBudget);
            if (previousCache == null) Holder.cache.remove(); else Holder.cache.set(previousCache);
        }
    }

    /** Small URI analysis cache whose lifetime ends with this preparation. */
    static java.util.Map<String, Object> preparationCache() { return Holder.cache.get(); }

    static void checkActive() {
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("AutoMix interrupted");
        Runnable budget = Holder.budget.get();
        if (budget != null) budget.run();
    }

    final class Holder {
        static final ThreadLocal<Runnable> budget = new ThreadLocal<>();
        static final ThreadLocal<java.util.Map<String, Object>> cache = new ThreadLocal<>();
        static final ThreadLocal<Opener> scoped = new ThreadLocal<>();
        static volatile Opener opener;

        private Holder() {}
    }
}
