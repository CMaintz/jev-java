package io.github.cmaintz.jev;

/**
 * The answer to a {@link Box}: the smallest rectangle around the target, each edge a
 * fraction from 0 to 1 measured from the image's top-left corner. Multiply {@code left}
 * and {@code right} by the image width, and {@code top} and {@code bottom} by its height,
 * to get pixels.
 *
 * @param top top edge, as a fraction of the height
 * @param bottom bottom edge, as a fraction of the height
 * @param left left edge, as a fraction of the width
 * @param right right edge, as a fraction of the width
 * @param confidence how sure the model is, in [0, 1]; NaN if the service omitted it.
 *     There is no recommended threshold yet, so tune one on your own images.
 */
public record BoxAnswer(double top, double bottom, double left, double right, double confidence) implements Answer {

    /**
     * True when {@link #confidence()} is at or above {@code threshold}; never true for a
     * missing (NaN) confidence.
     *
     * @param threshold the minimum confidence to accept
     * @return whether the box is confident enough
     */
    public boolean isConfident(double threshold) {
        return confidence >= threshold;
    }
}
