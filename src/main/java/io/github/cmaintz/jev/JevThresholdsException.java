package io.github.cmaintz.jev;

/**
 * A jev-eval {@code thresholds.json} could not be used: it is malformed, has an unsupported
 * version, or holds no gate that fits the questions you asked.
 */
public final class JevThresholdsException extends JevException {

    private static final long serialVersionUID = 1L;

    JevThresholdsException(String message) {
        this(message, null);
    }

    JevThresholdsException(String message, Throwable cause) {
        super(message, 0, null, cause);
    }
}
