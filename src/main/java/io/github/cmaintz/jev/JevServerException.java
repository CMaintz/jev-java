package io.github.cmaintz.jev;

/**
 * A 5xx other than 529: the service failed and retries were exhausted. The exact status
 * is on {@link #statusCode()}.
 */
public final class JevServerException extends JevException {

    private static final long serialVersionUID = 1L;

    JevServerException(String message, int statusCode, String responseBody) {
        super(message, statusCode, responseBody, null);
    }
}
