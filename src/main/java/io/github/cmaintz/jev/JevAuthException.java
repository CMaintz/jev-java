package io.github.cmaintz.jev;

/** 401: the API key is missing or invalid. */
public final class JevAuthException extends JevException {

    private static final long serialVersionUID = 1L;

    /** @param message description @param responseBody raw body, or null */
    public JevAuthException(String message, String responseBody) {
        super(message, 401, responseBody, null);
    }
}
