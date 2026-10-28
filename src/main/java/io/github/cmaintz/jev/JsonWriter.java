package io.github.cmaintz.jev;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Minimal JSON serializer for the closed set of value types this SDK emits: {@code Map}
 * (object), {@code Iterable} (array), {@code String}, {@code Number}, {@code Boolean},
 * and {@code null}, plus {@link Image} and {@link TextWithImages}, which are written as
 * their reference text and collected. State passed to the client must be composed of these.
 */
final class JsonWriter {

    private final StringBuilder sb = new StringBuilder();
    private final Collection<Image> images;

    private JsonWriter(Collection<Image> images) {
        this.images = images;
    }

    /**
     * Serialize {@code value} to a JSON string.
     *
     * @throws IllegalArgumentException on an unsupported type or a non-finite number
     */
    static String write(Object value) {
        return write(value, new ArrayList<>());
    }

    /** Serialize {@code value}, adding every image it holds to {@code images}. */
    static String write(Object value, Collection<Image> images) {
        var writer = new JsonWriter(images);
        writer.writeValue(value);
        return writer.sb.toString();
    }

    private void writeValue(Object value) { // foundry-allow-smell: one arm per JSON type, nothing to split
        switch (value) {
            case null -> sb.append("null");
            case String s -> writeString(s);
            case Boolean b -> sb.append(b.booleanValue());
            case Number n -> writeNumber(n);
            case Map<?, ?> m -> writeObject(m);
            case Iterable<?> it -> writeArray(it);
            case Image image -> writeImage(image);
            case TextWithImages text -> writeText(text);
            default -> throw new IllegalArgumentException(
                    "Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private void writeImage(Image image) {
        images.add(image);
        writeString(image.reference());
    }

    private void writeText(TextWithImages text) {
        images.addAll(text.images());
        writeString(text.text());
    }

    private void writeNumber(Number n) {
        if ((n instanceof Double d && !Double.isFinite(d)) || (n instanceof Float f && !Float.isFinite(f))) {
            throw new IllegalArgumentException("JSON cannot represent the number " + n);
        }
        // A whole double is written without ".0", so a 2 that came back parsed as 2.0 reads as sent.
        boolean whole = (n instanceof Double || n instanceof Float)
                && n.doubleValue() == Math.rint(n.doubleValue())
                && Math.abs(n.doubleValue()) < 1e15;
        sb.append(whole ? Long.toString(n.longValue()) : n.toString());
    }

    private void writeObject(Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()));
            sb.append(':');
            writeValue(entry.getValue());
        }
        sb.append('}');
    }

    private void writeArray(Iterable<?> items) {
        sb.append('[');
        boolean first = true;
        for (var item : items) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(item);
        }
        sb.append(']');
    }

    private void writeString(String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            appendChar(value.charAt(i));
        }
        sb.append('"');
    }

    private void appendChar(char c) {
        switch (c) {
            case '"' -> sb.append("\\\"");
            case '\\' -> sb.append("\\\\");
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            case '\b' -> sb.append("\\b");
            case '\f' -> sb.append("\\f");
            default -> appendPlainOrEscaped(c);
        }
    }

    private void appendPlainOrEscaped(char c) {
        if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
        } else {
            sb.append(c);
        }
    }
}
