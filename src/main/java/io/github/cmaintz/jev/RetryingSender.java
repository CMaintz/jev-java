package io.github.cmaintz.jev;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Sends a request body through an {@link HttpTransport}, retrying {@code 429} and
 * {@code 529} with exponential backoff plus jitter. A {@code Retry-After} header given
 * in seconds takes precedence over the computed delay. Every delay is capped. The
 * blocking path sleeps between attempts; the async path schedules the next attempt
 * without holding a thread.
 */
final class RetryingSender {

    /** How the two paths wait between attempts; swapped out in tests so they never really wait. */
    interface Delays {

        /** Blocks the calling thread for {@code duration}. */
        void sleep(Duration duration) throws InterruptedException;

        /** An executor that runs its task once {@code duration} has passed. */
        Executor after(Duration duration);
    }

    static final Delays REAL_DELAYS = new Delays() {
        @Override
        public void sleep(Duration duration) throws InterruptedException {
            Thread.sleep(duration);
        }

        @Override
        public Executor after(Duration duration) {
            return CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS);
        }
    };

    private static final Set<Integer> RETRYABLE = Set.of(429, 529);
    private static final Duration BASE_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_DELAY = Duration.ofSeconds(30);
    private static final long MAX_JITTER_MILLIS = 250;

    private final HttpTransport transport;
    private final String url;
    private final Map<String, String> headers;
    private final int maxRetries;
    private final Delays delays;

    RetryingSender(HttpTransport transport, String url, Map<String, String> headers, int maxRetries, Delays delays) {
        this.transport = transport;
        this.url = url;
        this.headers = Map.copyOf(headers);
        this.maxRetries = maxRetries;
        this.delays = delays;
    }

    /** The body of the first 2xx response; otherwise the typed {@link JevException}. */
    String send(byte[] body) {
        for (int attempt = 0; ; attempt++) {
            HttpTransport.Response response = attempt(body);
            if (response.isSuccess()) {
                return response.body();
            }
            if (!shouldRetry(response, attempt)) {
                throw JevException.forStatus(response.status(), response.body());
            }
            pause(delayFor(response, attempt));
        }
    }

    /** Non-blocking {@link #send}: the future fails with the same typed {@link JevException}. */
    CompletableFuture<String> sendAsync(byte[] body) {
        return attemptAsync(body, 0);
    }

    private CompletableFuture<String> attemptAsync(byte[] body, int attempt) {
        return startAsync(body)
                .exceptionallyCompose(e -> CompletableFuture.failedFuture(transportError(e)))
                .thenCompose(response -> afterAsync(response, body, attempt));
    }

    /** The transport's future, with a synchronous throw from a custom transport folded into it. */
    private CompletableFuture<HttpTransport.Response> startAsync(byte[] body) {
        try {
            return transport.sendAsync(url, headers, body);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> afterAsync(HttpTransport.Response response, byte[] body, int attempt) {
        if (response.isSuccess()) {
            return CompletableFuture.completedFuture(response.body());
        }
        if (!shouldRetry(response, attempt)) {
            return CompletableFuture.failedFuture(JevException.forStatus(response.status(), response.body()));
        }
        Executor later = delays.after(delayFor(response, attempt));
        return CompletableFuture.runAsync(() -> {}, later).thenCompose(ignored -> attemptAsync(body, attempt + 1));
    }

    private boolean shouldRetry(HttpTransport.Response response, int attempt) {
        return RETRYABLE.contains(response.status()) && attempt < maxRetries;
    }

    private HttpTransport.Response attempt(byte[] body) {
        try {
            return transport.send(url, headers, body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw transportError(e);
        } catch (IOException | RuntimeException e) {
            throw transportError(e);
        }
    }

    /**
     * The one rule both paths use for a transport that failed instead of answering: an
     * interruption, or anything else as a network error. A {@link JevException} passes through.
     */
    private static JevException transportError(Throwable e) {
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        return switch (cause) {
            case JevException jev -> jev;
            case InterruptedException interrupted -> new JevException(
                    "Interrupted while calling Jev.", 0, null, interrupted);
            default -> new JevException("Network error calling Jev: " + cause.getMessage(), 0, null, cause);
        };
    }

    static Duration delayFor(HttpTransport.Response response, int attempt) {
        Duration delay = retryAfter(response).orElseGet(() -> BASE_DELAY.multipliedBy(1L << Math.min(attempt, 16)));
        Duration jittered = delay.plusMillis(ThreadLocalRandom.current().nextLong(MAX_JITTER_MILLIS));
        return jittered.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : jittered;
    }

    private static Optional<Duration> retryAfter(HttpTransport.Response response) {
        return response.header("Retry-After").flatMap(RetryingSender::parseSeconds);
    }

    private static Optional<Duration> parseSeconds(String value) {
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty(); // an HTTP-date; fall back to the computed backoff
        }
    }

    private void pause(Duration delay) {
        try {
            delays.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw transportError(e);
        }
    }
}
