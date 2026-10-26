package io.github.cmaintz.jev;

import java.util.Map;

/**
 * A raw 2xx response body being decoded. Every way it can fail to be the documented
 * shape becomes the same {@link JevException}, carrying the body so callers can see
 * what the service actually sent.
 */
final class ResponseBody {

    private final String text;

    ResponseBody(String text) {
        this.text = text;
    }

    /** The body as parsed JSON. */
    Object parse() {
        try {
            return JsonParser.parse(text);
        } catch (IllegalArgumentException e) {
            throw new JevException("Malformed response: " + e.getMessage(), 0, text, e);
        }
    }

    /** The string under {@code field}, or a malformed-response error. */
    String requireString(Map<?, ?> node, String field) {
        if (node.get(field) instanceof String value) {
            return value;
        }
        throw malformed("missing string field '" + field + "'");
    }

    /** The error for a body that parsed but is not the documented shape. */
    JevException malformed(String detail) {
        return new JevException("Malformed response: " + detail + ".", 0, text, null);
    }
}
