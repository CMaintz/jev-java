package io.github.cmaintz.jev;

import java.util.List;
import java.util.Objects;

/**
 * Rate the state along an ordered scale you describe. Levels are ordered low to high;
 * each must describe a concrete situation and stand on its own. The answer's score can
 * fall between two levels, with a probability across levels and a confidence.
 *
 * @param instructions the dimension to rate, e.g. "How frustrated the customer appears"
 * @param criteria ordered level descriptions, low to high; between 2 and 10 of them
 */
public record Score(String instructions, List<String> criteria) implements Question {

    /** Validates and defensively copies the levels. */
    public Score {
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.size() < 2 || criteria.size() > 10) {
            throw new IllegalArgumentException("A Score needs between 2 and 10 ordered levels.");
        }
        criteria = List.copyOf(criteria);
    }

    @Override
    public String type() {
        return "score";
    }
}
