package io.github.cmaintz.jev;

/** Argument checks shared by the public records and the client builder. */
final class Preconditions {

    private Preconditions() {}

    /** Returns {@code value}, or throws if it is null or blank. */
    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank.");
        }
        return value;
    }
}
