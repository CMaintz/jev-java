package io.github.cmaintz.jev;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The result of one {@code system_one} request: every answer keyed by the question id
 * you sent, plus token usage. Use {@link #get(String)} for a known question, or the
 * type-filtered views ({@link #choices()}, {@link #scores()}, {@link #nouls()}).
 *
 * @param model the model that answered (e.g. the resolved version behind {@code jev-latest})
 * @param answers answers keyed by question id
 * @param usage token accounting, or null if the service omitted it
 */
public record SystemOneResponse(String model, Map<String, Answer> answers, Usage usage) {

    /**
     * The answer for {@code questionId}.
     *
     * @throws NoSuchElementException if no answer was returned under that id
     */
    public Answer get(String questionId) {
        Answer answer = answers.get(questionId);
        if (answer == null) {
            throw new NoSuchElementException("No answer for question id: " + questionId);
        }
        return answer;
    }

    /** Answers whose type is {@code choice}, keyed by question id. */
    public Map<String, Answer> choices() {
        return filter("choice");
    }

    /** Answers whose type is {@code score}, keyed by question id. */
    public Map<String, Answer> scores() {
        return filter("score");
    }

    /** Answers whose type is {@code noul}, keyed by question id. */
    public Map<String, Answer> nouls() {
        return filter("noul");
    }

    private Map<String, Answer> filter(String type) {
        var out = new LinkedHashMap<String, Answer>();
        answers.forEach((key, value) -> {
            if (type.equals(value.type())) {
                out.put(key, value);
            }
        });
        return out;
    }
}
