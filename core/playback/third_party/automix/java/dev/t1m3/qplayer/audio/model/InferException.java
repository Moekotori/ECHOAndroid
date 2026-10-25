package dev.t1m3.qplayer.audio.model;

public class InferException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        IO("cannot read model file: "),
        LOAD("cannot load ONNX graph: "),
        SHAPE("shape inference failed: "),
        PLAN("cannot build execution plan: "),
        RUN("inference run failed: "),
        OUTPUT("unexpected output: ");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    private final Kind kind;

    public InferException(Kind kind, String message) {
        super(kind.prefix() + message);
        this.kind = kind;
    }

    public InferException(Kind kind, String message, Throwable cause) {
        super(kind.prefix() + message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static InferException io(String message) {
        return new InferException(Kind.IO, message);
    }

    public static InferException io(String message, Throwable cause) {
        return new InferException(Kind.IO, message, cause);
    }

    public static InferException load(String message) {
        return new InferException(Kind.LOAD, message);
    }

    public static InferException shape(String message) {
        return new InferException(Kind.SHAPE, message);
    }

    public static InferException plan(String message) {
        return new InferException(Kind.PLAN, message);
    }

    public static InferException run(String message) {
        return new InferException(Kind.RUN, message);
    }

    public static InferException output(String message) {
        return new InferException(Kind.OUTPUT, message);
    }

    public static String brief(Throwable e) {
        return brief(e == null ? "" : String.valueOf(e.getMessage()));
    }

    public static String brief(String message) {
        String s = message == null ? "" : message;
        int nl = s.indexOf('\n');
        String first = (nl >= 0 ? s.substring(0, nl) : s).trim();
        if (first.isEmpty()) {
            first = s.trim();
        }
        if (first.codePointCount(0, first.length()) > 300) {
            int end = first.offsetByCodePoints(0, 300);
            return first.substring(0, end) + "…";
        }
        return first;
    }
}
