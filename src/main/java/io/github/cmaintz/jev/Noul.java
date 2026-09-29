package io.github.cmaintz.jev;

/**
 * A yes/no judgment. The answer is a single probability in {@code [0, 1]}: near 1 is a
 * strong yes, near 0 a strong no, near 0.5 genuinely uncertain. A Noul carries no
 * separate confidence. Use one Noul per label when several labels may apply at once.
 *
 * @param instructions the condition to judge, e.g. "Does the customer request a refund?"
 * @param whenTrue optional description of what a "yes" means (wire {@code criteria.true}); may be null
 * @param whenFalse optional description of what a "no" means (wire {@code criteria.false}); may be null
 */
public record Noul(String instructions, String whenTrue, String whenFalse) implements Question {

    /** A bare yes/no judgment with no glosses. */
    public Noul(String instructions) {
        this(instructions, null, null);
    }

    @Override
    public String type() {
        return "noul";
    }
}
