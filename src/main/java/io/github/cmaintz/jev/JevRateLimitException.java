package io.github.cmaintz.jev;

/** 429: the rate limit was exceeded and retries were exhausted. */
public final class JevRateLimitException extends JevException {

    private static final long serialVersionUID = 1L;

    JevRateLimitException(String message, String responseBody) {
        super(message, 429, responseBody, null);
    }
}
