package io.github.cmaintz.jev;

import java.util.List;
import java.util.Map;

/**
 * One typed judgment returned by Jev, keyed by the question id you sent. Which fields
 * are populated depends on {@link #type()}: a Noul fills only {@link #noul()}; a Choice
 * fills {@link #choice()}, {@link #probabilities()}, and {@link #confidence()}; a Score
 * fills {@link #score()}, {@link #legend()}, {@link #scoreProbabilities()}, and
 * {@link #confidence()}. Absent fields are {@code null}.
 *
 * @param type wire discriminator: {@code noul}, {@code choice}, or {@code score}
 * @param noul Noul probability in [0, 1], or null
 * @param choice selected option for a Choice, or null
 * @param probabilities per-option probabilities for a Choice, or null
 * @param score numeric position on the scale for a Score (may fall between levels), or null
 * @param scoreProbabilities per-level probabilities for a Score, ordered low to high, or null
 * @param legend index-to-description level map for a Score, or null
 * @param confidence distribution peakedness in [0, 1] for Choice and Score; null for Noul
 */
public record Answer(
        String type,
        Double noul,
        String choice,
        Map<String, Double> probabilities,
        Double score,
        List<Double> scoreProbabilities,
        Map<String, String> legend,
        Double confidence) {

    /**
     * True when this answer carries a {@link #confidence()} at or above {@code threshold}.
     * A Noul (no confidence) is never "confident" by this test; gate it on {@link #noul()} instead.
     */
    public boolean isConfident(double threshold) {
        return confidence != null && confidence >= threshold;
    }
}
