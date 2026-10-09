package io.github.cmaintz.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a {@link Score}.
 *
 * @param score position on the scale; may fall between two levels
 * @param probabilities probability per level, ordered low to high
 * @param legend level index (as text, e.g. {@code "0"}) to its description
 * @param confidence distribution peakedness in [0, 1]; NaN if the service omitted it
 */
public record ScoreAnswer(double score, List<Double> probabilities, Map<String, String> legend, double confidence)
        implements CalibratedAnswer {

    /** Requires non-null components and defensively copies them. */
    public ScoreAnswer {
        Objects.requireNonNull(probabilities, "probabilities");
        Objects.requireNonNull(legend, "legend");
        probabilities = List.copyOf(probabilities);
        legend = Collections.unmodifiableMap(new LinkedHashMap<>(legend));
    }
}
