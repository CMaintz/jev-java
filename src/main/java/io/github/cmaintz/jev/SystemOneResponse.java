package io.github.cmaintz.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The result of one {@code system_one} request: every answer keyed by the question id
 * you sent, plus token usage. Use the typed accessors ({@link #choice(String)},
 * {@link #score(String)}, {@link #noul(String)}) for a known question, or the
 * type-filtered views ({@link #choices()}, {@link #scores()}, {@link #nouls()}).
 *
 * @param model the model that answered (e.g. the resolved version behind {@code jev-latest}),
 *     or null if the service omitted it
 * @param answers answers keyed by question id, in the order the service returned them
 * @param usage token accounting, or null if the service omitted it
 */
public record SystemOneResponse(String model, Map<String, Answer> answers, Usage usage) {

    /** Defensively copies the answers. */
    public SystemOneResponse {
        answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }

    /**
     * The answer for {@code questionId}, whatever its type.
     *
     * @param questionId the id the question was sent under
     * @return the answer, to switch over or test with {@code instanceof}
     * @throws NoSuchElementException if no answer was returned under that id
     */
    public Answer get(String questionId) {
        Answer answer = answers.get(questionId);
        if (answer == null) {
            throw new NoSuchElementException("No answer for question id: " + questionId);
        }
        return answer;
    }

    /**
     * The answer to the {@link Choice} asked under {@code questionId}.
     *
     * @param questionId the id the Choice was sent under
     * @return the selected option with its per-option distribution
     * @throws NoSuchElementException if no answer was returned under that id
     * @throws IllegalStateException if that question was not a Choice
     */
    public ChoiceAnswer choice(String questionId) {
        return get(questionId, ChoiceAnswer.class);
    }

    /**
     * The answer to the {@link Score} asked under {@code questionId}; throws like {@link #choice(String)}.
     *
     * @param questionId the id the Score was sent under
     * @return the position on the scale with its per-level distribution
     */
    public ScoreAnswer score(String questionId) {
        return get(questionId, ScoreAnswer.class);
    }

    /**
     * The answer to the {@link Noul} asked under {@code questionId}; throws like {@link #choice(String)}.
     *
     * @param questionId the id the Noul was sent under
     * @return the probability that the condition holds
     */
    public NoulAnswer noul(String questionId) {
        return get(questionId, NoulAnswer.class);
    }

    /**
     * Every {@link ChoiceAnswer}, keyed by question id.
     *
     * @return an unmodifiable map in response order
     */
    public Map<String, ChoiceAnswer> choices() {
        return filter(ChoiceAnswer.class);
    }

    /**
     * Every {@link ScoreAnswer}, keyed by question id.
     *
     * @return an unmodifiable map in response order
     */
    public Map<String, ScoreAnswer> scores() {
        return filter(ScoreAnswer.class);
    }

    /**
     * Every {@link NoulAnswer}, keyed by question id.
     *
     * @return an unmodifiable map in response order
     */
    public Map<String, NoulAnswer> nouls() {
        return filter(NoulAnswer.class);
    }

    private <T extends Answer> T get(String questionId, Class<T> type) {
        Answer answer = get(questionId);
        if (!type.isInstance(answer)) {
            throw new IllegalStateException("Answer '" + questionId + "' is a "
                    + answer.getClass().getSimpleName() + ", not a " + type.getSimpleName() + ".");
        }
        return type.cast(answer);
    }

    private <T extends Answer> Map<String, T> filter(Class<T> type) {
        var out = new LinkedHashMap<String, T>();
        answers.forEach((id, answer) -> {
            if (type.isInstance(answer)) {
                out.put(id, type.cast(answer));
            }
        });
        return Collections.unmodifiableMap(out);
    }
}
