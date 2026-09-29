package io.github.cmaintz.jev;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Default {@link HttpTransport} backed by {@code java.net.http.HttpClient}. */
final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;
    private final Duration timeout;

    JdkHttpTransport(Duration timeout) {
        this.client = HttpClient.newHttpClient();
        this.timeout = timeout;
    }

    @Override
    public Response send(String url, Map<String, String> headers, byte[] body)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
