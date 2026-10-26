package io.github.cmaintz.jev;

import java.util.List;
import java.util.Objects;

/**
 * Rate the state along an ordered scale you describe. Levels are ordered low to high;
 * each must describe a concrete situation and stand on its own. The answer's score can
 * fall between two levels, with a probability across levels and a confidence.
 *
 * <p>Instructions and each level are text, or structured JSON as a {@code Map} or
 * {@code List}; see {@link Question#instructions()}.
 *
 * @param instructions the dimension to rate, e.g. "How frustrated the customer appears"
 * @param criteria ordered level descriptions, low to high; between 1 and 10 of them
 */
public record Score(Object instructions, List<?> criteria) implements Question {

    /** The fewest levels a Score may have. */
    public static final int MIN_LEVELS = 1;

    /** The most levels a Score may have. */
    public static final int MAX_LEVELS = 10;

    /** Validates and defensively copies the levels. */
    public Score {
        instructions = Prompts.require(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.size() < MIN_LEVELS || criteria.size() > MAX_LEVELS) {
            throw new IllegalArgumentException(
                    "A Score needs between " + MIN_LEVELS + " and " + MAX_LEVELS + " ordered levels.");
        }
        criteria = criteria.stream()
                .map(level -> Prompts.criterion(level, "level description"))
                .toList();
    }
}
