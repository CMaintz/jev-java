package io.github.cmaintz.jev;

/** Base type for every error surfaced by {@link TypeSafeClient}. */
public class JevException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final transient String responseBody;

    /**
     * @param message human-readable description
     * @param statusCode HTTP status that triggered the error, or 0 if not an HTTP response
     * @param responseBody raw response body, or null
     * @param cause underlying cause, or null
     */
    public JevException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /** HTTP status code, or 0 when the failure was not an HTTP response. */
    public int statusCode() {
        return statusCode;
    }

    /** Raw response body, or null. */
    public String responseBody() {
        return responseBody;
    }
}
