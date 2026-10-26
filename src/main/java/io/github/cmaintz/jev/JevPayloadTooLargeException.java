package io.github.cmaintz.jev;

/** 413: the request body is over the API's 32 MiB limit. */
public final class JevPayloadTooLargeException extends JevException {

    private static final long serialVersionUID = 1L;

    JevPayloadTooLargeException(String message, String responseBody) {
        super(message, 413, responseBody, null);
    }
}
