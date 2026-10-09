package io.github.cmaintz.jev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes a {@code system_one} response body into typed records. Each answer's shape is
 * taken from the question that was asked under its id, so the wire {@code type} field is
 * never needed. Answers under ids that were not asked are ignored, and an asked id with
 * no answer is simply absent (see {@link SystemOneResponse#get(String)}). A body that
 * is not JSON, or an answer missing its value, surfaces as a {@link JevException}
 * carrying the raw body.
 */
final class ResponseDecoder {

    private final String body;

    private ResponseDecoder(String body) {
        this.body = body;
    }

    static SystemOneResponse decode(String body, Map<String, Question> questions) {
        return new ResponseDecoder(body).decode(questions);
    }

    private SystemOneResponse decode(Map<String, Question> questions) {
        Map<?, ?> root = asObject(parse(), "response");
        String model = root.get("model") instanceof String s ? s : null;
        return new SystemOneResponse(model, answers(root.get("answers"), questions), usage(root.get("usage")));
    }

    private Object parse() {
        try {
            return JsonParser.parse(body);
        } catch (IllegalArgumentException e) {
            throw new JevException("Malformed response: " + e.getMessage(), 0, body, e);
        }
    }

    private Map<String, Answer> answers(Object node, Map<String, Question> questions) {
        var answers = new LinkedHashMap<String, Answer>();
        asObject(node, "answers").forEach((id, value) -> {
            Question question = questions.get(String.valueOf(id));
            if (question != null) {
                answers.put(String.valueOf(id), answer(question, asObject(value, "answer " + id)));
            }
        });
        return answers;
    }

    private Answer answer(Question question, Map<?, ?> node) {
        return switch (question) {
            case Choice c -> new ChoiceAnswer(
                    requireString(node, "choice"),
                    doubleMap(node.get("probabilities")),
                    optionalDouble(node, "confidence"));
            case Score s -> new ScoreAnswer(
                    requireDouble(node, "score"),
                    doubleList(node.get("probabilities")),
                    stringMap(node.get("legend")),
                    optionalDouble(node, "confidence"));
            case Noul n -> new NoulAnswer(requireDouble(node, "noul"));
        };
    }

    private static Usage usage(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        return new Usage(intOrZero(map.get("input_tokens")), intOrZero(map.get("output_tokens")));
    }

    private Map<?, ?> asObject(Object node, String what) {
        if (node instanceof Map<?, ?> map) {
            return map;
        }
        throw malformed(what + " is not a JSON object");
    }

    private String requireString(Map<?, ?> node, String field) {
        if (node.get(field) instanceof String s) {
            return s;
        }
        throw malformed("missing string field '" + field + "'");
    }

    private double requireDouble(Map<?, ?> node, String field) {
        if (node.get(field) instanceof Number n) {
            return n.doubleValue();
        }
        throw malformed("missing numeric field '" + field + "'");
    }

    private static double optionalDouble(Map<?, ?> node, String field) {
        return node.get(field) instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private static int intOrZero(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private Map<String, Double> doubleMap(Object node) {
        var out = new LinkedHashMap<String, Double>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> out.put(String.valueOf(key), number(value)));
        }
        return out;
    }

    private List<Double> doubleList(Object node) {
        var out = new ArrayList<Double>();
        if (node instanceof List<?> list) {
            list.forEach(value -> out.add(number(value)));
        }
        return out;
    }

    private static Map<String, String> stringMap(Object node) {
        var out = new LinkedHashMap<String, String>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> out.put(String.valueOf(key), String.valueOf(value)));
        }
        return out;
    }

    private double number(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw malformed("expected a number but got " + value);
    }

    private JevException malformed(String detail) {
        return new JevException("Malformed response: " + detail + ".", 0, body, null);
    }
}
