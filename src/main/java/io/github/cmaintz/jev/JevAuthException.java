package io.github.cmaintz.jev;

/** 401: the API key is missing or invalid. */
public final class JevAuthException extends JevException {

    private static final long serialVersionUID = 1L;

    JevAuthException(String message, String responseBody) {
        super(message, 401, responseBody, null);
    }
}
