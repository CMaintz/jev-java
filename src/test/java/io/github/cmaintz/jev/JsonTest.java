package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesNestedStructuresAndNumberForms() {
        var root = (Map<?, ?>) JsonParser.parse("{\"a\":[1,2.5,-3e2],\"b\":{\"c\":true,\"d\":null},\"e\":\"hi\"}");
        var array = (List<?>) root.get("a");

        assertEquals(1.0, array.get(0));
        assertEquals(2.5, array.get(1));
        assertEquals(-300.0, array.get(2));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) root.get("b")).get("c"));
        assertNull(((Map<?, ?>) root.get("b")).get("d"));
        assertEquals("hi", root.get("e"));
    }

    @Test
    void parsesEscapesAndUnicode() {
        assertEquals("tab\there", JsonParser.parse("\"tab\\there\""));
        assertEquals("quote\"end", JsonParser.parse("\"quote\\\"end\""));
        assertEquals("\u00e9", JsonParser.parse("\"\\u00e9\""));
    }

    @Test
    void writerEscapesAndRoundTrips() {
        var map = new LinkedHashMap<String, Object>();
        map.put("k", "line1\nline2\t\"q\"\u0001");
        map.put("n", 42);
        map.put("arr", List.of("x", "y"));

        var back = (Map<?, ?>) JsonParser.parse(JsonWriter.write(map));
        assertEquals("line1\nline2\t\"q\"\u0001", back.get("k"));
        assertEquals(42.0, back.get("n"));
        assertEquals(2, ((List<?>) back.get("arr")).size());
    }

    @Test
    void writerRejectsUnsupportedTypesAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(Float.POSITIVE_INFINITY));
    }

    @Test
    void parserRejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{} x"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("\"\\q\""));
    }
}
