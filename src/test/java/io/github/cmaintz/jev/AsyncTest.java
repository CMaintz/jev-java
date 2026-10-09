package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncTest {

    private static final Map<String, Question> ONE = Map.of("q", new Noul("Refund?"));
    private static final String NOUL_ANSWER = "{\"answers\":{\"q\":{\"noul\":0.9}}}";

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
        var client = TypeSafeClient.builder().apiKey("k").transport(transport).build();

        var response = client.systemOneAsync("s", ONE).get(10, TimeUnit.SECONDS);

        assertEquals(0.9, response.noul("q").probability());
    }

    @Test
    void asyncRetriesA429ThenSucceeds() throws Exception {
        var transport = new StubTransport().enqueue(429, "{}").enqueue(200, NOUL_ANSWER);

        var response = transport.client(1).systemOneAsync("s", ONE).get(10, TimeUnit.SECONDS);

        assertEquals(0.9, response.noul("q").probability());
        assertEquals(2, transport.calls);
        assertEquals(1, transport.waits.size());
    }

    @Test
    void asyncFailsWithTypedExceptionWhenRetriesAreExhausted() {
        var transport = new StubTransport().enqueue(529, "{}");

        var cause = failureOf(transport.client(0).systemOneAsync("s", ONE));

        assertInstanceOf(JevOverloadedException.class, cause);
        assertEquals(1, transport.calls);
    }

    @Test
    void asyncWrapsNetworkErrors() {
        var transport = new StubTransport().failWith(new IOException("connection reset"));

        var cause = failureOf(transport.client(0).systemOneAsync("s", ONE));

        assertInstanceOf(JevException.class, cause);
        assertTrue(cause.getMessage().contains("connection reset"));
        assertInstanceOf(IOException.class, cause.getCause());
    }

    @Test
    void asyncSurfacesMalformedBodiesAsJevException() {
        var transport = new StubTransport().enqueue(200, null);

        var cause = failureOf(transport.client(0).systemOneAsync("s", ONE));

        assertInstanceOf(JevException.class, cause);
        assertEquals("", ((JevException) cause).responseBody());
    }

    @Test
    void bothPathsWrapTheSameTransportFailureTheSameWay() {
        HttpTransport unchecked = (url, headers, body) -> {
            throw new UncheckedIOException(new IOException("reset"));
        };
        var client = TypeSafeClient.builder().apiKey("k").transport(unchecked).build();

        var blocking = assertThrows(JevException.class, () -> client.systemOne("s", ONE));
        var async = failureOf(client.systemOneAsync("s", ONE));

        assertInstanceOf(JevException.class, async);
        assertEquals(blocking.getMessage(), async.getMessage());
    }

    @Test
    void defaultSendAsyncCompletesEvenWhenSendThrowsAnError() {
        HttpTransport broken = (url, headers, body) -> {
            throw new AssertionError("boom");
        };
        var client = TypeSafeClient.builder().apiKey("k").transport(broken).build();

        var cause = failureOf(client.systemOneAsync("s", ONE));

        assertInstanceOf(AssertionError.class, cause.getCause());
    }

    @Test
    void asyncRejectsInvalidArgumentsImmediately() {
        var client = new StubTransport().client(0);
        assertThrows(IllegalArgumentException.class, () -> client.systemOneAsync("s", Map.of()));
    }
}
