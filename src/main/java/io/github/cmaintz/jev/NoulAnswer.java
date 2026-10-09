package io.github.cmaintz.jev;

/**
 * The answer to a {@link Noul}. It carries no separate confidence: gate on the
 * probability itself, where near 0.5 means genuinely uncertain rather than "medium yes".
 *
 * @param probability probability in [0, 1] that the condition holds
 */
public record NoulAnswer(double probability) implements Answer {

    /**
     * True when {@link #probability()} is at or above {@code threshold}, the same rule
     * {@link CalibratedAnswer#isConfident(double)} uses.
     *
     * @param threshold the minimum probability, in [0, 1]
     * @return whether the condition is judged to hold
     */
    public boolean isTrue(double threshold) {
        return probability >= threshold;
    }
}
