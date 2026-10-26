package io.github.cmaintz.jev;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Default {@link HttpTransport} backed by {@code java.net.http.HttpClient}. */
final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;
    private final Duration timeout;

    JdkHttpTransport(Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.timeout = timeout;
    }

    @Override
    public Response send(Request request) throws IOException, InterruptedException {
        return toResponse(client.send(jdkRequest(request), HttpResponse.BodyHandlers.ofString()));
    }

    @Override
    public CompletableFuture<Response> sendAsync(Request request) {
        return client.sendAsync(jdkRequest(request), HttpResponse.BodyHandlers.ofString())
                .thenApply(JdkHttpTransport::toResponse);
    }

    private HttpRequest jdkRequest(Request request) {
        var body = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.body());
        var builder = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(timeout)
                .method(request.method(), body);
        request.headers().forEach(builder::header);
        return builder.build();
    }

    private static Response toResponse(HttpResponse<String> response) {
        return new Response(response.statusCode(), response.body(), firstValues(response.headers()));
    }

    private static Map<String, String> firstValues(HttpHeaders headers) {
        var out = new HashMap<String, String>();
        headers.map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                out.put(name, values.get(0));
            }
        });
        return out;
    }
}
