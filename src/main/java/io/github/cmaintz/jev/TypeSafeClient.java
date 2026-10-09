package io.github.cmaintz.jev;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A client for TypeSafe AI's Jev "System One" model. Send a {@code state} plus a set of
 * typed {@link Question}s and get back typed answers with calibrated confidence.
 * Independent questions are evaluated in parallel, so batching them into one call is
 * close to free. Instances are thread-safe; build one and reuse it.
 */
public final class TypeSafeClient {

    private static final String ENV_KEY = "TYPESAFE_API_KEY";
    private static final String PATH = "/v1/systemone";

    private final String model;
    private final RetryingSender sender;

    private TypeSafeClient(Builder builder) {
        this.model = builder.model;
        this.sender = new RetryingSender(
                builder.transport != null ? builder.transport : new JdkHttpTransport(builder.timeout),
                endpoint(builder.baseUrl),
                Map.of("Authorization", "Bearer " + resolveApiKey(builder.apiKey), "Content-Type", "application/json"),
                builder.maxRetries,
                builder.delays);
    }

    private static String endpoint(String baseUrl) {
        String url = baseUrl.replaceAll("/+$", "") + PATH;
        if (!isHttpUrl(url)) {
            throw new IllegalArgumentException(
                    "baseUrl must be an absolute http(s) URL without a query or fragment: " + baseUrl);
        }
        return url;
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            boolean http = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
            return http && uri.getHost() != null && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String resolveApiKey(String configured) {
        String key = configured != null ? configured : System.getenv(ENV_KEY);
        if (key == null || key.isBlank()) {
            throw new JevException("No API key. Set " + ENV_KEY + " or Builder.apiKey.", 0, null, null);
        }
        key = key.strip();
        if (key.chars().anyMatch(c -> c < 0x21 || c > 0x7e)) {
            throw new JevException("The API key may only contain printable ASCII characters.", 0, null, null);
        }
        return key;
    }

    /**
     * A new builder with default settings.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A client with default settings that reads its key from {@code TYPESAFE_API_KEY}.
     *
     * @return the client
     * @throws JevException if the variable is unset or blank
     */
    public static TypeSafeClient fromEnvironment() {
        return builder().build();
    }

    /**
     * Evaluate {@code questions} against {@code state}. State may be a String, or any
     * tree of Map/List/String/Number/Boolean/null (a {@code Map} gives exact control
     * over field names). Answers come back keyed by the same ids.
     *
     * @param state the input to judge
     * @param questions the questions to ask, keyed by an id of your choosing
     * @return the typed answers, keyed by question id
     * @throws IllegalArgumentException if there are no questions or the state holds an unsupported type
     * @throws JevException on any network, HTTP, or response-format failure
     */
    public SystemOneResponse systemOne(Object state, Map<String, Question> questions) {
        String body = sender.send(encode(state, questions));
        return ResponseDecoder.decode(body, questions);
    }

    /**
     * Asynchronous variant of {@link #systemOne(Object, Map)}. The request and any retry
     * backoff are non-blocking; no thread is held while waiting on the network.
     *
     * @param state the input to judge
     * @param questions the questions to ask, keyed by an id of your choosing
     * @return a future completing with the typed answers, or exceptionally with the
     *     {@link JevException} that {@link #systemOne(Object, Map)} would throw
     * @throws IllegalArgumentException immediately, for the same invalid arguments as the blocking call
     */
    public CompletableFuture<SystemOneResponse> systemOneAsync(Object state, Map<String, Question> questions) {
        return sender.sendAsync(encode(state, questions)).thenApply(body -> ResponseDecoder.decode(body, questions));
    }

    private byte[] encode(Object state, Map<String, Question> questions) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("At least one question is required.");
        }
        return RequestEncoder.encode(model, state, questions);
    }

    /** Fluent builder for {@link TypeSafeClient}. */
    public static final class Builder {

        private String apiKey;
        private String baseUrl = "https://api.typesafe.ai";
        private String model = "jev-latest";
        private Duration timeout = Duration.ofSeconds(60);
        private int maxRetries = 3;
        private HttpTransport transport;
        private RetryingSender.Delays delays = RetryingSender.REAL_DELAYS;

        private Builder() {}

        /**
         * API key. If unset, {@code TYPESAFE_API_KEY} is used.
         *
         * @param value the key; surrounding whitespace is stripped
         * @return this builder
         */
        public Builder apiKey(String value) {
            this.apiKey = Preconditions.requireText(value, "apiKey");
            return this;
        }

        /**
         * Service base address; {@code /v1/systemone} is appended to it.
         *
         * @param value an absolute http(s) URL without a query or fragment, e.g. a proxy
         * @return this builder
         */
        public Builder baseUrl(String value) {
            this.baseUrl = Preconditions.requireText(value, "baseUrl");
            return this;
        }

        /**
         * Model id; defaults to {@code jev-latest}.
         *
         * @param value the model
         * @return this builder
         */
        public Builder model(String value) {
            this.model = Preconditions.requireText(value, "model");
            return this;
        }

        /**
         * Per-request and connect timeout for the default transport.
         *
         * @param value a positive duration
         * @return this builder
         */
        public Builder timeout(Duration value) {
            Objects.requireNonNull(value, "timeout");
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("timeout must be positive.");
            }
            this.timeout = value;
            return this;
        }

        /**
         * Maximum retries after a 429 or 529 before giving up; zero disables retrying.
         *
         * @param value a non-negative count
         * @return this builder
         */
        public Builder maxRetries(int value) {
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
        public Builder transport(HttpTransport value) {
            this.transport = Objects.requireNonNull(value, "transport");
            return this;
        }

        /** Test seam: replaces the real waits between retries. */
        Builder delays(RetryingSender.Delays value) {
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
        public TypeSafeClient build() {
            return new TypeSafeClient(this);
        }
    }
}
