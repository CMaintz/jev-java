package io.github.cmaintz.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The answer to a {@link Choice}.
 *
 * @param choice the selected option
 * @param probabilities probability per option, in the order the service returned them
 * @param confidence distribution peakedness in [0, 1]; NaN if the service omitted it
 */
public record ChoiceAnswer(String choice, Map<String, Double> probabilities, double confidence)
        implements CalibratedAnswer {

    /** Requires non-null components and defensively copies the probabilities. */
    public ChoiceAnswer {
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(probabilities, "probabilities");
        probabilities = Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
    }
}
