package dev.t1m3.qplayer.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class Logger {
    public interface Sink {
        void log(String level, String message);

        void exception(String message, Throwable t);
    }

    static {
        try {
            java.util.logging.Logger.getLogger("org.jaudiotagger")
                    .setLevel(java.util.logging.Level.WARNING);
        } catch (RuntimeException ignored) {
        }
    }

    private static final Sink JUL_SINK =
            new Sink() {
                private final java.util.logging.Logger delegate =
                        java.util.logging.Logger.getLogger("musicplayer");

                @Override
                public void log(String level, String message) {
                    delegate.log(julLevel(level), message);
                }

                @Override
                public void exception(String message, Throwable t) {
                    delegate.log(Level.SEVERE, message, t);
                }

                private Level julLevel(String level) {
                    return "E".equals(level)
                            ? Level.SEVERE
                            : "W".equals(level) ? Level.WARNING : Level.INFO;
                }
            };

    private static volatile Sink sink = JUL_SINK;

    private static final int CAPACITY = 200;
    private static final Deque<String> RING = new ArrayDeque<>(CAPACITY);
    private static final AtomicLong VERSION = new AtomicLong();

    private Logger() {}

    public static void setSink(Sink s) {
        sink = s != null ? s : JUL_SINK;
    }

    public static void info(String str, Object... o) {
        record("I", format(str, o));
    }

    public static void debug(String str, Object... o) {
        if (!VERBOSE) {
            return;
        }
        record("D", format(str, o));
    }

    public static boolean verbose() {
        return VERBOSE;
    }

    public static void setVerbose(boolean on) {
        VERBOSE = on;
    }

    private static volatile boolean VERBOSE = initialVerbose();

    private static boolean initialVerbose() {
        if (Boolean.getBoolean("qplayer.verbose")) {
            return true;
        }
        String env = System.getenv("QPLAYER_VERBOSE");
        return env != null && !env.isEmpty() && !"0".equals(env) && !"false".equalsIgnoreCase(env);
    }

    public static void warn(String str, Object... o) {
        record("W", format(str, o));
    }

    public static void error(String str, Object... o) {
        record("E", format(str, o));
    }

    public static void success(String str, Object... o) {
        record("I", format(str, o));
    }

    public static void exception(Throwable ex) {
        ringAdd("E", ex.toString());
        sink.exception(ex.getMessage(), ex);
    }

    private static void record(String level, String msg) {
        ringAdd(level, msg);
        sink.log(level, msg);
    }

    private static void ringAdd(String level, String msg) {
        synchronized (RING) {
            if (RING.size() >= CAPACITY) RING.removeFirst();
            RING.addLast(level + " " + msg);
        }
        VERSION.incrementAndGet();
    }

    public static long version() {
        return VERSION.get();
    }

    public static List<String> snapshot() {
        synchronized (RING) {
            return new ArrayList<>(RING);
        }
    }

    public static void clear() {
        synchronized (RING) {
            RING.clear();
        }
        VERSION.incrementAndGet();
    }

    private static String format(String str, Object... o) {
        if (str == null || o == null || o.length == 0) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        int arg = 0;
        int i = 0;
        while (i < str.length()) {
            if (arg < o.length
                    && i + 1 < str.length()
                    && str.charAt(i) == '{'
                    && str.charAt(i + 1) == '}') {
                sb.append(String.valueOf(o[arg++]));
                i += 2;
            } else {
                sb.append(str.charAt(i++));
            }
        }
        return sb.toString();
    }
}
