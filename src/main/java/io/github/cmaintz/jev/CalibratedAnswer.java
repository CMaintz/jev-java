package io.github.cmaintz.jev;

/**
 * An answer drawn from a probability distribution over options or levels, so it carries
 * a {@link #confidence()} you can gate on. A {@link NoulAnswer} is not calibrated this
 * way; gate it on its probability instead.
 */
public sealed interface CalibratedAnswer extends Answer permits ChoiceAnswer, ScoreAnswer {

    /**
     * How peaked the answer's distribution is.
     *
     * @return the confidence in [0, 1], or NaN if the service omitted it
     */
    double confidence();

    /**
     * True when {@link #confidence()} is at or above {@code threshold}.
     *
     * @param threshold the minimum confidence, in [0, 1]
     * @return whether the answer clears the bar; false when the confidence is NaN
     */
    default boolean isConfident(double threshold) {
        return confidence() >= threshold;
    }
}
