package io.github.cmaintz.jev;

/**
 * 400: the request broke one of the API's request rules, such as too much nesting or a
 * number that is not finite. With {@code max_tokens_exceeded} in the body, the request
 * is longer than the model's context length.
 */
public final class JevBadRequestException extends JevException {

    private static final long serialVersionUID = 1L;

    JevBadRequestException(String message, String responseBody) {
        super(message, 400, responseBody, null);
    }
}
