package io.github.cmaintz.jev;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * A test transport that records the last request and replays a scripted queue of
 * responses. Injected via {@code Builder.transport} so tests never touch the network.
 */
final class StubTransport implements HttpTransport {

    private final Deque<Response> responses = new ArrayDeque<>();

    int calls;
    String lastUrl;
    Map<String, String> lastHeaders;
    String lastBody;

    StubTransport enqueue(int status, String body) {
        responses.add(new Response(status, body));
        return this;
    }

    @Override
    public Response send(String url, Map<String, String> headers, byte[] body) {
        calls++;
        lastUrl = url;
        lastHeaders = headers;
        lastBody = new String(body, StandardCharsets.UTF_8);
        return responses.isEmpty() ? new Response(200, "{\"answers\":{}}") : responses.poll();
    }
}
