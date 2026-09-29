package io.github.cmaintz.jev;

import io.github.cmaintz.jev.json.JsonWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A client for TypeSafe AI's Jev "System One" model. Send a {@code state} plus a set of
 * typed {@link Question}s and get back typed answers with calibrated confidence.
 * Independent questions are evaluated in parallel, so batching them into one call is
 * close to free. Instances are thread-safe; build one and reuse it.
 */
public final class TypeSafeClient {

    private static final String ENV_KEY = "TYPESAFE_API_KEY";
    private static final Set<Integer> RETRYABLE = Set.of(429, 529);

    private final HttpTransport transport;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final int maxRetries;

    private TypeSafeClient(Builder builder) {
        String key = builder.apiKey != null ? builder.apiKey : System.getenv(ENV_KEY);
        if (key == null || key.isBlank()) {
            throw new JevException("No API key. Set " + ENV_KEY + " or Builder.apiKey.", 0, null, null);
        }
        this.apiKey = key;
        this.model = builder.model;
        this.maxRetries = Math.max(0, builder.maxRetries);
        this.endpoint = builder.baseUrl.replaceAll("/+$", "") + "/v1/systemone";
        this.transport = builder.transport != null ? builder.transport : new JdkHttpTransport(builder.timeout);
    }

    /** A new builder with default settings. */
    public static Builder builder() {
        return new Builder();
    }

    /** A client that reads its key from {@code TYPESAFE_API_KEY}. */
    public static TypeSafeClient fromEnvironment() {
        return builder().build();
    }

    /**
     * Evaluate {@code questions} against {@code state}. State may be a String, or any
     * tree of Map/List/String/Number/Boolean/null (a {@code Map} gives exact control
     * over field names). Answers come back keyed by the same ids.
     */
    public SystemOneResponse systemOne(Object state, Map<String, Question> questions) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("At least one question is required.");
        }
        byte[] body =
                JsonWriter.write(RequestBuilder.build(model, state, questions)).getBytes(StandardCharsets.UTF_8);
        return ResponseReader.parse(sendWithRetries(body));
    }

    /** Asynchronous variant of {@link #systemOne(Object, Map)}. */
    public CompletableFuture<SystemOneResponse> systemOneAsync(Object state, Map<String, Question> questions) {
        return CompletableFuture.supplyAsync(() -> systemOne(state, questions));
    }

    private String sendWithRetries(byte[] body) {
        Map<String, String> headers = Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json");
        for (int attempt = 0; ; attempt++) {
            HttpTransport.Response response = doSend(headers, body);
            if (response.status() >= 200 && response.status() < 300) {
                return response.body();
            }
            if (RETRYABLE.contains(response.status()) && attempt < maxRetries) {
                backoff(attempt);
                continue;
            }
            throw errorFor(response.status(), response.body());
        }
    }

    private HttpTransport.Response doSend(Map<String, String> headers, byte[] body) {
        try {
            return transport.send(endpoint, headers, body);
        } catch (IOException e) {
            throw new JevException("Network error calling Jev: " + e.getMessage(), 0, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JevException("Interrupted while calling Jev.", 0, null, e);
        }
    }

    private static void backoff(int attempt) {
        long base = (long) (500 * Math.pow(2, attempt));
        long jitter = ThreadLocalRandom.current().nextLong(0, 250);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static JevException errorFor(int status, String body) {
        return switch (status) {
            case 401 -> new JevAuthException("Unauthorized: invalid or missing API key.", body);
            case 422 -> new JevValidationException(
                    "Unprocessable entity: the request was rejected as malformed.", body);
            case 429 -> new JevRateLimitException("Rate limit exceeded; retries exhausted.", body);
            case 529 -> new JevOverloadedException("Service overloaded; retries exhausted.", body);
            default -> new JevException("Unexpected HTTP " + status + " from Jev.", status, body, null);
        };
    }

    /** Fluent builder for {@link TypeSafeClient}. */
    public static final class Builder {

        private String apiKey;
        private String baseUrl = "https://api.typesafe.ai";
        private String model = "jev-latest";
        private Duration timeout = Duration.ofSeconds(60);
        private int maxRetries = 3;
        private HttpTransport transport;

        private Builder() {}

        /** API key. If unset, {@code TYPESAFE_API_KEY} is used. */
        public Builder apiKey(String value) {
            this.apiKey = value;
            return this;
        }

        /** Service base address; {@code /v1/systemone} is appended. */
        public Builder baseUrl(String value) {
            this.baseUrl = value;
            return this;
        }

        /** Model id; defaults to {@code jev-latest}. */
        public Builder model(String value) {
            this.model = value;
            return this;
        }

        /** Per-request timeout for the default transport. */
        public Builder timeout(Duration value) {
            this.timeout = value;
            return this;
        }

        /** Maximum retries after a 429 or 529 before giving up. */
        public Builder maxRetries(int value) {
            this.maxRetries = value;
            return this;
        }

        /** Inject a transport (e.g. a stub for offline tests). */
        public Builder transport(HttpTransport value) {
            this.transport = value;
            return this;
        }

        /** Build the client. */
        public TypeSafeClient build() {
            return new TypeSafeClient(this);
        }
    }
}
