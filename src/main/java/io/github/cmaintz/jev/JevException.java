package io.github.cmaintz.jev;

/** Base type for every error surfaced by {@link TypeSafeClient}. */
public class JevException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP status that triggered the error, or 0. */
    private final int statusCode;

    /** Raw response body, or null. */
    private final String responseBody;

    /**
     * Creates an exception.
     *
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

    /** The typed exception for a non-2xx {@code status}. */
    static JevException forStatus(int status, String body) {
        return switch (status) {
            case 401 -> new JevAuthException("Unauthorized: invalid or missing API key.", body);
            case 422 -> new JevValidationException(
                    "Unprocessable entity: the request was rejected as malformed.", body);
            case 429 -> new JevRateLimitException("Rate limit exceeded; retries exhausted.", body);
            case 529 -> new JevOverloadedException("Service overloaded; retries exhausted.", body);
            default -> new JevException("Unexpected HTTP " + status + " from Jev.", status, body, null);
        };
    }

    /**
     * The HTTP status code.
     *
     * @return the status, or 0 when the failure was not an HTTP response
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The raw response body.
     *
     * @return the body, or null when there was none
     */
    public String responseBody() {
        return responseBody;
    }
}
