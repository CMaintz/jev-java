package io.github.cmaintz.jev;

import java.time.Duration;
import java.util.Objects;

/** Fluent builder for {@link JevClient}. */
public final class JevClientBuilder {

    String apiKey;
    String baseUrl = "https://api.typesafe.ai";
    String model = "jev-latest";
    Duration timeout = Duration.ofSeconds(60);
    int maxRetries = 3;
    HttpTransport transport;
    RetryingSender.Delays delays = RetryingSender.REAL_DELAYS;

    JevClientBuilder() {}

    /**
     * API key. If unset, {@code TYPESAFE_API_KEY} is used.
     *
     * @param value the key; surrounding whitespace is stripped
     * @return this builder
     */
    public JevClientBuilder apiKey(String value) {
        this.apiKey = Preconditions.requireText(value, "apiKey");
        return this;
    }

    /**
     * Service base address; endpoint paths such as {@code /v1/systemone} are appended to it.
     *
     * @param value an absolute http(s) URL without a query or fragment, e.g. a proxy
     * @return this builder
     */
    public JevClientBuilder baseUrl(String value) {
        this.baseUrl = Preconditions.requireText(value, "baseUrl");
        return this;
    }

    /**
     * Model id; defaults to {@code jev-latest}.
     *
     * @param value the model
     * @return this builder
     */
    public JevClientBuilder model(String value) {
        this.model = Preconditions.requireText(value, "model");
        return this;
    }

    /**
     * Per-request and connect timeout for the default transport.
     *
     * @param value a positive duration
     * @return this builder
     */
    public JevClientBuilder timeout(Duration value) {
        Objects.requireNonNull(value, "timeout");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("timeout must be positive.");
        }
        this.timeout = value;
        return this;
    }

    /**
     * Maximum retries after a 408, 429 or 5xx before giving up; zero disables retrying.
     *
     * @param value a non-negative count
     * @return this builder
     */
    public JevClientBuilder maxRetries(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative.");
        }
        this.maxRetries = value;
        return this;
    }

    /**
     * Inject a transport (e.g. a stub for offline tests).
     *
     * @param value the transport
     * @return this builder
     */
    public JevClientBuilder transport(HttpTransport value) {
        this.transport = Objects.requireNonNull(value, "transport");
        return this;
    }

    /** Test seam: replaces the real waits between retries. */
    JevClientBuilder delays(RetryingSender.Delays value) {
        this.delays = Objects.requireNonNull(value, "delays");
        return this;
    }

    /**
     * Build the client.
     *
     * @return the client
     * @throws IllegalArgumentException if the base URL is not an absolute http(s) URL, or has a query or fragment
     * @throws JevException if no usable API key is configured or found in the environment
     */
    public JevClient build() {
        return new JevClient(this);
    }
}
