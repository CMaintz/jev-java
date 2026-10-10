package io.github.cmaintz.jev;

import java.util.List;
import java.util.Objects;

/**
 * The confidence gate picked from a jev-eval {@code thresholds.json} for one set of
 * questions, with what jev-eval measured it buys. A row is covered (decided automatically)
 * when its confidence is at or above {@link #threshold()}; below it, escalate. Row
 * confidence is the lowest Choice/Score confidence across {@link #questionIds()}; Nouls
 * never take part. Log the measured fields as provenance.
 *
 * @param threshold the confidence cut-point
 * @param accuracy accuracy jev-eval measured on covered unseen rows, in [0, 1]
 * @param coverage share of unseen rows the gate covered, in [0, 1]
 * @param n number of labeled rows the measurement used
 * @param source {@code "composite"} for the row-level gate, else the question id it came from
 * @param questionIds the caller's Choice/Score question ids the gate applies to, sorted
 * @param model the model jev-eval measured on
 * @param warnings soft problems worth logging (a model change, a reworded question); empty when none
 */
public record ThresholdGate(
        double threshold,
        double accuracy,
        double coverage,
        int n,
        String source,
        List<String> questionIds,
        String model,
        List<String> warnings) {

    /** The {@link #source()} of a row-level gate measured over several questions. */
    public static final String COMPOSITE = "composite";

    /** Requires non-null components and defensively copies the lists. */
    public ThresholdGate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(model, "model");
        questionIds = List.copyOf(questionIds);
        warnings = List.copyOf(warnings);
    }

    /**
     * The row confidence: the lowest confidence among the answers to {@link #questionIds()}.
     *
     * @param response the answers to the questions this gate was picked for
     * @return the minimum confidence, or NaN if a gated question has no answer, is not a
     *     Choice or Score answer, or has a NaN confidence
     */
    public double rowConfidence(SystemOneResponse response) {
        double min = Double.POSITIVE_INFINITY;
        for (String id : questionIds) {
            min = Math.min(min, confidence(response, id));
        }
        return min;
    }

    /**
     * True when the row falls below the gate and should go to a person or a bigger model.
     * Fails toward review: a missing answer, an answer without a confidence, or a NaN
     * confidence escalates rather than throwing.
     *
     * @param response the answers to the questions this gate was picked for
     * @return whether to escalate
     */
    public boolean shouldEscalate(SystemOneResponse response) {
        return !(rowConfidence(response) >= threshold);
    }

    private static double confidence(SystemOneResponse response, String id) {
        return response.answers().get(id) instanceof CalibratedAnswer calibrated ? calibrated.confidence() : Double.NaN;
    }
}
