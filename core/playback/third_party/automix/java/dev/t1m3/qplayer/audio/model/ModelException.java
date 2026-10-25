package dev.t1m3.qplayer.audio.model;

public class ModelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        CATALOG("bad model manifest: "),
        UNKNOWN(""),
        NOT_DOWNLOADABLE(""),
        HTTP("download failed: "),
        CHECKSUM_MISMATCH(""),
        TOO_LARGE(""),
        IO("local file operation failed: "),
        NOT_AVAILABLE("no beat model available: ");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    private final Kind kind;

    private final String modelId;

    private final String expectedSha256;

    private final String actualSha256;

    private ModelException(
            Kind kind,
            String message,
            Throwable cause,
            String modelId,
            String expectedSha256,
            String actualSha256) {
        super(message, cause);
        this.kind = kind;
        this.modelId = modelId;
        this.expectedSha256 = expectedSha256;
        this.actualSha256 = actualSha256;
    }

    public Kind kind() {
        return kind;
    }

    public String modelId() {
        return modelId;
    }

    public String expectedSha256() {
        return expectedSha256;
    }

    public String actualSha256() {
        return actualSha256;
    }

    public static ModelException catalog(String message) {
        return new ModelException(
                Kind.CATALOG, Kind.CATALOG.prefix() + message, null, null, null, null);
    }

    public static ModelException unknown(String id) {
        return new ModelException(
                Kind.UNKNOWN, "no such model in manifest `" + id + "`", null, null, null, null);
    }

    public static ModelException notDownloadable(String id, String reason) {
        return new ModelException(
                Kind.NOT_DOWNLOADABLE,
                "model `" + id + "` is not downloadable right now: " + reason,
                null,
                id,
                null,
                null);
    }

    public static ModelException http(String message) {
        return new ModelException(Kind.HTTP, Kind.HTTP.prefix() + message, null, null, null, null);
    }

    public static ModelException http(String message, Throwable cause) {
        return new ModelException(Kind.HTTP, Kind.HTTP.prefix() + message, cause, null, null, null);
    }

    public static ModelException checksumMismatch(String expected, String actual) {
        return new ModelException(
                Kind.CHECKSUM_MISMATCH,
                "checksum mismatch - the file is truncated or replaced, **will not be used**.\n"
                        + "expected "
                        + expected
                        + "\nactual "
                        + actual,
                null,
                null,
                expected,
                actual);
    }

    public static ModelException tooLarge(long got, long limit) {
        return new ModelException(
                Kind.TOO_LARGE,
                "response " + got + " bytes, over the limit " + limit,
                null,
                null,
                null,
                null);
    }

    public static ModelException io(String message) {
        return new ModelException(Kind.IO, Kind.IO.prefix() + message, null, null, null, null);
    }

    public static ModelException io(String message, Throwable cause) {
        return new ModelException(Kind.IO, Kind.IO.prefix() + message, cause, null, null, null);
    }

    public static ModelException notAvailable(String message) {
        return new ModelException(
                Kind.NOT_AVAILABLE, Kind.NOT_AVAILABLE.prefix() + message, null, null, null, null);
    }
}
