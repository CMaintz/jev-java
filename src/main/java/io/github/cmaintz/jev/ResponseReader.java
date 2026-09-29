package io.github.cmaintz.jev;

import io.github.cmaintz.jev.json.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a parsed {@code system_one} response tree into typed records. Reads by hand
 * because {@code probabilities} is a map for a Choice but an array for a Score, and the
 * per-answer {@code type} may be absent (then it is inferred from present fields).
 */
final class ResponseReader {

    private ResponseReader() {}

    static SystemOneResponse parse(String json) {
        Object root = JsonParser.parse(json);
        if (!(root instanceof Map<?, ?> map)) {
            throw new JevException("Malformed response: expected a JSON object.", 0, json, null);
        }
        return new SystemOneResponse(
                asString(map.get("model"), ""), readAnswers(map.get("answers")), readUsage(map.get("usage")));
    }

    private static Map<String, Answer> readAnswers(Object node) {
        var answers = new LinkedHashMap<String, Answer>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> answers.put(String.valueOf(key), readAnswer(value)));
        }
        return answers;
    }

    private static Answer readAnswer(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new JevException("Malformed answer: expected a JSON object.", 0, null, null);
        }
        String type = map.get("type") instanceof String t ? t : infer(map);
        return new Answer(
                type,
                asDouble(map.get("noul")),
                asString(map.get("choice"), null),
                asDoubleMap(map.get("probabilities")),
                asDouble(map.get("score")),
                asDoubleList(map.get("probabilities")),
                asStringMap(map.get("legend")),
                asDouble(map.get("confidence")));
    }

    private static String infer(Map<?, ?> map) {
        if (map.containsKey("choice")) {
            return "choice";
        }
        if (map.containsKey("score")) {
            return "score";
        }
        return map.containsKey("noul") ? "noul" : "";
    }

    private static Map<String, Double> asDoubleMap(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        var out = new LinkedHashMap<String, Double>();
        map.forEach((key, value) -> out.put(String.valueOf(key), asDouble(value)));
        return out;
    }

    private static List<Double> asDoubleList(Object node) {
        if (!(node instanceof List<?> list)) {
            return null;
        }
        var out = new ArrayList<Double>(list.size());
        for (Object item : list) {
            out.add(asDouble(item));
        }
        return out;
    }

    private static Map<String, String> asStringMap(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        var out = new LinkedHashMap<String, String>();
        map.forEach((key, value) -> out.put(String.valueOf(key), asString(value, "")));
        return out;
    }

    private static Usage readUsage(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        Double in = asDouble(map.get("input_tokens"));
        Double out = asDouble(map.get("output_tokens"));
        return new Usage(in == null ? 0 : in.intValue(), out == null ? 0 : out.intValue());
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }
}
