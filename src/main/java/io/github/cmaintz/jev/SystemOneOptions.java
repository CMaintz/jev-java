package io.github.cmaintz.jev;

/**
 * Settings for a single {@link JevClient#systemOne(Object, java.util.Map, SystemOneOptions)}
 * call. Start from {@link #DEFAULT} and change what the call needs.
 *
 * @param model the model for this call, e.g. {@code jev-preview} or a pinned version; null
 *     uses the client's model
 * @param longContext raise the context limits to 262k tokens for this call (TypeSafe's
 *     {@code TypeSafe-Danger-Zone-Long-Context} header). Slower and less accurate than the
 *     default limits, so turn it on only for requests that need it.
 */
public record SystemOneOptions(String model, boolean longContext) {

    /** The client's model and the default context limits. */
    public static final SystemOneOptions DEFAULT = new SystemOneOptions(null, false);

    /** Requires a model override, when given, to be non-blank. */
    public SystemOneOptions {
        if (model != null) {
            Preconditions.requireText(model, "model");
        }
    }

    /**
     * These options with another model.
     *
     * @param value the model for this call; null for the client's model
     * @return the changed options
     */
    public SystemOneOptions withModel(String value) {
        return new SystemOneOptions(value, longContext);
    }

    /**
     * These options with long context turned on or off.
     *
     * @param value whether to raise the context limits for this call
     * @return the changed options
     */
    public SystemOneOptions withLongContext(boolean value) {
        return new SystemOneOptions(model, value);
    }
}
