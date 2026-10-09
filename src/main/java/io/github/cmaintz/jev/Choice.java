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
 * @param instructions what to decide, e.g. "Which team should handle this"
 * @param criteria option name to its description; 1 to 255 options, sent in the map's
 *     iteration order. The model cannot pick an option you omit.
 */
public record Choice(String instructions, Map<String, String> criteria) implements Question {

    /** The most options a single Choice may offer. */
    public static final int MAX_OPTIONS = 255;

    /** Validates and defensively copies the criteria, keeping their order. */
    public Choice {
        Preconditions.requireText(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.isEmpty() || criteria.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A Choice needs between 1 and " + MAX_OPTIONS + " options.");
        }
        criteria.forEach((option, description) -> {
            Preconditions.requireText(option, "option name");
            Preconditions.requireText(description, "description of option '" + option + "'");
        });
        criteria = Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
    }
}
