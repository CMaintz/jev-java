package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncAndRetryTest {

    private static final Map<String, Question> ONE = Map.of("q", new Noul("Refund?"));
    private static final String NOUL_ANSWER = "{\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.9}}}";

    private static TypeSafeClient client(HttpTransport transport, int retries) {
        return TypeSafeClient.builder()
                .apiKey("k")
                .transport(transport)
                .maxRetries(retries)
                .build();
    }

    private static Throwable failureOf(CompletableFuture<?> future) {
        var e = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        return e.getCause();
    }

    @Test
    void asyncUsesTheTransportsNonBlockingSend() throws Exception {
        HttpTransport transport = new HttpTransport() {
            @Override
            public Response send(String url, Map<String, String> headers, byte[] body) {
                throw new AssertionError("the async path must not call the blocking send");
            }

            @Override
            public CompletableFuture<Response> sendAsync(String url, Map<String, String> headers, byte[] body) {
                return CompletableFuture.completedFuture(new Response(200, NOUL_ANSWER));
            }
        };

        var response = client(transport, 0).systemOneAsync("s", ONE).get(10, TimeUnit.SECONDS);

        assertEquals(0.9, response.get("q").noul());
    }

    @Test
    void asyncRetriesA429ThenSucceeds() throws Exception {
        var transport = new StubTransport().enqueue(429, "{}").enqueue(200, NOUL_ANSWER);

        var response = client(transport, 1).systemOneAsync("s", ONE).get(10, TimeUnit.SECONDS);

        assertEquals(0.9, response.get("q").noul());
        assertEquals(2, transport.calls);
    }

    @Test
    void asyncFailsWithTypedExceptionWhenRetriesAreExhausted() {
        var transport = new StubTransport().enqueue(529, "{}");

        var cause = failureOf(client(transport, 0).systemOneAsync("s", ONE));

        assertInstanceOf(JevOverloadedException.class, cause);
        assertEquals(1, transport.calls);
    }

    @Test
    void asyncWrapsNetworkErrors() {
        HttpTransport failing = (url, headers, body) -> {
            throw new IOException("connection reset");
        };

        var cause = failureOf(client(failing, 0).systemOneAsync("s", ONE));

        assertInstanceOf(JevException.class, cause);
        assertTrue(cause.getMessage().contains("connection reset"));
        assertInstanceOf(IOException.class, cause.getCause());
    }

    @Test
    void interruptDuringBackoffStopsRetryingAndKeepsTheFlag() {
        var transport = new StubTransport().enqueue(429, "{}").enqueue(200, NOUL_ANSWER);
        Thread.currentThread().interrupt();
        try {
            var e = assertThrows(JevException.class, () -> client(transport, 3).systemOne("s", ONE));

            assertInstanceOf(InterruptedException.class, e.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, transport.calls);
        } finally {
            Thread.interrupted();
        }
    }
}
