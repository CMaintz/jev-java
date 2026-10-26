package io.github.cmaintz.jev;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A client for TypeSafe AI's Jev "System One" model. Send a {@code state} plus a set of
 * typed {@link Question}s and get back typed answers with calibrated confidence.
 * Independent questions are evaluated in parallel, so batching them into one call is
 * close to free. Instances are thread-safe; build one and reuse it.
 */
public final class JevClient {

    private final ApiRequests requests;
    private final RetryingSender sender;

    JevClient(JevClientBuilder builder) {
        this.requests = new ApiRequests(builder.baseUrl, builder.apiKey, builder.model);
        this.sender = new RetryingSender(
                builder.transport != null ? builder.transport : new JdkHttpTransport(builder.timeout),
                builder.maxRetries,
                builder.delays);
    }

    /**
     * A new builder with default settings.
     *
     * @return a fresh builder
     */
    public static JevClientBuilder builder() {
        return new JevClientBuilder();
    }

    /**
     * A client with default settings that reads its key from {@code TYPESAFE_API_KEY}.
     *
     * @return the client
     * @throws JevException if the variable is unset or blank
     */
    public static JevClient fromEnvironment() {
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
        String body = sender.send(requests.systemOne(state, questions));
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
        return sender.sendAsync(requests.systemOne(state, questions))
                .thenApply(body -> ResponseDecoder.decode(body, questions));
    }

    /**
     * The models and aliases this account can use. Pass a {@link ModelInfo#name()} as the
     * model. Retried and mapped to exceptions like {@link #systemOne(Object, Map)}.
     *
     * @return the available models, in the order the service lists them
     * @throws JevException on any network, HTTP, or response-format failure
     */
    public List<ModelInfo> models() {
        return ModelsDecoder.decode(sender.send(requests.models()));
    }

    /**
     * Asynchronous variant of {@link #models()}.
     *
     * @return a future completing with the available models, or exceptionally with the
     *     {@link JevException} that {@link #models()} would throw
     */
    public CompletableFuture<List<ModelInfo>> modelsAsync() {
        return sender.sendAsync(requests.models()).thenApply(ModelsDecoder::decode);
    }
}
