package io.github.cmaintz.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@code system_one} request as a tree of maps that {@code JsonWriter} can
 * serialize. Handled here rather than by reflection because {@code criteria} is a map
 * for a Choice but an array for a Score.
 */
final class RequestBuilder {

    private RequestBuilder() {}

    static Map<String, Object> build(String model, Object state, Map<String, Question> questions) {
        var root = new LinkedHashMap<String, Object>();
        root.put("model", model);
        root.put("state", state);
        root.put("questions", questionsMap(questions));
        return root;
    }

    private static Map<String, Object> questionsMap(Map<String, Question> questions) {
        var out = new LinkedHashMap<String, Object>();
        questions.forEach((id, question) -> out.put(id, questionMap(question)));
        return out;
    }

    private static Map<String, Object> questionMap(Question question) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", question.type());
        map.put("instructions", question.instructions());
        addCriteria(map, question);
        return map;
    }

    private static void addCriteria(Map<String, Object> map, Question question) {
        switch (question) {
            case Choice choice -> map.put("criteria", choice.criteria());
            case Score score -> map.put("criteria", score.criteria());
            case Noul noul -> addNoulCriteria(map, noul);
        }
    }

    private static void addNoulCriteria(Map<String, Object> map, Noul noul) {
        if (noul.whenTrue() == null && noul.whenFalse() == null) {
            return;
        }
        var criteria = new LinkedHashMap<String, Object>();
        if (noul.whenTrue() != null) {
            criteria.put("true", noul.whenTrue());
        }
        if (noul.whenFalse() != null) {
            criteria.put("false", noul.whenFalse());
        }
        map.put("criteria", criteria);
    }
}
