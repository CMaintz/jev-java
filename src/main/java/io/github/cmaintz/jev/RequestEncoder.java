package io.github.cmaintz.jev;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes a {@code system_one} request body. Built by hand rather than by reflection
 * because {@code criteria} is a map for a Choice but an array for a Score.
 */
final class RequestEncoder {

    private RequestEncoder() {}

    /** The UTF-8 JSON body for {@code questions} asked about {@code state}. */
    static byte[] encode(String model, Object state, Map<String, Question> questions) {
        var root = new LinkedHashMap<String, Object>();
        root.put("model", model);
        root.put("state", state);
        root.put("questions", questionsMap(questions));
        return JsonWriter.write(root).getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> questionsMap(Map<String, Question> questions) {
        var out = new LinkedHashMap<String, Object>();
        questions.forEach((id, question) -> out.put(id, questionMap(question)));
        return out;
    }

    private static Map<String, Object> questionMap(Question question) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", wireType(question));
        map.put("instructions", question.instructions());
        Object criteria = criteria(question);
        if (criteria != null) {
            map.put("criteria", criteria);
        }
        return map;
    }

    private static String wireType(Question question) {
        return switch (question) {
            case Choice c -> "choice";
            case Score s -> "score";
            case Noul n -> "noul";
        };
    }

    private static Object criteria(Question question) {
        return switch (question) {
            case Choice choice -> choice.criteria();
            case Score score -> score.criteria();
            case Noul noul -> noulCriteria(noul);
        };
    }

    /** The optional yes/no glosses, or null when neither is set. */
    private static Map<String, Object> noulCriteria(Noul noul) {
        var criteria = new LinkedHashMap<String, Object>();
        if (noul.whenTrue() != null) {
            criteria.put("true", noul.whenTrue());
        }
        if (noul.whenFalse() != null) {
            criteria.put("false", noul.whenFalse());
        }
        return criteria.isEmpty() ? null : criteria;
    }
}
