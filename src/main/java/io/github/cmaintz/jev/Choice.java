package io.github.cmaintz.jev;

import java.util.Map;
import java.util.Objects;

/**
 * Pick exactly one option from a defined set. The answer names the selected option and
 * gives a probability for every option; its confidence summarizes how peaked that
 * distribution is. Add a no-match option when nothing may fit.
 *
 * @param instructions what to decide, e.g. "Which team should handle this"
 * @param criteria option name to its description; max 255 options. The model cannot pick an option you omit.
 */
public record Choice(String instructions, Map<String, String> criteria) implements Question {

    /** Validates and defensively copies the criteria. */
    public Choice {
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("A Choice needs at least one option.");
        }
        criteria = Map.copyOf(criteria);
    }

    @Override
    public String type() {
        return "choice";
    }
}
