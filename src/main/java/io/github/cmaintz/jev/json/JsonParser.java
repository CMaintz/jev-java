package io.github.cmaintz.jev.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser. Returns a tree of {@link Map} (objects, key
 * order preserved), {@link List} (arrays), {@link String}, {@link Double} (all numbers),
 * {@link Boolean}, and {@code null}. Throws {@link JsonParseException} on malformed input.
 */
public final class JsonParser {

    private final String text;
    private int pos;

    private JsonParser(String text) {
        this.text = text;
    }

    /** Parse {@code json} into a tree of maps, lists, and scalars. */
    public static Object parse(String json) {
        var parser = new JsonParser(json);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos < parser.text.length()) {
            throw parser.error("trailing characters");
        }
        return value;
    }

    private Object readValue() {
        char c = peek();
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        var map = new LinkedHashMap<String, Object>();
        pos++; // consume '{'
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            if (!consumeSeparator('}')) {
                return map;
            }
        }
    }

    private List<Object> readArray() {
        var list = new ArrayList<>();
        pos++; // consume '['
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            if (!consumeSeparator(']')) {
                return list;
            }
        }
    }

    /** After a value: consume ',' (continue) or the closing bracket (stop). */
    private boolean consumeSeparator(char close) {
        skipWhitespace();
        char c = next();
        if (c == ',') {
            return true;
        }
        if (c == close) {
            return false;
        }
        throw error("expected ',' or '" + close + "'");
    }

    private String readString() {
        expect('"');
        var out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                readEscape(out);
            } else {
                out.append(c);
            }
        }
    }

    private void readEscape(StringBuilder out) {
        char c = next();
        switch (c) {
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            case '/' -> out.append('/');
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 't' -> out.append('\t');
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'u' -> out.append(readUnicode());
            default -> throw error("invalid escape \\" + c);
        }
    }

    private char readUnicode() {
        if (pos + 4 > text.length()) {
            throw error("truncated \\u escape");
        }
        String hex = text.substring(pos, pos + 4);
        pos += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("invalid \\u escape: " + hex);
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && isNumberChar(text.charAt(pos))) {
            pos++;
        }
        String token = text.substring(start, pos);
        try {
            return Double.valueOf(token);
        } catch (NumberFormatException e) {
            throw error("invalid number: '" + token + "'");
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
    }

    private Boolean readBoolean() {
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw error("invalid literal");
    }

    private Object readNull() {
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw error("invalid literal");
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected '" + c + "'");
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private JsonParseException error(String message) {
        return new JsonParseException(message + " at position " + pos);
    }
}
