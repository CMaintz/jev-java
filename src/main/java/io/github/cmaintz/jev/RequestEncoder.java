package io.github.cmaintz.jev;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Encodes a {@code system_one} request body. Built by hand rather than by reflection
 * because {@code criteria} is a map for a Choice but an array for a Score.
 */
final class RequestEncoder {

    private RequestEncoder() {}

    /**
     * The JSON request and the distinct images it refers to, in first-use order.
     *
     * @param json the request JSON, with an {@code assets} entry per image
     * @param images the images to send as file parts; empty for a plain JSON request
     */
    record Encoded(String json, List<Image> images) {}

    /** The request for {@code questions} asked about {@code state}. */
    static Encoded encode(String model, Object state, Map<String, Question> questions) {
        var root = new LinkedHashMap<String, Object>();
        root.put("model", model);
        root.put("state", state);
        root.put("questions", questionsMap(questions));
        var found = new LinkedHashSet<Image>();
        String json = JsonWriter.write(root, found);
        if (found.isEmpty()) {
            return new Encoded(json, List.of());
        }
        List<Image> images = List.copyOf(found);
        root.put("assets", assets(images));
        return new Encoded(JsonWriter.write(root), images);
    }

    /** Each image by reference, pointing at the multipart file part that carries it. */
    private static Map<String, Object> assets(List<Image> images) {
        var assets = new LinkedHashMap<String, Object>();
        for (int i = 0; i < images.size(); i++) {
            assets.put(images.get(i).reference(), Map.of("type", "image", "part", Multipart.partName(i)));
        }
        return assets;
    }

    private static Map<String, Object> questionsMap(Map<String, Question> questions) {
        var out = new LinkedHashMap<String, Object>();
        questions.forEach((id, question) -> out.put(id, questionMap(question)));
        return out;
    }

    /** One question as it goes on the wire; also what jev-eval records as its definition. */
    static Map<String, Object> questionMap(Question question) {
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
            case Box b -> "box";
        };
    }

    private static Object criteria(Question question) {
        return switch (question) {
            case Choice choice -> choice.criteria();
            case Score score -> score.criteria();
            case Noul noul -> noulCriteria(noul);
            case Box box -> null;
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
