package dev.t1m3.qplayer.audio.core;

public class AudioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Kind {

        IO,

        DECODE,

        UNSUPPORTED_FORMAT,

        OUTPUT,

        CONFIG
    }

    private final Kind kind;

    public AudioException(Kind kind, String message) {
        super(prefix(kind) + message);
        this.kind = kind;
    }

    public AudioException(Kind kind, String message, Throwable cause) {
        super(prefix(kind) + message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static AudioException io(String message, Throwable cause) {
        return new AudioException(Kind.IO, message, cause);
    }

    public static AudioException decode(String message) {
        return new AudioException(Kind.DECODE, message);
    }

    public static AudioException unsupportedFormat(String message) {
        return new AudioException(Kind.UNSUPPORTED_FORMAT, message);
    }

    public static AudioException output(String message) {
        return new AudioException(Kind.OUTPUT, message);
    }

    public static AudioException config(String message) {
        return new AudioException(Kind.CONFIG, message);
    }

    private static String prefix(Kind kind) {
        switch (kind) {
            case IO: return "io: ";
            case DECODE: return "decode: ";
            case UNSUPPORTED_FORMAT: return "unsupported format: ";
            case OUTPUT: return "output: ";
            default: return "config: ";
        }
    }
}
