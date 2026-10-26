package io.github.cmaintz.jev;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

/**
 * Request-body compression. The API accepts gzip or zstd; gzip is built into the JDK, so
 * the SDK stays dependency-free. Small bodies are left alone, since compressing them
 * saves little and costs a round of CPU on both ends.
 */
final class Gzip {

    /** Bodies at least this large are compressed, matching TypeSafe's own SDKs. */
    static final int MIN_BYTES = 1024;

    private Gzip() {}

    static byte[] compress(byte[] body) {
        var out = new ByteArrayOutputStream(body.length / 2 + 64);
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // in-memory streams do not fail
        }
        return out.toByteArray();
    }
}
