package io.github.cmaintz.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pick exactly one option from a defined set. The answer names the selected option and
 * gives a probability for every option; its confidence summarizes how peaked that
 * distribution is. Add a no-match option when nothing may fit.
 *
 * <p>Instructions and each option's description are text, or structured JSON as a
 * {@code Map} or {@code List}; see {@link Question#instructions()}.
 *
 * @param instructions what to decide, e.g. "Which team should handle this"
 * @param criteria option name to its description; 1 to 255 options, sent in the map's
 *     iteration order. The model cannot pick an option you omit.
 */
public record Choice(Object instructions, Map<String, ?> criteria) implements Question {

    /** The most options a single Choice may offer. */
    public static final int MAX_OPTIONS = 255;

    /** Validates and defensively copies the criteria, keeping their order. */
    public Choice {
        instructions = Prompts.require(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.isEmpty() || criteria.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A Choice needs between 1 and " + MAX_OPTIONS + " options.");
        }
        var options = new LinkedHashMap<String, Object>();
        criteria.forEach((option, description) -> options.put(
                Preconditions.requireText(option, "option name"),
                Prompts.criterion(description, "description of option '" + option + "'")));
        criteria = Collections.unmodifiableMap(options);
    }
}
