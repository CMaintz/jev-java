package io.github.cmaintz.jev;

/** 529: the service was overloaded and retries were exhausted. */
public final class JevOverloadedException extends JevException {

    private static final long serialVersionUID = 1L;

    JevOverloadedException(String message, String responseBody) {
        super(message, 529, responseBody, null);
    }
}
