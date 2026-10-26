package io.github.cmaintz.jev;

/**
 * A single judgment to ask Jev about the shared {@code state}. Pick the implementation
 * by what the answer means: {@link Choice} for one of a defined set, {@link Score} for a
 * position on an ordered scale, {@link Noul} for a yes/no probability, {@link Box} for
 * where something is in an image. Independent
 * questions sent together are evaluated in parallel.
 */
public sealed interface Question permits Noul, Choice, Score, Box {

    /**
     * The judgment to make: natural-language text, an {@link Image}, text with images
     * inline ({@link TextWithImages}), or structured JSON (a {@code Map} or {@code List},
     * which may hold images too) when definitions, contrasts or examples make it clearer.
     * Backticked paths such as {@code ticket.body} reference nested state.
     *
     * @return the instructions: text, an Image, a TextWithImages, or an unmodifiable Map or List
     */
    Object instructions();
}
