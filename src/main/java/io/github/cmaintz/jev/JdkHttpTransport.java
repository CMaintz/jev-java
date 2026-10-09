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
    public Response send(String url, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException {
        return toResponse(client.send(request(url, headers, body), HttpResponse.BodyHandlers.ofString()));
    }

    @Override
    public CompletableFuture<Response> sendAsync(String url, Map<String, String> headers, byte[] body) {
        return client.sendAsync(request(url, headers, body), HttpResponse.BodyHandlers.ofString())
                .thenApply(JdkHttpTransport::toResponse);
    }

    private HttpRequest request(String url, Map<String, String> headers, byte[] body) {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
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
