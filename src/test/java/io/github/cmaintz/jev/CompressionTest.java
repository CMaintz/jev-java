package io.github.cmaintz.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class CompressionTest {

    private static final Map<String, Question> ONE = Map.of("q", new Noul("Refund?"));
    private static final String LARGE_STATE = "x".repeat(4096);

    private static String gunzip(byte[] body) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void largeBodiesAreGzippedOnceAndResentAsIs() throws IOException {
        var transport = new StubTransport().enqueue(503, "{}");
        transport.client(1).systemOne(LARGE_STATE, ONE);

        assertEquals("gzip", transport.lastHeaders.get("Content-Encoding"));
        assertTrue(transport.lastBodyBytes.length < LARGE_STATE.length());
        var sent = (Map<?, ?>) JsonParser.parse(gunzip(transport.lastBodyBytes));
        assertEquals(LARGE_STATE, sent.get("state"));
        assertEquals(2, transport.calls);
    }

    @Test
    void smallBodiesAreSentPlain() {
        var transport = new StubTransport();
        transport.client(0).systemOne("short", ONE);

        assertFalse(transport.lastHeaders.containsKey("Content-Encoding"));
        assertEquals("short", ((Map<?, ?>) JsonParser.parse(transport.lastBody)).get("state"));
    }

    @Test
    void compressionCanBeTurnedOff() {
        var transport = new StubTransport();
        JevClient.builder()
                .apiKey("k")
                .transport(transport)
                .compressRequests(false)
                .build()
                .systemOne(LARGE_STATE, ONE);

        assertFalse(transport.lastHeaders.containsKey("Content-Encoding"));
        assertEquals(LARGE_STATE, ((Map<?, ?>) JsonParser.parse(transport.lastBody)).get("state"));
    }
}
