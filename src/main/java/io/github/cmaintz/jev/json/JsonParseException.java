package io.github.cmaintz.jev.json;

/** Thrown when {@link JsonParser} encounters malformed JSON. */
public final class JsonParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param message what went wrong, including the position */
    public JsonParseException(String message) {
        super(message);
    }
}
