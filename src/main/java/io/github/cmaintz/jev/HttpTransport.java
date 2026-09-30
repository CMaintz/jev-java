package io.github.cmaintz.jev;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The HTTP seam behind {@link TypeSafeClient}. The default implementation wraps the JDK
 * HTTP client; tests inject a stub so they never touch the network. This interface
 * exists because {@code java.net.http.HttpClient} is final and cannot be mocked.
 */
public interface HttpTransport {

    /**
     * POST {@code body} to {@code url} with {@code headers} and return the raw response.
     *
     * @throws IOException on a transport failure
     * @throws InterruptedException if the calling thread is interrupted
     */
    Response send(String url, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;

    /**
     * Non-blocking variant of {@link #send}. The default runs {@code send} on a virtual
     * thread so a blocking transport never ties up a shared pool; override it when the
     * underlying client is natively asynchronous.
     */
    default CompletableFuture<Response> sendAsync(String url, Map<String, String> headers, byte[] body) {
        var future = new CompletableFuture<Response>();
        Thread.ofVirtual().start(() -> {
            try {
                future.complete(send(url, headers, body));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * A raw HTTP response.
     *
     * @param status HTTP status code
     * @param body response body as text
     */
    record Response(int status, String body) {}
}
