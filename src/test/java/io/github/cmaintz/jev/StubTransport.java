package io.github.cmaintz.jev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * A test transport that records the last request and replays a scripted queue of
 * responses. Injected via {@code JevClientBuilder.transport} so tests never touch the network.
 */
final class StubTransport implements HttpTransport {

    private final Deque<Response> responses = new ArrayDeque<>();
    private IOException failure;

    /** Every retry wait requested, in order, by either the blocking or the async path. */
    final List<Duration> waits = new CopyOnWriteArrayList<>();

    volatile int calls;
    volatile String lastMethod;
    volatile String lastUrl;
    volatile Map<String, String> lastHeaders;
    volatile String lastBody;
    volatile byte[] lastBodyBytes;

    StubTransport enqueue(int status, String body) {
        return enqueue(new Response(status, body));
    }

    StubTransport enqueue(Response response) {
        responses.add(response);
        return this;
    }

    StubTransport failWith(IOException e) {
        this.failure = e;
        return this;
    }

    /** A client on this transport whose retry waits are recorded instead of taken. */
    JevClient client(int maxRetries) {
        return JevClient.builder()
                .apiKey("test-key")
                .transport(this)
                .maxRetries(maxRetries)
                .delays(new RecordingDelays())
                .build();
    }

    @Override
    public synchronized Response send(Request request) throws IOException {
        calls++;
        lastMethod = request.method();
        lastUrl = request.url();
        lastHeaders = request.headers();
        lastBodyBytes = request.body();
        lastBody = request.body() == null ? null : new String(request.body(), StandardCharsets.UTF_8);
        if (failure != null) {
            throw failure;
        }
        return responses.isEmpty() ? new Response(200, "{\"answers\":{}}") : responses.poll();
    }

    /** Records each wait and returns at once; async retries run on the calling thread. */
    private final class RecordingDelays implements RetryingSender.Delays {

        @Override
        public void sleep(Duration duration) {
            waits.add(duration);
        }

        @Override
        public Executor after(Duration duration) {
            waits.add(duration);
            return Runnable::run;
        }
    }
}
