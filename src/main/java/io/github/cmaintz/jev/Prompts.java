package io.github.cmaintz.jev;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and freezes question instructions and criteria. Each is text, an {@link Image},
 * {@link TextWithImages}, or structured
 * JSON built from {@link Map}s (text keys) and {@link List}s holding text, numbers,
 * booleans, nulls and further maps and lists. Structured values are deep-copied into
 * unmodifiable collections so a caller's later changes never reach the question.
 */
final class Prompts {

    /** The API's nesting limit for each of instructions and criteria. */
    private static final int MAX_DEPTH = 64;

    private Prompts() {}

    /** Instructions: non-blank text, an image, text with images, or a frozen copy of a non-empty Map or List. */
    static Object require(Object value, String name) {
        return require(value, name, 0);
    }

    /**
     * One criteria value (an option, a level, a gloss). It already sits one level inside
     * {@code criteria}, which the 64-level limit applies to as a whole.
     */
    static Object criterion(Object value, String name) {
        return require(value, name, 1);
    }

    /** Like {@link #criterion} but null stays null, for optional glosses. */
    static Object optionalCriterion(Object value, String name) {
        return value == null ? null : criterion(value, name);
    }

    private static Object require(Object value, String name, int depth) {
        if (value instanceof String text) {
            return Preconditions.requireText(text, name);
        }
        if (value instanceof Image || value instanceof TextWithImages) {
            return value;
        }
        if ((value instanceof Map<?, ?> map && !map.isEmpty()) || (value instanceof List<?> list && !list.isEmpty())) {
            return freeze(value, name, depth);
        }
        throw new IllegalArgumentException(name + " must be text, an Image, or a non-empty Map or List.");
    }

    private static Object freeze(Object value, String name, int depth) {
        return switch (value) {
            case null -> null;
            case String text -> text;
            case Boolean flag -> flag;
            case Image image -> image;
            case TextWithImages text -> text;
            case Number number -> immutableNumber(number, name);
            case Map<?, ?> map -> freezeMap(map, name, deeper(depth, name));
            case List<?> list -> freezeList(list, name, deeper(depth, name));
            default -> throw new IllegalArgumentException(name + " holds a "
                    + value.getClass().getSimpleName() + "; use text, numbers, booleans, Maps and Lists.");
        };
    }

    private static Map<String, Object> freezeMap(Map<?, ?> map, String name, int depth) {
        var copy = new LinkedHashMap<String, Object>();
        map.forEach((key, entry) -> {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException(name + " has a non-text key: " + key);
            }
            copy.put(text, freeze(entry, name, depth));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static List<Object> freezeList(List<?> list, String name, int depth) {
        var copy = new ArrayList<Object>(list.size());
        list.forEach(item -> copy.add(freeze(item, name, depth)));
        return Collections.unmodifiableList(copy);
    }

    private static int deeper(int depth, String name) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException(name + " nests deeper than " + MAX_DEPTH + " levels.");
        }
        return depth + 1;
    }

    // Mutable or custom Numbers (AtomicLong, LongAdder) would break the frozen copy and equality.
    private static Number immutableNumber(Number number, String name) {
        boolean immutable = number instanceof Integer
                || number instanceof Long
                || number instanceof Short
                || number instanceof Byte
                || number instanceof Double
                || number instanceof Float
                || number instanceof java.math.BigInteger
                || number instanceof java.math.BigDecimal;
        if (!immutable) {
            throw new IllegalArgumentException(name + " holds a "
                    + number.getClass().getSimpleName() + "; use Integer, Long, Double, BigInteger or BigDecimal.");
        }
        if (!Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(name + " holds a number JSON cannot carry: " + number);
        }
        return number;
    }
}
