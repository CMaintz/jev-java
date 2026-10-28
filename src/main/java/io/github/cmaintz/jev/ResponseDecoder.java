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

    private final ResponseBody body;

    private ResponseDecoder(String body) {
        this.body = new ResponseBody(body);
    }

    static SystemOneResponse decode(String body, Map<String, Question> questions) {
        return new ResponseDecoder(body).decode(questions);
    }

    private SystemOneResponse decode(Map<String, Question> questions) {
        Map<?, ?> root = asObject(body.parse(), "response");
        String model = root.get("model") instanceof String s ? s : null;
        return new SystemOneResponse(model, answers(root.get("answers"), questions), usage(root.get("usage")));
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
            case Choice ignored -> new ChoiceAnswer(
                    body.requireString(node, "choice"),
                    doubleMap(node.get("probabilities")),
                    optionalDouble(node, "confidence"));
            case Score ignored -> new ScoreAnswer(
                    requireDouble(node, "score"),
                    levelProbabilities(node.get("probabilities")),
                    legend(node.get("legend")),
                    optionalDouble(node, "confidence"));
            case Noul ignored -> new NoulAnswer(requireDouble(node, "noul"));
            case Box ignored -> box(node);
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

    private double requireDouble(Map<?, ?> node, String field) {
        if (node.get(field) instanceof Number n) {
            return n.doubleValue();
        }
        throw malformed("missing numeric field '" + field + "'");
    }

    private BoxAnswer box(Map<?, ?> node) {
        Map<?, ?> edges = asObject(node.get("box"), "box");
        return new BoxAnswer(
                requireDouble(edges, "top"),
                requireDouble(edges, "bottom"),
                requireDouble(edges, "left"),
                requireDouble(edges, "right"),
                optionalDouble(node, "confidence"));
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

    /** Score probabilities: an array, or (as the API sends them) an object keyed by level number. */
    private List<Double> levelProbabilities(Object node) {
        var out = new ArrayList<Double>();
        if (node instanceof List<?> list) {
            list.forEach(value -> out.add(number(value)));
        } else if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> put(out, level(key), number(value)));
        }
        return out;
    }

    // A level the service omits reads as 0, so indexes always line up with levels.
    private static void put(List<Double> levels, int level, double probability) {
        while (levels.size() <= level) {
            levels.add(0.0);
        }
        levels.set(level, probability);
    }

    private int level(Object key) {
        String text = String.valueOf(key);
        if (text.length() == 1 && text.charAt(0) >= '0' && text.charAt(0) <= '9') {
            return text.charAt(0) - '0';
        }
        throw malformed("expected a score level number but got \"" + key + "\"");
    }

    /** Score legend: text levels as they are, structured levels as compact JSON. */
    private static Map<String, String> legend(Object node) {
        var out = new LinkedHashMap<String, String>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) ->
                    out.put(String.valueOf(key), value instanceof String text ? text : JsonWriter.write(value)));
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
        return body.malformed(detail);
    }
}
