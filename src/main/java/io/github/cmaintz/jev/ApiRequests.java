package io.github.cmaintz.jev;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the HTTP requests {@link JevClient} sends: the endpoint URLs under the base URL,
 * the bearer authorization, and the encoded System One body. Validates the base URL and
 * API key once, when the client is built.
 */
final class ApiRequests {

    private static final String ENV_KEY = "TYPESAFE_API_KEY";
    private static final String SYSTEM_ONE_PATH = "/v1/systemone";
    private static final String MODELS_PATH = "/v1/models";

    private final String baseUrl;
    private final String authorization;
    private final String model;

    ApiRequests(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl(baseUrl);
        this.authorization = "Bearer " + resolveApiKey(apiKey);
        this.model = model;
    }

    /** The base URL without trailing slashes, so a proxy path prefix is kept in front of each endpoint. */
    private static String baseUrl(String configured) {
        String base = configured.replaceAll("/+$", "");
        if (!isHttpUrl(base + SYSTEM_ONE_PATH)) {
            throw new IllegalArgumentException(
                    "baseUrl must be an absolute http(s) URL without a query or fragment: " + configured);
        }
        return base;
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
            throw new JevException("No API key. Set " + ENV_KEY + " or JevClientBuilder.apiKey.", 0, null, null);
        }
        key = key.strip();
        if (key.chars().anyMatch(c -> c < 0x21 || c > 0x7e)) {
            throw new JevException("The API key may only contain printable ASCII characters.", 0, null, null);
        }
        return key;
    }

    /** {@code GET /v1/models}. */
    HttpTransport.Request models() {
        return new HttpTransport.Request("GET", baseUrl + MODELS_PATH, Map.of("Authorization", authorization), null);
    }

    /** {@code POST /v1/systemone} for {@code questions} about {@code state}; validates both first. */
    HttpTransport.Request systemOne(Object state, Map<String, Question> questions) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("At least one question is required.");
        }
        byte[] body = RequestEncoder.encode(model, state, questions);
        var headers = Map.of("Authorization", authorization, "Content-Type", "application/json");
        return new HttpTransport.Request("POST", baseUrl + SYSTEM_ONE_PATH, headers, body);
    }
}
