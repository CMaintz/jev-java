package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class RetryTest {

    private static final Map<String, Question> ONE = Map.of("q", new Noul("Refund?"));

    @Test
    void retriesA429ThenSucceedsWithBackoff() {
        var transport = new StubTransport().enqueue(429, "{}").enqueue(529, "{}");
        transport.client(3).systemOne("s", ONE);

        assertEquals(3, transport.calls);
        assertEquals(2, transport.waits.size());
        assertTrue(transport.waits.get(1).compareTo(Duration.ofMillis(1000)) >= 0);
    }

    @Test
    void retryAfterSecondsOverridesTheComputedDelay() {
        var transport = new StubTransport().enqueue(new HttpTransport.Response(429, "{}", Map.of("Retry-After", "4")));
        transport.client(1).systemOne("s", ONE);

        var slept = transport.waits.get(0);
        assertTrue(slept.compareTo(Duration.ofSeconds(4)) >= 0 && slept.compareTo(Duration.ofMillis(4250)) < 0);
    }

    @Test
    void delaysAreCapped() {
        var huge = new HttpTransport.Response(429, "{}", Map.of("retry-after", "3600"));
        assertEquals(Duration.ofSeconds(30), RetryingSender.delayFor(huge, 0));
        assertEquals(Duration.ofSeconds(30), RetryingSender.delayFor(new HttpTransport.Response(429, "{}"), 40));
    }

    @Test
    void exhaustedRetriesSurfaceAsTypedErrors() {
        var rateLimited = new StubTransport().enqueue(429, "{}").enqueue(429, "{}");
        assertThrows(JevRateLimitException.class, () -> rateLimited.client(1).systemOne("s", ONE));
        assertEquals(2, rateLimited.calls);

        var overloaded = new StubTransport().enqueue(529, "{}");
        assertThrows(JevOverloadedException.class, () -> overloaded.client(0).systemOne("s", ONE));
    }

    @Test
    void nonRetryableStatusesFailImmediately() {
        var unauthorized = new StubTransport().enqueue(401, "{}");
        assertThrows(JevAuthException.class, () -> unauthorized.client(3).systemOne("s", ONE));
        assertEquals(1, unauthorized.calls);

        var invalid = new StubTransport().enqueue(422, "{}");
        assertThrows(JevValidationException.class, () -> invalid.client(3).systemOne("s", ONE));

        var badRequest = new StubTransport().enqueue(400, "{\"detail\":\"max_tokens_exceeded\"}");
        var tooLong = assertThrows(
                JevBadRequestException.class, () -> badRequest.client(3).systemOne("s", ONE));
        assertEquals(1, badRequest.calls);
        assertEquals(400, tooLong.statusCode());

        var tooLarge = new StubTransport().enqueue(413, "{}");
        assertThrows(JevPayloadTooLargeException.class, () -> tooLarge.client(3).systemOne("s", ONE));
        assertEquals(1, tooLarge.calls);

        var forbidden = new StubTransport().enqueue(403, "nope");
        var error = assertThrows(JevException.class, () -> forbidden.client(3).systemOne("s", ONE));
        assertEquals(1, forbidden.calls);
        assertEquals(403, error.statusCode());
        assertEquals("nope", error.responseBody());
    }

    @Test
    void serverErrorsAndRequestTimeoutsAreRetried() {
        var transport =
                new StubTransport().enqueue(500, "{}").enqueue(503, "{}").enqueue(408, "{}");
        transport.client(3).systemOne("s", ONE);
        assertEquals(4, transport.calls);

        var down = new StubTransport().enqueue(502, "bad gateway").enqueue(502, "bad gateway");
        var error = assertThrows(JevServerException.class, () -> down.client(1).systemOne("s", ONE));
        assertEquals(2, down.calls);
        assertEquals(502, error.statusCode());
        assertEquals("bad gateway", error.responseBody());
    }

    @Test
    void retryAfterMillisecondsTakesPrecedence() {
        var response = new HttpTransport.Response(429, "{}", Map.of("retry-after-ms", "1500", "Retry-After", "9"));
        var delay = RetryingSender.delayFor(response, 0);

        assertTrue(
                delay.compareTo(Duration.ofMillis(1500)) >= 0 && delay.compareTo(Duration.ofMillis(1750)) < 0,
                delay::toString);
    }

    @Test
    void networkFailuresAreWrapped() {
        var transport = new StubTransport().failWith(new IOException("reset"));
        var error = assertThrows(JevException.class, () -> transport.client(3).systemOne("s", ONE));
        assertInstanceOf(IOException.class, error.getCause());
        assertEquals(0, error.statusCode());
    }

    @Test
    void interruptionDuringBackoffStopsRetrying() {
        var transport = new StubTransport().enqueue(429, "{}");
        var client = JevClient.builder()
                .apiKey("k")
                .transport(transport)
                .delays(new InterruptedDelays())
                .build();
        try {
            var error = assertThrows(JevException.class, () -> client.systemOne("s", ONE));
            assertInstanceOf(InterruptedException.class, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, transport.calls);
        } finally {
            Thread.interrupted(); // clear the flag for the next test
        }
    }

    /** Delays whose blocking wait is always interrupted. */
    private static final class InterruptedDelays implements RetryingSender.Delays {

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            throw new InterruptedException();
        }

        @Override
        public Executor after(Duration duration) {
            throw new UnsupportedOperationException("blocking path only");
        }
    }
}
