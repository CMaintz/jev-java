package io.github.cmaintz.jev;

/** 422: the request was rejected as malformed. */
public final class JevValidationException extends JevException {

    private static final long serialVersionUID = 1L;

    /** @param message description @param responseBody raw body, or null */
    public JevValidationException(String message, String responseBody) {
        super(message, 422, responseBody, null);
    }
}
