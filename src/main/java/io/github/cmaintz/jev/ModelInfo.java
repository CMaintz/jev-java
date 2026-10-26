package io.github.cmaintz.jev;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A model or alias the account can use, from {@link JevClient#models()}. Pass its
 * {@link #name()} as the client's model or a per-call override.
 *
 * @param name the model ID or alias, e.g. {@code jev-latest}
 * @param description what the model is for
 * @param releaseDate when the model was released
 */
public record ModelInfo(String name, String description, LocalDate releaseDate) {

    /** Requires every component. */
    public ModelInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(releaseDate, "releaseDate");
    }
}
