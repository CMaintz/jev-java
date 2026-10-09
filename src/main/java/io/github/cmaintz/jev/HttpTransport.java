package io.github.cmaintz.jev;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
     * @param url the absolute endpoint
     * @param headers request headers to set
     * @param body the request body
     * @return the raw response, whatever its status
     * @throws IOException on a transport failure
     * @throws InterruptedException if the calling thread is interrupted
     */
    Response send(String url, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;

    /**
     * Non-blocking variant of {@link #send}. The default runs {@code send} on a virtual
     * thread so a blocking transport never ties up a shared pool; override it when the
     * underlying client is natively asynchronous.
     *
     * @param url as for {@link #send}
     * @param headers as for {@link #send}
     * @param body as for {@link #send}
     * @return a future completing with the raw response, or exceptionally on a transport failure
     */
    default CompletableFuture<Response> sendAsync(String url, Map<String, String> headers, byte[] body) {
        var future = new CompletableFuture<Response>();
        Thread.ofVirtual().start(() -> {
            try {
                future.complete(send(url, headers, body));
            } catch (Throwable e) { // even an Error must complete the future, or the caller hangs
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * A raw HTTP response.
     *
     * @param status HTTP status code
     * @param body response body as text; null is stored as the empty string
     * @param headers response headers (first value per name), names lower-cased; null
     *     names or values are dropped
     */
    record Response(int status, String body, Map<String, String> headers) {

        /** Normalizes the body and headers. */
        public Response {
            body = body == null ? "" : body;
            headers = normalize(headers);
        }

        /**
         * A response without headers.
         *
         * @param status HTTP status code
         * @param body response body as text
         */
        public Response(int status, String body) {
            this(status, body, Map.of());
        }

        /**
         * True for a 2xx status.
         *
         * @return whether the request succeeded
         */
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        /**
         * The value of header {@code name}, matched case-insensitively.
         *
         * @param name the header name
         * @return the first value, or empty when absent
         */
        public Optional<String> header(String name) {
            return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
        }

        private static Map<String, String> normalize(Map<String, String> headers) {
            var out = new HashMap<String, String>();
            if (headers != null) {
                headers.forEach((name, value) -> {
                    if (name != null && value != null) {
                        out.put(name.toLowerCase(Locale.ROOT), value);
                    }
                });
            }
            return Map.copyOf(out);
        }
    }
}
