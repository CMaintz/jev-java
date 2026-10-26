package io.github.cmaintz.jev;

/**
 * Find where something is in an image. The answer is a bounding box: four edges as
 * fractions of the image's width and height, plus a confidence. A Box needs an image in
 * the request and Jev 1.14 or later; requests with one are sent with
 * TypeSafe's images header. Images and Box are experimental on TypeSafe's side: the
 * format, answers and limits can change between releases.
 *
 * @param instructions what to find and in which image, e.g. "the coffee mug in `photo`";
 *     text or structured JSON, as for any {@link Question}
 */
public record Box(Object instructions) implements Question {

    /** Validates and freezes the instructions. */
    public Box {
        instructions = Prompts.require(instructions, "instructions");
    }
}
