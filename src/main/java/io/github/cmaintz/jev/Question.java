package io.github.cmaintz.jev;

/**
 * A single judgment to ask Jev about the shared {@code state}. Pick the implementation
 * by what the answer means: {@link Choice} for one of a defined set, {@link Score} for a
 * position on an ordered scale, {@link Noul} for a yes/no probability. Independent
 * questions sent together are evaluated in parallel.
 */
public sealed interface Question permits Noul, Choice, Score {

    /** Wire discriminator: {@code noul}, {@code choice}, or {@code score}. */
    String type();

    /** The judgment to make, in natural language. Backticked paths such as {@code ticket.body} reference nested state. */
    String instructions();
}
